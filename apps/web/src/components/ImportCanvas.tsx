import { useCallback, useEffect, useRef, useState } from 'react'
import type { Lesson, LessonCanvas } from '../types'
import { Illustration } from './Illustrations'
import { DEMO_IMPORT_PACK } from '../data/lessons'
import { APP_NAME } from '../appMeta'

interface ImportProps {
  onOpenLesson: (id: string) => void
  onCancel: () => void
  lessons: Lesson[]
}

/** 01 延伸：课程导入入口 → 选包打开画布 */
export function ImportFlow({ onOpenLesson, onCancel, lessons }: ImportProps) {
  return (
    <div className="import-flow">
      <header className="app-topbar">
        <div className="brand">{APP_NAME}</div>
        <span className="topbar-sub">导入课程包</span>
        <nav className="top-nav">
          <span className="on">导入</span>
          <span>画布</span>
        </nav>
        <div className="topbar-right">
          <button type="button" className="btn btn--ghost" onClick={onCancel}>
            取消
          </button>
        </div>
      </header>

      <div className="import-body">
        <aside className="import-aside card-panel">
          <p className="label">可选课包（演示）</p>
          <div className="import-pack">
            <h2>{DEMO_IMPORT_PACK.name}</h2>
            <ul>
              {DEMO_IMPORT_PACK.lessons.map((n) => (
                <li key={n}>{n}</li>
              ))}
            </ul>
            <p className="muted small">{DEMO_IMPORT_PACK.note}</p>
          </div>
          <p className="muted small">真实环境将支持选择本地课包文件；本原型用内置样例模拟。</p>
        </aside>

        <main className="import-main">
          <div className="flow-steps">
            <span className="flow-step on">1 选课包</span>
            <span className="flow-arrow">→</span>
            <span className="flow-step on">2 预览</span>
            <span className="flow-arrow">→</span>
            <span className="flow-step">3 打开到画布</span>
          </div>
          <div className="import-preview-grid">
            {lessons.map((l) => (
              <article key={l.id} className="card-panel import-preview-card">
                <span className="chip">{l.sourceLabel}</span>
                <h3>{l.title}</h3>
                <p className="muted">{l.subtitle}</p>
                <p className="muted small">
                  概念 {l.concepts.length} · 例题 {l.examples.length} · 练习 {l.practices.length}
                </p>
                <button
                  type="button"
                  className="btn btn--primary"
                  onClick={() => onOpenLesson(l.id)}
                >
                  打开到画布
                </button>
              </article>
            ))}
          </div>
        </main>
      </div>

      <footer className="page-footer">
        <p>示例为原创教学演示，仅供产品原型验收 · 非教材官方附属</p>
      </footer>
    </div>
  )
}

interface CanvasProps {
  lesson: Lesson
  canvas: LessonCanvas
  onBack: () => void
  onWorkbench: () => void
  onProjection: () => void
}

const GOAL_MARKS = ['①', '②', '③', '④', '⑤']

type PosMap = Record<string, { x: number; y: number }>

function posKey(lessonId: string) {
  return `math-canvas-pos:${lessonId}`
}

function loadPositions(lessonId: string, canvas: LessonCanvas): PosMap {
  const base: PosMap = {}
  for (const b of canvas.blocks) base[b.id] = { x: b.x, y: b.y }
  try {
    const raw = localStorage.getItem(posKey(lessonId))
    if (!raw) return base
    const saved = JSON.parse(raw) as PosMap
    return { ...base, ...saved }
  } catch {
    return base
  }
}

function savePositions(lessonId: string, positions: PosMap) {
  localStorage.setItem(posKey(lessonId), JSON.stringify(positions))
}

