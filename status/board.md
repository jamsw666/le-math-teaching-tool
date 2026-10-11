# math-teaching-tool 打包状态（摘录）

更新：2026-10-11 18:45 Asia/Shanghai

| 任务 | 负责人 | 状态 | 更新 |
|---|---|---|---|
| Win exe / NSIS 打包 | 后端 | **blocked** | 云端 Linux 无法交叉编 Win：无 mingw、无 WebView2/MSVC、rustc 1.85&lt;1.90（Tauri 2.12）、无 webkit2gtk 头。壳源码已就绪（`src-tauri` + `desktop` feature）。步骤：`docs/packaging-windows.md`。源码包：`../math-teaching-tool-src.tar.gz` |
| 存储 `cargo test` | 后端 | done | 不加 desktop feature 可通过 |
| Spring | — | skipped | MVP 不上 |

完整团队看板：`/workspace/tech-team/status/board.md`
