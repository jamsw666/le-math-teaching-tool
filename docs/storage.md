# 本地存储方案（MVP）

- 作者：后端工程师 · 2026-10-11 Asia/Shanghai
- 对齐：`specs/math-teaching-tool-architecture.md`（SQLite 壳内）、产品 I1–I6 / C2–C3
- **无 Spring / 无 Java / 无 HTTP 账号服务**

---

## 1. 引擎与路径

| 项 | 约定 |
|---|---|
| 引擎 | **SQLite**（单文件，纯本地） |
| 运行态（Tauri） | 应用数据目录，例如 `%APPDATA%/math-teaching-tool/app.db`（Win）；由壳侧打开 |
| 开发态 | `packages/core/storage/data/app.db`（本仓库 gitignore） |
| 同步 | **不做**多机同步、不做账号 |

开发态原型使用 **sql.js**（纯 JS，免原生编译）；发版壳内可用 rusqlite / tauri-plugin-sql，**schema 共用** `packages/core/storage/schema.sql`。

---

## 2. 表设计草案

### 2.1 `lessons`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | TEXT PK | 课时 ID |
| title | TEXT | 标题 |
| chapter | TEXT | 章节（如「函数与导数 · 切线」） |
| tags_json | TEXT | 标签 JSON 数组 |
| reveal_index | INTEGER | 当前已揭示到的步骤 ordinal（含）；**-1 = 未揭示** |
| source | TEXT | `builtin` \| `user` |
| created_at | TEXT | ISO8601 |
| updated_at | TEXT | ISO8601 |

### 2.2 `lesson_steps`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | TEXT PK | 步骤 ID |
| lesson_id | TEXT FK | → lessons.id |
| ordinal | INTEGER | 步序（0-based） |
| kind | TEXT | `concept` \| `example` \| `exercise` \| `board` |
| content_json | TEXT | **结构化模板 JSON**（见下）；禁止要求老师写 LaTeX |

`content_json` 形状（摘要）：

```json
{
  "title": "例：求切线斜率",
  "blocks": [
    { "kind": "text", "text": "…" },
    { "kind": "template", "templateId": "power_rule", "slots": { "n": "2" }, "texInternal": "f'(x)=2x" }
  ],
  "answerBlocks": [ { "kind": "text", "text": "…" } ],
  "animateOnReveal": true
}
```

- 老师编辑面只碰 `templateId` + `slots` / 文本；`texInternal` 仅供渲染引擎。
- `answerBlocks` 用于练习答案；投影默认不展示，直至揭示逻辑允许。

### 2.3 `board_snapshots`（可选）

备课板书快照；MVP 可空表预留：`id`, `lesson_id`, `label`, `payload_json`, `created_at`。

---

## 3. I1：未揭示步骤不得进入投影读取路径

| 路径 | 行为 |
|---|---|
| 编辑态 `get_lesson` | 返回**全量**步骤（备课需要） |
| 投影态 `get_projection_view` | **仅** `ordinal <= reveal_index` 的步骤；未揭示步骤不出现在响应中 |
| `set_reveal_index` | 更新 `lessons.reveal_index`；前端以该值为唯一数据源绑定 CSS 步进 |

实现要点：过滤在**仓库层**完成，不依赖 UI 隐藏（防读屏/误挂载泄露）。

---

## 4. 导入 / 导出

| 方向 | 说明 |
|---|---|
| `content/samples/*.json` → SQLite | `seed-from-samples.mjs` / `import_sample`；`source=builtin` |
| SQLite → JSON | `export_lesson`；便于备份自建课 |

samples 必须自编；版权见 `content/README.md`。

---

## 5. MVP 明确不做

- 多机同步、账号、云端
- Spring Boot / Maven / 独立 Java API 进程
- 教材原文入库

---

## 6. 自测

```bash
# Rust（推荐 · I1）
cd src-tauri && cargo test

# sql.js 开发态
cd packages/core && npm run demo

# 前端 adapter 冒烟
cd apps/web && node scripts/smoke-adapter.mjs
```

详见根 `README.md`、`src-tauri/README.md`。
