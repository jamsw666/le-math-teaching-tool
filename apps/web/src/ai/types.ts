/** ExplanationProvider 契约类型 — 对齐 specs/math-teaching-tool-explanation-provider.md */

/** 调用方必须只填「当前要点」，禁止默认塞整课 */
export type ExplainScope = 'snippet' | 'step' | 'concept'

export interface ExplainRequest {
  /** 用户显式点释义的文本（题干句/步骤句/概念名），建议 ≤ 2_000 字，超长截断并标记 truncated */
  text: string
  scope: ExplainScope
  /** 可选上下文：仅短标签，如课时名、步骤序号；禁止整课 steps[] */
  context?: {
    lessonTitle?: string
    itemTitle?: string
    stepIndex?: number
    /** 显式 opt-in 才允许附带上一句；默认 false / 缺省不传 */
    includePrevStep?: boolean
    prevStepText?: string
  }
  /** 客户端超时建议 ms；Provider 应尊重并 abort */
  timeoutMs?: number // 默认 20_000
}

export type ExplainErrorCode =
  | 'disabled'
  | 'no_api_key'
  | 'offline'
  | 'timeout'
  | 'unauthorized'
  | 'rate_limited'
  | 'quota'
  | 'bad_request'
  | 'upstream'
  | 'cancelled'

export interface ExplainSuccess {
  ok: true
  text: string
  model?: string
  latencyMs?: number
}

export interface ExplainFailure {
  ok: false
  code: ExplainErrorCode
  /** 人话，可直接 toast；不得含 apiKey */
  message: string
}

export type ExplainResult = ExplainSuccess | ExplainFailure

export interface ExplanationProvider {
  readonly id: 'noop' | 'openai-compatible'
  /** 是否可点「释义」（关开关/无密钥 → false；断网时可仍 true，点后返回 offline） */
  isAvailable(): boolean
  explain(req: ExplainRequest): Promise<ExplainResult>
}
