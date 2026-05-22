"""
模型验证脚本 - 测试离线模型是否正常加载
"""
import torch
from pathlib import Path
import sys


def verify_model(model_path: str):
    print("=" * 60)
    print("DINOv2 离线模型验证工具")
    print("=" * 60)

    model_file = Path(model_path)

    if not model_file.exists():
        print(f"❌ 模型文件不存在: {model_path}")
        print(f"   当前工作目录: {Path.cwd()}")
        print(f"   请确认路径是否正确")
        return False

    file_size_mb = model_file.stat().st_size / (1024 * 1024)
    print(f"✅ 模型文件存在: {model_path}")
    print(f"📦 文件大小: {file_size_mb:.2f} MB")

    if file_size_mb < 100:
        print(f"⚠️  警告: 文件过小，可能下载不完整")
        return False

    try:
        print("\n🔄 尝试加载模型...")
        device = "cuda" if torch.cuda.is_available() else "cpu"
        print(f"💻 使用设备: {device}")

        model = torch.load(model_path, map_location=device, weights_only=False)

        if isinstance(model, dict):
            keys = list(model.keys())
            print(f"📋 模型格式: 字典")
            print(f"🔑 包含键: {keys[:5]}{'...' if len(keys) > 5 else ''}")

            if 'state_dict' in model or 'model_state_dict' in model:
                print("✅ 检测到标准 state_dict 格式")
            else:
                print("⚠️  非标准格式，可能需要特殊处理")

        elif hasattr(model, 'eval'):
            print(f"📋 模型格式: {type(model).__name__}")
            if hasattr(model, 'forward_features'):
                print("✅ 检测到 DINOv2 模型对象（含 forward_features）")
            else:
                print("⚠️  模型缺少 forward_features 方法")

        else:
            print(f"📋 模型格式: {type(model)}")
            print("⚠️  未知格式")

        print("\n✅ 模型验证通过！")
        print("=" * 60)
        return True

    except Exception as e:
        print(f"\n❌ 模型加载失败: {e}")
        import traceback
        traceback.print_exc()
        return False


if __name__ == "__main__":
    # 智能检测模型路径
    if len(sys.argv) > 1:
        model_path = sys.argv[1]
    else:
        # 尝试多个可能的路径
        possible_paths = [
            "./models/dinov2_model.pth",
            "models/dinov2_model.pth",
        ]

        model_path = None
        for path in possible_paths:
            if Path(path).exists():
                model_path = path
                break

        if model_path is None:
            model_path = "./models/dinov2_model.pth"  # 默认路径

    success = verify_model(model_path)
    sys.exit(0 if success else 1)
