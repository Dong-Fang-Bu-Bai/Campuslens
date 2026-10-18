# 地标检索原理

CampusLens 将照片检索为地标类别。每个地标由样本特征的均值与协方差表示，FAISS 召回候选后使用马氏距离重排序。

## 特征提取

DINOv2 ViT-B/14 提取 768 维 CLS 特征。图像转换为 RGB，缩放后中心裁剪为 518×518，按 ImageNet 均值和标准差归一化。输出特征执行 L2 归一化：

$$
x=\frac{f(I)}{\|f(I)\|_2}.
$$

预处理和特征接口见 [dinov2_extractor.py](app/models/dinov2_extractor.py)。

## 地标统计量

对地标 $c$ 的样本特征 $\{x_i\}_{i=1}^{n_c}$，计算：

$$
\mu_c=\frac{1}{n_c}\sum_i x_i,
\qquad
\Sigma_c=\frac{1}{n_c-1}\sum_i(x_i-\mu_c)(x_i-\mu_c)^\top.
$$

协方差使用正则项：

$$
\widetilde{\Sigma}_c=\Sigma_c+10^{-6}I.
$$

矩阵求逆失败时使用伪逆。均值的 L2 归一化向量用于建立 FAISS `IndexFlatIP`；均值、协方差逆矩阵、样本数与地标名称保存在统计文件中。

样本数小于特征维度时，协方差估计依赖正则化。样本的视角和光照覆盖会影响检索表现。

## 候选召回

查询特征归一化后，在地标中心索引上执行内积检索。候选数量为：

$$
K_{\mathrm{recall}}=\min\{\max(5K,30),C\},
$$

其中 $K$ 为目标返回数量，$C$ 为索引地标数。对于 10 个地标、Top-5 设置，所有地标均进入距离重排序。

## 马氏距离与匹配分

查询特征 $q$ 到候选地标 $c$ 的距离为：

$$
d_c=\sqrt{(q-\mu_c)^\top\widetilde{\Sigma}_c^{-1}(q-\mu_c)}.
$$

距离通过单调映射转换为经验匹配分：

$$
s_c=\frac{1}{1+\exp\left(a[\log(d_c+1)-\log(b+1)]\right)}.
$$

默认 $a=5$，$b=700$，分别由 `MATCH_SCORE_SLOPE` 与 `MATCH_SCORE_CENTER_DISTANCE` 配置。实现对指数范围进行限制，以避免数值溢出。

按匹配分降序返回 Top-K。该映射不是概率校准，不据此给出识别准确率。

| 匹配分 | confidenceLevel |
| --- | --- |
| score ≥ 0.80 | high |
| 0.40 ≤ score < 0.80 | medium |
| score < 0.40 | low |

`CONFIDENCE_THRESHOLD` 为兼容配置；展示等级由 [scoring.py](app/utils/scoring.py) 中的门限决定。

## 索引构建

1. 遍历地标分类目录，提取图像特征。
2. 按地标计算统计量，构建中心向量索引。
3. 保存 FAISS 索引、图片元数据与地标统计。
4. 验证候选模型及索引，原子发布活动版本。

索引、模型和统计参数需要保持版本一致。改变特征模型、预处理或样本集合后重新构建索引。

## 检索结果

候选返回排名、地标编号、名称、经验匹配分、展示等级与马氏距离。后端根据地标编号补齐封面、介绍和地图位置。可选 SAR 轨道的更新与状态管理见 [SAR 说明](SAR_IMPLEMENTATION.md)。

## 实现入口

| 文件 | 功能 |
| --- | --- |
| [faiss_manager.py](app/utils/faiss_manager.py) | 地标统计、中心向量索引、候选召回与重排序 |
| [scoring.py](app/utils/scoring.py) | 匹配分、匹配等级与熵 |
| [search_service.py](app/services/search_service.py) | 检索编排与响应元数据 |
| [feature_service.py](app/services/feature_service.py) | 特征、版本与索引构建 |

数据准备见[数据集规范](../docs/04_dataset_rules.md)，评测方法见[测试说明](../docs/testing.md)。
