/**
 * 冒烟：内存仓库 I1（不启 Vite）
 * 用法：node scripts/smoke-adapter.mjs
 */
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const sample = JSON.parse(
  readFileSync(
    join(__dirname, '../src/api/samples/lesson-derivative-tangent.json'),
    'utf8',
  ),
)

/** @type {Map<string, any>} */
const lessons = new Map()

function importSample(s) {
  const lesson = {
    id: s.id,
    title: s.title,
    chapter: s.chapter,
    tags: s.tags ?? [],
    revealIndex: -1,
    source: 'builtin',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    steps: s.steps.map((st) => ({
      id: st.id,
      lessonId: s.id,
      ordinal: st.ordinal,
      kind: st.kind,
      content: st.content,
    })),
  }
  lessons.set(lesson.id, lesson)
  return lesson
}

function getProjectionView(id) {
  const l = lessons.get(id)
  if (!l) return null
  const revealedSteps =
    l.revealIndex < 0 ? [] : l.steps.filter((s) => s.ordinal <= l.revealIndex)
  return { revealedSteps }
}

importSample(sample)
const id = sample.id
if (getProjectionView(id).revealedSteps.length !== 0) throw new Error('I1 empty fail')
lessons.get(id).revealIndex = 2
const proj = getProjectionView(id)
if (proj.revealedSteps.length !== 3) throw new Error('I1 filter len fail')
if (!proj.revealedSteps.every((s) => s.ordinal <= 2)) throw new Error('I1 ordinal fail')
console.log('smoke-adapter OK:', id, 'revealed=', proj.revealedSteps.length)
