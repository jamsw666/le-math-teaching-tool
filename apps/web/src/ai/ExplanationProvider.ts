import {
  loadAiRuntime,
  loadAiSettings,
  type AiRuntime,
  type AiSettings,
} from '../aiSettings'
import { HttpOpenAICompatibleProvider } from './HttpOpenAICompatibleProvider'
import { NoopExplanationProvider } from './NoopExplanationProvider'
import { isTauriShell } from './tauriBridge'
import type { ExplanationProvider } from './types'

export type { ExplanationProvider } from './types'
export { NoopExplanationProvider } from './NoopExplanationProvider'
export { HttpOpenAICompatibleProvider } from './HttpOpenAICompatibleProvider'

/**
 * createProvider 规则：
 * - !enabled → Noop
 * - enabled → Http（hasApiKey 决定 isAvailable；壳内走 ai_explain）
 */
export function createProvider(
  settingsOrRuntime?: AiSettings | AiRuntime,
): ExplanationProvider {
  let settings: AiSettings
  let hasApiKey: boolean
  let preferTauri = isTauriShell()

  if (settingsOrRuntime && 'settings' in settingsOrRuntime) {
    settings = settingsOrRuntime.settings
    hasApiKey = settingsOrRuntime.hasApiKey
    preferTauri = settingsOrRuntime.mode === 'tauri'
  } else {
    settings = settingsOrRuntime ?? loadAiSettings()
    hasApiKey = Boolean(settings.apiKey?.trim())
  }

  if (!settings.enabled) return new NoopExplanationProvider()
  return new HttpOpenAICompatibleProvider(settings, { hasApiKey, preferTauri })
}

/** 推荐：异步拉齐壳内 hasApiKey 后再 create */
export async function createProviderAsync(): Promise<{
  provider: ExplanationProvider
  runtime: AiRuntime
}> {
  const runtime = await loadAiRuntime()
  return { provider: createProvider(runtime), runtime }
}
