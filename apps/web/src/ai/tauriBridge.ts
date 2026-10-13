/**
 * Tauri AI invoke 薄封装（docs/local-api.md §5）。
 * 壳内密钥走 ai_set_api_key；永不把 apiKey 打进日志。
 */

declare global {
  interface Window {
    __TAURI_INTERNALS__?: unknown
  }
}

export function isTauriShell(): boolean {
  return typeof window !== 'undefined' && Boolean(window.__TAURI_INTERNALS__)
}

type InvokeFn = (cmd: string, args?: Record<string, unknown>) => Promise<unknown>

let invokeFn: InvokeFn | null | undefined

async function getInvoke(): Promise<InvokeFn | null> {
  if (invokeFn !== undefined) return invokeFn
  if (!isTauriShell()) {
    invokeFn = null
    return null
  }
  try {
    const spec = '@tauri-apps/api/core'
    const mod = (await import(/* @vite-ignore */ spec).catch(() => null)) as {
      invoke?: InvokeFn
    } | null
    invokeFn = mod?.invoke ?? null
  } catch {
    invokeFn = null
  }
  return invokeFn
}

export async function aiInvoke<T>(
  cmd: string,
  args?: Record<string, unknown>,
): Promise<T> {
  const invoke = await getInvoke()
  if (!invoke) throw new Error('Tauri invoke 不可用')
  return (await invoke(cmd, args)) as T
}

/** 非密钥设置（与 Rust AiSettings camelCase 对齐） */
export interface TauriAiSettings {
  enabled: boolean
  provider: string
  baseUrl: string
  model: string
  timeoutMs: number
}

export async function tauriGetSettings(): Promise<TauriAiSettings> {
  return aiInvoke<TauriAiSettings>('ai_get_settings')
}

export async function tauriSaveSettings(settings: TauriAiSettings): Promise<void> {
  await aiInvoke('ai_save_settings', { settings })
}

export async function tauriSetApiKey(apiKey: string): Promise<void> {
  await aiInvoke('ai_set_api_key', { apiKey })
}

export async function tauriClearApiKey(): Promise<void> {
  await aiInvoke('ai_clear_api_key')
}

export async function tauriHasApiKey(): Promise<boolean> {
  return aiInvoke<boolean>('ai_has_api_key')
}

export async function tauriExplain(req: Record<string, unknown>): Promise<unknown> {
  return aiInvoke('ai_explain', { req })
}
