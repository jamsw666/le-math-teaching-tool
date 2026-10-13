import { useEffect, useState } from 'react'
import type { AnnotColor, AnnotWidth, Lesson, WorkbenchTab } from '../types'
import { ConceptCards } from './ConceptCards'
import { ExamplePanel } from './ExamplePanel'
import { PracticePanel } from './PracticePanel'
import { AnnotationLayer } from './AnnotationLayer'
import { AiExplainButton } from './Settings'

interface Props {
  lesson: Lesson
  tab: WorkbenchTab
  conceptReveal: number
  exampleIndex: number
  stepReveal: number
  showAnswer: boolean
  skipAnimation: boolean
  onExit: () => void
  onTab: (t: WorkbenchTab) => void
  onConceptRevealMore: () => void
  onConceptRevealLess: () => void
  onConceptReset: () => void
  onPrevExample: () => void
  onNextExample: () => void
  onPrevStep: () => void
  onNextStep: () => void
  onToggleAnswer: () => void
  onSkipAnimation: () => void
  scratchOpen?: boolean
  onOpenScratch?: () => void
  onCloseScratch?: () => void
}

const COLORS: AnnotColor[] = ['#ef4444', '#fbbf24', '#60a5fa', '#22c55e']

/**
 * 03/04/06：投影讲题 / 练习 / 批注。
 * I9：主路径控件在 annot 层外的安全底栏，画笔开着也能点。
 */
