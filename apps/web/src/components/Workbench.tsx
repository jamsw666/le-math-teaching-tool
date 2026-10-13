import type { Lesson, WorkbenchTab } from '../types'
import { ConceptCards } from './ConceptCards'
import { ExamplePanel } from './ExamplePanel'
import { PracticePanel } from './PracticePanel'
import { FormulaBar } from './FormulaBar'
import { LessonCanvasView } from './ImportCanvas'
import { APP_NAME, APP_ABOUT } from '../appMeta'

interface Props {
  lesson: Lesson
  tab: WorkbenchTab
  conceptReveal: number
  exampleIndex: number
  stepReveal: number
  showAnswer: boolean
  skipAnimation: boolean
  onTab: (t: WorkbenchTab) => void
  onBack: () => void
  onProjection: () => void
  onOpenCanvas: () => void
  onConceptRevealMore: () => void
  onConceptRevealLess: () => void
  onConceptReset: () => void
  onPrevExample: () => void
  onNextExample: () => void
  onPrevStep: () => void
  onNextStep: () => void
  onToggleAnswer: () => void
  onSkipAnimation: () => void
  onOpenScratch?: () => void
  onCloseScratch?: () => void
  scratchOpen?: boolean
  onSettings?: () => void
}

const TABS: { id: WorkbenchTab; label: string }[] = [
  { id: 'canvas', label: '画布' },
  { id: 'concepts', label: '概念' },
  { id: 'examples', label: '例题' },
  { id: 'practice', label: '练习' },
  { id: 'formula', label: '公式编辑' },
]

export function Workbench(props: Props) {
  const { lesson, tab, onTab, onBack, onProjection } = props

  if (tab === 'canvas' && lesson.canvas) {
    return (
      <LessonCanvasView
        lesson={lesson}
        canvas={lesson.canvas}
        onBack={onBack}
        onWorkbench={() => onTab('concepts')}
        onProjection={onProjection}
      />
    )
  }

  return (
    <div className="workbench">
      <header className="workbench-head">
        <div>
          <button type="button" className="btn btn--ghost" onClick={onBack}>
            ← 课时列表
          </button>
          <h1>{lesson.title}</h1>
          <p className="muted">{lesson.subtitle}</p>
        </div>
        <div className="workbench-actions">
          {lesson.canvas && (
            <button type="button" className="btn" onClick={props.onOpenCanvas}>
              画布
            </button>
          )}
          <button
            type="button"
            className={`btn ${props.scratchOpen ? 'btn--primary' : ''}`}
            onClick={() =>
              props.scratchOpen ? props.onCloseScratch?.() : props.onOpenScratch?.()
            }
          >
            草稿
          </button>
          {props.onSettings && (
            <button type="button" className="btn" onClick={props.onSettings}>
              设置
            </button>
          )}
          <button type="button" className="btn btn--primary" onClick={onProjection}>
            进入投影
          </button>
        </div>
      </header>

      <nav className="tab-bar" aria-label="工作台分区">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            className={`tab ${tab === t.id ? 'tab--active' : ''}`}
            onClick={() => onTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </nav>

      <main className="workbench-main">
        {tab === 'concepts' && (
          <ConceptCards
            concepts={lesson.concepts}
            revealIndex={props.conceptReveal}
            skipAnimation={props.skipAnimation}
            onRevealMore={props.onConceptRevealMore}
            onRevealLess={props.onConceptRevealLess}
            onReset={props.onConceptReset}
          />
        )}
        {tab === 'examples' && (
          <ExamplePanel
            examples={lesson.examples}
            exampleIndex={props.exampleIndex}
            revealIndex={props.stepReveal}
            showAnswer={props.showAnswer}
            skipAnimation={props.skipAnimation}
            onPrevExample={props.onPrevExample}
            onNextExample={props.onNextExample}
            onPrevStep={props.onPrevStep}
            onNextStep={props.onNextStep}
            onToggleAnswer={props.onToggleAnswer}
            onSkipAnimation={props.onSkipAnimation}
          />
        )}
        {tab === 'practice' && <PracticePanel practices={lesson.practices} />}
        {tab === 'formula' && <FormulaBar />}
        {tab === 'canvas' && !lesson.canvas && (
          <p className="muted empty-hint">本课时暂无画布布局。</p>
        )}
      </main>

      <footer className="page-footer">
        <p><strong>关于 · {APP_NAME}</strong> · {APP_ABOUT}</p>
      </footer>
    </div>
  )
}
