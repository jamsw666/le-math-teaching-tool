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

/** 07：教材示例感画布（节标题 / 例题框 / 插图，非空白编辑器） */
export function LessonCanvasView({
  lesson,
  canvas,
  onBack,
  onWorkbench,
  onProjection,
}: CanvasProps) {
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
          <span>教材示例组织 · 拖块仅为示意（本原型固定排布）</span>
          <span className="muted" style={{ marginLeft: 'auto' }}>
            块：情境 → 旧知 → 新问 → 目标
          </span>
        </div>

        <div className="lesson-canvas" role="region" aria-label="课时画布">
          {canvas.blocks.map((b) => (
            <div
              key={b.id}
              className={`canvas-block canvas-block--${b.tagTone ?? 'orange'}`}
              style={{ left: b.x, top: b.y, width: b.w }}
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
          ))}
          <p className="canvas-tip-footer muted">课时：{lesson.title}</p>
        </div>
      </div>
    </div>
  )
}
