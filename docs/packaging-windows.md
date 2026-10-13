# Windows 本机打包说明（Tauri 2 → exe / NSIS）

- 作者：后端工程师 · 2026-10-11 Asia/Shanghai
- 目标产物：`阿乐老师的数学课堂` 安装包（NSIS）或便携 exe
- **云端 Linux box 无法交叉编出真正的 Win `.exe`**（见文末「为何云端打不出」）

---

## 0. 本仓库关系

```
math-teaching-tool/           ← 解压/克隆后的根目录
  apps/web/                   ← React + Vite 前端（beforeBuildCommand 会 npm run build）
  src-tauri/                  ← Rust：rusqlite 存储库 + Tauri 壳（feature = desktop）
  docs/packaging-windows.md   ← 本文
  packages/ / content/ …
```

- MVP **不上 Spring**；勿起 `services/api` Java。
- 浏览器开发：`cd apps/web && npm run dev`（内存 mock）。
- 桌面运行态：壳内 SQLite（`app_data_dir/app.db`）+ `invoke` commands。

---

## 1. Windows 环境准备

### 1.1 必装

| 组件 | 说明 |
|---|---|
| **Node.js 20+** | [nodejs.org](https://nodejs.org/) LTS |
| **Rust stable ≥ 1.90** | [rustup.rs](https://rustup.rs/)；当前 Tauri 2.12 锁文件要求 rustc 1.90+（云端 box 为 1.85，勿在云端编壳） |
| **WebView2 Runtime** | Win10/11 通常已带；否则装 [Evergreen Runtime](https://developer.microsoft.com/microsoft-edge/webview2/) |
| **Visual Studio Build Tools** | C++ 桌面开发工作负载（MSVC + Windows SDK）。Rust/Tauri 链接需要 |

可选：`winget install OpenJS.NodeJS.LTS Rustlang.Rustup Microsoft.VisualStudio.2022.BuildTools`

### 1.2 校验

在 **x64 Native Tools / 普通 PowerShell**（已装好 MSVC）中：

```powershell
node -v
npm -v
rustc -V
cargo -V
```

---

## 2. 取代码

若已有源码包：

```powershell
# 例：解压到 D:\dev\
tar -xzf math-teaching-tool-src.tar.gz
cd math-teaching-tool
```

或 `git clone <你们的仓库> && cd math-teaching-tool`。

---

## 3. 安装前端与 Tauri CLI

```powershell
cd apps\web
npm install
npm install -D @tauri-apps/cli@2
npm install @tauri-apps/api@2
cd ..\..
```

根目录也可（任选）：

```powershell
# 在 apps\web 的 package.json scripts 里已可加（见 §5）
```

---

## 4. 一键打包（推荐）

在仓库**根目录**（有根 `package.json` 与 `src-tauri/`）：

```powershell
npm install
npm --prefix apps\web install
npm run tauri:build
```

说明：

- 根脚本：`tauri build --config src-tauri/tauri.conf.json -- --features desktop`
- `beforeBuildCommand` 会跑 `apps/web` 的 `prepare:tauri`（Vite `base: './'` 构建）
- Rust 启用 **`desktop`** feature，编译 `src-tauri/src/main.rs`
- 产物大致在：

```
src-tauri\target\release\阿乐老师的数学课堂.exe
src-tauri\target\release\bundle\nsis\*.exe
```

（具体文件名以 `tauri.conf.json` 的 `productName` / bundle 为准。）

仅开发热重载（不打安装包）：

```powershell
npm run tauri:dev
```

---

## 5. 根目录 scripts（已写入）

仓库根 `package.json` 已含：

- `npm run tauri:dev` — 热重载
- `npm run tauri:build` — NSIS / exe
- `npm run test:storage` — 仅 rusqlite 单测（无需 WebView）

`apps/web/package.json` 另有同名脚本（需加 `--config` 指到 `../../src-tauri` 时再用根脚本更省事）。

---

## 6. 仅测 Rust 存储（无需 WebView）

任意有 Rust 的机器（含本云端 Linux）：

```bash
cd src-tauri
cargo test
# 不要加 --features desktop，除非已装齐系统 WebView 库
```

---

## 7. 自检清单（装完 exe 后）

1. 安装/解压后能启动窗口  
2. 列表页左上角数据源为 **Tauri SQLite**（非「内存 mock」）  
3. 能打开样例课时、投影步进；刷新后数据仍在（本地 `app.db`）  
4. **无** Spring / 无强制联网（WebView2 bootstrapper 仅首次缺运行时时）

---

## 8. 为何云端 Linux 打不出 Win exe

| 检查项 | 云端 box（2026-10-11） | 结论 |
|---|---|---|
| `uname` | Linux x86_64 | 非 Windows 主机 |
| `x86_64-w64-mingw32-gcc` | **无** | 无法链接 Windows 目标 |
| `rustup` / Windows 目标 | **无 rustup**；系统 rustc **1.85.1** | Tauri 2.12 需 **rustc ≥ 1.90**；无 MSVC 目标链 |
| WebView2 / MSVC | 仅 Windows 提供 | 交叉编 Tauri Win 极不现实 |
| `webkit2gtk` 开发头 | **无**（sudo apt 不可用） | 连本机 Linux 调试包也编不了 |
| mingw-w64 | **无** | 无法 `x86_64-pc-windows-gnu` 链接 |

因此：**不在此环境假装产出 `.exe`**。请把 `math-teaching-tool-src.tar.gz` 拷到 Windows，按上文打包。

Linux 本机若要打 **Linux** 调试包（非交付主路径），需先安装：`libwebkit2gtk-4.1-dev`、`librsvg2-dev`、`patchelf` 等，再 `cargo build --features desktop` / `npx tauri build`。

---

## 9. 与前端联调注意

- `apps/web/src/api/lessonClient.ts`：有 `window.__TAURI_INTERNALS__` 时走 `invoke`
- `tauri.conf.json` 已设 `withGlobalTauri: true`；仍建议安装 `@tauri-apps/api`
- command 名见 `docs/local-api.md`（`list_lessons` 等）

## AI 释义 HTTPS（可选）

默认 `ureq` 无 TLS，便于云端 `cargo test`（rustc 1.85）。Windows 本机访问 DeepSeek 等 HTTPS 时，打包启用：

```powershell
cargo tauri build --features "desktop,ai-tls"
# 或在 Cargo.toml / tauri 配置中把 ai-tls 绑进 desktop
```
