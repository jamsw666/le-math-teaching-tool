/**
 * 本地仓库接口（非 HTTP）。
 * 实现：开发态 Node/SQLite；运行态 Tauri commands → 壳内 SQLite。
 */
import type {
  Lesson,
  LessonMeta,
  LessonSampleFile,
  ProjectionLessonView,
} from "./types.js";

export interface LessonRepository {
  listLessons(): Promise<LessonMeta[]>;
  getLesson(id: string): Promise<Lesson | null>;
  /** I1：投影路径只返回已揭示步骤 */
  getProjectionView(id: string): Promise<ProjectionLessonView | null>;
  saveLesson(lesson: Lesson): Promise<void>;
  setRevealIndex(id: string, revealIndex: number): Promise<void>;
  deleteLesson(id: string): Promise<void>;
  importSample(sample: LessonSampleFile): Promise<Lesson>;
  exportLesson(id: string): Promise<LessonSampleFile | null>;
}

/** 对应 src-tauri 侧 command 名（见 docs/local-api.md） */
export const TAURI_COMMANDS = [
  "list_lessons",
  "get_lesson",
  "get_projection_view",
  "save_lesson",
  "set_reveal_index",
  "delete_lesson",
  "import_sample",
  "export_lesson",
] as const;

export type TauriCommandName = (typeof TAURI_COMMANDS)[number];
