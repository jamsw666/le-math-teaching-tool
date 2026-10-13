import { useCallback, useEffect, useRef, useState } from 'react'
import { APP_NAME } from '../appMeta'
import {
  BOOT_CHIME_FALLBACK,
  BOOT_CHIME_SRC,
  loadBootChimeMuted,
  saveBootChimeMuted,
} from '../soundPrefs'

interface Props {
  onEnter: () => void
}

/**
 * D2.2 cold-start welcome: immersive classroom cover BG + dark overlay card.
 * Fullscreen until click / 「进入课堂」; boot chime once; mute in localStorage.
 */
export function WelcomeSplash({ onEnter }: Props) {
  const [muted, setMuted] = useState(() => loadBootChimeMuted())
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const playedRef = useRef(false)
  const closingRef = useRef(false)

  const stopChime = useCallback((fadeMs = 400) => {
    const a = audioRef.current
    if (!a) return
    const startVol = a.volume
    if (fadeMs <= 0 || startVol <= 0 || a.paused) {
      try {
        a.pause()
        a.currentTime = 0
      } catch {
        /* ignore */
      }
      return
    }
    const steps = 8
    const stepMs = fadeMs / steps
    let i = 0
    const tick = () => {
      i += 1
      a.volume = Math.max(0, startVol * (1 - i / steps))
      if (i >= steps) {
        try {
          a.pause()
          a.currentTime = 0
          a.volume = startVol
        } catch {
          /* ignore */
        }
        return
      }
      window.setTimeout(tick, stepMs)
    }
    window.setTimeout(tick, stepMs)
  }, [])

  const tryPlay = useCallback((src: string): HTMLAudioElement => {
    const a = new Audio(src)
    a.loop = false
    a.volume = 0.85
    return a
  }, [])

  const playChime = useCallback(() => {
    if (playedRef.current) return
    playedRef.current = true
    if (loadBootChimeMuted()) return
    try {
      const a = tryPlay(BOOT_CHIME_SRC)
      audioRef.current = a
      void a.play().catch(() => {
        /* preferred path failed — try fallback once */
        try {
          const b = tryPlay(BOOT_CHIME_FALLBACK)
          audioRef.current = b
          void b.play().catch(() => {
            /* autoplay blocked or missing — fail silent */
          })
        } catch {
          /* fail silent */
        }
      })
    } catch {
      try {
        const b = tryPlay(BOOT_CHIME_FALLBACK)
        audioRef.current = b
        void b.play().catch(() => {})
      } catch {
        /* fail silent */
      }
    }
  }, [tryPlay])

  useEffect(() => {
    playChime()
    return () => {
      stopChime(0)
    }
  }, [playChime, stopChime])

  const enter = useCallback(() => {
    if (closingRef.current) return
    closingRef.current = true
    stopChime(400)
    onEnter()
  }, [onEnter, stopChime])

  const toggleMute = (e: React.MouseEvent) => {
    e.stopPropagation()
    const next = !muted
    setMuted(next)
    saveBootChimeMuted(next)
    if (next) {
      stopChime(0)
    }
  }

  return (
    <div
      className="welcome-splash"
      role="dialog"
      aria-label={APP_NAME}
      onClick={enter}
    >
      <div className="welcome-splash__card" onClick={(e) => e.stopPropagation()}>
        <div className="welcome-splash__badge" aria-hidden>
          数学
        </div>
        <h1 className="welcome-splash__title">欢迎来到阿乐老师的数学课堂</h1>
        <p className="welcome-splash__hint muted">点击任意处或下方按钮进入</p>
        <div className="welcome-splash__actions">
          <button type="button" className="btn btn--primary welcome-splash__enter" onClick={enter}>
            进入课堂
          </button>
          <button
            type="button"
            className="btn btn--ghost welcome-splash__mute"
            onClick={toggleMute}
            aria-pressed={muted}
            title={muted ? '已静音 · 点击开声' : '点击静音'}
          >
            {muted ? '🔇 已静音' : '🔊 开机音'}
          </button>
        </div>
      </div>
    </div>
  )
}
