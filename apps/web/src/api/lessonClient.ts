/**
 * 课时客户端：Tauri invoke（若可用）否则 memory mock。
 * 切换：存在 window.__TAURI_INTERNALS__ → tauri；否则 mock。
 */
import type {
  ClientMode,
  CoreLesson,
  LessonMeta,
  LessonRepository,
  LessonSampleFile,
  ProjectionLessonView,
} from './coreTypes'
import { MemoryLessonStore } from './memoryStore'
import { metaToListStub, resolveUiLesson } from './mapper'
import type { Lesson } from '../types'

declare global {
  interface Window {
    __TAURI_INTERNALS__?: unknown
  }
}

export function detectClientMode(): ClientMode {
  if (typeof window !== 'undefined' && window.__TAURI_INTERNALS__) return 'tauri'
  return 'mock'
}

async function tauriInvoke<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  // 动态导入，避免纯浏览器打包硬依赖
  const spec = '@tauri-apps/api/core'
  const mod = (await import(/* @vite-ignore */ spec).catch(() => null)) as {
    invoke?: (c: string, a?: Record<string, unknown>) => Promise<unknown>
  } | null
  if (!mod?.invoke) {
    throw new Error('Tauri API 不可用，请回退 mock 或安装 @tauri-apps/api')
  }
  return (await mod.invoke(cmd, args)) as T
}

class TauriLessonStore implements LessonRepository {
  listLessons() {
    return tauriInvoke<LessonMeta[]>('list_lessons')
  }
  getLesson(id: string) {
    return tauriInvoke<CoreLesson | null>('get_lesson', { id })
  }
  getProjectionView(id: string) {
    return tauriInvoke<ProjectionLessonView | null>('get_projection_view', { id })
  }
  saveLesson(lesson: CoreLesson) {
    return tauriInvoke<void>('save_lesson', { lesson })
  }
  setRevealIndex(id: string, revealIndex: number) {
    return tauriInvoke<void>('set_reveal_index', { id, revealIndex })
  }
  deleteLesson(id: string) {
    return tauriInvoke<void>('delete_lesson', { id })
  }
  importSample(sample: LessonSampleFile) {
    return tauriInvoke<CoreLesson>('import_sample', { sample })
  }
  exportLesson(id: string) {
    return tauriInvoke<LessonSampleFile | null>('export_lesson', { id })
  }
}

export type LessonClient = LessonRepository & {
  mode: ClientMode
  /** UI 友好：列表（含富课时优先） */
  listUiLessons(): Promise<Lesson[]>
  getUiLesson(id: string): Promise<Lesson | null>
}

let singleton: LessonClient | null = null

export function getLessonClient(forceMode?: ClientMode): LessonClient {
  if (singleton && !forceMode) return singleton
  const mode = forceMode ?? detectClientMode()
  const repo: LessonRepository =
    mode === 'tauri' ? new TauriLessonStore() : new MemoryLessonStore()

  const client: LessonClient = {
    mode,
    listLessons: () => repo.listLessons(),
    getLesson: (id) => repo.getLesson(id),
    getProjectionView: (id) => repo.getProjectionView(id),
    saveLesson: (l) => repo.saveLesson(l),
    setRevealIndex: (id, i) => repo.setRevealIndex(id, i),
    deleteLesson: (id) => repo.deleteLesson(id),
    importSample: (s) => repo.importSample(s),
    exportLesson: (id) => repo.exportLesson(id),
    async listUiLessons() {
      const metas = await repo.listLessons()
      const out: Lesson[] = []
      for (const m of metas) {
        const core = await repo.getLesson(m.id)
        out.push(metaToListStub(m, core))
      }
      return out
    },
    async getUiLesson(id: string) {
      const core = await repo.getLesson(id)
      return resolveUiLesson(core)
    },
  }
  if (!forceMode) singleton = client
  return client
}

/** 测试/冒烟用：重置单例 */
export function resetLessonClient() {
  singleton = null
}
