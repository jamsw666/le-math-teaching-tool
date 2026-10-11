import type { ExampleItem } from '../types'
import { KatexView } from './KatexView'
import { StepReveal } from './StepReveal'

interface Props {
  examples: ExampleItem[]
  exampleIndex: number
  revealIndex: number
  showAnswer: boolean
  skipAnimation: boolean
  large?: boolean
  onPrevExample: () => void
  onNextExample: () => void
  onPrevStep: () => void
  onNextStep: () => void
  onToggleAnswer: () => void
  onSkipAnimation: () => void
  /** 投影态由外层安全底栏承载主控件 */
  hideStepControls?: boolean
}

export function ExamplePanel({
  examples,
  exampleIndex,
  revealIndex,
  showAnswer,
  skipAnimation,
  large,
  onPrevExample,
  onNextExample,
  onPrevStep,
  onNextStep,
  onToggleAnswer,
  onSkipAnimation,
  hideStepControls,
}: Props) {
  const ex = examples[exampleIndex]
  if (!ex) return <p className="muted">暂无例题</p>

  const atEnd = revealIndex >= ex.steps.length

  return (
    <div className={`example-panel ${large ? 'example-panel--large' : ''}`}>
      <div className="toolbar toolbar--wrap">
        <button type="button" className="btn" onClick={onPrevExample} disabled={exampleIndex <= 0}>
          上一题
        </button>
        <button
          type="button"
          className="btn"
          onClick={onNextExample}
          disabled={exampleIndex >= examples.length - 1}
        >
          下一题
        </button>
        <span className="muted">
          例题 {exampleIndex + 1}/{examples.length}
        </span>
      </div>

      <article className="example-stem">
        <h2>{ex.title}</h2>
        <p>{ex.stem}</p>
        {ex.stemTex && (
          <div className="stem-formula">
            <KatexView tex={ex.stemTex} display />
          </div>
        )}
      </article>

      {!hideStepControls && (
        <div className="toolbar toolbar--wrap">
          <button type="button" className="btn btn--primary" onClick={onPrevStep} disabled={revealIndex <= 0}>
            上一步
          </button>
          <button
            type="button"
            className="btn btn--primary"
            onClick={onNextStep}
            disabled={revealIndex >= ex.steps.length}
          >
            下一步
          </button>
          <button type="button" className="btn" onClick={onSkipAnimation}>
            {skipAnimation ? '已跳过动画' : '跳过动画'}
          </button>
          <button type="button" className="btn btn--ghost" onClick={onToggleAnswer}>
            {showAnswer ? '隐藏答案' : '显示答案'}
          </button>
          <span className="muted">
            步骤 {revealIndex}/{ex.steps.length}
            {atEnd ? ' · 已完成' : ''}
          </span>
        </div>
      )}

      <StepReveal
        steps={ex.steps}
        revealIndex={revealIndex}
        skipAnimation={skipAnimation}
        large={large}
      />

      {showAnswer && (
        <div className="answer-box answer-box--final step-item--anim">
          <strong>参考答案</strong>
          <KatexView tex={ex.answerTex} display />
          <p>{ex.answerText}</p>
        </div>
      )}
    </div>
  )
}