export function ProjectionMode(props: Props) {
  const { lesson, tab, onExit, onTab, onPrevStep, onNextStep, onToggleAnswer } = props
  const [penOn, setPenOn] = useState(false)
  const [color, setColor] = useState<AnnotColor>('#ef4444')
  const [width, setWidth] = useState<AnnotWidth>(3)
  const [clearToken, setClearToken] = useState(0)

  const ex = lesson.examples[props.exampleIndex]

  useEffect(() => {
    const root = document.documentElement
    root.classList.add('projection-active')
    const tryFs = async () => {
      try {
        if (!document.fullscreenElement) {
          await document.documentElement.requestFullscreen()
        }
      } catch {
        /* 浏览器可能拒绝 */
      }
    }
    void tryFs()

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight' || e.key === 'n' || e.key === 'N') {
        e.preventDefault()
        if (tab === 'concepts') props.onConceptRevealMore()
        else onNextStep()
      }
      if (e.key === 'ArrowLeft' || e.key === 'p' || e.key === 'P') {
        e.preventDefault()
        if (tab === 'concepts') props.onConceptRevealLess()
        else onPrevStep()
      }
      if (e.key === 'a' || e.key === 'A') {
        e.preventDefault()
        onToggleAnswer()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => {
      root.classList.remove('projection-active')
      window.removeEventListener('keydown', onKey)
      if (document.fullscreenElement) {
        void document.exitFullscreen().catch(() => {})
      }
    }
  }, [
    tab,
    onNextStep,
    onPrevStep,
    onToggleAnswer,
    props.onConceptRevealMore,
    props.onConceptRevealLess,
  ])

  /** 切题清空批注（会话级策略） */
  useEffect(() => {
    setClearToken((n) => n + 1)
  }, [props.exampleIndex, tab])

  return (
    <div className="projection">
      <header className="projection-bar">
        <div>
          <strong>{lesson.title}</strong>
          <span className="muted"> · 投影模式</span>
        </div>
        <div className="projection-bar__controls" data-annot-safe>
          <button
            type="button"
            className={`chip ${tab === 'concepts' ? 'chip--on' : ''}`}
            onClick={() => onTab('concepts')}
          >
            概念
          </button>
          <button
            type="button"
            className={`chip ${tab === 'examples' ? 'chip--on' : ''}`}
            onClick={() => onTab('examples')}
          >
            例题
          </button>
          <button
            type="button"
            className={`chip ${tab === 'practice' ? 'chip--on' : ''}`}
            onClick={() => onTab('practice')}
          >
            练习
          </button>

          <span className="annot-sep" />

          <button
            type="button"
            className={`btn ${penOn ? 'btn--primary' : ''}`}
            onClick={() => setPenOn((v) => !v)}
            aria-pressed={penOn}
          >
            ✏ {penOn ? '画笔开' : '画笔关'}
          </button>
          {COLORS.map((c) => (
            <button
              key={c}
              type="button"
              className={`annot-swatch ${color === c ? 'annot-swatch--on' : ''}`}
              style={{ background: c }}
              aria-label={`颜色 ${c}`}
              onClick={() => setColor(c)}
            />
          ))}
          <button
            type="button"
            className={`chip ${width === 3 ? 'chip--on' : ''}`}
            onClick={() => setWidth(3)}
          >
            细
          </button>
          <button
            type="button"
            className={`chip ${width === 6 ? 'chip--on' : ''}`}
            onClick={() => setWidth(6)}
          >
            粗
          </button>
          <button type="button" className="btn" onClick={() => setClearToken((n) => n + 1)}>
            清空批注
          </button>

          <button
            type="button"
            className={`btn ${props.scratchOpen ? 'btn--primary' : ''}`}
            onClick={() => (props.scratchOpen ? props.onCloseScratch?.() : props.onOpenScratch?.())}
          >
            草稿
          </button>

          <button type="button" className="btn btn--ghost" onClick={onExit}>
            退出投影
          </button>
        </div>
      </header>

      {/* 内容区可批注；主控件在下方安全底栏，不在 annot 覆盖内 */}
      <div className="projection-stage-wrap">
        <main className="projection-stage">
          {tab === 'concepts' && (
            <ConceptCards
              concepts={lesson.concepts}
              revealIndex={props.conceptReveal}
              skipAnimation={props.skipAnimation}
              large
              hideStepControls
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
              large
              hideStepControls
              onPrevExample={props.onPrevExample}
              onNextExample={props.onNextExample}
              onPrevStep={props.onPrevStep}
              onNextStep={props.onNextStep}
              onToggleAnswer={props.onToggleAnswer}
              onSkipAnimation={props.onSkipAnimation}
            />
          )}
          {tab === 'practice' && <PracticePanel practices={lesson.practices} large />}
          {(tab === 'formula' || tab === 'canvas') && (
            <p className="muted">投影态请使用概念 / 例题 / 练习；公式与画布请回工作台。</p>
          )}
        </main>
        <AnnotationLayer
          enabled={penOn}
          color={color}
          width={width}
          clearToken={clearToken}
        />
      </div>

      <div className="projection-dock" data-annot-safe>
        <span className="dock-label">主路径（批注不挡）</span>
        {tab === 'concepts' && (
          <>
            <button
              type="button"
              className="btn"
              onClick={props.onConceptRevealLess}
              disabled={props.conceptReveal <= 0}
            >
              上一步
            </button>
            <button
              type="button"
              className="btn btn--primary"
              onClick={props.onConceptRevealMore}
              disabled={props.conceptReveal >= lesson.concepts.length}
            >
              下一步
            </button>
            <button type="button" className="btn" onClick={props.onSkipAnimation}>
              {props.skipAnimation ? '已跳过当前动画' : '跳过动画'}
            </button>
            <button type="button" className="btn btn--ghost" onClick={props.onConceptReset}>
              重新揭示
            </button>
            <span className="muted">
              已揭示 {props.conceptReveal}/{lesson.concepts.length}
            </span>
          </>
        )}
        {tab === 'examples' && ex && (
          <>
            <button type="button" className="btn" onClick={props.onPrevExample} disabled={props.exampleIndex <= 0}>
              上一题
            </button>
            <button
              type="button"
              className="btn"
              onClick={props.onNextExample}
              disabled={props.exampleIndex >= lesson.examples.length - 1}
            >
              下一题
            </button>
            <button type="button" className="btn btn--primary" onClick={onPrevStep} disabled={props.stepReveal <= 0}>
              上一步
            </button>
            <button
              type="button"
              className="btn btn--primary"
              onClick={onNextStep}
              disabled={props.stepReveal >= ex.steps.length}
            >
              下一步
            </button>
            <button type="button" className="btn" onClick={props.onSkipAnimation}>
              {props.skipAnimation ? '已跳过当前动画' : '跳过动画'}
            </button>
            <button type="button" className="btn btn--ghost" onClick={onToggleAnswer}>
              {props.showAnswer ? '隐藏答案' : '显示答案'}
            </button>
            <AiExplainButton
              snippet={
                props.stepReveal > 0
                  ? (ex.steps[props.stepReveal - 1]?.caption || ex.title)
                  : (ex.stem || ex.title)
              }
              scope={props.stepReveal > 0 ? 'step' : 'snippet'}
              context={{
                lessonTitle: lesson.title,
                itemTitle: ex.title,
                stepIndex: props.stepReveal > 0 ? props.stepReveal : undefined,
              }}
            />
            <span className="muted">
              步骤 {props.stepReveal}/{ex.steps.length}
            </span>
          </>
        )}
        {tab === 'practice' && <span className="muted">练习区：在卡片内显示/隐藏各题答案</span>}
      </div>

      <footer className="projection-hint muted">
        快捷键：←/→ 或 P/N 步进 · A 显示/隐藏答案 · 跳过动画只结束当前过渡
      </footer>
    </div>
  )
}
