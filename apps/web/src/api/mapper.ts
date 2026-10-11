/**
 * core（扁平 steps）↔ UI Lesson（concepts / examples / practices）
 * 对齐债：映射为演示可用的近似；富 UI 数据仍以 data/lessons.ts 为准。
 */
import type { Lesson, ConceptPoint, ExampleItem, PracticeItem, StepItem } from '../types'
import type { CoreLesson, LessonMeta, LessonStep, StepContent } from './coreTypes'
import { SAMPLE_LESSONS } from '../data/lessons'

function textFromContent(content: StepContent): string {
  const t = content.blocks.find((b) => b.kind === 'text')
  return t && t.kind === 'text' ? t.text : content.title ?? ''
}

function texFromContent(content: StepContent): string | undefined {
  const f = content.blocks.find((b) => b.kind === 'template')
  return f && f.kind === 'template' ? f.texInternal : undefined
}

/** 优先返回前端自带的富课时；否则从 core 近似映射 */
export function resolveUiLesson(core: CoreLesson | null): Lesson | null {
  if (!core) return null
  const rich = SAMPLE_LESSONS.find((l) => l.id === core.id)
  if (rich) return rich
  return coreToUiLesson(core)
}

export function metaToListStub(meta: LessonMeta, core?: CoreLesson | null): Lesson {
  const rich = SAMPLE_LESSONS.find((l) => l.id === meta.id)
  if (rich) return rich
  if (core) return coreToUiLesson(core)
  return {
    id: meta.id,
    title: meta.title,
    subtitle: meta.chapter,
    gradeHint: meta.chapter,
    sourceLabel: meta.source === 'builtin' ? '内置样例' : '自建课时',
    concepts: [],
    examples: [],
    practices: [],
  }
}

export function coreToUiLesson(core: CoreLesson): Lesson {
  const concepts: ConceptPoint[] = []
  const examples: ExampleItem[] = []
  const practices: PracticeItem[] = []

  let currentExample: ExampleItem | null = null

  for (const step of [...core.steps].sort((a, b) => a.ordinal - b.ordinal)) {
    if (step.kind === 'concept' || step.kind === 'board') {
      concepts.push({
        id: step.id,
        title: step.content.title ?? '要点',
        bodyText: textFromContent(step.content),
        bodyTex: texFromContent(step.content),
      })
      continue
    }
    if (step.kind === 'example') {
      const hasTemplate = step.content.blocks.some((b) => b.kind === 'template')
      const isStem = !hasTemplate && !currentExample
      if (isStem || !currentExample) {
        currentExample = {
          id: step.id,
          title: step.content.title ?? '例题',
          stem: textFromContent(step.content),
          stemTex: texFromContent(step.content),
          steps: [],
          answerTex: '',
          answerText: '',
        }
        examples.push(currentExample)
        if (hasTemplate) {
          currentExample.steps.push(stepToUiStep(step))
        }
      } else {
        currentExample.steps.push(stepToUiStep(step))
      }
      continue
    }
    if (step.kind === 'exercise') {
      currentExample = null
      const ans = step.content.answerBlocks?.[0]
      practices.push({
        id: step.id,
        question: textFromContent(step.content),
        questionTex: texFromContent(step.content),
        answerTex: ans && ans.kind === 'template' ? (ans.texInternal ?? '') : '',
        answerText: ans && ans.kind === 'text' ? ans.text : '见课堂讲解',
      })
    }
  }

  return {
    id: core.id,
    title: core.title,
    subtitle: core.chapter,
    gradeHint: core.chapter,
    sourceLabel: core.source === 'builtin' ? '内置样例' : '自建课时',
    concepts,
    examples:
      examples.length > 0
        ? examples
        : [
            {
              id: `${core.id}-empty-ex`,
              title: '（暂无例题）',
              stem: '本课时在存储层尚无 example 步骤。',
              steps: [],
              answerTex: '',
              answerText: '',
            },
          ],
    practices,
  }
}

function stepToUiStep(step: LessonStep): StepItem {
  return {
    id: step.id,
    caption: step.content.title ?? textFromContent(step.content),
    formulaTex: texFromContent(step.content),
    animate: step.content.animateOnReveal ?? false,
  }
}

/** UI 富课时 → core 扁平 steps（供 mock 仓库灌数） */
export function uiLessonToCore(lesson: Lesson, revealIndex = -1): CoreLesson {
  const now = new Date().toISOString()
  const steps: LessonStep[] = []
  let ordinal = 0
  for (const c of lesson.concepts) {
    steps.push({
      id: c.id,
      lessonId: lesson.id,
      ordinal: ordinal++,
      kind: 'concept',
      content: {
        title: c.title,
        blocks: [
          { kind: 'text', text: c.bodyText },
          ...(c.bodyTex
            ? [
                {
                  kind: 'template' as const,
                  templateId: 'inline',
                  slots: {},
                  texInternal: c.bodyTex,
                },
              ]
            : []),
        ],
        animateOnReveal: true,
      },
    })
  }
  for (const ex of lesson.examples) {
    steps.push({
      id: `${ex.id}-stem`,
      lessonId: lesson.id,
      ordinal: ordinal++,
      kind: 'example',
      content: {
        title: ex.title,
        blocks: [
          { kind: 'text', text: ex.stem },
          ...(ex.stemTex
            ? [
                {
                  kind: 'template' as const,
                  templateId: 'inline',
                  slots: {},
                  texInternal: ex.stemTex,
                },
              ]
            : []),
        ],
        animateOnReveal: false,
      },
    })
    for (const s of ex.steps) {
      steps.push({
        id: s.id,
        lessonId: lesson.id,
        ordinal: ordinal++,
        kind: 'example',
        content: {
          title: s.caption,
          blocks: [
            { kind: 'text', text: s.caption },
            ...(s.formulaTex
              ? [
                  {
                    kind: 'template' as const,
                    templateId: 'inline',
                    slots: {},
                    texInternal: s.formulaTex,
                  },
                ]
              : []),
          ],
          animateOnReveal: s.animate ?? true,
        },
      })
    }
  }
  for (const p of lesson.practices) {
    steps.push({
      id: p.id,
      lessonId: lesson.id,
      ordinal: ordinal++,
      kind: 'exercise',
      content: {
        title: '练习',
        blocks: [
          { kind: 'text', text: p.question },
          ...(p.questionTex
            ? [
                {
                  kind: 'template' as const,
                  templateId: 'inline',
                  slots: {},
                  texInternal: p.questionTex,
                },
              ]
            : []),
        ],
        answerBlocks: [{ kind: 'text', text: p.answerText }],
        animateOnReveal: false,
      },
    })
  }
  return {
    id: lesson.id,
    title: lesson.title,
    chapter: lesson.gradeHint || lesson.subtitle,
    tags: ['函数与导数', '自编样例'],
    revealIndex,
    source: 'builtin',
    createdAt: now,
    updatedAt: now,
    steps,
  }
}
