"""
DINOv2 ViT 模型架构实现（纯 PyTorch，无外部依赖）
基于分析结果：ViT-B/14, embed_dim=768, 12 layers, patch_size=14
"""
import torch
import torch.nn as nn
import math


class PatchEmbed(nn.Module):
    def __init__(self, img_size=224, patch_size=14, in_chans=3, embed_dim=768):
        super().__init__()
        self.img_size = img_size
        self.patch_size = patch_size
        self.num_patches = (img_size // patch_size) ** 2
        
        self.proj = nn.Conv2d(in_chans, embed_dim, kernel_size=patch_size, stride=patch_size)
    
    def forward(self, x):
        B, C, H, W = x.shape
        x = self.proj(x).flatten(2).transpose(1, 2)
        return x


class Attention(nn.Module):
    def __init__(self, dim, num_heads=12, qkv_bias=False):
        super().__init__()
        self.num_heads = num_heads
        head_dim = dim // num_heads
        self.scale = head_dim ** -0.5
        
        self.qkv = nn.Linear(dim, dim * 3, bias=qkv_bias)
        self.proj = nn.Linear(dim, dim)
    
    def forward(self, x):
        B, N, C = x.shape
        qkv = self.qkv(x).reshape(B, N, 3, self.num_heads, C // self.num_heads).permute(2, 0, 3, 1, 4)
        q, k, v = qkv.unbind(0)
        
        attn = (q @ k.transpose(-2, -1)) * self.scale
        attn = attn.softmax(dim=-1)
        
        x = (attn @ v).transpose(1, 2).reshape(B, N, C)
        x = self.proj(x)
        return x


class Mlp(nn.Module):
    def __init__(self, in_features, hidden_features=None, out_features=None):
        super().__init__()
        out_features = out_features or in_features
        hidden_features = hidden_features or in_features * 4
        
        self.fc1 = nn.Linear(in_features, hidden_features)
        self.fc2 = nn.Linear(hidden_features, out_features)
    
    def forward(self, x):
        x = self.fc1(x)
        x = nn.functional.gelu(x)
        x = self.fc2(x)
        return x


class LayerScale(nn.Module):
    """LayerScale 模块（匹配官方权重结构）"""
    def __init__(self, dim):
        super().__init__()
        self.gamma = nn.Parameter(torch.ones(dim))
    
    def forward(self, x):
        return x * self.gamma


class BlockWithLS(nn.Module):
    """带 LayerScale 的 Transformer Block（DINOv2 特色）"""
    def __init__(self, dim, num_heads=12, mlp_ratio=4.0, qkv_bias=False):
        super().__init__()
        self.norm1 = nn.LayerNorm(dim)
        self.attn = Attention(dim, num_heads=num_heads, qkv_bias=qkv_bias)
        self.ls1 = LayerScale(dim)  # LayerScale 作为子模块
        
        self.norm2 = nn.LayerNorm(dim)
        self.mlp = Mlp(in_features=dim, hidden_features=int(dim * mlp_ratio))
        self.ls2 = LayerScale(dim)  # LayerScale 作为子模块
    
    def forward(self, x):
        x = x + self.ls1(self.attn(self.norm1(x)))
        x = x + self.ls2(self.mlp(self.norm2(x)))
        return x


class DinoV2ViT(nn.Module):
    """
    DINOv2 ViT-B/14 模型
    - embed_dim: 768
    - depth: 12
    - num_heads: 12
    - patch_size: 14
    - img_size: 518 (DINOv2 默认训练尺寸)
    """
    def __init__(self, img_size=518, patch_size=14, embed_dim=768, depth=12, num_heads=12):
        super().__init__()
        self.embed_dim = embed_dim
        self.num_patches = (img_size // patch_size) ** 2
        
        self.patch_embed = PatchEmbed(img_size, patch_size, 3, embed_dim)
        
        self.cls_token = nn.Parameter(torch.zeros(1, 1, embed_dim))
        self.pos_embed = nn.Parameter(torch.zeros(1, self.num_patches + 1, embed_dim))
        self.mask_token = nn.Parameter(torch.zeros(1, embed_dim))  # 保留以匹配官方权重结构
        
        self.blocks = nn.ModuleList([
            BlockWithLS(embed_dim, num_heads=num_heads, mlp_ratio=4.0, qkv_bias=True)
            for _ in range(depth)
        ])
        
        self.norm = nn.LayerNorm(embed_dim)
        
        # 初始化
        self._init_weights()
    
    def _init_weights(self):
        trunc_normal_(self.pos_embed, std=0.02)
        trunc_normal_(self.cls_token, std=0.02)
    
    def forward_features(self, x):
        B = x.shape[0]
        x = self.patch_embed(x)
        
        cls_tokens = self.cls_token.expand(B, -1, -1)
        x = torch.cat((cls_tokens, x), dim=1)
        
        x = x + self.pos_embed
        
        for blk in self.blocks:
            x = blk(x)
        
        x = self.norm(x)
        
        # 返回 CLS token 和所有 tokens
        return {
            'x_norm_clstoken': x[:, 0],
            'x_prenorm': x
        }
    
    def forward(self, x):
        return self.forward_features(x)


def trunc_normal_(tensor, mean=0., std=1.):
    """截断正态分布初始化"""
    with torch.no_grad():
        tensor.normal_(mean, std)
        l = tensor.mean() - 2 * std
        u = tensor.mean() + 2 * std
        tensor.clamp_(min=l, max=u)


def create_dinov2_vitb14():
    """创建 DINOv2 ViT-B/14 模型"""
    model = DinoV2ViT(
        img_size=518,
        patch_size=14,
        embed_dim=768,
        depth=12,
        num_heads=12
    )
    return model
