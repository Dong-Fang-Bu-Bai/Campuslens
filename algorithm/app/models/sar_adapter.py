"""
SAR (Sharpness-Aware and Reliable entropy minimization) adapter for DINOv2
Reference: https://arxiv.org/abs/2302.03011 (ICLR 2023 Oral)

This adapter wraps a model and performs test-time adaptation using SAR algorithm.
"""

from copy import deepcopy
import math
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
def update_ema(ema, new_data):
    """Update exponential moving average"""
    if ema is None:
        return new_data
    else:
        with torch.no_grad():
            return 0.9 * ema + (1 - 0.9) * new_data


def normalized_softmax_entropy(x: torch.Tensor, temperature: float = 1.0) -> torch.Tensor:
    """
    Normalized entropy of a score distribution.

    - x is treated as retrieval scores / logits-like values
    - temperature controls sharpness
    - output is normalized to [0, 1]
    """
    if x.dim() != 2:
        raise RuntimeError("normalized_softmax_entropy expects a 2D tensor")

    temp = temperature if temperature > 1e-6 else 1e-6
    logits = x / temp
    logits = logits - logits.max(dim=1, keepdim=True).values
    probs = torch.softmax(logits, dim=1)

    entropy = -(probs * torch.log(probs + 1e-12)).sum(dim=1)
    norm = math.log(float(x.size(1))) if x.size(1) > 1 else 1.0
    return entropy / norm


def get_entropy_from_features(outputs, landmark_means=None, landmark_cov_invs=None):
    """
    Compute entropy from model outputs, handling both tensor and dict outputs.
    
    If landmark statistics are provided, uses Mahalanobis distance-based entropy.
    Otherwise, falls back to feature-based entropy.
    
    Args:
        outputs: Model outputs (tensor or dict)
        landmark_means: List of landmark mean vectors (numpy arrays)
        landmark_cov_invs: List of landmark covariance inverse matrices (numpy arrays)
    
    Returns:
        entropy: Entropy values (batch_size,)
    """
    if isinstance(outputs, dict):
        # DINOv2 returns dict with keys like 'x_norm_clstoken', 'x_norm_patchtokens'
        # Use the normalized cls token feature for entropy calculation
        x = outputs.get('x_norm_clstoken')
        if x is None:
            last_hidden = outputs.get('last_hidden_state')
            if last_hidden is not None:
                x = last_hidden[:, 0]  # Get CLS token
            else:
                # Try to get the first available tensor
                for key, val in outputs.items():
                    if isinstance(val, torch.Tensor):
                        x = val
                        if len(x.shape) == 3:
                            x = x[:, 0]  # Get CLS token for sequence outputs
                        break
            if x is None:
                raise ValueError("No valid tensor found in model output dict")
    elif isinstance(outputs, torch.Tensor):
        x = outputs
    else:
        raise ValueError(f"Unexpected output type: {type(outputs)}")
    
    # Ensure x is 2D (batch_size, feature_dim)
    if len(x.shape) == 3:
        x = x[:, 0]  # Use CLS token

    # Align SAR entropy features with retrieval-side features.
    x = F.normalize(x, p=2, dim=-1)
    
    # If landmark statistics are provided, use Mahalanobis distance-based entropy
    if landmark_means is not None and landmark_cov_invs is not None and len(landmark_means) > 0:
        return compute_mahalanobis_entropy(x, landmark_means, landmark_cov_invs, top_k=3, temperature=0.2)

    # Fallback: original entropy calculation (treating features as logits)
    return normalized_softmax_entropy(x)


