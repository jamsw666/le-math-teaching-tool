# 阿乐老师的数学课堂

课堂投影、练习与备课。纯本地 Windows 程序（Tauri 2 + React）。  
仓库：<https://github.com/jamsw666/le-math-teaching-tool>

MVP **没有 Spring Boot**，没有账号，也没有多机同步。`services/api/` 只是预留目录，不要假设 `http://localhost:8080` 一类的 Spring API。

## 目录

```
apps/web/                 # React + Vite 前端（含 api/lessonClient）
packages/core/            # 课时模型、仓库接口、SQLite schema/种子（sql.js）
packages/formula/ …       # 公式 / 可视化 / 步进（占位）
src-tauri/                # rusqlite 存储库 + commands（可 cargo test）
content/samples/          # 自编样例课时 JSON
services/api/             # 预留，MVP 不上 Spring Boot
docs/packaging-windows.md # Windows 打包
docs/storage.md
docs/local-api.md
prototypes/               # 线框
```

## 存储

- 引擎：SQLite。开发态用 sql.js / 浏览器内存 mock；桌面运行态在壳内用 rusqlite。
- 投影读取路径按 `reveal_index` 过滤未揭示步骤。

## 浏览器开发

在 `apps/web` 里安装依赖并启动 Vite：

```bash
cd apps/web
npm install
npm run dev
```

构建与适配器冒烟（不启浏览器）：

```bash
cd apps/web
npm run build
node scripts/smoke-adapter.mjs
```

列表页左上角会显示数据源：`内存 mock` 或 `Tauri SQLite`。  
存在 `window.__TAURI_INTERNALS__` 时走 Tauri `invoke`，否则用 `MemoryLessonStore`。

挂到 Tauri 的前端生产构建：

```bash
cd apps/web && npm run prepare:tauri
```

产物在 `apps/web/dist/`（相对路径 assets），并写出 `dist/.tauri-frontend-ready.json`。壳的 `frontendDist` 指向 `../apps/web/dist`。说明见 `docs/frontend-tauri-mount.md`。

Rust 存储库：

```bash
cd src-tauri && cargo test
```

可选的开发态 sql.js 种子：

```bash
cd packages/core && npm install && npm run demo
```

## 给前端

1. 对接入口：`apps/web/src/api/lessonClient.ts`（`getLessonClient()`）
2. UI 的 `Lesson`（concepts / examples / practices）和 core 的扁平 `steps` 不是同一形状；`api/mapper.ts` 做近似映射。富演示数据仍以 `src/data/lessons.ts` 为准（同 id 优先）。
3. 投影步进：UI 本地 `stepReveal` 会同步 `setRevealIndex`；严格按揭示下标读取用 `getProjectionView`。

## 获取源码与 Windows 打包

```bash
git clone https://github.com/jamsw666/le-math-teaching-tool.git
cd le-math-teaching-tool
```

在 **Windows x64** 上按 [`docs/packaging-windows.md`](docs/packaging-windows.md) 安装 Node、Rust ≥ 1.90、WebView2 和 VS Build Tools，然后：

```powershell
npm install
npm --prefix apps\web install
npm run tauri:build
```

云端 Linux 不能交叉编出真正的 `.exe`。存储库仍可在本机执行 `cd src-tauri && cargo test`。

## 版权

`content/samples` 为团队自编演示，不是教材原文。详见 `content/README.md`。
