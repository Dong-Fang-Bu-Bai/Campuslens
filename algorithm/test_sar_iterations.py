"""
SAR 自适应效果测试脚本

测试 SAR 算法在多次迭代后的自适应效果。
"""

import requests
import time
import json
from pathlib import Path


def test_sar_iterations(image_path: str, n: int = 10, use_sar: bool = True, reset_between: bool = False):
    """
    测试 SAR 多次迭代效果
    
    Args:
        image_path: 测试图像路径
        n: 测试次数
        use_sar: 是否启用 SAR
        reset_between: 每次测试前是否重置 SAR
    """
    url = "http://localhost:8000/api/v1/search/sar"
    reset_url = "http://localhost:8000/api/v1/sar/reset"
    
    print(f"\n{'='*60}")
    print(f"SAR 自适应效果测试")
    print(f"{'='*60}")
    print(f"图像: {image_path}")
    print(f"测试次数: {n}")
    print(f"SAR 启用: {use_sar}")
    print(f"每次重置: {reset_between}")
    print(f"{'='*60}\n")
    
    results = []
    
    for i in range(n):
        # 可选：每次测试前重置 SAR
        if reset_between and i > 0:
            try:
                requests.post(reset_url, timeout=5)
                print(f"[{i+1}/{n}] SAR 已重置")
            except Exception as e:
                print(f"[{i+1}/{n}] 重置失败: {e}")
        
        # 发送请求
        try:
            with open(image_path, "rb") as f:
                files = {"file": f}
                params = {"use_sar": str(use_sar).lower()}
                response = requests.post(url, files=files, params=params, timeout=30)
            
            if response.status_code == 200:
                data = response.json()
                top_result = data["results"][0] if data["results"] else None
                
                if top_result:
                    result_info = {
                        "iteration": i + 1,
                        "landmarkCode": top_result["landmarkCode"],
                        "score": top_result["score"],
                        "mahalanobisDistance": top_result["mahalanobisDistance"],
                        "confidenceLevel": top_result["confidenceLevel"]
                    }
                    results.append(result_info)
                    
                    print(f"[{i+1}/{n}] {top_result['landmarkCode']} | "
                          f"分数: {top_result['score']:.4f} | "
                          f"马氏距离: {top_result['mahalanobisDistance']:.4f} | "
                          f"置信度: {top_result['confidenceLevel']}")
                else:
                    print(f"[{i+1}/{n}] 无结果")
            else:
                print(f"[{i+1}/{n}] 请求失败: {response.status_code} - {response.text}")
                
        except Exception as e:
            print(f"[{i+1}/{n}] 错误: {e}")
        
        # 短暂延迟
        time.sleep(0.1)
    
    # 统计分析
    if results:
        print(f"\n{'='*60}")
        print(f"统计分析")
        print(f"{'='*60}")
        
        distances = [r["mahalanobisDistance"] for r in results]
        scores = [r["score"] for r in results]
        
        print(f"马氏距离:")
        print(f"  初始值: {distances[0]:.4f}")
        print(f"  最终值: {distances[-1]:.4f}")
        print(f"  最小值: {min(distances):.4f}")
        print(f"  最大值: {max(distances):.4f}")
        print(f"  平均值: {sum(distances)/len(distances):.4f}")
        print(f"  变化量: {distances[-1] - distances[0]:.4f}")
        
        print(f"\n分数:")
        print(f"  初始值: {scores[0]:.4f}")
        print(f"  最终值: {scores[-1]:.4f}")
        print(f"  最大值: {max(scores):.4f}")
        print(f"  变化量: {scores[-1] - scores[0]:.4f}")
        
        # 判断 SAR 是否生效
        distance_change = abs(distances[-1] - distances[0])
        if distance_change < 0.01:
            print(f"\n⚠️  马氏距离几乎无变化，SAR 自适应效果不明显")
            print(f"   可能原因:")
            print(f"   1. 图像质量较好，模型已经能准确识别")
            print(f"   2. SAR 步数太少（默认 1 步）")
            print(f"   3. 熵值低于阈值，样本被认为是可靠的")
        else:
            print(f"\n✅ 马氏距离有变化，SAR 自适应已生效")
    
    return results


def compare_sar_vs_no_sar(image_path: str):
    """对比 SAR 启用和禁用的效果"""
    print(f"\n{'='*60}")
    print(f"对比测试: SAR 启用 vs 禁用")
    print(f"{'='*60}")
    
    # 先重置
    try:
        requests.post("http://localhost:8000/api/v1/sar/reset", timeout=5)
    except:
        pass
    
    # 测试禁用 SAR
    print("\n[1] 禁用 SAR:")
    results_no_sar = test_sar_iterations(image_path, n=3, use_sar=False)
    
    # 重置
    try:
        requests.post("http://localhost:8000/api/v1/sar/reset", timeout=5)
    except:
        pass
    
    # 测试启用 SAR
    print("\n[2] 启用 SAR:")
    results_sar = test_sar_iterations(image_path, n=3, use_sar=True)
    
    # 对比
    if results_no_sar and results_sar:
        print(f"\n{'='*60}")
        print(f"对比结果")
        print(f"{'='*60}")
        print(f"禁用 SAR - 马氏距离: {results_no_sar[0]['mahalanobisDistance']:.4f}")
        print(f"启用 SAR - 马氏距离: {results_sar[0]['mahalanobisDistance']:.4f}")


if __name__ == "__main__":
    import sys
    
    # 默认测试图像
    default_image = r"C:\programmingProjects\Campuslens\datasets\图片模糊处理.png"
    
    # 从命令行获取参数
    image_path = sys.argv[1] if len(sys.argv) > 1 else default_image
    n = int(sys.argv[2]) if len(sys.argv) > 2 else 10
    
    # 检查图像是否存在
    if not Path(image_path).exists():
        print(f"错误: 图像不存在 - {image_path}")
        sys.exit(1)
    
    # 运行测试
    print("\n[测试 1] 多次迭代测试（不重置）")
    test_sar_iterations(image_path, n=n, use_sar=True, reset_between=False)
    
    print("\n[测试 2] 多次迭代测试（每次重置）")
    test_sar_iterations(image_path, n=n, use_sar=True, reset_between=True)
    
    print("\n[测试 3] 对比测试")
    compare_sar_vs_no_sar(image_path)
