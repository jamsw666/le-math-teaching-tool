# 前端资源挂载（Tauri）

## 产物

```bash
cd apps/web
npm run prepare:tauri   # = build + 相对路径校验 + .tauri-frontend-ready.json
```

挂载目录（相对 `src-tauri/`）：

```
frontendDist → ../apps/web/dist
```

校验通过标志：`apps/web/dist/.tauri-frontend-ready.json`（含 `contentHash16`）。

## Vite 约定

- `base: './'`（相对资源，适配 `asset://` / 离线包）
- `outDir: dist`
- 开发：`http://127.0.0.1:5173/`（`npm run dev`）

## 壳侧片段（待完整 WebView 环境时合入 `tauri.conf.json`）

见 `src-tauri/tauri.frontend.fragment.json`。

## 与存储联调

- 浏览器：`MemoryLessonStore`（mock）
- 壳内：`window.__TAURI_INTERNALS__` → `lessonClient` `invoke`（`docs/local-api.md`）
- 可选依赖：`@tauri-apps/api`（`optionalDependencies`）；未装时纯浏览器仍可跑 mock
