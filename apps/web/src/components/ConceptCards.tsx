import type { ConceptPoint } from '../types'
import { KatexView } from './KatexView'
import { Illustration } from './Illustrations'

interface Props {
  concepts: ConceptPoint[]
  revealIndex: number
  skipAnimation: boolean
  large?: boolean
  onRevealMore: () => void
  onRevealLess?: () => void
  onReset: () => void
  hideStepControls?: boolean
}

/**
 * 08：概念 + 示例图诠释；图与「下一步」同步。
 * revealIndex = 已揭示数量（0 表示尚未揭示）。
 */
export function ConceptCards({
  concepts,
  revealIndex,
  skipAnimation,
  large,
  onRevealMore,
  onRevealLess,
  onReset,
  hideStepControls,
}: Props) {
  const current = revealIndex > 0 ? concepts[revealIndex - 1] : undefined
  const animClass = skipAnimation ? 'step-item--instant' : 'step-item--anim'

  return (
    <div className={`concept-panel ${large ? 'concept-panel--large' : ''}`}>
      {!hideStepControls && (
        <div className="toolbar toolbar--wrap">
          <button
            type="button"
            className="btn"
            onClick={onRevealLess}
            disabled={!onRevealLess || revealIndex <= 0}
          >
            上一步
          </button>
          <button
            type="button"
            className="btn btn--primary"
            onClick={onRevealMore}
            disabled={revealIndex >= concepts.length}
          >
            下一步
          </button>
          <button type="button" className="btn btn--ghost" onClick={onReset}>
            重新揭示
          </button>
          <span className="muted">
            已揭示 {revealIndex}/{concepts.length}
          </span>
        </div>
      )}

      <div className={`concept-illus-layout ${large ? 'concept-illus-layout--large' : ''}`}>
        <div className="concept-main card-panel">
          {current ? (
            <article className={`concept-focus ${animClass}`}>
              <p className="label">概念卡 · 当前揭示 {revealIndex} / {concepts.length}</p>
              <h2>{current.title}</h2>
              <p className="concept-body">{current.bodyText}</p>
              {current.bodyTex && (
                <div className="concept-formula">
                  <KatexView tex={current.bodyTex} display />
                </div>
              )}
              {current.diagram && (
                <div className="concept-illus-box">
                  <Illustration id={current.diagram.illustrationId} />
                  <p className="caption">{current.diagram.caption}</p>
                </div>
              )}
            </article>
          ) : (
            <p className="muted empty-hint">
              点击「下一步」逐步展示概念与示例图；未揭示内容不会提前出现。
            </p>
          )}
        </div>

        <aside className="concept-side">
          <p className="label">本课概念目录（与示例图同步）</p>
          <div className="side-list">
            {concepts.map((c, i) => {
              const revealed = i < revealIndex
              const on = i === revealIndex - 1
              return (
                <div
                  key={c.id}
                  className={`side-card ${on ? 'side-card--on' : ''} ${
                    revealed ? '' : 'side-card--locked'
                  }`}
                >
                  {c.diagram && (
                    <span className="badge">
                      示例图 ·{' '}
                      {c.diagram.kind === 'life'
                        ? '生活'
                        : c.diagram.kind === 'graph'
                          ? '图像'
                          : '符号'}
                    </span>
                  )}
                  <h4>{c.title}</h4>
                  <p>{revealed ? c.bodyText.slice(0, 42) + (c.bodyText.length > 42 ? '…' : '') : '尚未揭示'}</p>
                </div>
              )
            })}
          </div>
          <p className="muted small side-note">概念区配生动示例图；禁止只有文字定义。</p>
        </aside>
      </div>
    </div>
  )
}