def compute_mahalanobis_entropy(x, landmark_means, landmark_cov_invs, top_k: int = 3, temperature: float = 0.2):
    """
    Compute normalized entropy based on Mahalanobis distance match scores.

    This version is aligned with the current retrieval-side entropy logic:
    1. Mahalanobis distance -> match score
    2. Top-k masking
    3. Temperature softmax over scores
    4. Normalized entropy in [0, 1]
    """
    batch_size = x.shape[0]
    num_landmarks = len(landmark_means)

    device = x.device
    means = [torch.tensor(m, dtype=x.dtype, device=device) for m in landmark_means]
    cov_invs = [torch.tensor(ci, dtype=x.dtype, device=device) for ci in landmark_cov_invs]

    mahalanobis_dists = torch.zeros((batch_size, num_landmarks), dtype=x.dtype, device=device)

    for i in range(batch_size):
        for j in range(num_landmarks):
            diff = x[i] - means[j]
            dist_sq = diff @ cov_invs[j] @ diff
            mahalanobis_dists[i, j] = torch.sqrt(torch.clamp(dist_sq, min=0.0))

    if batch_size > 0:
        print(f"[DEBUG] Mahalanobis distances: {[f'{d:.2f}' for d in mahalanobis_dists[0].tolist()]}")

    CENTER = 901.0
    SLOPE = 3.0
    log_dist = torch.log(mahalanobis_dists + 1.0)
    log_center = math.log(CENTER + 1.0)

    match_scores = 1.0 / (1.0 + torch.exp(SLOPE * (log_dist - log_center)))

    if batch_size > 0:
        print(f"[DEBUG] Match scores: {[f'{s:.4f}' for s in match_scores[0].tolist()]}")

    k = min(top_k, num_landmarks)
    masked_scores = torch.zeros_like(match_scores)

    for i in range(batch_size):
        topk_values, topk_indices = match_scores[i].topk(k)
        masked_scores[i, topk_indices] = match_scores[i, topk_indices]

    if batch_size > 0:
        non_zero_count = (masked_scores[0] != 0).sum().item()
        print(f"[DEBUG] After top-{k} masking: {non_zero_count} non-zero scores")
        print(f"[DEBUG] Masked scores: {[f'{l:.4f}' if l != 0 else '0' for l in masked_scores[0].tolist()]}")

    entropy = normalized_softmax_entropy(masked_scores, temperature=temperature)
    if batch_size > 0:
        print(f"[DEBUG] Normalized entropy: {entropy[0].item():.6f}")

    return entropy


@torch.enable_grad()
def forward_and_adapt_sar(x, model, optimizer, margin, reset_constant, ema, 
                          labels=None, label_confidence=None, 
                          landmark_means=None, landmark_cov_invs=None):
    """
    Forward and adapt model with SAR algorithm.
    
    Args:
        x: Input tensor
        model: Model to adapt
        optimizer: SAM optimizer
        margin: Entropy threshold for reliable sample filtering
        reset_constant: EMA threshold for model recovery
        ema: Exponential moving average of loss
        labels: Optional labels for label-guided adaptation
        label_confidence: Confidence of labels (0-1)
        landmark_means: List of landmark mean vectors for Mahalanobis entropy
        landmark_cov_invs: List of landmark covariance inverse matrices
    
    Returns:
        outputs: Model outputs
        ema: Updated EMA
        reset_flag: Whether to reset the model
    """
    optimizer.zero_grad()
    
    # First forward pass
    outputs = model(x)
    
    # Check if outputs is None
    if outputs is None:
        raise ValueError("Model forward pass returned None")
    
    # Compute entropy using helper function that handles dict outputs
    if labels is not None and label_confidence is not None and label_confidence > 0.3:
        # Label-guided entropy
        h_label = label_guided_entropy(outputs, labels, landmark_means, landmark_cov_invs)
        h_original = get_entropy_from_features(outputs, landmark_means, landmark_cov_invs)
        effective_weight = 0.5 * label_confidence  # Mix weight based on confidence
        h_fused = effective_weight * h_label + (1 - effective_weight) * h_original
    else:
        # Original entropy (no label or low confidence)
        h_fused = get_entropy_from_features(outputs, landmark_means, landmark_cov_invs)
    
    # Filter reliable samples
    filter_ids_1 = torch.where(h_fused < margin)
    h_filtered = h_fused[filter_ids_1]
    
    if len(h_filtered) == 0:
        # No reliable samples, skip update
        return outputs, ema, False
    
    # First loss and backward
    loss = h_filtered.mean(0)
    loss.backward()
    
    # SAM first step: parameter perturbation
    optimizer.first_step(zero_grad=True)
    
    if filter_ids_1[0].size(0) > 0:
        # Second forward pass (at perturbed parameters)
        outputs = model(x)
        
        # Recompute entropy
        if labels is not None and label_confidence is not None and label_confidence > 0.3:
            h_label = label_guided_entropy(outputs, labels, landmark_means, landmark_cov_invs)
            h_original = get_entropy_from_features(outputs, landmark_means, landmark_cov_invs)
            h_fused = effective_weight * h_label + (1 - effective_weight) * h_original
        else:
            h_fused = get_entropy_from_features(outputs, landmark_means, landmark_cov_invs)
        
        # Second filtering
        filter_ids_2 = torch.where(h_fused < margin)
        loss_second = h_fused[filter_ids_2].mean(0)
        
        # Update EMA for model recovery
        if not np.isnan(loss_second.item()):
            ema = update_ema(ema, loss_second.item())
        
        # SAM second step: parameter update
        loss_second.backward()
        optimizer.second_step(zero_grad=True)
    
    # Model recovery check
    reset_flag = False
    if ema is not None and ema < reset_constant:
        reset_flag = True
    
    return outputs, ema, reset_flag


