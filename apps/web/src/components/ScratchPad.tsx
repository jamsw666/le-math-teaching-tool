import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'

type Tool = 'pen' | 'eraser'
type ScratchColor = '#111827' | '#4f46e5' | '#059669'

interface Stroke {
  color: string
  width: number
  points: { x: number; y: number }[]
  erase?: boolean
}

interface Props {
  open: boolean
  onClose: () => void
}

const COLORS: ScratchColor[] = ['#111827', '#4f46e5', '#059669']
/** Fallback only when .projection-dock is missing/hidden (viewport bottom inset). */
const DOCK_SAFE = 88
const PANEL_W = 720
const PANEL_H = 560

/** Live dock top edge; panel.bottom must stay ≤ this. Fallback: innerHeight − DOCK_SAFE. */
function getDockCeiling(): number {
  if (typeof document === 'undefined' || typeof window === 'undefined') {
    return 712
  }
  const dock = document.querySelector('.projection-dock')
  if (dock instanceof HTMLElement) {
    const r = dock.getBoundingClientRect()
    if (r.height > 0 && r.width > 0) return r.top
  }
  return window.innerHeight - DOCK_SAFE
}

/**
 * D2.2 玻璃可拖大浮层草稿：毛玻璃面板 + 轻遮罩（chrome 更高 z-index 可点）。
 * 关闭不丢本会话草稿（组件保持挂载，仅隐藏）；Y 钳制按真实 dock.top（非仅 viewport−88）。
 */
