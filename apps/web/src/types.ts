/** 课时与工作台领域类型（可点原型） */

export interface ConceptDiagram {
  /** 图示类型：生活 / 图像 / 符号 */
  kind: 'life' | 'graph' | 'symbol'
  caption: string
  /** 内置 SVG 图示 id，由组件渲染 */
  illustrationId: string
}

export interface ConceptPoint {
  id: string
  title: string
  /** 内部 TeX，不在老师 UI 展示源码 */
  bodyTex?: string
  bodyText: string
  /** 与「下一步」同步的示例图 */
  diagram?: ConceptDiagram
}

export interface StepItem {
  id: string
  caption: string
  formulaTex?: string
  /** 是否使用关键步骤动画 */
  animate?: boolean
}

export interface ExampleItem {
  id: string
  title: string
  stem: string
  stemTex?: string
  steps: StepItem[]
  answerTex: string
  answerText: string
}

export interface PracticeItem {
  id: string
  question: string
  questionTex?: string
  answerTex: string
  answerText: string
}

/** 画布块：教材示例感（情境/旧知/新问/目标/提示） */
export interface CanvasBlock {
  id: string
  tag: string
  tagTone?: 'orange' | 'green' | 'blue' | 'amber'
  title: string
  body: string
  formulaPlain?: string
  illustrationId?: string
  x: number
  y: number
  w: number
}

export interface LessonCanvas {
  chapterLabel: string
  blocks: CanvasBlock[]
  goals: string[]
  tip?: string
}

export interface Lesson {
  id: string
  title: string
  subtitle: string
  gradeHint: string
  sourceLabel: string
  concepts: ConceptPoint[]
  examples: ExampleItem[]
  practices: PracticeItem[]
  canvas?: LessonCanvas
}

export type WorkbenchTab = 'canvas' | 'concepts' | 'examples' | 'practice' | 'formula'
export type ViewMode = 'list' | 'import' | 'workbench' | 'projection'

export type AnnotColor = '#ef4444' | '#fbbf24' | '#60a5fa' | '#22c55e'
export type AnnotWidth = 3 | 6
