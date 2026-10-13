# 阿乐老师的数学课堂（math-teaching-tool）

课堂投影 + 练习 + 备课；纯本地 Win exe（Tauri 2 + React）。  
规格：`../specs/math-teaching-tool.md` · 架构：`../specs/math-teaching-tool-architecture.md`

## 目录摘要

```
math-teaching-tool/
  apps/web/                 # React + Vite 前端（含 api/lessonClient）
  packages/core/            # 课时模型、仓库接口、SQLite schema/种子（sql.js）
  packages/formula/ …       # 公式 / 可视化 / 步进（占位）
  src-tauri/                # rusqlite 存储库 + commands（可 cargo test）
  content/samples/          # 自编样例课时 JSON
  services/api/             # 预留，MVP 不上 Spring
  docs/storage.md
  docs/local-api.md
  prototypes/               # 线框（勿改坏）
```

## 存储摘要

- 引擎：SQLite；开发态 sql.js / 浏览器内存 mock；运行态壳内 rusqlite
- I1：投影读取路径按 `reveal_index` 过滤未揭示步骤
- MVP：**无** Spring、**无** 账号、**无** 多机同步

## 启动与自测

### 前端（Vite · 浏览器 mock）

```bash
cd apps/web && npm install && npm run dev
# 构建
npm run build
# adapter I1 冒烟（不启浏览器）
node scripts/smoke-adapter.mjs
```

列表页左上角会显示数据源：`内存 mock` 或 `Tauri SQLite`。  
切换规则：存在 `window.__TAURI_INTERNALS__` → Tauri `invoke`；否则 mock（`MemoryLessonStore`）。


### 前端生产构建（挂 Tauri）

```bash
cd apps/web && npm run prepare:tauri
# 产物：dist/（相对路径 assets）+ dist/.tauri-frontend-ready.json
# 壳挂载：src-tauri → frontendDist = ../apps/web/dist
# 说明：docs/frontend-tauri-mount.md
```

### Rust 本地存储（rusqlite）

```bash
cd src-tauri && cargo test
```

### 开发态 sql.js 种子（可选）

```bash
cd packages/core && npm install && npm run demo
```

## 给前端的注意

1. 对接入口：`apps/web/src/api/lessonClient.ts`（`getLessonClient()`）
2. **类型对齐债**：UI `Lesson`（concepts/examples/practices）≠ core 扁平 `steps`；`api/mapper.ts` 做近似映射；富演示数据仍以 `src/data/lessons.ts` 为准（同 id 优先）
3. 投影步进：UI 本地 `stepReveal` 会同步 `setRevealIndex`；严格 I1 读取用 `getProjectionView`
4. **不要**假设 `http://localhost:8080` Spring API


## AI 释义（ExplanationProvider）

- Tauri 命令：`ai_get_settings` / `ai_save_settings` / `ai_set_api_key` / `ai_clear_api_key` / `ai_has_api_key` / `ai_explain`
- 实现：`src-tauri/src/ai.rs`；契约见 `../specs/math-teaching-tool-explanation-provider.md`
- 密钥仅存应用私有目录；`ai_has_api_key` 只回 bool。**勿把密钥放 localStorage**
- 详对接：`docs/local-api.md` §5

## 版权

`content/samples` 为团队自编演示，非教材原文。详见 `content/README.md`。

## 如何拷走 / 如何在 Windows 打包

1. **源码包**：`/workspace/tech-team/math-teaching-tool-src.tar.gz`（或本目录 `git clone` / 拷贝；已排除 `node_modules`、`target`）
2. 在 **Windows x64** 解压后按 **`docs/packaging-windows.md`** 安装 Node / Rust≥1.90 / WebView2 / VS Build Tools，然后：

```powershell
npm install
npm --prefix apps\web install
npm run tauri:build
```

3. 云端 Linux **不能**交叉编出真正 `.exe`（无 mingw / WebView2 / 足够新的 rustc 编 Tauri 壳）。存储库仍可 `cd src-tauri && cargo test`。
4. MVP **不上 Spring**。
