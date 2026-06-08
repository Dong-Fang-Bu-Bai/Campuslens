# SAR 算法集成实现说明

## 概述

SAR (Sharpness-Aware and Reliable entropy minimization) 是一种测试时自适应算法，通过熵最小化和 Sharpness-Aware 优化实现模型在测试阶段的自适应调整，增强对噪声图像的识别能力。

本项目将 SAR 算法集成到 CampusLens AI 图像检索服务中，支持：
- 无标签上传时的纯 SAR 熵最小化自适应
- 用户反馈时的标签引导 SAR 自适应
- 基于用户反馈的持续学习机制

---

## 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      SAR 集成架构                               │
├─────────────────────────────────────────────────────────────────┤
│  用户层                                                         │
│  ┌─────────────────┐  ┌─────────────────┐                      │
│  │  无标签上传搜索  │  │  用户反馈       │                      │
│  └────────┬────────┘  └────────┬────────┘                      │
│           │                    │                               │
├───────────┼────────────────────┼───────────────────────────────┤
│  API 层   │                    │                               │
│  ┌────────▼────────┐  ┌────────▼────────┐                      │
│  │ /search/sar     │  │ /feedback       │                      │
│  └────────┬────────┘  └────────┬────────┘                      │
│           │                    │                               │
├───────────┼────────────────────┼───────────────────────────────┤
│  服务层   │                    │                               │
│  ┌────────▼────────────────────▼────────┐                      │
│  │        SARSearchService              │                      │
│  │  - SAR 自适应搜索                    │                      │
│  │  - 用户反馈处理                       │                      │
│  └──────────────────┬───────────────────┘                      │
│                     │                                          │
├─────────────────────┼───────────────────────────────────────────┤
│  模型层             │                                          │
│  ┌──────────────────▼──────────────────┐                      │
│  │        SARDINOv2Extractor           │                      │
│  │  ┌──────────────────────────────┐   │                      │
│  │  │     DINOv2 特征提取器        │   │                      │
│  │  └──────────────────┬───────────┘   │                      │
│  │                     │               │                      │
│  │  ┌──────────────────▼───────────┐   │                      │
│  │  │     SARAdapter (测试时自适应) │   │                      │
│  │  │  - 熵最小化                    │   │                      │
│  │  │  - SAM 优化器                  │   │                      │
│  │  └──────────────────────────────┘   │                      │
│  └─────────────────────────────────────┘                      │
├─────────────────────────────────────────────────────────────────┤
│  数据层                                                         │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    FAISS 向量索引                        │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 核心组件

### 1. SAM 优化器 (`app/utils/sam_optimizer.py`)

Sharpness-Aware Minimization 优化器，通过参数扰动和两次梯度更新寻找更平坦的极小值。

**关键特性：**
- 支持自适应扰动缩放（adaptive=True）
- 支持自定义扰动半径（rho 参数）
- 兼容标准 PyTorch 优化器接口

### 2. SAR 适配器 (`app/models/sar_adapter.py`)

实现 SAR 算法的核心逻辑：

**自适应流程：**
1. **熵计算**：计算模型输出的 softmax 熵
2. **可靠样本筛选**：根据熵阈值过滤可靠样本
3. **SAM 更新**：使用 SAM 优化器进行参数更新
4. **模型恢复**：当 EMA 低于阈值时重置模型

**标签引导自适应：**
- 结合用户反馈的标签信息
- 根据标签置信度动态调整标签权重
- 实现标签引导的熵最小化

### 3. SAR-DINOv2 提取器 (`app/models/sar_dinov2_extractor.py`)

集成 SAR 的 DINOv2 特征提取器：

**功能特性：**
- 支持 SAR 自适应特征提取
- 支持标签引导的特征提取
- 兼容原有 DINOv2 接口

### 4. SAR 搜索服务 (`app/services/sar_search_service.py`)

提供 SAR 增强的搜索能力：

**主要方法：**
| 方法 | 功能 |
|------|------|
| `search_similar_landmarks()` | SAR 自适应搜索 |
| `feedback_update()` | 用户反馈处理（自动计算标签置信度） |
| `reset_sar()` | 重置 SAR 适配器 |

---

## API 接口

### 1. SAR 增强搜索

**POST** `/api/v1/search/sar`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | File | 是 | 查询图像文件 |
| `use_sar` | bool | 否 | 是否启用 SAR（默认 True） |

**响应示例：**
```json
{
  "results": [
    {
      "rank": 1,
      "landmarkCode": "L01",
      "score": 0.95,
      "confidenceLevel": "high",
      "mahalanobisDistance": 1.2
    }
  ],
  "lowConfidence": false,
  "sarEnabled": true,
  "message": "Search successful"
}
```

### 2. 用户反馈

**POST** `/api/v1/feedback`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | File | 是 | 反馈图像文件 |
| `landmark_code` | string | 是 | 确认的地标代码 |
| `update_index` | bool | 否 | 是否更新索引（默认 True） |

