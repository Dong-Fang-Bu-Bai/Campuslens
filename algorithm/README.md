# CampusLens AI 图像检索服务

**版本**: v2.1  
**最后更新**: 2026-06-11

基于 DINOv2 特征提取、FAISS 地标召回、马氏距离经验匹配分和 SAR 测试时自适应的校园地标检索微服务。

## 重要说明

本服务仅支持离线模式，必须预先准备本地模型文件和本地数据集。

仓库不会提交以下产物：
- `models/dinov2_model.pth`
- `data/faiss_index/landmark_index.faiss`
- `data/faiss_index/metadata.pkl`
- `data/faiss_index/landmark_stats.pkl`

其中：
- 模型文件需要手动放到 `algorithm/models/dinov2_model.pth`
- FAISS 索引和地标统计文件由 `POST /api/v1/index/rebuild` 根据本地数据集生成

## 当前版本能力

当前 `algorithm/` 的能力不是单纯的基础检索，而是三条并行能力：

1. 基础地标检索
   - DINOv2 提取 768 维 CLS 特征
   - FAISS 做地标级召回
   - 马氏距离映射为经验匹配分 `score`
   - 返回 Top-5 地标候选

2. SAR 增强搜索
   - `POST /api/v1/search/sar`
   - 对测试样本计算归一化熵 `entropy`
   - 返回 `trustLevel` 和 `trustScore`
   - 对低熵样本执行在线轻量更新

3. 用户反馈分流
   - `POST /api/v1/feedback`
   - 不直接信任用户标签
   - 返回 `feedbackTrust`、`pending`、`modelEntropy`、`modelTrust`
   - 阻止错误标签或恶意反馈直接污染索引

## 技术路线

当前实现采用两条核心链路。

### 搜索链路

DINOv2 特征提取 -> FAISS 地标召回 -> 马氏距离经验匹配分 -> Top-K 分数归一化熵 -> `trustLevel` / `trustScore`

### 模型链路

归一化 CLS 特征 -> 与搜索侧一致的马氏距离熵构造 -> SARAdapter -> SAM 更新归一化层参数

关键点：
- 搜索侧和模型侧都使用 L2 归一化后的 CLS 特征
- SAR 只对低熵样本执行在线更新
- feedback 接口与 SAR 更新解耦，先分流再决定是否接受

## 前置要求

### 1. 模型文件

- 模型名称：DINOv2 ViT-B/14
- 文件格式：`.pth`
- 文件大小：约 330MB
- 存放位置：`algorithm/models/dinov2_model.pth`

可参考来源：
- Facebook DINOv2 官方仓库
- HuggingFace `facebook/dinov2-base`

### 2. 地标图片数据集

数据集目录应放在：`datasets/landmarks/`

目录示例：

```text
datasets/landmarks/
├── L01_library/
├── L02_academic_auditorium/
├── L03_wenyong_square/
└── ...
```

要求：
- 每个地标一个目录
- 目录名前缀使用地标编号，如 `L01_...`
- 每个地标至少准备多角度、多光照图片

## 快速开始

### 1. 安装依赖

```bash
cd algorithm
pip install -r requirements.txt
```

默认安装 CPU 版本依赖。GPU 运行请参考 `GPU_SUPPORT.md`。

### 2. 验证模型文件

```bash
python verify_model.py ./models/dinov2_model.pth
```

### 3. 配置环境变量

```bash
cp .env.example .env
```

常用配置：
- `DEVICE=auto|cpu|cuda`
- `IMAGE_SIZE=518`
- `BATCH_SIZE=32`

### 4. 启动服务

```bash
python app/main.py
```

当前版本启动时会做这些事情：
- 验证模型文件
- 尝试读取 `data/faiss_index/` 下已有索引和统计文件
- 初始化 SAR 提取器和 SAR 适配器
- 初始化基础搜索服务和 SAR 搜索服务

如果 `landmark_stats.pkl`、`metadata.pkl` 或 `landmark_index.faiss` 损坏，服务可能无法正常读取索引。此时建议删除 `data/faiss_index/` 下损坏文件，再重新构建索引。

### 5. 重建索引

```bash
curl.exe -X POST http://localhost:8000/api/v1/index/rebuild
```

PowerShell 中请使用 `curl.exe`，不要直接用 `curl` 别名。

### 6. 基础检索测试

```powershell
curl.exe -X POST http://localhost:8000/api/v1/search -F "file=@test.jpg"
```

### 7. SAR 检索测试

```powershell
curl.exe -X POST "http://localhost:8000/api/v1/search/sar?use_sar=true" -F "file=@test.jpg"
```

## API 接口

### 1. 健康检查

```text
GET /api/v1/health
```

典型返回：

```json
{
  "status": "healthy",
  "service": "CampusLens AI Search",
  "version": "1.0.0",
  "sarAvailable": true
}
```

### 2. 基础地标检索

```text
POST /api/v1/search
Content-Type: multipart/form-data
```

返回字段：
- `results`
- `lowConfidence`
- `message`

`results` 中每一项包含：
- `rank`
- `landmarkCode`
- `landmarkName`
- `score`
- `confidenceLevel`
- `mahalanobisDistance`

