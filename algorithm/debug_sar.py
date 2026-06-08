"""
SAR 自适应调试脚本

检查 SAR 自适应是否真正在工作，打印详细的调试信息。
"""

import torch
import numpy as np
from PIL import Image
from pathlib import Path
import sys

# 添加项目路径
project_root = Path(__file__).parent
sys.path.insert(0, str(project_root))

from app.models.sar_dinov2_extractor import SARDINOv2Extractor
from app.config import Config
from app.utils.faiss_manager import FAISSManager


def load_landmark_stats():
    """从 FAISS 索引加载地标统计信息"""
    faiss_manager = FAISSManager(dimension=768)
    if faiss_manager.load_index():
        if hasattr(faiss_manager, 'landmark_codes') and faiss_manager.landmark_codes:
            landmark_means = []
            landmark_cov_invs = []
            for code in faiss_manager.landmark_codes:
                stats = faiss_manager.landmark_stats[code]
                landmark_means.append(stats['mean'])
                landmark_cov_invs.append(stats['cov_inv'])
            print(f"[INFO] 加载了 {len(landmark_means)} 个地标的统计信息")
            return landmark_means, landmark_cov_invs, len(landmark_means)
    return None, None, 0


def debug_sar_adaptation(image_path: str, n_iterations: int = 5):
    """
    调试 SAR 自适应过程
    
    Args:
        image_path: 测试图像路径
        n_iterations: 迭代次数
    """
    print(f"\n{'='*70}")
    print(f"SAR 自适应调试")
    print(f"{'='*70}")
    print(f"图像: {image_path}")
    print(f"迭代次数: {n_iterations}")
    print(f"{'='*70}\n")
    
    # 加载地标统计信息
    landmark_means, landmark_cov_invs, num_landmarks = load_landmark_stats()
    
    # 加载模型
    print("[1] 加载 SAR-DINOv2 模型...")
    extractor = SARDINOv2Extractor(
        model_path=Config.DINO_MODEL_PATH,
        device=Config.DEVICE,
        sar_steps=1,
        landmark_means=landmark_means,
        landmark_cov_invs=landmark_cov_invs,
        num_classes=num_landmarks if num_landmarks > 0 else 1000
    )
    
    # 加载图像
    print(f"[2] 加载图像: {image_path}")
    image = Image.open(image_path).convert('RGB')
    
    # 预处理图像
    input_tensor = extractor.transform(image).unsqueeze(0).to(extractor.device)
    print(f"    输入张量形状: {input_tensor.shape}")
    
    # 获取初始模型参数
    print("\n[3] 获取初始模型参数...")
    initial_params = {}
    for name, param in extractor.model.named_parameters():
        if param.requires_grad:
            initial_params[name] = param.detach().clone()
    print(f"    可训练参数数量: {len(initial_params)}")
    
    # 多次迭代测试
    print(f"\n[4] 开始 {n_iterations} 次迭代测试...")
    print(f"{'-'*70}")
    
    features_list = []
    ema_list = []
    
    for i in range(n_iterations):
        print(f"\n--- 迭代 {i+1}/{n_iterations} ---")
        
        # 记录迭代前的参数
        params_before = {}
        for name, param in extractor.model.named_parameters():
            if param.requires_grad:
                params_before[name] = param.detach().clone()
        
        # 提取特征（启用 SAR）
        feature = extractor.extract_single(image, use_sar=True)
        features_list.append(feature)
        
        # 记录迭代后的参数
        params_after = {}
        for name, param in extractor.model.named_parameters():
            if param.requires_grad:
                params_after[name] = param.detach().clone()
        
        # 计算参数变化
        param_changes = {}
        total_change = 0.0
        for name in params_before:
            diff = (params_after[name] - params_before[name]).abs().mean().item()
            param_changes[name] = diff
            total_change += diff
        
        # 获取 EMA
        ema = extractor.get_sar_ema()
        ema_list.append(ema)
        
        # 打印信息
        print(f"  特征向量范数: {np.linalg.norm(feature):.6f}")
        print(f"  EMA 值: {ema if ema is not None else 'None'}")
        print(f"  参数总变化量: {total_change:.8f}")
        
        # 打印前 3 个参数的变化
        sorted_changes = sorted(param_changes.items(), key=lambda x: x[1], reverse=True)
        print(f"  参数变化 (Top 3):")
        for name, change in sorted_changes[:3]:
            print(f"    {name}: {change:.8f}")
        
        # 检查特征是否变化
        if i > 0:
            feature_diff = np.linalg.norm(feature - features_list[i-1])
            print(f"  特征变化量: {feature_diff:.8f}")
    
    # 统计分析
    print(f"\n{'='*70}")
    print(f"统计分析")
    print(f"{'='*70}")
    
    # 特征变化分析
    print("\n特征向量分析:")
    feature_norms = [np.linalg.norm(f) for f in features_list]
    print(f"  所有范数: {[f'{n:.6f}' for n in feature_norms]}")
    
    # 计算特征之间的差异
    if len(features_list) > 1:
        print("\n特征差异矩阵:")
        for i in range(len(features_list)):
            diffs = []
            for j in range(len(features_list)):
                diff = np.linalg.norm(features_list[i] - features_list[j])
                diffs.append(f"{diff:.8f}")
            print(f"  迭代{i+1}: {diffs}")
    
    # EMA 分析
    print(f"\nEMA 值变化:")
    print(f"  {ema_list}")
    
    # 最终参数与初始参数对比
    print(f"\n最终参数与初始参数对比:")
    final_total_change = 0.0
    for name, initial_param in initial_params.items():
        for n, p in extractor.model.named_parameters():
            if n == name:
                diff = (p.detach() - initial_param).abs().mean().item()
                final_total_change += diff
                if diff > 1e-8:
                    print(f"  {name}: 变化量 = {diff:.8f}")
    
    print(f"\n总参数变化量: {final_total_change:.8f}")
    
    # 结论
    print(f"\n{'='*70}")
    print(f"结论")
    print(f"{'='*70}")
    
    if final_total_change < 1e-8:
        print("❌ SAR 自适应未生效：模型参数完全没有变化")
        print("   可能原因:")
        print("   1. 梯度计算有问题")
        print("   2. 优化器未正确更新参数")
        print("   3. 所有样本被过滤（熵值过高）")
    else:
        print("✅ SAR 自适应已生效：模型参数有变化")
        
    # 检查特征是否变化
    if len(features_list) > 1:
        feature_diff = np.linalg.norm(features_list[-1] - features_list[0])
        if feature_diff < 1e-8:
            print("⚠️  特征向量未变化：虽然参数变化了，但输出特征相同")
        else:
            print(f"✅ 特征向量有变化: {feature_diff:.8f}")


