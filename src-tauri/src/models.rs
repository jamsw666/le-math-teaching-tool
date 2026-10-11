//! 与 packages/core 类型 / docs/local-api.md JSON 形状对齐。

use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LessonMeta {
    pub id: String,
    pub title: String,
    pub chapter: String,
    pub tags: Vec<String>,
    pub reveal_index: i64,
    pub source: String,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LessonStep {
    pub id: String,
    pub lesson_id: String,
    pub ordinal: i64,
    pub kind: String,
    pub content: Value,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Lesson {
    #[serde(flatten)]
    pub meta: LessonMeta,
    pub steps: Vec<LessonStep>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProjectionLessonView {
    pub meta: LessonMeta,
    pub revealed_steps: Vec<LessonStep>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LessonSampleFile {
    pub id: String,
    pub title: String,
    pub chapter: String,
    pub tags: Vec<String>,
    #[serde(default)]
    pub copyright: String,
    pub steps: Vec<LessonSampleStep>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LessonSampleStep {
    pub id: String,
    pub ordinal: i64,
    pub kind: String,
    pub content: Value,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OkResponse {
    pub ok: bool,
}
