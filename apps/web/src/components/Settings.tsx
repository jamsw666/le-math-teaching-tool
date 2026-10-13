import { useCallback, useEffect, useRef, useState } from 'react'
import {
  clearAiApiKeyRuntime,
  loadAiRuntime,
  saveAiRuntimeSettings,
  setAiApiKey,
  type AiRuntime,
  type AiSettings,
} from '../aiSettings'
import { createProvider } from '../ai/ExplanationProvider'
import type { ExplainScope } from '../ai/types'
import { APP_NAME } from '../appMeta'
import { loadBootChimeMuted, saveBootChimeMuted } from '../soundPrefs'

interface Props {
  onBack: () => void
}

/** 设置页：AI 释义默认关；壳内密钥走 Tauri，浏览器才写 localStorage */
export function Settings({ onBack }: Props) {
  const [runtime, setRuntime] = useState<AiRuntime | null>(null)
  const [keyDraft, setKeyDraft] = useState('')
  const [savedHint, setSavedHint] = useState('')
  const [busy, setBusy] = useState(false)
  const [bootMuted, setBootMuted] = useState(() => loadBootChimeMuted())

  useEffect(() => {
    let cancelled = false
    void loadAiRuntime().then((r) => {
      if (!cancelled) setRuntime(r)
    })
    return () => {
      cancelled = true
    }
  }, [])

  const settings = runtime?.settings

  const patchSettings = async (patch: Partial<AiSettings>) => {
    if (!runtime) return
    setBusy(true)
    try {
      const next = { ...runtime.settings, ...patch, apiKey: runtime.mode === 'tauri' ? '' : (patch.apiKey ?? runtime.settings.apiKey) }
      const r = await saveAiRuntimeSettings(next)
      setRuntime(r)
      setSavedHint('已保存到本机')
    } catch {
      setSavedHint('保存失败，请重试')
    } finally {
      setBusy(false)
    }
  }

  const onSaveKey = async () => {
    if (!runtime || !keyDraft.trim()) return
    setBusy(true)
    try {
      const r = await setAiApiKey(runtime.settings, keyDraft)
      setRuntime(r)
      setKeyDraft('')
      setSavedHint(runtime.mode === 'tauri' ? '密钥已存入应用私有存储' : '密钥已保存到本机')
    } catch {
      setSavedHint('密钥保存失败')
    } finally {
      setBusy(false)
    }
  }

  const onClearKey = async () => {
    if (!runtime) return
    setBusy(true)
    try {
      const r = await clearAiApiKeyRuntime(runtime.settings)
      setRuntime(r)
      setKeyDraft('')
      setSavedHint('密钥已清除')
    } catch {
      setSavedHint('清除失败')
    } finally {
      setBusy(false)
    }
  }

  if (!settings || !runtime) {
    return (
      <div className="settings-page">
        <div className="settings-body">
          <p className="muted">加载设置…</p>
        </div>
      </div>
    )
  }

  return (
    <div className="settings-page">
      <header className="app-topbar">
        <div className="brand">{APP_NAME}</div>
        <nav className="top-nav">
          <span>课时</span>
          <span className="on">设置</span>
        </nav>
        <div className="topbar-right">
          <button type="button" className="btn btn--ghost" onClick={onBack}>
            ← 返回课时
          </button>
        </div>
      </header>

      <div className="settings-body">
        <h1>设置</h1>
        <p className="muted">主路径始终可离线上课；AI 释义为可选联网能力。</p>

        <section className="card-panel settings-card">
          <h2 className="settings-card-title">AI 释义</h2>
          <div className="settings-row">
            <div>
              <strong>开启 AI 释义</strong>
              <p className="muted small">默认关闭。开启后需联网与服务商配置。</p>
            </div>
            <label className="toggle">
              <input
                type="checkbox"
                checked={settings.enabled}
                disabled={busy}
                onChange={(e) => void patchSettings({ enabled: e.target.checked })}
              />
              <span className="knob" />
            </label>
          </div>

          <div className={`settings-fields ${settings.enabled ? '' : 'settings-fields--dim'}`}>
            <label>
              <span>服务商</span>
              <input
                type="text"
                value={settings.provider}
                onChange={(e) => void patchSettings({ provider: e.target.value })}
                placeholder="例如 DeepSeek"
                disabled={!settings.enabled || busy}
              />
            </label>
            <label>
              <span>服务地址</span>
              <input
                type="text"
                value={settings.baseUrl}
                onChange={(e) => void patchSettings({ baseUrl: e.target.value })}
                placeholder="https://api.deepseek.com"
                disabled={!settings.enabled || busy}
              />
            </label>
            <label>
              <span>API 密钥{runtime.mode === 'tauri' ? '（写入应用私有存储，不回显）' : '（仅本机）'}</span>
              <input
                type="password"
                value={keyDraft}
                onChange={(e) => setKeyDraft(e.target.value)}
                placeholder={
                  runtime.hasApiKey
                    ? '已配置密钥 · 输入新密钥可覆盖'
                    : runtime.mode === 'tauri'
                      ? '输入后点「保存密钥」'
                      : '仅保存在本机'
                }
                disabled={!settings.enabled || busy}
                autoComplete="off"
              />
            </label>
            <label>
              <span>模型名</span>
              <input
                type="text"
                value={settings.model}
                onChange={(e) => void patchSettings({ model: e.target.value })}
                placeholder="deepseek-chat"
                disabled={!settings.enabled || busy}
              />
            </label>
            <div className="settings-actions">
              <button
                type="button"
                className="btn btn--primary"
                onClick={() => void onSaveKey()}
                disabled={!settings.enabled || busy || !keyDraft.trim()}
              >
                保存密钥
              </button>
              <button
                type="button"
                className="btn"
                onClick={() => void onClearKey()}
                disabled={busy || !runtime.hasApiKey}
              >
                清除密钥
              </button>
              {savedHint && <span className="muted small">{savedHint}</span>}
            </div>
            <p className="muted small">
              密钥状态：{runtime.hasApiKey ? '已配置' : '未配置'}
              {runtime.mode === 'tauri' ? ' · 存储：Tauri 私有目录' : ' · 存储：浏览器 localStorage'}
            </p>
          </div>
          <p className="ai-note">
            离线底可用 · 释义需联网时再开。关闭、断网或失败时只提示人话，不挡上课主路径。
            桌面壳内密钥不进 localStorage；浏览器开发态仍可暂存本机。
          </p>
        </section>

        <section className="card-panel settings-card">
          <h2 className="settings-card-title">声音</h2>
          <div className="settings-row">
            <div>
              <strong>开机欢迎音</strong>
              <p className="muted small">冷启动欢迎页播放短音；关闭后本机记住偏好（默认开声）。</p>
            </div>
            <label className="toggle">
              <input
                type="checkbox"
                checked={!bootMuted}
                onChange={(e) => {
                  const muted = !e.target.checked
                  setBootMuted(muted)
                  saveBootChimeMuted(muted)
                }}
              />
              <span className="knob" />
            </label>
          </div>
        </section>
      </div>
    </div>
  )
}

