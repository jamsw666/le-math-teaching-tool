/**
 * AI 释义配置。
 * - 浏览器开发：非密钥 + apiKey 暂存 localStorage（契约允许 MVP）
 * - Tauri 壳：enabled/baseUrl/model/timeoutMs → ai_get/save_settings；
 *   密钥 → ai_set/clear/has_api_key，**不**进 localStorage（见 docs/local-api.md §5）
 */

import {
  isTauriShell,
  tauriClearApiKey,
  tauriGetSettings,
  tauriHasApiKey,
  tauriSaveSettings,
  tauriSetApiKey,
  type TauriAiSettings,
} from './ai/tauriBridge'

export interface AiSettings {
  enabled: boolean
  provider: string
  baseUrl: string
  /**
   * 仅浏览器 fallback 持有明文。
   * 壳内恒为 ''；有无密钥看 AiRuntime.hasApiKey / ai_has_api_key。
   */
  apiKey: string
  model: string
  /** 请求超时 ms，默认 20000 */
  timeoutMs?: number
}

export type AiMode = 'tauri' | 'browser'

export interface AiRuntime {
  settings: AiSettings
  /** 壳内来自 ai_has_api_key；浏览器来自 settings.apiKey 非空 */
  hasApiKey: boolean
  mode: AiMode
}

export const AI_STORAGE_KEY = 'math-ai-settings'

export const DEFAULT_AI_SETTINGS: AiSettings = {
  enabled: false,
  provider: 'DeepSeek',
  baseUrl: 'https://api.deepseek.com',
  apiKey: '',
  model: 'deepseek-chat',
  timeoutMs: 20_000,
}

function readLocalRaw(): Partial<AiSettings> | null {
  try {
    const raw = localStorage.getItem(AI_STORAGE_KEY)
    if (!raw) return null
    return JSON.parse(raw) as Partial<AiSettings>
  } catch {
    return null
  }
}

function writeLocal(s: AiSettings): void {
  localStorage.setItem(AI_STORAGE_KEY, JSON.stringify(s))
}

/** 同步读：浏览器用；壳内仅作迁移前兜底，apiKey 会被剥掉 */
export function loadAiSettings(): AiSettings {
  const parsed = readLocalRaw()
  if (!parsed) return { ...DEFAULT_AI_SETTINGS }
  return {
    ...DEFAULT_AI_SETTINGS,
    ...parsed,
    enabled: Boolean(parsed.enabled),
    timeoutMs:
      typeof parsed.timeoutMs === 'number' && parsed.timeoutMs > 0
        ? parsed.timeoutMs
        : DEFAULT_AI_SETTINGS.timeoutMs,
  }
}

/** 浏览器：整包写入（含 apiKey）。壳内请用 saveAiRuntime / setAiApiKey。 */
export function saveAiSettings(s: AiSettings): void {
  writeLocal(s)
}

export function clearAiApiKey(s: AiSettings): AiSettings {
  const next = { ...s, apiKey: '' }
  saveAiSettings(next)
  return next
}

function toTauriSettings(s: AiSettings): TauriAiSettings {
  return {
    enabled: Boolean(s.enabled),
    provider: s.provider || DEFAULT_AI_SETTINGS.provider,
    baseUrl: s.baseUrl || DEFAULT_AI_SETTINGS.baseUrl,
    model: s.model || DEFAULT_AI_SETTINGS.model,
    timeoutMs: s.timeoutMs ?? DEFAULT_AI_SETTINGS.timeoutMs ?? 20_000,
  }
}

function fromTauriSettings(t: TauriAiSettings): AiSettings {
  return {
    enabled: Boolean(t.enabled),
    provider: t.provider || DEFAULT_AI_SETTINGS.provider,
    baseUrl: t.baseUrl || DEFAULT_AI_SETTINGS.baseUrl,
    model: t.model || DEFAULT_AI_SETTINGS.model,
    timeoutMs: t.timeoutMs || 20_000,
    apiKey: '',
  }
}

