"""
SAR-enhanced DINOv2 feature extractor.

This extractor integrates SAR (Sharpness-Aware and Reliable entropy minimization)
algorithm for test-time adaptation, improving robustness to noisy images.
"""

import torch
import torch.nn.functional as F
from torchvision import transforms
from PIL import Image
from typing import Union, Optional
import numpy as np
from pathlib import Path

from .sar_adapter import SARAdapter, configure_model, collect_params
from app.config import Config


class SARDINOv2Extractor:
    def __init__(self, model_path: str, device: str = None, sar_steps: int = 1,
                 landmark_means=None, landmark_cov_invs=None, num_classes: int = 1000):
        """
        Initialize SAR-enhanced DINOv2 extractor.
        
        Args:
            model_path: Path to DINOv2 model weights
            device: Device to use (auto, cpu, cuda)
            sar_steps: Number of SAR adaptation steps per extraction
            landmark_means: List of landmark mean vectors for Mahalanobis entropy
            landmark_cov_invs: List of landmark covariance inverse matrices
            num_classes: Number of classes for entropy margin calculation
        """
        # Device configuration
        if device == "auto" or device is None:
            self.device = "cuda" if torch.cuda.is_available() else "cpu"
        else:
            self.device = device
        
        print(f"[INFO] Loading SAR-enhanced DINOv2 model from: {model_path}")
        print(f"[INFO] Using device: {self.device}")
        print(f"[INFO] SAR adaptation steps: {sar_steps}")
        print(f"[INFO] Number of classes for entropy margin: {num_classes}")
        
        # Store number of classes for dynamic margin calculation
        self.num_classes = num_classes
        
        # Store landmark statistics for Mahalanobis entropy
        self.landmark_means = landmark_means
        self.landmark_cov_invs = landmark_cov_invs
        if landmark_means is not None and landmark_cov_invs is not None:
            print(f"[INFO] Using Mahalanobis distance-based entropy with {len(landmark_means)} landmarks")
        
        # Load base model
        self.base_model = self._load_local_model(model_path)
        
        # SAR configuration
        self.sar_steps = sar_steps
        self._configure_sar()
        
        # Image preprocessing
        self.transform = transforms.Compose([
            transforms.Resize(518),
            transforms.CenterCrop(518),
            transforms.ToTensor(),
            transforms.Normalize(
                mean=[0.485, 0.456, 0.406],
                std=[0.229, 0.224, 0.225]
            ),
        ])
        
        # Get feature dimension
        self.feature_dim = self._get_feature_dimension()
        print(f"[OK] SAR-DINOv2 model loaded successfully. Feature dimension: {self.feature_dim}")
    
    def _load_local_model(self, model_path: str):
        """Load DINOv2 model from local file."""
        model_file = Path(model_path)
        
        if not model_file.exists():
            raise FileNotFoundError(
                f"[ERROR] Model file not found: {model_path}\n"
                f"Current working directory: {Path.cwd()}"
            )
        
        file_size_mb = model_file.stat().st_size / (1024 * 1024)
        print(f"[INFO] Model file size: {file_size_mb:.2f} MB")
        
        try:
            model = torch.load(model_path, map_location=self.device, weights_only=False)
            
            if hasattr(model, 'eval') and callable(getattr(model, 'forward_features', None)):
                print("[OK] Loaded full DINOv2 model object")
                return model
            
            if isinstance(model, dict):
                return self._build_model_from_dict(model)
            
            raise ValueError(f"Unsupported model format: {type(model)}")
            
        except Exception as e:
            print(f"[ERROR] Model loading failed: {e}")
            raise
    
    def _build_model_from_dict(self, model_dict: dict):
        """Build model from state dict."""
        print("[INFO] Building model from dict...")
        
        state_dict = None
        if 'state_dict' in model_dict:
            state_dict = model_dict['state_dict']
        elif 'model_state_dict' in model_dict:
            state_dict = model_dict['model_state_dict']
        else:
            state_dict = model_dict
        
        try:
            from app.models.dinov2_vit import create_dinov2_vitb14
            
            model = create_dinov2_vitb14()
            missing_keys, unexpected_keys = model.load_state_dict(state_dict, strict=False)
            
            if missing_keys:
                print(f"[WARN] Missing keys: {len(missing_keys)}")
            if unexpected_keys:
                print(f"[WARN] Unexpected keys: {len(unexpected_keys)}")
            
            print("[OK] Successfully loaded DINOv2 ViT-B/14 model")
            return model
            
        except Exception as e:
            print(f"[ERROR] Model building failed: {e}")
            import traceback
            traceback.print_exc()
            raise
    
    def _configure_sar(self):
        """Configure SAR adapter for test-time adaptation."""
        # Configure model for SAR (train mode, enable norm layer gradients)
        self.model = configure_model(self.base_model)
        
        # Collect parameters to adapt (normalization layers only)
        params, names = collect_params(self.model)
        print(f"[INFO] SAR adaptation parameters: {len(names)} parameters")
        
        # Create optimizer
        try:
            from app.utils.sam_optimizer import SAM
            from torch.optim import Adam
            
            # Use SAM optimizer with Adam as base
            base_optimizer = Adam
            self.optimizer = SAM(params, base_optimizer, lr=1e-4, rho=0.05)
            print("[OK] SAM optimizer configured")
        except ImportError:
            # Fallback to Adam if SAM is not available
            from torch.optim import Adam
            self.optimizer = Adam(params, lr=1e-4)
            print("[WARN] SAM optimizer not available, using Adam")
        
        # Calculate dynamic margin based on number of classes
        # margin = 0.4 * log(num_classes)
        # This ensures the threshold adapts to the number of landmarks
        margin_e0 = Config.SAR_MARGIN
        print(f"[INFO] SAR entropy margin: {margin_e0:.4f} (based on {self.num_classes} classes)")
        
        # Create SAR adapter with landmark statistics
        self.sar_model = SARAdapter(
            self.model,
            self.optimizer,
            steps=self.sar_steps,
            margin_e0=margin_e0,
            reset_constant_em=0.2,
            landmark_means=self.landmark_means,
            landmark_cov_invs=self.landmark_cov_invs
        )
        
        self.sar_model.to(self.device)
        print("[OK] SAR adapter configured")
    
    def _get_feature_dimension(self) -> int:
        """Get feature dimension by testing with dummy input."""
        dummy_input = torch.randn(1, 3, 518, 518).to(self.device)
        with torch.no_grad():
            features = self._extract_features(dummy_input)
            if isinstance(features, dict):
                return features['x_norm_clstoken'].shape[-1]
            return features.shape[-1]
    
    def _extract_features(self, input_tensor):
        """Extract features from model."""
        try:
            features = self.model.forward_features(input_tensor)
        except AttributeError:
            features = self.model(input_tensor)
        return features
    
    def extract_single(self, image: Union[str, Image.Image], 
                      use_sar: bool = True, 
                      label: Optional[int] = None,
                      label_confidence: Optional[float] = None,
                      landmark_means=None, landmark_cov_invs=None) -> np.ndarray:
        """
        Extract feature from a single image.
        
        Args:
            image: Input image (path or PIL Image)
            use_sar: Whether to apply SAR adaptation
            label: Optional label for label-guided adaptation
            label_confidence: Confidence of the label (0-1)
            landmark_means: Override landmark means for this extraction
            landmark_cov_invs: Override landmark cov inverses for this extraction
        
        Returns:
            feature_vector: 768-dimensional feature vector
        """
        if isinstance(image, str):
            image = Image.open(image).convert('RGB')
        
        input_tensor = self.transform(image).unsqueeze(0).to(self.device)
        
        if use_sar:
            # SAR adaptation
            with torch.enable_grad():
                # Use provided statistics or fall back to stored ones
                means = landmark_means if landmark_means is not None else self.landmark_means
                cov_invs = landmark_cov_invs if landmark_cov_invs is not None else self.landmark_cov_invs
                features = self.sar_model(input_tensor, label, label_confidence, means, cov_invs)
                
                if features is None:
                    raise ValueError("SAR model returned None")
                
                if isinstance(features, dict):
                    feature_vector = features.get('x_norm_clstoken')
                    if feature_vector is None:
                        last_hidden = features.get('last_hidden_state')
                        if last_hidden is not None:
                            feature_vector = last_hidden[:, 0]
                        else:
                            # Try to get the first available tensor
                            for key, val in features.items():
                                if isinstance(val, torch.Tensor):
                                    feature_vector = val
                                    if len(feature_vector.shape) == 3:
                                        feature_vector = feature_vector[:, 0]
                                    break
                    if feature_vector is None:
                        raise ValueError("No valid feature tensor found in SAR model output")
                elif len(features.shape) == 4:
                    feature_vector = features[:, 0]
                else:
                    feature_vector = features
        else:
            # Normal extraction without SAR
            with torch.no_grad():
                features = self._extract_features(input_tensor)
                
                if isinstance(features, dict):
                    feature_vector = features['x_norm_clstoken']
                else:
                    if len(features.shape) == 3:
                        feature_vector = features[:, 0]
                    else:
                        feature_vector = features
        
        feature_vector = F.normalize(feature_vector, p=2, dim=-1)
        # Detach if requires grad (for SAR mode)
        if feature_vector.requires_grad:
            feature_vector = feature_vector.detach()
        return feature_vector.cpu().numpy().flatten()
    
    def extract_batch(self, images: list, use_sar: bool = True) -> np.ndarray:
        """
        Extract features from a batch of images.
        
        Args:
            images: List of images (paths or PIL Images)
            use_sar: Whether to apply SAR adaptation
        
        Returns:
            feature_vectors: Batch of feature vectors
        """
        tensors = []
        for img in images:
            if isinstance(img, str):
                img = Image.open(img).convert('RGB')
            tensors.append(self.transform(img))
        
        batch_tensor = torch.stack(tensors).to(self.device)
        
        if use_sar:
            with torch.enable_grad():
                features = self.sar_model(batch_tensor)
                
                if isinstance(features, dict):
                    feature_vectors = features.get('x_norm_clstoken', features.get('last_hidden_state')[:, 0])
                elif len(features.shape) == 4:
                    feature_vectors = features[:, 0]
                else:
                    feature_vectors = features
        else:
            with torch.no_grad():
                features = self._extract_features(batch_tensor)
                
                if isinstance(features, dict):
                    feature_vectors = features['x_norm_clstoken']
                else:
                    if len(features.shape) == 3:
                        feature_vectors = features[:, 0]
                    else:
                        feature_vectors = features
        
        feature_vectors = F.normalize(feature_vectors, p=2, dim=-1)
        return feature_vectors.cpu().numpy()
    
    def reset_sar(self):
        """Reset SAR adapter to initial state."""
        self.sar_model.reset()
        print("[OK] SAR model reset")
    
    def get_sar_ema(self):
        """Get current EMA value of SAR adapter."""
        return self.sar_model.get_ema()
    
    def set_sar_steps(self, steps: int):
        """Set number of SAR adaptation steps."""
        self.sar_steps = steps
        self.sar_model.set_steps(steps)
    
    def set_sar_margin(self, margin: float):
        """Set entropy margin for reliable sample filtering."""
        self.sar_model.set_margin(margin)