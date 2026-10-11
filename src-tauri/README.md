# src-tauri — 本地存储 + Tauri 2 壳

- **默认（无 WebView）**：`cargo test` — rusqlite 存储与 I1 投影过滤
- **桌面壳**：`--features desktop` + `tauri.conf.json` + `src/main.rs`  
  完整打 Win 包见仓库 `docs/packaging-windows.md`

## 自测（任意有 Rust 的机器）

```bash
cd src-tauri
cargo test
# 不要在无系统 WebView 库时加 --features desktop
```

## Commands

与 `docs/local-api.md` 同名；壳内由 `main.rs` 挂 `#[tauri::command]`，状态为 `Mutex<Store>`，库文件在 `app_data_dir/app.db`。

| Command | 说明 |
|---|---|
| `list_lessons` / `get_lesson` / `get_projection_view` | I1 投影过滤 |
| `save_lesson` / `set_reveal_index` / `delete_lesson` | 写 |
| `import_sample` / `export_lesson` | 样例 |

空库首次启动会导入 `fixtures/lesson-derivative-tangent.json`。