def label_guided_entropy(outputs, labels, landmark_means=None, landmark_cov_invs=None):
    """
    Compute label-guided entropy.
    
    Args:
        outputs: Model outputs (tensor or dict)
        labels: Ground truth labels (batch_size,)
        landmark_means: List of landmark mean vectors for Mahalanobis entropy
        landmark_cov_invs: List of landmark covariance inverse matrices
    
    Returns:
        h_label: Label-guided entropy (batch_size,)
    """
    # Handle dict outputs from DINOv2
    if isinstance(outputs, dict):
        x = outputs.get('x_norm_clstoken')
        if x is None:
            last_hidden = outputs.get('last_hidden_state')
            if last_hidden is not None:
                x = last_hidden[:, 0]  # Get CLS token
            else:
                # Try to get the first available tensor
                for key, val in outputs.items():
                    if isinstance(val, torch.Tensor):
                        x = val
                        if len(x.shape) == 3:
                            x = x[:, 0]  # Get CLS token for sequence outputs
                        break
            if x is None:
                raise ValueError("No valid tensor found in model output dict")
    elif isinstance(outputs, torch.Tensor):
        x = outputs
    else:
        raise ValueError(f"Unexpected output type: {type(outputs)}")
    
    # Ensure x is 2D (batch_size, feature_dim)
    if len(x.shape) == 3:
        x = x[:, 0]
    
    # If landmark statistics are provided, use Mahalanobis distance-based label-guided entropy
    if landmark_means is not None and landmark_cov_invs is not None and len(landmark_means) > 0:
        batch_size = x.shape[0]
        num_landmarks = len(landmark_means)
        
        # Convert landmark statistics to torch tensors
        device = x.device
        means = [torch.tensor(m, dtype=x.dtype, device=device) for m in landmark_means]
        cov_invs = [torch.tensor(ci, dtype=x.dtype, device=device) for ci in landmark_cov_invs]
        
        # Compute Mahalanobis distance for each sample to each landmark
        mahalanobis_dists = torch.zeros((batch_size, num_landmarks), dtype=x.dtype, device=device)
        
        for i in range(batch_size):
            for j in range(num_landmarks):
                diff = x[i] - means[j]
                dist_sq = diff @ cov_invs[j] @ diff
                mahalanobis_dists[i, j] = torch.sqrt(dist_sq)
        
        # Convert distances to logits: negative distance (smaller distance = higher logit)
        logits = -mahalanobis_dists
        
        # Get log probability of the correct label
        log_probs = logits.log_softmax(1)
        h_label = -log_probs.gather(1, labels.unsqueeze(1)).squeeze(1)
        return h_label
    
    # Fallback: original label-guided entropy
    log_probs = x.log_softmax(1)
    h_label = -log_probs.gather(1, labels.unsqueeze(1)).squeeze(1)
    return h_label


def collect_params(model):
    """
    Collect affine scale + shift parameters from normalization layers.
    
    Args:
        model: PyTorch model
    
    Returns:
        params: List of parameters to adapt
        names: List of parameter names
    """
    params = []
    names = []
    for nm, m in model.named_modules():
        # Skip top layers for adaptation
        if 'layer4' in nm:
            continue
        if 'blocks.9' in nm:
            continue
        if 'blocks.10' in nm:
            continue
        if 'blocks.11' in nm:
            continue
        if 'norm.' in nm:
            continue
        if nm in ['norm']:
            continue
        
        if isinstance(m, (nn.BatchNorm2d, nn.LayerNorm, nn.GroupNorm)):
            for np, p in m.named_parameters():
                if np in ['weight', 'bias']:
                    params.append(p)
                    names.append(f"{nm}.{np}")
    
    return params, names