export function ScratchPad({ open, onClose }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const panelRef = useRef<HTMLElement>(null)
  const [strokes, setStrokes] = useState<Stroke[]>([])
  const [tool, setTool] = useState<Tool>('pen')
  const [color, setColor] = useState<ScratchColor>('#111827')
  const [calcText, setCalcText] = useState('')
  const [pos, setPos] = useState<{ left: number; top: number } | null>(null)
  const [dragging, setDragging] = useState(false)
  const drawing = useRef(false)
  const current = useRef<Stroke | null>(null)
  const dragOrigin = useRef<{ x: number; y: number; left: number; top: number } | null>(null)

  /** Prefer measured panel size; Y max so panel.bottom ≤ live dock.top. */
  const clampPos = useCallback((left: number, top: number) => {
    const ceiling = getDockCeiling()
    const avail = Math.max(120, ceiling - 8)
    const rect = panelRef.current?.getBoundingClientRect()
    const w =
      rect && rect.width > 0 ? rect.width : Math.min(PANEL_W, window.innerWidth * 0.92)
    let h =
      rect && rect.height > 0
        ? rect.height
        : Math.min(PANEL_H, window.innerHeight - DOCK_SAFE - 48)
    h = Math.min(h, avail)
    const maxLeft = Math.max(8, window.innerWidth - w - 8)
    // panel.top + height <= dock.top (or viewport−DOCK_SAFE fallback)
    const maxTop = Math.max(8, ceiling - h)
    return {
      left: Math.min(Math.max(8, left), maxLeft),
      top: Math.min(Math.max(8, top), maxTop),
    }
  }, [])

  const centerPos = useCallback(() => {
    const ceiling = getDockCeiling()
    const avail = Math.max(120, ceiling - 8)
    const w = Math.min(PANEL_W, window.innerWidth * 0.92)
    const h = Math.min(PANEL_H, avail)
    return clampPos((window.innerWidth - w) / 2, Math.max(8, (ceiling - h) / 2))
  }, [clampPos])

  useEffect(() => {
    if (open && !pos) setPos(centerPos())
  }, [open, pos, centerPos])

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
      ctx.strokeStyle = s.erase ? '#000' : s.color
      ctx.lineWidth = s.erase ? 16 : s.width
      ctx.lineCap = 'round'
      ctx.lineJoin = 'round'
      if (s.erase) ctx.globalCompositeOperation = 'destination-out'
      else ctx.globalCompositeOperation = 'source-over'
      ctx.moveTo(s.points[0].x, s.points[0].y)
      for (let i = 1; i < s.points.length; i++) {
        ctx.lineTo(s.points[i].x, s.points[i].y)
      }
      ctx.stroke()
    }
    ctx.globalCompositeOperation = 'source-over'
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
    if (!open) return
    // Re-clamp after layout so measured height (not estimate) is used.
    const t = window.setTimeout(() => {
      setPos((p) => (p ? clampPos(p.left, p.top) : centerPos()))
      resize()
    }, 30)
    const onWin = () => {
      setPos((p) => (p ? clampPos(p.left, p.top) : centerPos()))
      resize()
    }
    window.addEventListener('resize', onWin)
    return () => {
      window.clearTimeout(t)
      window.removeEventListener('resize', onWin)
    }
  }, [open, resize, clampPos, centerPos])

  const pointerPos = (e: React.PointerEvent) => {
    const canvas = canvasRef.current
    if (!canvas) return { x: 0, y: 0 }
    const r = canvas.getBoundingClientRect()
    return { x: e.clientX - r.left, y: e.clientY - r.top }
  }

  const onDown = (e: React.PointerEvent) => {
    e.preventDefault()
    canvasRef.current?.setPointerCapture(e.pointerId)
    drawing.current = true
    const p = pointerPos(e)
    current.current = {
      color,
      width: 2.5,
      points: [p],
      erase: tool === 'eraser',
    }
  }

  const onMove = (e: React.PointerEvent) => {
    if (!drawing.current || !current.current) return
    e.preventDefault()
    current.current.points.push(pointerPos(e))
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

  const clearAll = () => {
    setStrokes([])
    redraw([])
  }

  const onDragStart = (e: React.PointerEvent) => {
    if ((e.target as HTMLElement).closest('button')) return
    e.preventDefault()
    const base = pos ?? centerPos()
    dragOrigin.current = { x: e.clientX, y: e.clientY, left: base.left, top: base.top }
    setDragging(true)
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  }

  const onDragMove = (e: React.PointerEvent) => {
    if (!dragOrigin.current) return
    const dx = e.clientX - dragOrigin.current.x
    const dy = e.clientY - dragOrigin.current.y
    setPos(clampPos(dragOrigin.current.left + dx, dragOrigin.current.top + dy))
  }

  const onDragEnd = (e: React.PointerEvent) => {
    dragOrigin.current = null
    setDragging(false)
    try {
      ;(e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId)
    } catch {
      /* ignore */
    }
  }

  // Shrink panel when space above real dock is < nominal 560 so clamp can keep gap ≥ 0.
  const availAboveDock = Math.max(120, getDockCeiling() - 8)
  const panelStyle: CSSProperties = {
    maxHeight: availAboveDock,
    ...(pos != null ? { left: pos.left, top: pos.top, transform: 'none' as const } : {}),
  }

  return (
    <div className={`scratch-root ${open ? 'scratch-root--open' : ''}`} aria-hidden={!open}>
      {open && <div className="scratch-mask" aria-hidden />}
      <aside
        ref={panelRef}
        className={`scratch-panel${dragging ? ' scratch-panel--dragging' : ''}`}
        role="dialog"
        aria-label="草稿本"
        style={panelStyle}
      >
        <div
          className="scratch-head"
          onPointerDown={onDragStart}
          onPointerMove={onDragMove}
          onPointerUp={onDragEnd}
          onPointerCancel={onDragEnd}
        >
          <strong>草稿本</strong>
          <span className="scratch-sub">上课演算 · 不写入例题</span>
          <button type="button" className="scratch-close" onClick={onClose} aria-label="关闭草稿">
            ×
          </button>
        </div>
        <div className="scratch-tools">
          <button
            type="button"
            className={`tool ${tool === 'pen' ? 'on' : ''}`}
            onClick={() => setTool('pen')}
          >
            笔
          </button>
          <button
            type="button"
            className={`tool ${tool === 'eraser' ? 'on' : ''}`}
            onClick={() => setTool('eraser')}
          >
            橡皮
          </button>
          <button type="button" className="tool" onClick={clearAll}>
            清空
          </button>
          {COLORS.map((c) => (
            <button
              key={c}
              type="button"
              className={`swatch ${color === c && tool === 'pen' ? 'swatch--on' : ''}`}
              style={{ background: c }}
              aria-label={`颜色 ${c}`}
              onClick={() => {
                setColor(c)
                setTool('pen')
              }}
            />
          ))}
        </div>
        <div className="scratch-pad">
          <canvas
            ref={canvasRef}
            className="scratch-canvas"
            onPointerDown={onDown}
            onPointerMove={onMove}
            onPointerUp={onUp}
            onPointerCancel={onUp}
          />
        </div>
        <label className="scratch-calc">
          <span className="scratch-calc-label">演算一行</span>
          <input
            type="text"
            value={calcText}
            onChange={(e) => setCalcText(e.target.value)}
            placeholder="例如：(1+h)² − 1 = 2h + h²"
          />
        </label>
      </aside>
    </div>
  )
}