/** 壳内：把旧 localStorage apiKey 迁到 ai_set_api_key，并清掉 LS 里的明文 */
async function migrateLegacyKeyIfNeeded(): Promise<void> {
  const parsed = readLocalRaw()
  const legacyKey = parsed?.apiKey?.trim()
  if (!legacyKey) {
    // 仍确保 LS 不残留空字段以外的密钥形态
    if (parsed && 'apiKey' in parsed) {
      const { apiKey: _drop, ...rest } = parsed as AiSettings
      writeLocal({ ...DEFAULT_AI_SETTINGS, ...rest, apiKey: '' })
    }
    return
  }
  try {
    await tauriSetApiKey(legacyKey)
  } catch {
    // 迁移失败则保留 LS，下次再试；不把密钥打进日志
    return
  }
  const stripped: AiSettings = {
    ...DEFAULT_AI_SETTINGS,
    ...parsed,
    enabled: Boolean(parsed?.enabled),
    apiKey: '',
  }
  writeLocal(stripped)
  // 非密钥也推一版到壳，避免只迁了密钥
  try {
    await tauriSaveSettings(toTauriSettings(stripped))
  } catch {
    /* ignore */
  }
}

/** 异步加载：壳优先 Tauri；浏览器 localStorage */
export async function loadAiRuntime(): Promise<AiRuntime> {
  if (isTauriShell()) {
    await migrateLegacyKeyIfNeeded()
    try {
      const [remote, has] = await Promise.all([tauriGetSettings(), tauriHasApiKey()])
      const settings = fromTauriSettings(remote)
      // 壳内 LS 只作非密钥缓存（无 apiKey）
      writeLocal({ ...settings, apiKey: '' })
      return { settings, hasApiKey: Boolean(has), mode: 'tauri' }
    } catch {
      // 命令异常时退回浏览器态读写，避免设置页挂死
      const settings = loadAiSettings()
      return {
        settings: { ...settings, apiKey: settings.apiKey },
        hasApiKey: Boolean(settings.apiKey?.trim()),
        mode: 'browser',
      }
    }
  }
  const settings = loadAiSettings()
  return {
    settings,
    hasApiKey: Boolean(settings.apiKey?.trim()),
    mode: 'browser',
  }
}

/** 保存非密钥字段 */
export async function saveAiRuntimeSettings(settings: AiSettings): Promise<AiRuntime> {
  if (isTauriShell()) {
    await tauriSaveSettings(toTauriSettings(settings))
    const has = await tauriHasApiKey()
    const next = { ...settings, apiKey: '' }
    writeLocal(next)
    return { settings: next, hasApiKey: has, mode: 'tauri' }
  }
  saveAiSettings(settings)
  return {
    settings,
    hasApiKey: Boolean(settings.apiKey?.trim()),
    mode: 'browser',
  }
}

export async function setAiApiKey(
  settings: AiSettings,
  apiKey: string,
): Promise<AiRuntime> {
  const key = apiKey.trim()
  if (isTauriShell()) {
    if (key) await tauriSetApiKey(key)
    const has = await tauriHasApiKey()
    const next = { ...settings, apiKey: '' }
    writeLocal(next)
    return { settings: next, hasApiKey: key ? true : has, mode: 'tauri' }
  }
  const next = { ...settings, apiKey: key }
  saveAiSettings(next)
  return { settings: next, hasApiKey: Boolean(key), mode: 'browser' }
}

export async function clearAiApiKeyRuntime(settings: AiSettings): Promise<AiRuntime> {
  if (isTauriShell()) {
    await tauriClearApiKey()
    const next = { ...settings, apiKey: '' }
    writeLocal(next)
    return { settings: next, hasApiKey: false, mode: 'tauri' }
  }
  const next = clearAiApiKey(settings)
  return { settings: next, hasApiKey: false, mode: 'browser' }
}
