/**
 * 与 packages/core 对齐的存储形状（本地副本，避免 Vite 跨包路径债）。
 * 对齐债：UI Lesson（concepts/examples/practices）≠ 本模型（扁平 steps）。
 */

export type StepKind = 'concept' | 'example' | 'exercise' | 'board'

export type BoardBlock =
  | { kind: 'text'; text: string }
  | {
      kind: 'template'
      templateId: string
      slots: Record<string, string>
      texInternal?: string
    }

export type StepContent = {
  title?: string
  blocks: BoardBlock[]
  answerBlocks?: BoardBlock[]
  animateOnReveal?: boolean
}

export type LessonStep = {
  id: string
  lessonId: string
  ordinal: number
  kind: StepKind
  content: StepContent
}

export type LessonMeta = {
  id: string
  title: string
  chapter: string
  tags: string[]
  revealIndex: number
  source: 'builtin' | 'user'
  createdAt: string
  updatedAt: string
}

export type CoreLesson = LessonMeta & { steps: LessonStep[] }

export type ProjectionLessonView = {
  meta: LessonMeta
  revealedSteps: LessonStep[]
}

export type LessonSampleFile = {
  id: string
  title: string
  chapter: string
  tags: string[]
  copyright?: string
  steps: Array<{
    id: string
    ordinal: number
    kind: StepKind
    content: StepContent
  }>
}

export interface LessonRepository {
  listLessons(): Promise<LessonMeta[]>
  getLesson(id: string): Promise<CoreLesson | null>
  getProjectionView(id: string): Promise<ProjectionLessonView | null>
  saveLesson(lesson: CoreLesson): Promise<void>
  setRevealIndex(id: string, revealIndex: number): Promise<void>
  deleteLesson(id: string): Promise<void>
  importSample(sample: LessonSampleFile): Promise<CoreLesson>
  exportLesson(id: string): Promise<LessonSampleFile | null>
}

export type ClientMode = 'tauri' | 'mock'
