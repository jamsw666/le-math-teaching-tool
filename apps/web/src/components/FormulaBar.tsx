import { useMemo, useState } from 'react'
import { KatexView } from './KatexView'

/**
 * 老师侧只看到中文/符号字形；内部再映射为渲染串。
 * 禁止把反斜杠命令写进输入框或槽位明文。
 */
function humanToTex(s: string): string {
  return (s || '')
    .replaceAll('×', '\\times ')
    .replaceAll('÷', '\\div ')
    .replaceAll('π', '\\pi ')
    .replaceAll('∞', '\\infty ')
    .replaceAll('≤', '\\le ')
    .replaceAll('≥', '\\ge ')
    .replaceAll('Δ', '\\Delta ')
    .replaceAll('→', '\\to ')
    .replaceAll('′', "'")
    .replaceAll('□', '\\square ')
}

const TEMPLATES: {
  id: string
  label: string
  build: (parts: string[]) => string
  slots: string[]
  /** 对人友好的默认文案（可进输入框） */
  defaults: string[]
}[] = [
  {
    id: 'frac',
    label: '分式 a/b',
    slots: ['分子', '分母'],
    defaults: ['f(1+h)-f(1)', 'h'],
    build: ([a, b]) =>
      `\\frac{${humanToTex(a) || '\\square'}}{${humanToTex(b) || '\\square'}}`,
  },
  {
    id: 'sqrt',
    label: '根号',
    slots: ['里面'],
    defaults: ['x'],
    build: ([a]) => `\\sqrt{${humanToTex(a) || '\\square'}}`,
  },
  {
    id: 'pow',
    label: '乘方',
    slots: ['底', '指数'],
    defaults: ['x', '2'],
    build: ([a, b]) => `${humanToTex(a) || 'x'}^{${humanToTex(b) || 'n'}}`,
  },
  {
    id: 'deriv',
    label: '导数 f′',
    slots: ['函数名'],
    defaults: ['f'],
    build: ([a]) => `${humanToTex(a) || 'f'}'(x)`,
  },
  {
    id: 'lim',
    label: '极限',
    slots: ['趋向', '表达式'],
    defaults: ['h→0', '(f(x+h)-f(x))/h'],
    build: ([a, b]) =>
      `\\lim_{${humanToTex(a) || 'x\\to 0'}}${humanToTex(b) || 'f(x)'}`,
  },
  {
    id: 'avg',
    label: 'Δy/Δx',
    slots: ['Δy', 'Δx'],
    defaults: ['f(b)-f(a)', 'b-a'],
    build: ([a, b]) =>
      `\\frac{${humanToTex(a) || '\\Delta y'}}{${humanToTex(b) || '\\Delta x'}}`,
  },
]

/** label 给按钮看；insert 写入槽位的友好字符（不是反斜杠串） */
const SYMBOLS: { label: string; insert: string }[] = [
  { label: '+', insert: '+' },
  { label: '−', insert: '-' },
  { label: '×', insert: '×' },
  { label: '÷', insert: '÷' },
  { label: '=', insert: '=' },
  { label: '(', insert: '(' },
  { label: ')', insert: ')' },
  { label: 'x', insert: 'x' },
  { label: 'h', insert: 'h' },
  { label: '1', insert: '1' },
  { label: '2', insert: '2' },
  { label: 'π', insert: 'π' },
  { label: '∞', insert: '∞' },
  { label: '≤', insert: '≤' },
  { label: '≥', insert: '≥' },
  { label: 'Δ', insert: 'Δ' },
  { label: '→', insert: '→' },
]

/**
 * 05：公式可视化编辑 — 点选模板 + 公式区点选编辑。
 * 界面禁止出现专业排版术语；槽位/输入只存友好文案。
 */
