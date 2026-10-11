//! Tauri 2 桌面入口（需 `--features desktop`）。
//! 无桌面库时仍可：`cargo test`（不启 feature）。

#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

#[cfg(feature = "desktop")]
mod desktop {
    use math_teaching_tool_storage::{
        commands, Lesson, LessonMeta, LessonSampleFile, OkResponse, ProjectionLessonView, Store,
    };
    use std::path::PathBuf;
    use std::sync::Mutex;
    use tauri::{Manager, State};

    pub struct AppState {
        pub store: Mutex<Store>,
    }

    fn map_err(e: impl ToString) -> String {
        e.to_string()
    }

    #[tauri::command(rename_all = "camelCase")]
    pub fn list_lessons(state: State<'_, AppState>) -> Result<Vec<LessonMeta>, String> {
        let store = state.store.lock().map_err(map_err)?;
        commands::list_lessons(&store).map_err(map_err)
    }

    #[tauri::command(rename_all = "camelCase")]
    pub fn get_lesson(state: State<'_, AppState>, id: String) -> Result<Option<Lesson>, String> {
        let store = state.store.lock().map_err(map_err)?;
        commands::get_lesson(&store, &id).map_err(map_err)
    }

    #[tauri::command(rename_all = "camelCase")]
    pub fn get_projection_view(
        state: State<'_, AppState>,
        id: String,
    ) -> Result<Option<ProjectionLessonView>, String> {
        let store = state.store.lock().map_err(map_err)?;
        commands::get_projection_view(&store, &id).map_err(map_err)
    }

    #[tauri::command(rename_all = "camelCase")]
    pub fn save_lesson(state: State<'_, AppState>, lesson: Lesson) -> Result<(), String> {
        let store = state.store.lock().map_err(map_err)?;
        commands::save_lesson(&store, &lesson).map_err(map_err)
    }

    #[tauri::command(rename_all = "camelCase")]
    pub fn set_reveal_index(
        state: State<'_, AppState>,
        id: String,
        reveal_index: i64,
    ) -> Result<OkResponse, String> {
        let store = state.store.lock().map_err(map_err)?;
        commands::set_reveal_index(&store, &id, reveal_index).map_err(map_err)
    }

    #[tauri::command(rename_all = "camelCase")]
    pub fn delete_lesson(state: State<'_, AppState>, id: String) -> Result<OkResponse, String> {
        let store = state.store.lock().map_err(map_err)?;
        commands::delete_lesson(&store, &id).map_err(map_err)
    }

    #[tauri::command(rename_all = "camelCase")]
    pub fn import_sample(
        state: State<'_, AppState>,
        sample: LessonSampleFile,
    ) -> Result<Lesson, String> {
        let store = state.store.lock().map_err(map_err)?;
        commands::import_sample(&store, &sample).map_err(map_err)
    }

    #[tauri::command(rename_all = "camelCase")]
    pub fn export_lesson(
        state: State<'_, AppState>,
        id: String,
    ) -> Result<Option<LessonSampleFile>, String> {
        let store = state.store.lock().map_err(map_err)?;
        commands::export_lesson(&store, &id).map_err(map_err)
    }

    fn open_store(app: &tauri::App) -> Result<Store, String> {
        let dir: PathBuf = app
            .path()
            .app_data_dir()
            .map_err(|e| format!("app_data_dir: {e}"))?;
        std::fs::create_dir_all(&dir).map_err(map_err)?;
        let db = dir.join("app.db");
        let store = Store::open(&db).map_err(map_err)?;
        // 空库时灌入内置样例
        if store.list_lessons().map_err(map_err)?.is_empty() {
            let sample_json = include_str!("../fixtures/lesson-derivative-tangent.json");
            if let Ok(sample) = serde_json::from_str::<LessonSampleFile>(sample_json) {
                let _ = store.import_sample(&sample);
            }
        }
        Ok(store)
    }

    pub fn run() {
        tauri::Builder::default()
            .setup(|app| {
                let store = open_store(app)?;
                app.manage(AppState {
                    store: Mutex::new(store),
                });
                Ok(())
            })
            .invoke_handler(tauri::generate_handler![
                list_lessons,
                get_lesson,
                get_projection_view,
                save_lesson,
                set_reveal_index,
                delete_lesson,
                import_sample,
                export_lesson,
            ])
            .run(tauri::generate_context!())
            .expect("error while running tauri application");
    }
}

fn main() {
    #[cfg(feature = "desktop")]
    desktop::run();

    #[cfg(not(feature = "desktop"))]
    {
        eprintln!(
            "math-teaching-tool: 请使用 --features desktop 启动桌面壳，或见 docs/packaging-windows.md"
        );
        std::process::exit(1);
    }
}