说明：
- `score` 是基于马氏距离映射得到的经验匹配分，不是概率
- `confidenceLevel` 是 `high` / `medium` / `low`
- `mahalanobisDistance` 越小，越接近该地标分布中心

### 3. SAR 增强搜索

```text
POST /api/v1/search/sar
Content-Type: multipart/form-data
```

查询参数：
- `use_sar=true|false`

除基础检索字段外，还会返回：
- `sarEnabled`
- `entropy`
- `trustLevel`
- `trustScore`

典型返回：

```json
{
  "results": [
    {
      "rank": 1,
      "landmarkCode": "L05",
      "landmarkName": "qin_lake_huxin_island",
      "score": 0.9999,
      "confidenceLevel": "high",
      "mahalanobisDistance": 36.7681
    }
  ],
  "lowConfidence": false,
  "sarEnabled": true,
  "entropy": 0.580008,
  "trustLevel": "uncertain",
  "trustScore": 0.419992,
  "message": "Search successful"
}
```

字段说明：
- `entropy`：基于 Top-K 经验匹配分构造的归一化熵，范围 `[0, 1]`
- `trustLevel`：`trusted` / `uncertain` / `untrusted`
- `trustScore`：简化信任分，当前为 `1 - entropy`

默认门控阈值：
- `entropy < 0.35`：`trusted`
- `0.35 <= entropy < 0.60`：`uncertain`
- `entropy >= 0.60`：`untrusted`

### 4. 用户反馈分流

```text
POST /api/v1/feedback
Content-Type: multipart/form-data
```

查询参数：
- `landmark_code`：用户确认的地标编号，如 `L01`
- `update_index=true|false`

返回字段通常包括：
- `success`
- `message`
- `landmarkCode`
- `modelPrediction`
- `modelScore`
- `modelEntropy`
- `modelTrust`
- `mahalanobisDistance`
- `labelConfidence`
- `feedbackTrust`
- `pending`
- `index_updated`
- `sar_ema`

说明：
- 反馈不会直接视为真值写入模型
- 系统会结合当前模型预测、模型熵和马氏距离做分流
- 典型分流结果：`accepted` / `review` / `pending`

### 5. 重置 SAR 状态

```text
POST /api/v1/sar/reset
```

作用：
- 重置 SAR 适配器状态
- 便于重复实验和调试

### 6. 重建索引

```text
POST /api/v1/index/rebuild
```

作用：
- 重新提取全部数据集特征
- 重建 FAISS 索引
- 重建 `metadata.pkl` 和 `landmark_stats.pkl`

### 7. 查询索引状态

```text
GET /api/v1/index/stats
```

典型返回：

```json
{
  "status": "ready",
  "totalVectors": 10,
  "dimension": 768,
  "indexedLandmarks": 10
}
```

## 当前算法说明

### 基础检索

当前基础检索不是简单余弦相似度排序，而是：

1. DINOv2 提取 768 维图像特征
2. 计算每个地标的均值向量和协方差矩阵
3. 使用马氏距离衡量查询图与地标分布的接近程度
4. 用 sigmoid 把距离转换成经验匹配分
5. 按分数返回 Top-K 地标候选

### SAR 测试时自适应

当前 SAR 实现不是标准分类器训练流程，而是适配当前检索场景的测试时自适应。

实现方式：
- 使用归一化 CLS 特征
- 对地标匹配分做归一化熵构造
- 用低熵样本作为可靠样本
- 用 SAM 更新归一化层参数
- 通过 EMA 观察模型更新状态

### 反馈分流

反馈链路的目标是防污染，而不是立即训练。

策略：
- 用户反馈先与模型预测做比对
- 再结合模型熵和马氏距离判断一致性
- 高风险反馈进入 `review` 或 `pending`
- 只有足够可信的反馈才允许进入后续更新路径

## 调试与验证

### 健康检查

```bash
python check_service.py
```

### SAR 调试

```powershell
python debug_sar.py --image ..datasets编号11_琴湖及湖心岛_19.jpg
```

`debug_sar.py` 同时检查两层内容：

1. API CHECK
   - `health`
   - `index stats`
   - `search`
   - `search/sar`
   - `feedback`

2. MODEL CHECK
   - `Mahalanobis distances`
   - `Match scores`
   - `Normalized entropy`
   - `param delta total`
   - `ema history`
   - `feature drift`

### 如何判断当前 SAR 是否生效

可参考这些现象：
- `search/sar` 返回 `entropy`、`trustLevel`、`trustScore`
- 模型侧马氏距离与搜索侧量级一致
- 模型侧归一化熵不再恒为 `1.0`
- `param delta total` 为非零
- `ema` 会随迭代更新
- `feature drift` 会逐步累积

## 当前实现边界

当前版本已经验证 SAR 在线更新可运行，但仍有明确边界：

- 主要更新归一化层参数，不做全模型训练
- 熵阈值、温度和 score 中心仍是经验值，需要后续标定
- 反馈分流已完成，但没有数据库持久化审核队列
- 当前更适合实验验证、鲁棒性测试和课程项目演示，不是生产级在线学习系统

## 相关文档

- `SAR_IMPLEMENTATION.md`：当前 SAR 技术路线与实现说明
- `IMPLEMENTATION.md`：基础检索算法实现详解
- `GPU_SUPPORT.md`：GPU 运行说明
- `CHECKLIST.md`：本地检查项