/** 07：教材示例感画布 · S1 纸感块 + 可拖 · 坐标按课时存 localStorage */
export function LessonCanvasView({
  lesson,
  canvas,
  onBack,
  onWorkbench,
  onProjection,
}: CanvasProps) {
  const [positions, setPositions] = useState<PosMap>(() =>
    loadPositions(lesson.id, canvas),
  )
  const dragRef = useRef<{
    id: string
    ox: number
    oy: number
    startX: number
    startY: number
  } | null>(null)
  const [draggingId, setDraggingId] = useState<string | null>(null)

  useEffect(() => {
    setPositions(loadPositions(lesson.id, canvas))
  }, [lesson.id, canvas])

  const onPointerDown = useCallback(
    (e: React.PointerEvent, id: string) => {
      if ((e.target as HTMLElement).closest('button, a, input')) return
      e.preventDefault()
      const pos = positions[id]
      if (!pos) return
      ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
      dragRef.current = {
        id,
        ox: pos.x,
        oy: pos.y,
        startX: e.clientX,
        startY: e.clientY,
      }
      setDraggingId(id)
    },
    [positions],
  )

  const onPointerMove = useCallback((e: React.PointerEvent) => {
    const d = dragRef.current
    if (!d) return
    const dx = e.clientX - d.startX
    const dy = e.clientY - d.startY
    const next = {
      x: Math.max(0, d.ox + dx),
      y: Math.max(0, d.oy + dy),
    }
    setPositions((prev) => ({ ...prev, [d.id]: next }))
  }, [])

  const onPointerUp = useCallback(
    (e: React.PointerEvent) => {
      if (!dragRef.current) return
      try {
        ;(e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId)
      } catch {
        /* ignore */
      }
      dragRef.current = null
      setDraggingId(null)
      setPositions((prev) => {
        savePositions(lesson.id, prev)
        return prev
      })
    },
    [lesson.id],
  )

  const resetLayout = () => {
    const base: PosMap = {}
    for (const b of canvas.blocks) base[b.id] = { x: b.x, y: b.y }
    setPositions(base)
    savePositions(lesson.id, base)
  }

  return (
    <div className="canvas-page">
      <header className="app-topbar">
        <div className="brand">{APP_NAME}</div>
        <span className="topbar-sub">课程导入 · 画布备课</span>
        <span className="topbar-sub">{canvas.chapterLabel}</span>
        <div className="topbar-right">
          <button type="button" className="btn btn--ghost" onClick={onBack}>
            ← 课时列表
          </button>
          <button type="button" className="btn" onClick={onWorkbench}>
            进入工作台
          </button>
          <button type="button" className="btn btn--primary" onClick={onProjection}>
            进入课堂投影
          </button>
        </div>
      </header>

      <div className="canvas-wrap">
        <div className="canvas-toolbar">
          <span className="chip">画布布局</span>
          <span>纸感卡片 · 拖动块可改位置（按课时记住）</span>
          <button type="button" className="btn btn--ghost" onClick={resetLayout}>
            重置位置
          </button>
          <span className="muted" style={{ marginLeft: 'auto' }}>
            块：情境 → 旧知 → 新问 → 目标
          </span>
        </div>

        <div className="lesson-canvas" role="region" aria-label="课时画布">
          {canvas.blocks.map((b) => {
            const pos = positions[b.id] ?? { x: b.x, y: b.y }
            return (
              <div
                key={b.id}
                className={`canvas-block canvas-block--${b.tagTone ?? 'orange'} ${
                  draggingId === b.id ? 'canvas-block--dragging' : ''
                }`}
                style={{ left: pos.x, top: pos.y, width: b.w }}
                onPointerDown={(e) => onPointerDown(e, b.id)}
                onPointerMove={onPointerMove}
                onPointerUp={onPointerUp}
                onPointerCancel={onPointerUp}
              >
                <div className="canvas-tag">{b.tag}</div>
                <h3>{b.title}</h3>
                <p>{b.body}</p>
                {b.formulaPlain && <div className="canvas-formula">{b.formulaPlain}</div>}
                {b.illustrationId && (
                  <div className="canvas-illus">
                    <Illustration id={b.illustrationId} />
                  </div>
                )}
                {b.id === 'b4' && (
                  <div className="canvas-goals">
                    {canvas.goals.map((g, i) => (
                      <div key={g} className="canvas-goal">
                        <b>{GOAL_MARKS[i] ?? `${i + 1}.`}</b> {g}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )
          })}
          <p className="canvas-tip-footer muted">课时：{lesson.title} · 拖拽块移动</p>
        </div>
      </div>
    </div>
  )
}
