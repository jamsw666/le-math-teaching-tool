# 本地接口草案（非 HTTP）

- 作者：后端工程师 · 2026-10-11 Asia/Shanghai
- **无 REST、无 Spring**；`services/api/` 仅预留空目录
- 运行态：Tauri commands ↔ 壳内 SQLite（**Rust 已实现**）
- 开发态：浏览器 **内存 mock** / Node sql.js

类型源：`packages/core/src/types.ts`、`packages/core/src/repository.ts`  
前端副本：`apps/web/src/api/coreTypes.ts`（对齐债见根 README）

---

## 实现状态

| 层 | 路径 | 状态 |
|---|---|---|
| Rust 仓库 + commands | `src-tauri/src/storage.rs`、`commands.rs` | **已实现**；`cargo test` 过 I1 |
| 前端 adapter | `apps/web/src/api/lessonClient.ts` | **已实现**：Tauri `invoke` / 内存 mock 自动切换 |
| 完整 Tauri 壳（WebView） | `src-tauri`（`main.rs` + `desktop` feature） | **源码已补**；云端未产出 exe，Win 本机按 `docs/packaging-windows.md` |
| Spring / Java | — | **不做（MVP）** |

---

## 1. TypeScript 仓库接口

```ts
interface LessonRepository {
  listLessons(): Promise<LessonMeta[]>;
  getLesson(id: string): Promise<Lesson | null>;
  getProjectionView(id: string): Promise<ProjectionLessonView | null>; // I1
  saveLesson(lesson: Lesson): Promise<void>;
  setRevealIndex(id: string, revealIndex: number): Promise<void>;
  deleteLesson(id: string): Promise<void>;
  importSample(sample: LessonSampleFile): Promise<Lesson>;
  exportLesson(id: string): Promise<LessonSampleFile | null>;
}
```

---

## 2. Tauri command 名

| Command | 对应仓库方法 | Rust | 前端 |
|---|---|---|---|
| `list_lessons` | `listLessons` | ✅ | `lessonClient` |
| `get_lesson` | `getLesson` | ✅ | 同上 |
| `get_projection_view` | `getProjectionView` | ✅ I1 | 同上 |
| `save_lesson` | `saveLesson` | ✅ | 同上 |
| `set_reveal_index` | `setRevealIndex` | ✅ | App 步进同步 |
| `delete_lesson` | `deleteLesson` | ✅ | 同上 |
| `import_sample` | `importSample` | ✅ | 同上 |
| `export_lesson` | `exportLesson` | ✅ | 同上 |

前端通过 `@tauri-apps/api/core` 的 `invoke(name, args)` 调用（仅壳内）；纯浏览器走 mock。

---

## 3. 请求 / 响应 JSON 示例

### `list_lessons` →

```json
[
  {
    "id": "sample-derivative-tangent-01",
    "title": "导数与切线（自编样例）",
    "chapter": "函数与导数 · 切线的几何意义",
    "tags": ["函数与导数", "切线", "自编样例"],
    "revealIndex": -1,
    "source": "builtin",
    "createdAt": "2026-10-11T09:00:00.000Z",
    "updatedAt": "2026-10-11T09:00:00.000Z"
  }
]
```

### `get_lesson` ← `{ "id": "sample-derivative-tangent-01" }` →

完整 `Lesson`（含全量 `steps`）。

### `get_projection_view` ← `{ "id": "…" }` →（I1）

```json
{
  "meta": { "id": "…", "revealIndex": 2, "title": "…" },
  "revealedSteps": [ /* 仅 ordinal <= 2 */ ]
}
```

未揭示步骤**不得**出现在 `revealedSteps` 中。

### `set_reveal_index` ←

```json
{ "id": "sample-derivative-tangent-01", "revealIndex": 3 }
```

→ `{ "ok": true }`

---

## 4. 给前端的对接注意

1. **入口**：`getLessonClient()`（`apps/web/src/api/lessonClient.ts`）
2. **切换 mock / tauri**：有 `window.__TAURI_INTERNALS__` → tauri；否则 mock
3. **投影步进**：UI 可调 `setRevealIndex` + 本地 state；严格防泄露用 `getProjectionView`
4. **不要**假设存在 `http://localhost:8080` 等 Spring API
5. 公式块用 `template` + `slots`；老师 UI 不展示 `texInternal`
6. UI 类型与 core 不一致时走 `api/mapper.ts`（对齐债）
