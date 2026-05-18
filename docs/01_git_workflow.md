# Git 分支与协作规则

## 核心原则

- 分支按 M1-M5 垂直功能模块划分。
- 目录按 `frontend/`、`backend/`、`algorithm/`、`database/`、`docs/` 技术工程划分。
- 所有个人分支从 `dev` 拉出，完成后合回 `dev`。
- `main` 只保存每周可提交版本，不接受个人分支直接合并。

## 分支和权限

| 分支 | 创建人 | 可写权限 | 合并权限 |
| --- | --- | --- | --- |
| `main` | 仓库初始化者或马启凡 | 只建议马启凡可写 | 只允许从 `dev` 合入 |
| `dev` | 马启凡 | 组长或经审核成员 | 个人分支经检查后合入 |
| `feature/m1-search-maqifan` | 马启凡 | 马启凡 | 合入 `dev` |
| `feature/m2-landmark-yebingliang` | 叶炳良 | 叶炳良 | 合入 `dev` |
| `feature/m3-vision-zhouzidong` | 周子栋 | 周子栋 | 合入 `dev` |
| `feature/m4-result-map-hongchuankai` | 洪传凯 | 洪传凯 | 合入 `dev` |
| `feature/m5-feedback-zhuangzijie` | 庄子杰 | 庄子杰 | 合入 `dev` |

如使用 Gitee/GitHub，建议设置：

- `main`：保护分支，禁止直接 push，必须由组长合并。
- `dev`：保护分支，允许组长合并；成员通过 Pull Request/Merge Request 提交。
- `feature/*`：成员自行维护，可自由 push，但不得强推覆盖他人提交。

## 创建分支

组长创建 `dev`：

```bash
git checkout main
git pull origin main
git checkout -b dev
git push -u origin dev
```

成员创建个人分支：

```bash
git checkout dev
git pull origin dev
git checkout -b feature/m1-search-maqifan
git push -u origin feature/m1-search-maqifan
```

## 同步最新代码

每天开发前：

```bash
git checkout dev
git pull origin dev
git checkout feature/m1-search-maqifan
git merge dev
```

如果出现冲突，优先保留已在 `dev` 集成通过的公共接口、数据字段和目录结构，再调整个人模块代码。

## 提交规范

提交信息建议格式：

```text
type(scope): summary
```

常用类型：

- `feat`：新增功能
- `fix`：修复问题
- `docs`：文档更新
- `api`：接口契约更新
- `db`：数据库脚本更新
- `refactor`：重构
- `test`：测试

示例：

```bash
git commit -m "feat(search): add image upload endpoint"
git commit -m "api(search): update top5 response schema"
git commit -m "docs(dataset): add landmark image naming rules"
```

## 合入 dev

个人分支准备合入 `dev` 前：

```bash
git checkout dev
git pull origin dev
git checkout feature/m1-search-maqifan
git merge dev
# 解决冲突并自测
git push
```

合并方式：

```bash
git checkout dev
git pull origin dev
git merge --no-ff feature/m1-search-maqifan
git push origin dev
```

推荐由组长执行合并，或通过平台 Merge Request 合并。

## 合入 main

每周验收前，确认 `dev` 可运行后由组长执行：

```bash
git checkout main
git pull origin main
git merge --no-ff dev
git tag v1-week1
git push origin main --tags
```

后续版本标签建议：

- `v1-week1`
- `v2-week2`
- `v3-week3`
- `v4-week4`
- `v5-final`

## 禁止事项

- 禁止个人分支直接合入 `main`。
- 禁止未同步最新 `dev` 就发起合并。
- 禁止随意修改他人模块的核心文件；确需修改时先在群内说明。
- 禁止把大体积样本图片、模型权重、临时输出、IDE 私有配置直接提交到仓库。