**响应示例：**
```json
{
  "success": true,
  "message": "Feedback processed successfully",
  "landmarkCode": "L01",
  "mahalanobisDistance": 150.5,
  "labelConfidence": 0.7865,
  "index_updated": true,
  "sar_ema": 0.35
}
```

**响应字段说明：**
| 字段 | 类型 | 说明 |
|------|------|------|
| `landmarkCode` | string | 用户确认的地标代码 |
| `mahalanobisDistance` | float | 查询图像到该地标分布的马氏距离 |
| `labelConfidence` | float | 自动计算的标签置信度（0-1） |
| `index_updated` | bool | 是否更新了索引 |

**标签置信度计算规则（指数衰减）：**

```
labelConfidence = exp(-ln(10)/1000 * mahalanobisDistance)
```

| 马氏距离 | 置信度 | 说明 |
|----------|--------|------|
| 0 | 100% | 完美匹配 |
| 100 | 78.5% | 高置信度 |
| 500 | 30.8% | 中等置信度 |
| 1000 | 10% | 低置信度 |
| 2000 | 1% | 极低置信度 |
| ∞ | 0% | 无匹配 |

### 3. 重置 SAR 适配器

**POST** `/api/v1/sar/reset`

**响应示例：**
```json
{
  "success": true,
  "message": "SAR adapter reset"
}
```

---

## 使用流程

### 场景一：用户正常上传搜索（无标签）

```
用户上传图像 → /search/sar → SAR 熵最小化自适应 → 特征提取 → FAISS 搜索 → 返回结果
```

### 场景二：用户反馈（带标签）

```
用户上传反馈图像+标签 → /feedback → 标签引导 SAR 自适应 → 提取特征 → 更新 FAISS 索引 → 模型微调
```

---

## 持续学习策略

### 1. 模型微调
- 仅微调归一化层参数
- 使用 SAM 优化器进行更新
- 保持特征提取器主体不变

### 2. 索引更新
- 高置信度反馈自动添加到 FAISS 索引
- 支持动态索引扩展
- 定期重建索引优化

### 3. 统计更新
- 更新地标统计特征
- 自适应调整匹配阈值
- 维护马氏距离统计量

### 4. 模型恢复
- 监控 EMA（指数移动平均）
- 当 EMA 过低时自动重置模型
- 防止模型过度适应噪声

---

## 配置参数

| 参数 | 环境变量 | 默认值 | 说明 |
|------|----------|--------|------|
| SAR 步数 | SAR_STEPS | 1 | 每批次自适应步数 |
| 熵阈值 | SAR_MARGIN | 0.4*log(1000) | 可靠样本筛选阈值 |
| 重置阈值 | SAR_RESET_EM | 0.2 | EMA 重置阈值 |
| SAM 扰动半径 | SAM_RHO | 0.05 | 参数扰动幅度 |
| 学习率 | SAR_LR | 1e-4 | 自适应学习率 |

---

## 技术原理

### SAR 算法核心

**熵最小化损失：**
```
loss = E[H(p(y|x))] for x where H(p(y|x)) < margin
```

**SAM 优化步骤：**
1. 计算梯度并扰动参数：`w ← w + ρ·g/||g||`
2. 在扰动点计算梯度
3. 在原始点更新参数

**标签引导自适应：**
```
h_fused = α·h_label + (1-α)·h_original
其中 α = 0.5 * label_confidence
```

---

## 使用说明

### 1. 启动服务

```bash
# 进入算法服务目录
cd c:\programmingProjects\Campuslens\algorithm

# 激活虚拟环境
.venv\Scripts\Activate.ps1

# 启动服务
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 2. 基础测试

#### 2.1 健康检查

```bash
curl.exe http://localhost:8000/api/v1/health
```

预期响应：
```json
{
  "status": "healthy",
  "service": "CampusLens AI Search",
  "version": "1.0.0",
  "sarAvailable": true
}
```

#### 2.2 测试 SAR 搜索

```bash
# 无标签 SAR 搜索
curl.exe -X POST http://localhost:8000/api/v1/search/sar `
  -F "file=@test_image.jpg"

# 禁用 SAR（使用标准搜索）
curl.exe -X POST "http://localhost:8000/api/v1/search/sar?use_sar=false" `
  -F "file=@test_image.jpg"
```

#### 2.3 测试用户反馈

```bash
curl.exe -X POST "http://localhost:8000/api/v1/feedback?landmark_code=L01&update_index=true" `
  -F "file=@test_image.jpg"
```

#### 2.5 重置 SAR 适配器

```bash
curl.exe -X POST http://localhost:8000/api/v1/sar/reset
```

### 3. Python 客户端示例

```python
import requests

