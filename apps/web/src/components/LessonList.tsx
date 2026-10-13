import type { Lesson } from '../types'
import { APP_NAME, APP_ABOUT } from '../appMeta'

interface Props {
  lessons: Lesson[]
  onOpen: (id: string) => void
  onOpenCanvas: (id: string) => void
  onProjection: (id: string) => void
  onImport: () => void
  onSettings: () => void
}

export function LessonList({
  lessons,
  onOpen,
  onOpenCanvas,
  onProjection,
  onImport,
  onSettings,
}: Props) {
  return (
    <div className="lesson-list">
      <header className="app-topbar">
        <div className="brand">{APP_NAME}</div>
        <nav className="top-nav">
          <span className="on">课时</span>
          <button type="button" className="nav-link" onClick={onSettings}>
            设置
          </button>
        </nav>
        <div className="topbar-right">
          <span className="muted">离线可用</span>
          <button type="button" className="btn" onClick={onSettings}>
            设置
          </button>
          <button type="button" className="btn" onClick={onImport}>
            导入课程
          </button>
          <button type="button" className="btn btn--primary" disabled title="原型：用内置样例即可">
            ＋ 新建课时
          </button>
        </div>
      </header>

      <div className="list-body">
        <div className="list-head">
          <div>
            <h1>我的课时</h1>
            <p className="muted">函数与导数 · 先深做 1～2 节 · 内置样例 + 导入入口</p>
          </div>
          <p className="muted">纯本地 · 后续进 Tauri Win exe</p>
        </div>

        <ul className="card-grid">
          {lessons.map((lesson) => (
            <li key={lesson.id} className="lesson-card">
              <div className="lesson-card__row">
                <span className="chip">{lesson.sourceLabel}</span>
                <span className="chip chip--ok">可投影</span>
              </div>
              <h2>{lesson.title}</h2>
              <p>{lesson.subtitle}</p>
              <ul className="lesson-card__stats">
                <li>概念 {lesson.concepts.length}</li>
                <li>例题 {lesson.examples.length}</li>
                <li>练习 {lesson.practices.length}</li>
              </ul>
              <div className="lesson-card__actions">
                <button type="button" className="btn btn--primary" onClick={() => onOpen(lesson.id)}>
                  打开
                </button>
                <button type="button" className="btn" onClick={() => onOpenCanvas(lesson.id)}>
                  画布
                </button>
                <button type="button" className="btn" onClick={() => onProjection(lesson.id)}>
                  投影
                </button>
              </div>
            </li>
          ))}
          <li className="lesson-card lesson-card--dashed">
            <span className="chip chip--warn">导入</span>
            <h2>从课包导入到画布</h2>
            <p>教材示例感画布：节标题 / 例题框 / 插图</p>
            <div className="lesson-card__actions">
              <button type="button" className="btn btn--primary" onClick={onImport}>
                开始导入
              </button>
            </div>
          </li>
        </ul>
      </div>

      <footer className="page-footer">
        <p>
          <strong>关于</strong> · {APP_ABOUT}
        </p>
      </footer>
    </div>
  )
}
