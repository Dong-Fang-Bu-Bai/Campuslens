# 前端设计

## 页面组织

页面围绕检索、地图、历史、社区、账号与管理组织。主要组件位于 [frontend/src/components](../frontend/src/components/)，应用入口为 [App.vue](../frontend/src/App.vue)。

| 组件 | 用途 |
| --- | --- |
| AuthPanel | 登录与注册 |
| AccountPanel、AvatarCropper、UserAvatar | 账号资料与头像 |
| HistoryView | 检索历史 |
| LandmarkModal | 地标详情 |
| CheckInBoard | 社区打卡与讨论 |
| AdminPanel | 管理功能 |
| InteractiveBackground、PreferencesDock | 背景与显示偏好 |

## 检索状态

上传后显示排队与处理状态，终态为成功、低匹配等级或失败。只有成功或低匹配等级结果展示候选；失败时显示错误信息并提供重试入口。

候选卡片显示地标名称、经验匹配分、匹配等级、简介及地图、反馈入口。普通和 SAR 模式共用任务提交与轮询流程。

## 地图与详情

地图素材为 [campus-map.png](assets/campus-map.png)，前端资源位于 [frontend/public](../frontend/public/)。地标位置使用 0–100 的百分比坐标，随底图尺寸变化缩放。

点击候选或地图标注查看详情，页面在上传、结果和地图间保持检索上下文。

## 账号与社区

登录用户可查看服务端检索历史；游客使用后端分配的持久化编号。发布打卡需关联本人检索记录和候选地标，照片展示由发布者选择。社区列表加载摘要，详情页加载回复树。

## 网络与资源

前端使用 HTTPS，并同源代理后端接口和上传图片。地标展示优先使用后端图片，资源加载失败时显示占位或错误状态。

运行与构建见[前端工程说明](../frontend/README.md)。
