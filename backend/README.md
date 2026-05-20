# CampusLens Backend

Spring Boot 后端最小工程，用于初始阶段 M1/M2/M5 的接口联调和第二周主流程开发准备。

## 技术栈

- Java 17+
- Spring Boot 3.3.6
- Maven 3.9+

## 启动

```powershell
mvn spring-boot:run
```

默认地址：

```text
http://localhost:8080
```

健康检查：

```text
GET http://localhost:8080/api/health
```

## 已提供接口

| 接口 | 说明 |
| --- | --- |
| `GET /api/health` | 后端健康检查 |
| `GET /api/landmarks` | 获取首批 L01-L10 地标列表 |
| `GET /api/landmarks/{id}` | 获取地标详情与地图坐标 |
| `POST /api/search/upload` | 上传图片并返回 Top-5 演示检索结果 |
| `POST /api/feedback` | 提交正确、错误或不确定反馈 |

## 当前实现边界

当前版本服务于第一周初始阶段交付，采用内存地标数据和演示 Top-5 结果。`SearchService` 已保留算法服务集成边界，第二周可替换为调用 `http://localhost:8000/api/v1/search`，并将检索记录和反馈记录落库到 MySQL。

上传图片会保存到 `uploads/yyyyMMdd/`，该目录已由 `.gitignore` 排除。

## 验证

```powershell
mvn test
```