class SARClient:
    def __init__(self, base_url="http://localhost:8000"):
        self.base_url = base_url
    
    def search_sar(self, image_path, use_sar=True):
        """SAR 自适应搜索"""
        url = f"{self.base_url}/api/v1/search/sar"
        with open(image_path, "rb") as f:
            files = {"file": f}
            params = {"use_sar": use_sar}
            response = requests.post(url, files=files, params=params)
        return response.json()
    
    def feedback(self, image_path, landmark_code, update_index=True):
        """用户反馈（标签置信度自动计算）"""
        url = f"{self.base_url}/api/v1/feedback"
        with open(image_path, "rb") as f:
            files = {"file": f}
            params = {
                "landmark_code": landmark_code,
                "update_index": update_index
            }
            response = requests.post(url, files=files, params=params)
        return response.json()
    
    def reset_sar(self):
        """重置 SAR 适配器"""
        url = f"{self.base_url}/api/v1/sar/reset"
        response = requests.post(url)
        return response.json()

# 使用示例
if __name__ == "__main__":
    client = SARClient()
    
    # 1. 无标签 SAR 搜索
    result1 = client.search_sar("test_image.jpg", use_sar=True)
    print("SAR 搜索结果:", result1)
    
    # 2. 用户反馈（标签置信度由系统自动计算）
    result2 = client.feedback("test_image.jpg", landmark_code="L01")
    print("反馈结果:", result2)
    print("标签置信度:", result2.get("labelConfidence"))
    
    # 3. 重置 SAR
    result3 = client.reset_sar()
    print("重置结果:", result3)
```

### 4. 响应参数说明

#### 4.1 搜索响应

| 字段 | 类型 | 说明 |
|------|------|------|
| `results` | array | 匹配结果列表 |
| `results[].rank` | int | 排名 |
| `results[].landmarkCode` | string | 地标代码 |
| `results[].score` | float | 匹配分数（0-1） |
| `results[].confidenceLevel` | string | 置信度等级（high/medium/low） |
| `results[].mahalanobisDistance` | float | 马氏距离 |
| `lowConfidence` | bool | 是否低置信度 |
| `sarEnabled` | bool | 是否启用 SAR |
| `message` | string | 结果消息 |

### 5. 测试场景建议

| 场景 | 测试目的 | 测试步骤 |
|------|----------|----------|
| **噪声图像测试** | 验证 SAR 对噪声图像的鲁棒性 | 使用添加高斯噪声的图像进行搜索，比较启用/禁用 SAR 的结果差异 |
| **低质量图像测试** | 验证 SAR 对模糊图像的处理能力 | 使用低分辨率或模糊图像进行测试 |
| **用户反馈测试** | 验证反馈机制效果 | 上传图像并提交反馈，观察索引更新情况和后续搜索结果变化 |
| **参数恢复测试** | 验证 SAR 可逆性 | 启用 SAR → 搜索 → 重置 → 再次搜索，比较两次结果 |
| **持续学习测试** | 验证用户反馈机制 | 多次提交同一图像的不同反馈标签，观察索引更新效果 |

### 6. 性能监控

```bash
# 监控服务状态
curl.exe http://localhost:8000/api/v1/health

# 查看 SAR 状态（需要实现状态接口）
curl.exe http://localhost:8000/api/v1/sar/status
```

预期状态响应：
```json
{
  "sar_enabled": true,
  "ema": 0.35,
  "adaptation_count": 15,
  "reset_count": 3,
  "params_modified": true
}
```

### 7. 常见问题

#### Q1: SAR 启用后响应变慢？

**A:** SAR 自适应会增加约 2-3 倍推理时间，这是正常现象。可以通过 `use_sar=false` 参数选择性启用。

#### Q2: 如何判断 SAR 是否生效？

**A:** 检查响应中的 `sarEnabled` 字段是否为 `true`。

#### Q3: 参数恢复后结果不一致？

**A:** 重置 SAR 后模型参数恢复到初始状态，搜索结果可能会有差异，这是预期行为。

#### Q4: 标签置信度是如何计算的？

**A:** 标签置信度基于马氏距离计算，采用指数衰减公式：

```
labelConfidence = exp(-ln(10)/1000 * mahalanobisDistance)
```

- 马氏距离越小，置信度越高
- 马氏距离 = 0 时，置信度 = 100%
- 马氏距离 = 1000 时，置信度 = 10%
- 马氏距离 = ∞ 时，置信度 = 0%
- 置信度低于 0.7 时不会更新 FAISS 索引

---

## 注意事项

1. **性能影响**：SAR 自适应会增加推理时间（约 2-3 倍），建议在需要时启用
2. **内存占用**：SAR 需要保存优化器状态，内存占用略有增加
3. **模型恢复**：建议定期调用 `/sar/reset` 重置适配器状态
4. **置信度阈值**：用户反馈置信度低于 0.7 时不会更新索引

---

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-08 | 初始版本，支持 SAR 基本功能 |

---

## 参考资料

- SAR 论文：https://arxiv.org/abs/2302.03011
- SAM 论文：https://arxiv.org/abs/2010.01412
- SAR 实现：https://github.com/mr-eggplant/SAR