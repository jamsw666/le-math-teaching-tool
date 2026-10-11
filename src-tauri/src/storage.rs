//! SQLite 仓库实现（rusqlite bundled）。
//! 启动执行 schema.sql；I1：get_projection_view 仅返回 ordinal <= reveal_index。

use crate::error::{Result, StorageError};
use crate::models::{
    Lesson, LessonMeta, LessonSampleFile, LessonStep, OkResponse, ProjectionLessonView,
};
use rusqlite::{params, Connection, OptionalExtension};
use std::path::Path;

const SCHEMA: &str = include_str!("../schema.sql");

pub struct Store {
    conn: Connection,
}

impl Store {
    pub fn open_memory() -> Result<Self> {
        let conn = Connection::open_in_memory()?;
        let store = Self { conn };
        store.migrate()?;
        Ok(store)
    }

    pub fn open(path: impl AsRef<Path>) -> Result<Self> {
        let conn = Connection::open(path)?;
        let store = Self { conn };
        store.migrate()?;
        Ok(store)
    }

    pub fn migrate(&self) -> Result<()> {
        self.conn.execute_batch(SCHEMA)?;
        Ok(())
    }

    pub fn list_lessons(&self) -> Result<Vec<LessonMeta>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, title, chapter, tags_json, reveal_index, source, created_at, updated_at
             FROM lessons ORDER BY updated_at DESC, id ASC",
        )?;
        let rows = stmt.query_map([], |row| Self::meta_from_row(row))?;
        let mut out = Vec::new();
        for r in rows {
            out.push(r?);
        }
        Ok(out)
    }

    pub fn get_lesson(&self, id: &str) -> Result<Option<Lesson>> {
        let meta = match self.get_meta(id)? {
            Some(m) => m,
            None => return Ok(None),
        };
        let steps = self.get_steps(id, None)?;
        Ok(Some(Lesson { meta, steps }))
    }

    /// I1：仅 ordinal <= reveal_index；reveal_index < 0 时 revealed_steps 为空。
    pub fn get_projection_view(&self, id: &str) -> Result<Option<ProjectionLessonView>> {
        let meta = match self.get_meta(id)? {
            Some(m) => m,
            None => return Ok(None),
        };
        let steps = self.get_steps(id, Some(meta.reveal_index))?;
        Ok(Some(ProjectionLessonView {
            meta,
            revealed_steps: steps,
        }))
    }

    pub fn save_lesson(&self, lesson: &Lesson) -> Result<()> {
        let tags = serde_json::to_string(&lesson.meta.tags)?;
        self.conn.execute(
            "INSERT INTO lessons (id, title, chapter, tags_json, reveal_index, source, created_at, updated_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
             ON CONFLICT(id) DO UPDATE SET
               title=excluded.title,
               chapter=excluded.chapter,
               tags_json=excluded.tags_json,
               reveal_index=excluded.reveal_index,
               source=excluded.source,
               updated_at=excluded.updated_at",
            params![
                lesson.meta.id,
                lesson.meta.title,
                lesson.meta.chapter,
                tags,
                lesson.meta.reveal_index,
                lesson.meta.source,
                lesson.meta.created_at,
                lesson.meta.updated_at,
            ],
        )?;
        self.conn
            .execute("DELETE FROM lesson_steps WHERE lesson_id = ?", [&lesson.meta.id])?;
        for step in &lesson.steps {
            let content = serde_json::to_string(&step.content)?;
            self.conn.execute(
                "INSERT INTO lesson_steps (id, lesson_id, ordinal, kind, content_json)
                 VALUES (?1, ?2, ?3, ?4, ?5)",
                params![step.id, lesson.meta.id, step.ordinal, step.kind, content],
            )?;
        }
        Ok(())
    }

    pub fn set_reveal_index(&self, id: &str, reveal_index: i64) -> Result<OkResponse> {
        let n = self.conn.execute(
            "UPDATE lessons SET reveal_index = ?1, updated_at = ?2 WHERE id = ?3",
            params![reveal_index, now_iso(), id],
        )?;
        if n == 0 {
            return Err(StorageError::NotFound(id.to_string()));
        }
        Ok(OkResponse { ok: true })
    }

    pub fn delete_lesson(&self, id: &str) -> Result<OkResponse> {
        let n = self
            .conn
            .execute("DELETE FROM lessons WHERE id = ?", [id])?;
        if n == 0 {
            return Err(StorageError::NotFound(id.to_string()));
        }
        Ok(OkResponse { ok: true })
    }

    pub fn import_sample(&self, sample: &LessonSampleFile) -> Result<Lesson> {
        let ts = now_iso();
        let tags = serde_json::to_string(&sample.tags)?;
        self.conn.execute(
            "INSERT INTO lessons (id, title, chapter, tags_json, reveal_index, source, created_at, updated_at)
             VALUES (?1, ?2, ?3, ?4, -1, 'builtin', ?5, ?6)
             ON CONFLICT(id) DO UPDATE SET
               title=excluded.title,
               chapter=excluded.chapter,
               tags_json=excluded.tags_json,
               source='builtin',
               updated_at=excluded.updated_at",
            params![sample.id, sample.title, sample.chapter, tags, ts, ts],
        )?;
        self.conn
            .execute("DELETE FROM lesson_steps WHERE lesson_id = ?", [&sample.id])?;
        for step in &sample.steps {
            let content = serde_json::to_string(&step.content)?;
            self.conn.execute(
                "INSERT INTO lesson_steps (id, lesson_id, ordinal, kind, content_json)
                 VALUES (?1, ?2, ?3, ?4, ?5)",
                params![step.id, sample.id, step.ordinal, step.kind, content],
            )?;
        }
        self.get_lesson(&sample.id)?
            .ok_or_else(|| StorageError::NotFound(sample.id.clone()))
    }

    pub fn export_lesson(&self, id: &str) -> Result<Option<LessonSampleFile>> {
        let lesson = match self.get_lesson(id)? {
            Some(l) => l,
            None => return Ok(None),
        };
        Ok(Some(LessonSampleFile {
            id: lesson.meta.id,
            title: lesson.meta.title,
            chapter: lesson.meta.chapter,
            tags: lesson.meta.tags,
            copyright: "导出自本地库；请确认内容来源与版权声明。".to_string(),
            steps: lesson
                .steps
                .into_iter()
                .map(|s| crate::models::LessonSampleStep {
                    id: s.id,
                    ordinal: s.ordinal,
                    kind: s.kind,
                    content: s.content,
                })
                .collect(),
        }))
    }

    fn get_meta(&self, id: &str) -> Result<Option<LessonMeta>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, title, chapter, tags_json, reveal_index, source, created_at, updated_at
             FROM lessons WHERE id = ?",
        )?;
        let meta = stmt
            .query_row([id], |row| Self::meta_from_row(row))
            .optional()?;
        Ok(meta)
    }

    /// max_ordinal: Some(n) → ordinal <= n；n < 0 → 空；None → 全量
    fn get_steps(&self, lesson_id: &str, max_ordinal: Option<i64>) -> Result<Vec<LessonStep>> {
        let (sql, bind_max): (&str, Option<i64>) = match max_ordinal {
            None => (
                "SELECT id, lesson_id, ordinal, kind, content_json
                 FROM lesson_steps WHERE lesson_id = ?1 ORDER BY ordinal ASC",
                None,
            ),
            Some(n) if n < 0 => {
                return Ok(Vec::new());
            }
            Some(n) => (
                "SELECT id, lesson_id, ordinal, kind, content_json
                 FROM lesson_steps WHERE lesson_id = ?1 AND ordinal <= ?2 ORDER BY ordinal ASC",
                Some(n),
            ),
        };

        let mut stmt = self.conn.prepare(sql)?;
        let map_row = |row: &rusqlite::Row| -> rusqlite::Result<LessonStep> {
            let content_json: String = row.get(4)?;
            let content: serde_json::Value =
                serde_json::from_str(&content_json).unwrap_or(serde_json::Value::Null);
            Ok(LessonStep {
                id: row.get(0)?,
                lesson_id: row.get(1)?,
                ordinal: row.get(2)?,
                kind: row.get(3)?,
                content,
            })
        };

        let rows = if let Some(n) = bind_max {
            stmt.query_map(params![lesson_id, n], map_row)?
        } else {
            stmt.query_map(params![lesson_id], map_row)?
        };

        let mut out = Vec::new();
        for r in rows {
            out.push(r?);
        }
        Ok(out)
    }

    fn meta_from_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<LessonMeta> {
        let tags_json: String = row.get(3)?;
        let tags: Vec<String> = serde_json::from_str(&tags_json).unwrap_or_default();
        Ok(LessonMeta {
            id: row.get(0)?,
            title: row.get(1)?,
            chapter: row.get(2)?,
            tags,
            reveal_index: row.get(4)?,
            source: row.get(5)?,
            created_at: row.get(6)?,
            updated_at: row.get(7)?,
        })
    }
}

fn now_iso() -> String {
    // 无 chrono 依赖：用简单 UTC 近似；测试不依赖精确时钟格式以外的值
    use std::time::{SystemTime, UNIX_EPOCH};
    let secs = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    format!("{secs}Z")
}
