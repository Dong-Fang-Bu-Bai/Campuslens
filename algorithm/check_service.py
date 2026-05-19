"""
简单的服务健康检查脚本
"""
import requests
import sys


def check_health():
    """检查服务是否正常运行"""
    try:
        response = requests.get("http://localhost:8000/api/v1/health", timeout=5)
        if response.status_code == 200:
            data = response.json()
            print("✅ 服务正常运行")
            print(f"   服务名称: {data.get('service')}")
            print(f"   版本: {data.get('version')}")
            return True
        else:
            print(f"❌ 服务异常，状态码: {response.status_code}")
            return False
    except requests.exceptions.ConnectionError:
        print("❌ 无法连接到服务，请确认服务已启动")
        print("   运行命令: python app/main.py")
        return False
    except Exception as e:
        print(f"❌ 检查失败: {e}")
        return False


def check_index():
    """检查索引状态"""
    try:
        response = requests.get("http://localhost:8000/api/v1/index/stats", timeout=5)
        if response.status_code == 200:
            data = response.json()
            print("\n📊 索引状态:")
            print(f"   状态: {data.get('status')}")
            print(f"   向量总数: {data.get('totalVectors', 0)}")
            print(f"   维度: {data.get('dimension', 0)}")
            print(f"   地标数量: {data.get('indexedLandmarks', 0)}")
            
            if data.get('status') == 'not_initialized' or data.get('totalVectors', 0) == 0:
                print("\n⚠️  索引为空，需要重建索引")
                print("   运行命令: curl -X POST http://localhost:8000/api/v1/index/rebuild")
                return False
            return True
        else:
            print(f"❌ 获取索引状态失败，状态码: {response.status_code}")
            return False
    except Exception as e:
        print(f"❌ 检查索引失败: {e}")
        return False


def main():
    print("=" * 60)
    print("CampusLens AI 服务健康检查")
    print("=" * 60)
    
    health_ok = check_health()
    
    if health_ok:
        index_ok = check_index()
        
        print("\n" + "=" * 60)
        if health_ok and index_ok:
            print("✅ 所有检查通过，服务可以正常使用")
        else:
            print("⚠️  服务运行正常，但需要重建索引")
        print("=" * 60)
    
    return 0 if health_ok else 1


if __name__ == "__main__":
    sys.exit(main())
