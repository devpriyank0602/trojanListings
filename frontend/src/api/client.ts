// Typed against specs/001-listing-injection-defense/contracts/rest-api.md.
// All calls go to localhost:8080 via the Vite proxy. No CDN, no external service --
// the UI must work with the network disconnected (SC-013).

export type Verdict = 'TROJAN' | 'CLEAN'
export type Mode = 'FULL' | 'DEGRADED_NO_CLASSIFIER'
export type ImageStatus = 'SCREENED' | 'NOT_SCREENED' | 'NO_IMAGE'
export type Layer = 'STRUCTURAL' | 'PATTERN' | 'CLASSIFIER' | 'IMAGE_TEXT'
export type Concealment =
  | 'ZERO_WIDTH' | 'HOMOGLYPH' | 'ENCODED_PAYLOAD'
  | 'CHAT_TEMPLATE' | 'INVISIBLE_MARKUP' | 'NONE'

export interface Finding {
  layer: Layer
  concealment: Concealment
  sourceField: string
  span: string
  startOffset: number
  endOffset: number
  revealedSpan: string
  score: number
  explanation: string
}

export interface ScreenResponse {
  verdict: Verdict
  confidence: number
  /** The exact text screened; finding offsets index into this. */
  assembledText: string
  mode: Mode
  imageScreened: ImageStatus
  imageNote: string | null
  extractedImageText: string | null
  elapsedMs: number
  findings: Finding[]
}

export interface ScreenRequest {
  title?: string
  description?: string
  itemSpecifics?: Record<string, string>
  imageBase64?: string | null
}

export interface Rates {
  total: number
  fullCompliance: number
  partialCompliance: number
  refusal: number
  complianceRate: number
}

export interface ExposureReport {
  runId: string
  byTechnique: Record<string, Rates>
  byGoal: Record<string, Rates>
  benignControlRate: Rates
  notMeasured: number
}

export interface Trial {
  runId: string
  fixtureId: string
  technique: string | null
  goal: string | null
  hostile: boolean
  agentModel: string
  agentResponse: string
  outcome: 'FULL_COMPLIANCE' | 'PARTIAL_COMPLIANCE' | 'REFUSAL' | 'NOT_MEASURED'
  conditionEvaluated: string
  matchedText: string
  recordedAt: string
  error: string | null
}

export interface RunSummary {
  runId: string
  recordedAt: string
  agentModel: string
  trialCount: number
}

export interface Sample {
  id: string
  hostile: boolean
  technique: string
  goal: string
  note: string
  hasImage: boolean
  listing: {
    title: string
    description: string
    itemSpecifics: Record<string, string>
    imagePath: string | null
  }
}

export interface Health {
  status: string
  classifierLoaded: boolean
  ocrAvailable: boolean
  corpusSize: number
}

export class BackendUnreachable extends Error {
  constructor() {
    super('Backend not running — start it with:  cd backend && mvn spring-boot:run')
    this.name = 'BackendUnreachable'
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  // Browser-console log, separate from the Vite-terminal log in vite.config.ts --
  // this one is visible in devtools even if you never look at the terminal.
  const method = init?.method ?? 'GET'
  const started = performance.now()
  console.log(`%c→ ${method} ${path}`, 'color:#4F8CFF')

  let res: Response
  try {
    res = await fetch(path, init)
  } catch (e) {
    console.log(`%c✗ ${method} ${path} -> unreachable`, 'color:#D93025', e)
    // A network-level failure here means the backend is down, not that the user did
    // something wrong. Say so plainly and give the command that fixes it.
    throw new BackendUnreachable()
  }

  const ms = Math.round(performance.now() - started)
  console.log(`%c← ${method} ${path} -> ${res.status} (${ms} ms)`, res.ok ? 'color:#1E7E45' : 'color:#B26B00')

  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    const err = new Error((body as { error?: string }).error ?? `HTTP ${res.status}`)
    ;(err as Error & { status?: number }).status = res.status
    throw err
  }
  return res.json() as Promise<T>
}

export const api = {
  screen: (body: ScreenRequest) =>
    request<ScreenResponse>('/api/screen', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),

  health: () => request<Health>('/api/health'),
  samples: () => request<{ samples: Sample[] }>('/api/samples'),

  /**
   * The corpus photo for a fixture, as a data URL ready for the photo box.
   *
   * Returns null rather than throwing when the fixture has no photo, because
   * "this sample is text-only" is an ordinary outcome of loading a sample, not an
   * error worth interrupting the reviewer for.
   */
  sampleImage: async (id: string): Promise<string | null> => {
    const path = `/api/samples/${encodeURIComponent(id)}/image`
    try {
      const res = await fetch(path)
      if (!res.ok) return null
      const blob = await res.blob()
      return await new Promise<string>((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(String(reader.result))
        reader.onerror = () => reject(reader.error)
        reader.readAsDataURL(blob)
      })
    } catch {
      return null
    }
  },

  listRuns: () => request<{ runs: RunSummary[] }>('/api/runs'),
  report: (runId: string) => request<ExposureReport>(`/api/runs/${runId}/report`),
  trials: (runId: string, params: Record<string, string> = {}) => {
    const qs = new URLSearchParams(params).toString()
    return request<{ trials: Trial[] }>(`/api/runs/${runId}/trials${qs ? `?${qs}` : ''}`)
  },
}

// --- display helpers -------------------------------------------------------
// Enum values are never shown raw. A judge reading the screen should not have to
// decode identifiers (contracts/ui-contract.md, SC-008).

export const CONCEALMENT_LABEL: Record<Concealment, string> = {
  ZERO_WIDTH: 'Zero-width characters',
  HOMOGLYPH: 'Look-alike letters',
  ENCODED_PAYLOAD: 'Encoded payload',
  CHAT_TEMPLATE: 'Forged chat delimiter',
  INVISIBLE_MARKUP: 'Hidden markup',
  NONE: 'Plain instruction',
}

export const LAYER_LABEL: Record<Layer, string> = {
  STRUCTURAL: 'Structure scan',
  PATTERN: 'Known phrasing',
  CLASSIFIER: 'Classifier',
  IMAGE_TEXT: 'Text in photo',
}

export const TECHNIQUE_LABEL: Record<string, string> = {
  FREE_TEXT: 'Free text',
  STRUCTURED_FIELD: 'Structured field',
  OBFUSCATED: 'Obfuscated',
  IN_IMAGE: 'In image',
  BENIGN: 'Benign control',
}

export const OUTCOME_LABEL: Record<string, string> = {
  FULL_COMPLIANCE: 'Full compliance',
  PARTIAL_COMPLIANCE: 'Partial compliance',
  REFUSAL: 'Refusal',
  NOT_MEASURED: 'Not measured',
}

export function fieldLabel(sourceField: string): string {
  if (sourceField === 'image') return 'photo'
  if (sourceField.startsWith('specific:')) return sourceField.slice('specific:'.length)
  return sourceField
}