export interface AiExplainButtonProps {
  snippet?: string
  scope?: ExplainScope
  context?: {
    lessonTitle?: string
    itemTitle?: string
    stepIndex?: number
  }
}

/**
 * 释义按钮：调 ExplanationProvider.explain；
 * 灰显看 enabled + hasApiKey（壳内 ai_has_api_key）；失败用人话 message。
 */
export function AiExplainButton({
  snippet,
  scope = 'snippet',
  context,
}: AiExplainButtonProps) {
  const [runtime, setRuntime] = useState<AiRuntime | null>(null)
  const [busy, setBusy] = useState(false)
  const [panel, setPanel] = useState<
    | null
    | { kind: 'ok'; text: string; model?: string; latencyMs?: number }
    | { kind: 'err'; message: string }
  >(null)
  const abortGen = useRef(0)

  useEffect(() => {
    let cancelled = false
    void loadAiRuntime().then((r) => {
      if (!cancelled) setRuntime(r)
    })
    return () => {
      cancelled = true
    }
  }, [])

  const provider = runtime ? createProvider(runtime) : null
  const ready = Boolean(provider?.isAvailable())

  const title = !runtime
    ? '加载配置…'
    : !runtime.settings.enabled
      ? '需在设置中开启 AI 释义并联网'
      : !runtime.hasApiKey
        ? '请先在设置中填写 API 密钥'
        : busy
          ? '正在请求释义…'
          : '对当前片段请求 AI 释义'

  const dismiss = useCallback(() => setPanel(null), [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') dismiss()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [dismiss])

  const onClick = async () => {
    if (!provider || !ready || busy) return
    const gen = ++abortGen.current
    setBusy(true)
    setPanel(null)
    try {
      const result = await provider.explain({
        text: (snippet || '').trim() || '当前片段',
        scope,
        context,
      })
      if (gen !== abortGen.current) return
      if (result.ok) {
        setPanel({
          kind: 'ok',
          text: result.text,
          model: result.model,
          latencyMs: result.latencyMs,
        })
      } else {
        setPanel({ kind: 'err', message: result.message })
      }
    } catch {
      if (gen !== abortGen.current) return
      setPanel({ kind: 'err', message: '释义服务暂时不可用，请稍后重试' })
    } finally {
      if (gen === abortGen.current) setBusy(false)
    }
  }

  return (
    <span className="ai-explain-wrap">
      <button
        type="button"
        className="btn ai-explain-btn"
        disabled={!ready || busy}
        title={title}
        onClick={() => void onClick()}
      >
        {busy ? '释义中…' : 'AI 释义'}
      </button>

      {panel && (
        <div
          className={`ai-explain-panel ${panel.kind === 'err' ? 'ai-explain-panel--err' : ''}`}
          role="status"
        >
          <div className="ai-explain-panel__head">
            <strong>{panel.kind === 'ok' ? 'AI 释义' : '释义未完成'}</strong>
            <button type="button" className="btn btn--ghost ai-explain-panel__x" onClick={dismiss}>
              ×
            </button>
          </div>
          {panel.kind === 'ok' ? (
            <>
              <p className="ai-explain-panel__body">{panel.text}</p>
              <div className="ai-explain-panel__meta muted small">
                {panel.model && <span>{panel.model}</span>}
                {typeof panel.latencyMs === 'number' && <span>{panel.latencyMs} ms</span>}
              </div>
              <div className="ai-explain-panel__actions">
                <button type="button" className="btn btn--primary" onClick={dismiss}>
                  采纳
                </button>
                <button type="button" className="btn" onClick={dismiss}>
                  弃用
                </button>
              </div>
            </>
          ) : (
            <>
              <p className="ai-explain-panel__body">{panel.message}</p>
              <div className="ai-explain-panel__actions">
                <button type="button" className="btn" onClick={dismiss}>
                  知道了
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </span>
  )
}
