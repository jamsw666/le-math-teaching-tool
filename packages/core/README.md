# @math-teaching-tool/core

课时领域模型、`revealIndex` / I1 校验约定、本地仓库接口。

## 给前端

- 类型：`packages/core/src/types.ts`
- 仓库接口：`packages/core/src/repository.ts`
- 本地 API 草案：`docs/local-api.md`
- 存储方案：`docs/storage.md`

## 存储原型（开发态）

```bash
cd packages/core
npm run demo
# 等价：node storage/seed-from-samples.mjs && node storage/query-demo.mjs
```

开发库文件默认：`packages/core/storage/data/app.db`（gitignore）。
