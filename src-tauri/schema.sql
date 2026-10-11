-- MVP 本地 SQLite schema（壳内 / 开发态共用）
-- 引擎：SQLite；无账号、无多机同步

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS lessons (
  id            TEXT PRIMARY KEY,
  title         TEXT NOT NULL,
  chapter       TEXT NOT NULL,
  tags_json     TEXT NOT NULL DEFAULT '[]',
  reveal_index  INTEGER NOT NULL DEFAULT -1,
  source        TEXT NOT NULL CHECK (source IN ('builtin', 'user')),
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS lesson_steps (
  id            TEXT PRIMARY KEY,
  lesson_id     TEXT NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
  ordinal       INTEGER NOT NULL,
  kind          TEXT NOT NULL CHECK (kind IN ('concept', 'example', 'exercise', 'board')),
  content_json  TEXT NOT NULL,
  UNIQUE (lesson_id, ordinal)
);

CREATE INDEX IF NOT EXISTS idx_steps_lesson_ordinal
  ON lesson_steps(lesson_id, ordinal);

-- 可选：板书/快照（备课恢复）；MVP 可空表先建
CREATE TABLE IF NOT EXISTS board_snapshots (
  id            TEXT PRIMARY KEY,
  lesson_id     TEXT NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
  label         TEXT NOT NULL DEFAULT '',
  payload_json  TEXT NOT NULL,
  created_at    TEXT NOT NULL
);
