//! Tauri 2 桌面入口（需 `--features desktop`）。
//! 无桌面库时仍可：`cargo test`（不启 feature）。

#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

#[cfg(feature = "desktop")]
mod desktop {
    use math_teaching_tool_storage::{
        commands, AiPaths, AiSettings, ExplainRequest, ExplainResult, Lesson, LessonMeta,
        LessonSampleFile, OkResponse, ProjectionLessonView, Store,
    };
    use std::path::PathBuf;
    use std::sync::Mutex;
    use tauri::{Manager, State};

    pub struct AppState {
        pub store: Mutex<Store>,
        pub ai: AiPaths,
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

    // ——— ExplanationProvider（密钥不出 renderer）———

    #[tauri::command(rename_all = "camelCase")]
    pub fn ai_get_settings(state: State<'_, AppState>) -> AiSettings {
        math_teaching_tool_storage::ai_get_settings(&state.ai)
    }

    #[tauri::command(rename_all = "camelCase")]
    pub fn ai_save_settings(state: State<'_, AppState>, settings: AiSettings) -> Result<(), String> {
        math_teaching_tool_storage::ai_save_settings(&state.ai, settings)
    }

    #[tauri::command(rename_all = "camelCase")]
    pub fn ai_set_api_key(state: State<'_, AppState>, api_key: String) -> Result<(), String> {
        math_teaching_tool_storage::ai_set_api_key(&state.ai, api_key)
    }

    #[tauri::command(rename_all = "camelCase")]
    pub fn ai_clear_api_key(state: State<'_, AppState>) -> Result<(), String> {
        math_teaching_tool_storage::ai_clear_api_key(&state.ai)
    }

    #[tauri::command(rename_all = "camelCase")]
    pub fn ai_has_api_key(state: State<'_, AppState>) -> bool {
        math_teaching_tool_storage::ai_has_api_key(&state.ai)
    }

    #[tauri::command(rename_all = "camelCase")]
    pub fn ai_explain(state: State<'_, AppState>, req: ExplainRequest) -> ExplainResult {
        math_teaching_tool_storage::ai_explain(&state.ai, req)
    }

    fn open_store(app: &tauri::App) -> Result<(Store, AiPaths), String> {
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
        let ai_dir = dir.join("ai");
        let ai = AiPaths::new(ai_dir);
        ai.ensure_dir().map_err(map_err)?;
        Ok((store, ai))
    }

    pub fn run() {
        tauri::Builder::default()
            .setup(|app| {
                let (store, ai) = open_store(app)?;
                app.manage(AppState {
                    store: Mutex::new(store),
                    ai,
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
                ai_get_settings,
                ai_save_settings,
                ai_set_api_key,
                ai_clear_api_key,
                ai_has_api_key,
                ai_explain,
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
