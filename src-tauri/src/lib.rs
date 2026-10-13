//! math-teaching-tool 本地存储库（rusqlite）+ ExplanationProvider（ai）。
//!
//! - 默认：`cargo test` / `cargo check`（无需 WebView 系统库）
//! - 桌面壳：`cargo build --features desktop` 或 Windows 上 `npm run tauri build`
//!   （见 `docs/packaging-windows.md`）

pub mod ai;
pub mod commands;
pub mod error;
pub mod models;
pub mod storage;

pub use ai::{
    ai_clear_api_key, ai_explain, ai_get_settings, ai_has_api_key, ai_save_settings, ai_set_api_key,
    AiPaths, AiSettings, ExplainRequest, ExplainResult, ExplainScope,
};
pub use commands::*;
pub use error::{Result, StorageError};
pub use models::*;
pub use storage::Store;
