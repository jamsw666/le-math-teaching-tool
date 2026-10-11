import { useMemo } from 'react'
import katex from 'katex'
import 'katex/dist/katex.min.css'

interface Props {
  tex?: string
  display?: boolean
  className?: string
}

/** 仅渲染结果；不向老师展示源码字符串 */
export function KatexView({ tex, display = false, className }: Props) {
  const html = useMemo(() => {
    if (!tex) return ''
    try {
      return katex.renderToString(tex, {
        throwOnError: false,
        displayMode: display,
        strict: 'ignore',
      })
    } catch {
      return ''
    }
  }, [tex, display])

  if (!tex) return null
  return (
    <span
      className={className}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  )
}
