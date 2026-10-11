//! Tauri command 薄封装（与 docs/local-api.md 同名）。
//! 完整 `#[tauri::command]` 壳后续接入；此处供库内调用与单测。

use crate::error::Result;
use crate::models::{
    Lesson, LessonMeta, LessonSampleFile, OkResponse, ProjectionLessonView,
};
use crate::storage::Store;

pub fn list_lessons(store: &Store) -> Result<Vec<LessonMeta>> {
    store.list_lessons()
}

pub fn get_lesson(store: &Store, id: &str) -> Result<Option<Lesson>> {
    store.get_lesson(id)
}

pub fn get_projection_view(store: &Store, id: &str) -> Result<Option<ProjectionLessonView>> {
    store.get_projection_view(id)
}

pub fn save_lesson(store: &Store, lesson: &Lesson) -> Result<()> {
    store.save_lesson(lesson)
}

pub fn set_reveal_index(store: &Store, id: &str, reveal_index: i64) -> Result<OkResponse> {
    store.set_reveal_index(id, reveal_index)
}

pub fn delete_lesson(store: &Store, id: &str) -> Result<OkResponse> {
    store.delete_lesson(id)
}

pub fn import_sample(store: &Store, sample: &LessonSampleFile) -> Result<Lesson> {
    store.import_sample(sample)
}

pub fn export_lesson(store: &Store, id: &str) -> Result<Option<LessonSampleFile>> {
    store.export_lesson(id)
}
