import torch
import torch.nn.functional as F
from torchvision import transforms
from PIL import Image
from typing import Union
import numpy as np
from pathlib import Path


class DINOv2Extractor:
    def __init__(self, model_path: str, device: str = None):
        # 自动检测设备或使用指定设备
        if device == "auto" or device is None:
            self.device = "cuda" if torch.cuda.is_available() else "cpu"
        else:
            self.device = device
        
        print(f"Loading DINOv2 model from: {model_path}")
        print(f"Using device: {self.device}")
        
        self.model = self._load_local_model(model_path)
        self.model.to(self.device)
        self.model.eval()
        
        self.transform = transforms.Compose([
            transforms.Resize(518),  # DINOv2 使用 518x518
            transforms.CenterCrop(518),
            transforms.ToTensor(),
            transforms.Normalize(
                mean=[0.485, 0.456, 0.406],
                std=[0.229, 0.224, 0.225]
            ),
        ])
        
        self.feature_dim = self.get_feature_dimension()
        print(f"DINOv2 model loaded successfully. Feature dimension: {self.feature_dim}")
    
    def _load_local_model(self, model_path: str):
        model_file = Path(model_path)
        
        if not model_file.exists():
            raise FileNotFoundError(
                f"❌ 模型文件不存在: {model_path}\n"
                f"请确保模型文件已放置在正确位置。\n"
                f"当前工作目录: {Path.cwd()}"
            )
        
        file_size_mb = model_file.stat().st_size / (1024 * 1024)
        print(f"📦 模型文件大小: {file_size_mb:.2f} MB")
        
        try:
            model = torch.load(model_path, map_location=self.device, weights_only=False)
            
            if hasattr(model, 'eval') and callable(getattr(model, 'forward_features', None)):
                print("✅ 加载完整的 DINOv2 模型对象")
                return model
            
            if isinstance(model, dict):
                return self._build_model_from_dict(model)
            
            raise ValueError(f"不支持的模型格式: {type(model)}")
            
        except Exception as e:
            print(f"❌ 模型加载失败: {e}")
            raise
    
    def _build_model_from_dict(self, model_dict: dict):
        print("🔧 从字典构建模型...")
        
        state_dict = None
        if 'state_dict' in model_dict:
            state_dict = model_dict['state_dict']
            print("  - 使用 'state_dict' 键")
        elif 'model_state_dict' in model_dict:
            state_dict = model_dict['model_state_dict']
            print("  - 使用 'model_state_dict' 键")
        else:
            state_dict = model_dict
            print("  - 直接使用字典作为 state_dict")
        
        try:
            # 使用纯 PyTorch 实现的 DINOv2 模型
            from app.models.dinov2_vit import create_dinov2_vitb14
            
            model = create_dinov2_vitb14()
            
            # 直接加载权重（键名完全匹配）
            missing_keys, unexpected_keys = model.load_state_dict(state_dict, strict=False)
            
            if missing_keys:
                print(f"  ⚠️  缺少的键: {len(missing_keys)} 个")
            if unexpected_keys:
                print(f"  ⚠️  多余的键: {len(unexpected_keys)} 个")
            
            print("✅ 成功加载 DINOv2 ViT-B/14 模型（纯 PyTorch 实现）")
            return model
            
        except Exception as e:
            print(f"❌ 模型构建失败: {e}")
            import traceback
            traceback.print_exc()
            raise
    
    def get_feature_dimension(self) -> int:
        # 使用 518x518 作为测试输入（DINOv2 默认尺寸）
        dummy_input = torch.randn(1, 3, 518, 518).to(self.device)
        with torch.no_grad():
            features = self._extract_features(dummy_input)
            if isinstance(features, dict):
                return features['x_norm_clstoken'].shape[-1]
            return features.shape[-1]
    
    def _extract_features(self, input_tensor):
        try:
            features = self.model.forward_features(input_tensor)
        except AttributeError:
            features = self.model(input_tensor)
        return features
    
    def extract_single(self, image: Union[str, Image.Image]) -> np.ndarray:
        if isinstance(image, str):
            image = Image.open(image).convert('RGB')
        
        input_tensor = self.transform(image).unsqueeze(0).to(self.device)
        
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
        
        return feature_vector.cpu().numpy().flatten()
    
    def extract_batch(self, images: list) -> np.ndarray:
        tensors = []
        for img in images:
            if isinstance(img, str):
                img = Image.open(img).convert('RGB')
            tensors.append(self.transform(img))
        
        batch_tensor = torch.stack(tensors).to(self.device)
        
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