def copy_model_and_optimizer(model, optimizer):
    """Copy model and optimizer states for resetting."""
    model_state = deepcopy(model.state_dict())
    optimizer_state = deepcopy(optimizer.state_dict())
    return model_state, optimizer_state


def load_model_and_optimizer(model, optimizer, model_state, optimizer_state):
    """Restore model and optimizer from saved states."""
    model.load_state_dict(model_state, strict=True)
    optimizer.load_state_dict(optimizer_state)


def configure_model(model):
    """Configure model for SAR adaptation."""
    # Set to train mode
    model.train()
    # Disable gradients for all parameters
    model.requires_grad_(False)
    
    # Enable gradients for normalization layers
    for m in model.modules():
        if isinstance(m, nn.BatchNorm2d):
            m.requires_grad_(True)
            m.track_running_stats = False
            m.running_mean = None
            m.running_var = None
        if isinstance(m, (nn.LayerNorm, nn.GroupNorm)):
            m.requires_grad_(True)
    
    return model


class SARAdapter(nn.Module):
    """
    SAR adapter that wraps a model for test-time adaptation.
    
    Args:
        model: Base model to adapt
        optimizer: Optimizer for adaptation
        steps: Number of adaptation steps per forward
        episodic: Whether to reset model between episodes
        margin_e0: Entropy margin for reliable sample filtering
        reset_constant_em: EMA threshold for model recovery
        landmark_means: List of landmark mean vectors for Mahalanobis entropy
        landmark_cov_invs: List of landmark covariance inverse matrices
    """
    
    def __init__(self, model, optimizer, steps=1, episodic=False, 
                 margin_e0=0.4*math.log(1000), reset_constant_em=0.2,
                 landmark_means=None, landmark_cov_invs=None):
        super().__init__()
        self.model = model
        self.optimizer = optimizer
        self.num_forwards = 0
        self.num_backwards = 0
        self.steps = steps
        assert steps > 0, "SAR requires >= 1 step(s) to forward and update"
        self.episodic = episodic
        
        self.margin_e0 = margin_e0
        self.reset_constant_em = reset_constant_em
        self.ema = None
        
        # Landmark statistics for Mahalanobis entropy
        self.landmark_means = landmark_means
        self.landmark_cov_invs = landmark_cov_invs
        
        # Save initial state for resetting
        self.model_state, self.optimizer_state = copy_model_and_optimizer(self.model, self.optimizer)
    
    def forward(self, x, labels=None, label_confidence=None, 
                landmark_means=None, landmark_cov_invs=None):
        """
        Forward pass with SAR adaptation.
        
        Args:
            x: Input tensor
            labels: Optional labels for label-guided adaptation
            label_confidence: Confidence of labels (0-1)
            landmark_means: Override landmark means for this forward pass
            landmark_cov_invs: Override landmark cov inverses for this forward pass
        
        Returns:
            outputs: Model outputs after adaptation
        """
        if self.episodic:
            self.reset()
        
        # Use provided statistics or fall back to stored ones
        means = landmark_means if landmark_means is not None else self.landmark_means
        cov_invs = landmark_cov_invs if landmark_cov_invs is not None else self.landmark_cov_invs
        
        for _ in range(self.steps):
            outputs, ema, reset_flag = forward_and_adapt_sar(
                x, self.model, self.optimizer, 
                self.margin_e0, self.reset_constant_em, 
                self.ema, labels, label_confidence, means, cov_invs
            )
            if reset_flag:
                self.reset()
            self.ema = ema
        
        return outputs
    
    def reset(self):
        """Reset model and optimizer to initial state."""
        if self.model_state is None or self.optimizer_state is None:
            raise Exception("Cannot reset without saved model/optimizer state")
        load_model_and_optimizer(self.model, self.optimizer,
                                 self.model_state, self.optimizer_state)
        self.ema = None
    
    def get_ema(self):
        """Get current EMA value."""
        return self.ema
    
    def set_steps(self, steps):
        """Set number of adaptation steps."""
        self.steps = steps
    
    def set_margin(self, margin):
        """Set entropy margin."""
        self.margin_e0 = margin