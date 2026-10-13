import type { AiSettings } from '../aiSettings'
import { humanExplainError, mapHttpStatus, mapUnknownError } from './explainErrors'
import { isTauriShell, tauriExplain } from './tauriBridge'
import type {
  ExplainErrorCode,
  ExplainRequest,
  ExplainResult,
  ExplanationProvider,
} from './types'

const MAX_TEXT = 2000
const DEFAULT_TIMEOUT_MS = 20_000

const SYSTEM_PROMPT =
  '你是高中数学助教。用简明中文解释用户给出的概念或解题步骤，面向课堂老师备课/讲解，不要输出整份教案，不要索取更多课时全文。'

const KNOWN_CODES: ExplainErrorCode[] = [
  'disabled',
  'no_api_key',
  'offline',
  'timeout',
  'unauthorized',
  'rate_limited',
  'quota',
  'bad_request',
  'upstream',
  'cancelled',
]

function asErrorCode(raw: unknown): ExplainErrorCode {
  const s = String(raw || '')
  return (KNOWN_CODES as string[]).includes(s) ? (s as ExplainErrorCode) : 'upstream'
}

export interface HttpProviderOptions {
  /** 壳内来自 ai_has_api_key；浏览器来自 local apiKey */
  hasApiKey: boolean
  /** 强制走 Tauri ai_explain（默认：检测到壳则 true） */
  preferTauri?: boolean
}

/** 规范化 baseUrl → chat/completions 完整 URL（避免 /v1 重复） */
export function buildChatCompletionsUrl(baseUrl: string): string {
  const trimmed = (baseUrl || '').trim().replace(/\/+$/, '')
  if (!trimmed) return 'https://api.deepseek.com/v1/chat/completions'
  if (/\/chat\/completions$/i.test(trimmed)) return trimmed
  if (/\/v1$/i.test(trimmed)) return `${trimmed}/chat/completions`
  return `${trimmed}/v1/chat/completions`
}

/** 仅组装当前片段 + 可选短标签（I10：禁止整课） */
export function buildUserContent(req: ExplainRequest): {
  content: string
  truncated: boolean
} {
  let text = (req.text || '').trim()
  let truncated = false
  if (text.length > MAX_TEXT) {
    text = text.slice(0, MAX_TEXT)
    truncated = true
  }
  const parts: string[] = [text || '（空片段）']
  const ctx = req.context
  if (ctx) {
    const tags: string[] = []
    if (ctx.lessonTitle) tags.push(`课时：${ctx.lessonTitle}`)
    if (ctx.itemTitle) tags.push(`条目：${ctx.itemTitle}`)
    if (typeof ctx.stepIndex === 'number') tags.push(`步骤：${ctx.stepIndex}`)
    if (tags.length) parts.push(tags.join('｜'))
    if (ctx.includePrevStep && ctx.prevStepText?.trim()) {
      parts.push(`上一句：${ctx.prevStepText.trim().slice(0, 400)}`)
    }
  }
  if (truncated) parts.push('（文本已截断）')
  return { content: parts.join('\n'), truncated }
}

type TauriExplainPayload =
  | { ok: true; text: string; model?: string; latencyMs?: number }
  | { ok: false; code?: string; message?: string }

/**
 * OpenAI 兼容 Chat Completions（DeepSeek 等）。
 * 壳内优先 `ai_explain`（密钥不出 renderer）；无壳则浏览器 fetch。
 * 日志不得打印 apiKey（I11）。
 */
export class HttpOpenAICompatibleProvider implements ExplanationProvider {
  readonly id = 'openai-compatible' as const
  private settings: AiSettings
  private hasApiKey: boolean
  private preferTauri: boolean

  constructor(settings: AiSettings, opts?: HttpProviderOptions) {
    this.settings = settings
    this.hasApiKey = Boolean(opts?.hasApiKey ?? settings.apiKey?.trim())
    this.preferTauri = opts?.preferTauri ?? isTauriShell()
  }

  isAvailable(): boolean {
    return Boolean(this.settings.enabled && this.hasApiKey)
  }

  async explain(req: ExplainRequest): Promise<ExplainResult> {
    if (!this.settings.enabled) return humanExplainError('disabled')
    if (!this.hasApiKey && !this.settings.apiKey?.trim()) {
      return humanExplainError('no_api_key')
    }

    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      return humanExplainError('offline')
    }

    const timeoutMs = req.timeoutMs ?? this.settings.timeoutMs ?? DEFAULT_TIMEOUT_MS

    if (this.preferTauri && isTauriShell()) {
      const viaTauri = await this.tryTauriExplain(req, timeoutMs)
      if (viaTauri !== 'fallback') return viaTauri
    }

    // 浏览器 fallback：需要前端持有的 apiKey
    if (!this.settings.apiKey?.trim()) {
      return humanExplainError('no_api_key')
    }
    const { content } = buildUserContent(req)
    return this.fetchExplain(content, timeoutMs)
  }

  private async tryTauriExplain(
    req: ExplainRequest,
    timeoutMs: number,
  ): Promise<ExplainResult | 'fallback'> {
    try {
      const started = Date.now()
      // 契约入参：仅 ExplainRequest；密钥由后端读取
      const raw = (await tauriExplain({
        text: req.text,
        scope: req.scope,
        context: req.context,
        timeoutMs,
      })) as TauriExplainPayload

      if (raw && typeof raw === 'object' && 'ok' in raw) {
        if (raw.ok === true) {
          return {
            ok: true,
            text: String(raw.text || '').trim(),
            model: raw.model ?? this.settings.model,
            latencyMs: raw.latencyMs ?? Date.now() - started,
          }
        }
        // 失败用人话 message（后端已映射）；勿改写敏感信息
        return humanExplainError(asErrorCode(raw.code), raw.message)
      }
      return 'fallback'
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err)
      if (/not found|unknown command|command.*ai_explain|does not exist/i.test(msg)) {
        if (import.meta.env.DEV) {
          console.info('[ai] ai_explain unavailable, fetch fallback (dev)')
        }
        return 'fallback'
      }
      return mapUnknownError(err)
    }
  }

  private async fetchExplain(
    userContent: string,
    timeoutMs: number,
  ): Promise<ExplainResult> {
    const url = buildChatCompletionsUrl(this.settings.baseUrl)
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), timeoutMs)
    const started = Date.now()

    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this.settings.apiKey}`,
        },
        body: JSON.stringify({
          model: this.settings.model || 'deepseek-chat',
          messages: [
            { role: 'system', content: SYSTEM_PROMPT },
            { role: 'user', content: userContent },
          ],
          temperature: 0.3,
        }),
        signal: controller.signal,
      })

      const bodyText = await res.text()
      if (!res.ok) {
        return mapHttpStatus(res.status, bodyText.slice(0, 400))
      }

      let data: {
        choices?: Array<{ message?: { content?: string } }>
        model?: string
      }
      try {
        data = JSON.parse(bodyText) as typeof data
      } catch {
        return humanExplainError('upstream', '释义返回无法解析')
      }

      const text = data.choices?.[0]?.message?.content?.trim() ?? ''
      if (!text) return humanExplainError('upstream', '释义返回为空')

      return {
        ok: true,
        text,
        model: data.model ?? this.settings.model,
        latencyMs: Date.now() - started,
      }
    } catch (err) {
      return mapUnknownError(err)
    } finally {
      clearTimeout(timer)
    }
  }
}
