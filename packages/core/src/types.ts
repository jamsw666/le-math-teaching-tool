/**
 * 课时 / 步骤领域模型（MVP）
 * 对齐产品 I1–I6、架构 packages/core
 */

/** 模板块：老师侧不暴露 LaTeX；内部可选 tex 供渲染引擎 */
export type FormulaBlock = {
  kind: "template";
  templateId: string;
  slots: Record<string, string>;
  /** 仅供 KaTeX 等引擎；UI 默认不展示 */
  texInternal?: string;
};

export type PlainBlock = {
  kind: "text";
  text: string;
};

export type BoardBlock = FormulaBlock | PlainBlock;

export type StepKind = "concept" | "example" | "exercise" | "board";

/** 单步内容：结构化 JSON，禁止要求老师手写 LaTeX */
export type StepContent = {
  title?: string;
  blocks: BoardBlock[];
  /** 练习题答案块；投影默认隐藏，由 reveal 控制 */
  answerBlocks?: BoardBlock[];
  /** 是否触发关键步骤动画（产品 DoD） */
  animateOnReveal?: boolean;
};

export type LessonStep = {
  id: string;
  lessonId: string;
  ordinal: number;
  kind: StepKind;
  content: StepContent;
};

export type LessonMeta = {
  id: string;
  title: string;
  chapter: string;
  /** 函数与导数等课标章节标签 */
  tags: string[];
  /** 当前已揭示到的步骤下标（含）；-1 表示尚未揭示 */
  revealIndex: number;
  source: "builtin" | "user";
  createdAt: string;
  updatedAt: string;
};

export type Lesson = LessonMeta & {
  steps: LessonStep[];
};

/** 投影读取视图：仅含 ordinal <= revealIndex 的步骤（I1） */
export type ProjectionLessonView = {
  meta: LessonMeta;
  revealedSteps: LessonStep[];
};

/** content/samples 导入 JSON 形状 */
export type LessonSampleFile = {
  id: string;
  title: string;
  chapter: string;
  tags: string[];
  copyright: string;
  steps: Array<{
    id: string;
    ordinal: number;
    kind: StepKind;
    content: StepContent;
  }>;
};
