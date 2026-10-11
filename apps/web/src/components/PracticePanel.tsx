import { useState } from 'react'
import type { PracticeItem } from '../types'
import { KatexView } from './KatexView'

interface Props {
  practices: PracticeItem[]
  large?: boolean
  /** 投影态：强制答案默认隐藏，由外部统一控制也可 */
  forceHideAnswers?: boolean
}

export function PracticePanel({ practices, large, forceHideAnswers }: Props) {
  const [shown, setShown] = useState<Record<string, boolean>>({})

  const toggle = (id: string) => {
    if (forceHideAnswers) {
      setShown((s) => ({ ...s, [id]: !s[id] }))
      return
    }
    setShown((s) => ({ ...s, [id]: !s[id] }))
  }

  return (
    <div className={`practice-panel ${large ? 'practice-panel--large' : ''}`}>
      <p className="muted">答案默认隐藏，投影时请先让学生思考再点「显示答案」。</p>
      <ul className="practice-list">
        {practices.map((p, idx) => {
          const open = !!shown[p.id]
          return (
            <li key={p.id} className="practice-card">
              <div className="practice-q">
                <span className="badge">练 {idx + 1}</span>
                <p>{p.question}</p>
                {p.questionTex && <KatexView tex={p.questionTex} display />}
              </div>
              <div className="practice-a">
                <button type="button" className="btn btn--ghost" onClick={() => toggle(p.id)}>
                  {open ? '隐藏答案' : '显示答案'}
                </button>
                {open ? (
                  <div className="answer-box">
                    <KatexView tex={p.answerTex} display />
                    <p>{p.answerText}</p>
                  </div>
                ) : (
                  <div className="answer-box answer-box--locked" aria-hidden>
                    <span>答案已隐藏</span>
                  </div>
                )}
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
