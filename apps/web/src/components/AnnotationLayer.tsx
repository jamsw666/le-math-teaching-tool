import { useCallback, useEffect, useRef, useState } from 'react'
import type { AnnotColor, AnnotWidth } from '../types'

interface Stroke {
  color: AnnotColor
  width: AnnotWidth
  points: { x: number; y: number }[]
}

interface Props {
  enabled: boolean
  color: AnnotColor
  width: AnnotWidth
  /** 切题/切换内容时递增，用于清空会话批注 */
  clearToken: number
}

/**
 * 投影批注层：鼠标/触控圈画。
 * pointer-events 仅在画笔开启时接收；主控件在上层不受挡。
 */
export function AnnotationLayer({ enabled, color, width, clearToken }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [strokes, setStrokes] = useState<Stroke[]>([])
  const drawing = useRef(false)
  const current = useRef<Stroke | null>(null)

  const redraw = useCallback((list: Stroke[]) => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const dpr = window.devicePixelRatio || 1
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    ctx.clearRect(0, 0, canvas.width / dpr, canvas.height / dpr)
    for (const s of list) {
      if (s.points.length < 2) continue
      ctx.beginPath()
      ctx.strokeStyle = s.color
      ctx.lineWidth = s.width
      ctx.lineCap = 'round'
      ctx.lineJoin = 'round'
      ctx.moveTo(s.points[0].x, s.points[0].y)
      for (let i = 1; i < s.points.length; i++) {
        ctx.lineTo(s.points[i].x, s.points[i].y)
      }
      ctx.stroke()
    }
  }, [])

  const resize = useCallback(() => {
    const canvas = canvasRef.current
    if (!canvas || !canvas.parentElement) return
    const parent = canvas.parentElement
    const rect = parent.getBoundingClientRect()
    const dpr = window.devicePixelRatio || 1
    canvas.width = Math.floor(rect.width * dpr)
    canvas.height = Math.floor(rect.height * dpr)
    canvas.style.width = `${rect.width}px`
    canvas.style.height = `${rect.height}px`
    redraw(strokes)
  }, [redraw, strokes])

  useEffect(() => {
    resize()
    window.addEventListener('resize', resize)
    return () => window.removeEventListener('resize', resize)
  }, [resize])

  useEffect(() => {
    setStrokes([])
    redraw([])
  }, [clearToken, redraw])

  const pos = (e: React.PointerEvent) => {
    const canvas = canvasRef.current
    if (!canvas) return { x: 0, y: 0 }
    const r = canvas.getBoundingClientRect()
    return { x: e.clientX - r.left, y: e.clientY - r.top }
  }

  const onDown = (e: React.PointerEvent) => {
    if (!enabled) return
    e.preventDefault()
    canvasRef.current?.setPointerCapture(e.pointerId)
    drawing.current = true
    const p = pos(e)
    current.current = { color, width, points: [p] }
  }

  const onMove = (e: React.PointerEvent) => {
    if (!enabled || !drawing.current || !current.current) return
    e.preventDefault()
    current.current.points.push(pos(e))
    redraw([...strokes, current.current])
  }

  const onUp = (e: React.PointerEvent) => {
    if (!drawing.current || !current.current) return
    drawing.current = false
    const done = current.current
    current.current = null
    setStrokes((s) => {
      const next = [...s, done]
      redraw(next)
      return next
    })
    try {
      canvasRef.current?.releasePointerCapture(e.pointerId)
    } catch {
      /* ignore */
    }
  }

  return (
    <canvas
      ref={canvasRef}
      className={`annot-layer ${enabled ? 'annot-layer--on' : ''}`}
      aria-hidden
      onPointerDown={onDown}
      onPointerMove={onMove}
      onPointerUp={onUp}
      onPointerCancel={onUp}
    />
  )
}

export function clearAnnotationToken(n: number) {
  return n + 1
}
