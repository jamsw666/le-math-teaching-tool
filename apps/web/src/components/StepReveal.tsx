import type { StepItem } from '../types'
import { KatexView } from './KatexView'

interface Props {
  steps: StepItem[]
  revealIndex: number
  /** 跳过动画：直接显示到当前步，无过渡 */
  skipAnimation: boolean
  large?: boolean
}

/**
 * 关键步骤揭示。未揭示步骤不挂载（满足不提前暴露）。
 * revealIndex 为「已揭示步数」（0 = 尚未揭示任何步）。
 */
export function StepReveal({ steps, revealIndex, skipAnimation, large }: Props) {
  const visible = steps.slice(0, Math.max(0, revealIndex))

  return (
    <ol className={`step-list ${large ? 'step-list--large' : ''}`}>
      {visible.map((step, i) => {
        const isCurrent = i === revealIndex - 1
        const useAnim = step.animate !== false && !skipAnimation
        return (
          <li
            key={step.id}
            className={[
              'step-item',
              isCurrent ? 'step-item--current' : '',
              useAnim ? 'step-item--anim' : 'step-item--instant',
            ]
              .filter(Boolean)
              .join(' ')}
          >
            <div className="step-index">步骤 {i + 1}</div>
            <p className="step-caption">{step.caption}</p>
            {step.formulaTex && (
              <div className="step-formula">
                <KatexView tex={step.formulaTex} display />
              </div>
            )}
          </li>
        )
      })}
    </ol>
  )
}