def test_entropy_calculation(image_path: str):
    """测试熵计算"""
    print(f"\n{'='*70}")
    print(f"熵计算测试")
    print(f"{'='*70}")
    
    # 加载地标统计信息
    landmark_means, landmark_cov_invs, num_landmarks = load_landmark_stats()
    
    # 加载模型
    extractor = SARDINOv2Extractor(
        model_path=Config.DINO_MODEL_PATH,
        device=Config.DEVICE,
        sar_steps=1,
        landmark_means=landmark_means,
        landmark_cov_invs=landmark_cov_invs,
        num_classes=num_landmarks if num_landmarks > 0 else 1000
    )
    
    # 加载图像
    image = Image.open(image_path).convert('RGB')
    input_tensor = extractor.transform(image).unsqueeze(0).to(extractor.device)
    
    # 前向传播
    with torch.no_grad():
        outputs = extractor.model(input_tensor)
    
    # 计算熵
    from app.models.sar_adapter import get_entropy_from_features
    
    entropy = get_entropy_from_features(outputs, landmark_means, landmark_cov_invs)
    margin = extractor.sar_model.margin_e0  # 使用模型的实际阈值
    
    print(f"模型输出类型: {type(outputs)}")
    if isinstance(outputs, dict):
        print(f"输出键: {list(outputs.keys())}")
        for key, val in outputs.items():
            if isinstance(val, torch.Tensor):
                print(f"  {key}: 形状 {val.shape}")
    
    print(f"\n地标数量: {num_landmarks}")
    print(f"熵值: {entropy.item():.6f}")
    print(f"熵阈值 (margin): {margin:.6f}")
    print(f"是否为可靠样本: {entropy.item() < margin}")


if __name__ == "__main__":
    # 默认测试图像
    default_image = r"C:\programmingProjects\Campuslens\datasets\编号11_琴湖及湖心岛_19.jpg"
    
    # 从命令行获取参数
    image_path = sys.argv[1] if len(sys.argv) > 1 else default_image
    n_iterations = int(sys.argv[2]) if len(sys.argv) > 2 else 5
    
    # 检查图像是否存在
    if not Path(image_path).exists():
        print(f"错误: 图像不存在 - {image_path}")
        sys.exit(1)
    
    # 运行调试
    test_entropy_calculation(image_path)
    debug_sar_adaptation(image_path, n_iterations)