export function FormulaBar() {
  const [templateId, setTemplateId] = useState('frac')
  const template = TEMPLATES.find((t) => t.id === templateId) ?? TEMPLATES[0]
  const [parts, setParts] = useState<string[]>([...template.defaults])
  const [activeSlot, setActiveSlot] = useState(0)

  const previewTex = useMemo(() => template.build(parts), [template, parts])

  const pickTemplate = (id: string) => {
    const t = TEMPLATES.find((x) => x.id === id)
    if (!t) return
    setTemplateId(id)
    setParts([...t.defaults])
    setActiveSlot(0)
  }

  const updatePart = (i: number, v: string) => {
    // 拦截粘贴进来的反斜杠命令，避免露出源码感
    const cleaned = v.replace(/\\[a-zA-Z]+/g, '')
    setParts((prev) => prev.map((p, idx) => (idx === i ? cleaned : p)))
  }

  const insertIntoActive = (s: string) => {
    setParts((prev) =>
      prev.map((p, idx) => (idx === activeSlot ? (p || '') + s : p)),
    )
  }

  const backspace = () => {
    setParts((prev) =>
      prev.map((p, idx) => (idx === activeSlot ? p.slice(0, -1) : p)),
    )
  }

  const clearAll = () => {
    setParts(template.slots.map(() => ''))
  }

  return (
    <div className="formula-bar formula-bar--visual">
      <header className="formula-bar__head">
        <h2>公式编辑</h2>
        <p className="muted">
          先点模板，再在编辑区点选要改的部分；也可用符号键。预览即课堂效果。
        </p>
      </header>

      <div className="formula-layout">
        <aside className="formula-side">
          <section className="card-panel">
            <p className="label">常用模板（点一下插入）</p>
            <div className="key-grid">
              {TEMPLATES.map((t) => (
                <button
                  key={t.id}
                  type="button"
                  className={`key-btn ${templateId === t.id ? 'key-btn--active' : ''}`}
                  onClick={() => pickTemplate(t.id)}
                >
                  {t.label}
                </button>
              ))}
            </div>
          </section>

          <section className="card-panel">
            <p className="label">符号键（像计算器）</p>
            <div className="key-grid key-grid--symbols">
              {SYMBOLS.map((s) => (
                <button
                  key={s.label}
                  type="button"
                  className="key-btn key-btn--sym"
                  onClick={() => insertIntoActive(s.insert)}
                >
                  {s.label}
                </button>
              ))}
              <button type="button" className="key-btn" onClick={backspace}>
                ⌫
              </button>
              <button type="button" className="key-btn key-btn--danger" onClick={clearAll}>
                清空
              </button>
            </div>
          </section>
        </aside>

        <section className="formula-editor card-panel">
          <p className="label">公式编辑区（点哪里改哪里）</p>
          <div className="formula-edit-zone" role="group" aria-label="公式编辑区">
            {template.slots.map((slotName, i) => (
              <button
                key={slotName}
                type="button"
                className={`formula-slot ${activeSlot === i ? 'formula-slot--active' : ''}`}
                onClick={() => setActiveSlot(i)}
              >
                <span className="formula-slot__name">{slotName}</span>
                <span className="formula-slot__value">
                  {parts[i] ? (
                    <KatexView tex={humanToTex(parts[i])} />
                  ) : (
                    <span className="muted">□</span>
                  )}
                </span>
              </button>
            ))}
          </div>

          <div className="formula-slot-inputs">
            {template.slots.map((slotName, i) => (
              <label key={slotName}>
                正在编辑：{slotName}
                <input
                  value={parts[i] ?? ''}
                  onFocus={() => setActiveSlot(i)}
                  onChange={(e) => updatePart(i, e.target.value)}
                  aria-label={`编辑${slotName}`}
                  placeholder="用符号键或输入普通文字"
                  autoComplete="off"
                  spellCheck={false}
                />
              </label>
            ))}
          </div>

          <div className="formula-bar__preview">
            <p className="label">预览（投影里会更大）</p>
            <div className="preview-box" aria-live="polite">
              {previewTex ? (
                <KatexView tex={previewTex} display />
              ) : (
                <span className="muted">点上方模板或符号即可生成</span>
              )}
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}
