import { humanExplainError } from './explainErrors'
import type { ExplainRequest, ExplainResult, ExplanationProvider } from './types'

/** 开关关 / 不可用时的空实现：不发网、不挡主路径 */
export class NoopExplanationProvider implements ExplanationProvider {
  readonly id = 'noop' as const

  isAvailable(): boolean {
    return false
  }

  async explain(_req: ExplainRequest): Promise<ExplainResult> {
    return humanExplainError('disabled')
  }
}
