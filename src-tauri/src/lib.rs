//! math-teaching-tool 本地存储库（rusqlite）。
//!
//! - 默认：`cargo test` / `cargo check`（无需 WebView 系统库）
//! - 桌面壳：`cargo build --features desktop` 或 Windows 上 `npm run tauri build`
//!   （见 `docs/packaging-windows.md`）

pub mod commands;
pub mod error;
pub mod models;
pub mod storage;

pub use commands::*;
pub use error::{Result, StorageError};
pub use models::*;
pub use storage::Store;
