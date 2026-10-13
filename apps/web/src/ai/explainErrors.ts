import type { ExplainErrorCode, ExplainFailure } from './types'

const MESSAGES: Record<ExplainErrorCode, string> = {
  disabled: 'AI 释义未开启，可在设置中打开',
  no_api_key: '请先在设置中填写 API 密钥',
  offline: '当前无法联网，释义暂不可用',
  timeout: '释义超时，请稍后重试',
  unauthorized: '密钥无效或无权限，请检查设置',
  rate_limited: '请求过于频繁，请稍后再试',
  quota: '服务余额不足或配额用尽',
  bad_request: '请求格式有误，请稍后重试',
  upstream: '释义服务暂时不可用，请稍后重试',
  cancelled: '已取消本次释义',
}

/** 人话错误；message 不得含 apiKey */
export function humanExplainError(
  code: ExplainErrorCode,
  override?: string,
): ExplainFailure {
  return {
    ok: false,
    code,
    message: override?.trim() || MESSAGES[code],
  }
}

/** 将 HTTP 状态 / 上游文案映射为错误码（不得把密钥拼进 message） */
export function mapHttpStatus(status: number, bodyText?: string): ExplainFailure {
  const lower = (bodyText || '').toLowerCase()
  if (status === 401 || status === 403) return humanExplainError('unauthorized')
  if (status === 429) return humanExplainError('rate_limited')
  if (status === 400) return humanExplainError('bad_request')
  if (
    status === 402 ||
    lower.includes('insufficient') ||
    lower.includes('quota') ||
    lower.includes('balance') ||
    lower.includes('billing') ||
    lower.includes('欠费') ||
    lower.includes('余额')
  ) {
    return humanExplainError('quota')
  }
  if (status >= 500) return humanExplainError('upstream')
  return humanExplainError('upstream')
}

export function mapUnknownError(err: unknown): ExplainFailure {
  if (err && typeof err === 'object' && 'name' in err) {
    const name = String((err as { name?: string }).name)
    if (name === 'AbortError' || name === 'TimeoutError') {
      return humanExplainError('timeout')
    }
  }
  const msg = err instanceof Error ? err.message : String(err ?? '')
  const lower = msg.toLowerCase()
  // 绝不把含 Authorization / key 的原文回显给 UI
  if (
    lower.includes('failed to fetch') ||
    lower.includes('networkerror') ||
    lower.includes('network request failed') ||
    lower.includes('net::err') ||
    lower.includes('offline')
  ) {
    return humanExplainError('offline')
  }
  if (lower.includes('abort') || lower.includes('timeout')) {
    return humanExplainError('timeout')
  }
  return humanExplainError('upstream')
}
