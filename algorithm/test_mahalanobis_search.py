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
        print(f"[ERROR] 测试图片不存在: {test_image}")
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
        print(f"[ERROR] 请求失败: {response.status_code}")
        print(f"错误信息: {response.text}")
        return
    
    result = response.json()
    
    print("\n[OK] 搜索成功")
    print(f"低匹配等级标记: {result.get('lowConfidence', False)}")
    print(f"消息: {result.get('message', '')}")
    print(f"\n返回 {len(result['results'])} 个地标结果:\n")
    
    for i, landmark in enumerate(result['results'], 1):
        print(f"{'='*60}")
        print(f"排名 #{i}: {landmark['landmarkName']} ({landmark['landmarkCode']})")
        print(f"{'='*60}")
        print(f"  经验匹配分:     {landmark['score']:.4f}")
        print(f"  马氏距离:       {landmark['mahalanobisDistance']:.4f}")
        print(f"  匹配等级:       {landmark['confidenceLevel']}")
        
        # 分析马氏距离的意义
        score = landmark['score']
        if score >= 0.8:
            interpretation = "[OK] 查询图与该地标特征分布高度接近"
        elif score >= 0.4:
            interpretation = "[OK] 查询图与该地标特征分布有一定接近度"
        else:
            interpretation = "[WARN] 查询图与该地标特征分布差异较大，建议人工核验"
        
        print(f"\n  解读: {interpretation}")
        print()
    
    print("=" * 80)
    print("对比分析:")
    print("=" * 80)
    
    # 比较评分和马氏距离
    print("\n评分与马氏距离对比:")
    for landmark in result['results']:
        score = landmark['score']
        mah_dist = landmark['mahalanobisDistance']
        print(f"  {landmark['landmarkCode']:5s}: score={score:.4f}, mahalanobis={mah_dist:.2f}")
    
    print("\n" + "=" * 80)
    print("关键改进说明:")
    print("=" * 80)
    print("""
1. 马氏距离考虑了特征之间的协方差结构
2. 不需要手动设置调整因子，完全基于数据统计特性
3. 通过 sigmoid 将马氏距离映射为 0-1 经验匹配分，便于排序和展示区分度
4. score 不具备概率或统计置信度含义，低匹配结果建议人工核验
5. 精简的响应格式：只返回核心信息，减少冗余
    """)


if __name__ == "__main__":
    try:
        test_mahalanobis_search()
    except requests.exceptions.ConnectionError:
        print("[ERROR] 无法连接到服务器，请确保服务正在运行:")
        print("   python app/main.py")
    except Exception as e:
        print(f"[ERROR] 测试失败: {e}")
        import traceback
        traceback.print_exc()
