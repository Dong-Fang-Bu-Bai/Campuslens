"""
测试基于马氏距离的地标类别搜索功能
"""
import requests
import json
from pathlib import Path

# API 基础 URL
BASE_URL = "http://localhost:8000"

def test_mahalanobis_search():
    """测试马氏距离搜索功能"""
    
    # 测试图片路径
    test_image = Path("datasets/landmarks/L01_library/编号1_图书馆_1.jpg")
    
    if not test_image.exists():
        print(f"❌ 测试图片不存在: {test_image}")
        return
    
    print("=" * 80)
    print("测试基于马氏距离的地标类别搜索")
    print("=" * 80)
    
    # 发送搜索请求
    url = f"{BASE_URL}/api/v1/search"
    
    with open(test_image, 'rb') as f:
        files = {'file': ('test.jpg', f, 'image/jpeg')}
        response = requests.post(url, files=files)
    
    if response.status_code != 200:
        print(f"❌ 请求失败: {response.status_code}")
        print(f"错误信息: {response.text}")
        return
    
    result = response.json()
    
    print(f"\n✅ 搜索成功!")
    print(f"低置信度标记: {result.get('lowConfidence', False)}")
    print(f"消息: {result.get('message', '')}")
    print(f"\n返回 {len(result['results'])} 个地标结果:\n")
    
    for i, landmark in enumerate(result['results'], 1):
        print(f"{'='*60}")
        print(f"排名 #{i}: {landmark['landmarkName']} ({landmark['landmarkCode']})")
        print(f"{'='*60}")
        print(f"  原始余弦相似度: {landmark['rawScore']:.4f}")
        print(f"  马氏距离评分:   {landmark['adaptiveScore']:.4f}")
        print(f"  置信度等级:     {landmark['confidenceLevel']}")
        print(f"  图片数量:       {landmark['imageCount']}")
        
        stats = landmark['statistics']
        print(f"\n  统计信息:")
        print(f"    - 平均相似度:     {stats['avgSimilarity']:.4f}")
        print(f"    - 马氏距离:       {stats['mahalanobisDistance']:.4f}")
        print(f"    - 标准差:         {stats['stdDeviation']:.4f}")
        print(f"    - 紧凑程度:       {stats['compactness']}")
        
        # 分析马氏距离的意义
        mah_dist = stats['mahalanobisDistance']
        if mah_dist < 1.0:
            interpretation = "✓ 查询点在该地标分布的核心区域"
        elif mah_dist < 2.0:
            interpretation = "✓ 查询点在该地标分布的正常范围内"
        elif mah_dist < 3.0:
            interpretation = "⚠ 查询点接近该地标分布的边缘"
        else:
            interpretation = "✗ 查询点可能不属于该地标分布"
        
        print(f"\n  解读: {interpretation}")
        print()
    
    print("=" * 80)
    print("对比分析:")
    print("=" * 80)
    
    # 比较原始分数和马氏距离分数的差异
    print("\n原始分数 vs 马氏距离评分:")
    for landmark in result['results']:
        raw = landmark['rawScore']
        adaptive = landmark['adaptiveScore']
        diff = adaptive - raw
        direction = "↑" if diff > 0 else "↓" if diff < 0 else "="
        print(f"  {landmark['landmarkCode']:5s}: {raw:.4f} → {adaptive:.4f} ({direction}{abs(diff):.4f})")
    
    print("\n" + "=" * 80)
    print("关键改进说明:")
    print("=" * 80)
    print("""
1. 马氏距离考虑了特征之间的协方差结构
2. 不需要手动设置调整因子，完全基于数据统计特性
3. 有严格的概率解释：马氏距离 < χ²临界值 表示在95%置信区间内
4. 自动适应不同地标的分散程度，无需人工干预
    """)


if __name__ == "__main__":
    try:
        test_mahalanobis_search()
    except requests.exceptions.ConnectionError:
        print("❌ 无法连接到服务器，请确保服务正在运行:")
        print("   python app/main.py")
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        import traceback
        traceback.print_exc()
