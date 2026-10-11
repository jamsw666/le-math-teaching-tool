/**
 * 浏览器 / Vite 开发态内存仓库：行为与 docs/local-api.md 一致（含 I1）。
 */
import type {
  CoreLesson,
  LessonMeta,
  LessonRepository,
  LessonSampleFile,
  ProjectionLessonView,
} from './coreTypes'
import { SAMPLE_LESSONS } from '../data/lessons'
import { uiLessonToCore } from './mapper'
import sampleDerivative from './samples/lesson-derivative-tangent.json'

function cloneLesson(l: CoreLesson): CoreLesson {
  return JSON.parse(JSON.stringify(l)) as CoreLesson
}

export class MemoryLessonStore implements LessonRepository {
  private lessons = new Map<string, CoreLesson>()

  constructor() {
    for (const ui of SAMPLE_LESSONS) {
      const core = uiLessonToCore(ui, -1)
      this.lessons.set(core.id, core)
    }
    const sample = sampleDerivative as LessonSampleFile
    void this.importSample(sample)
  }

  async listLessons(): Promise<LessonMeta[]> {
    return [...this.lessons.values()]
      .map((l) => stripSteps(l))
      .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt) || a.id.localeCompare(b.id))
  }

  async getLesson(id: string): Promise<CoreLesson | null> {
    const l = this.lessons.get(id)
    return l ? cloneLesson(l) : null
  }

  async getProjectionView(id: string): Promise<ProjectionLessonView | null> {
    const l = this.lessons.get(id)
    if (!l) return null
    const meta = stripSteps(l)
    const revealedSteps =
      meta.revealIndex < 0
        ? []
        : l.steps.filter((s) => s.ordinal <= meta.revealIndex).map((s) => ({ ...s }))
    return { meta, revealedSteps }
  }

  async saveLesson(lesson: CoreLesson): Promise<void> {
    const copy = cloneLesson(lesson)
    copy.updatedAt = new Date().toISOString()
    this.lessons.set(copy.id, copy)
  }

  async setRevealIndex(id: string, revealIndex: number): Promise<void> {
    const l = this.lessons.get(id)
    if (!l) throw new Error(`lesson not found: ${id}`)
    l.revealIndex = revealIndex
    l.updatedAt = new Date().toISOString()
  }

  async deleteLesson(id: string): Promise<void> {
    if (!this.lessons.delete(id)) throw new Error(`lesson not found: ${id}`)
  }

  async importSample(sample: LessonSampleFile): Promise<CoreLesson> {
    const now = new Date().toISOString()
    const lesson: CoreLesson = {
      id: sample.id,
      title: sample.title,
      chapter: sample.chapter,
      tags: sample.tags ?? [],
      revealIndex: -1,
      source: 'builtin',
      createdAt: now,
      updatedAt: now,
      steps: sample.steps.map((s) => ({
        id: s.id,
        lessonId: sample.id,
        ordinal: s.ordinal,
        kind: s.kind,
        content: s.content,
      })),
    }
    this.lessons.set(lesson.id, lesson)
    return cloneLesson(lesson)
  }

  async exportLesson(id: string): Promise<LessonSampleFile | null> {
    const l = this.lessons.get(id)
    if (!l) return null
    return {
      id: l.id,
      title: l.title,
      chapter: l.chapter,
      tags: l.tags,
      copyright: '导出自本地内存库；请确认内容来源与版权声明。',
      steps: l.steps.map((s) => ({
        id: s.id,
        ordinal: s.ordinal,
        kind: s.kind,
        content: s.content,
      })),
    }
  }
}

function stripSteps(l: CoreLesson): LessonMeta {
  return {
    id: l.id,
    title: l.title,
    chapter: l.chapter,
    tags: l.tags,
    revealIndex: l.revealIndex,
    source: l.source,
    createdAt: l.createdAt,
    updatedAt: l.updatedAt,
  }
}
