import type { ScreenResponse } from '../api/client'

/**
 * The TROJAN / CLEAN badge (FR-019).
 *
 * Confidence and latency always carry their label -- a bare number means nothing to
 * a first-time viewer, and SC-008 requires the verdict to be understood unaided.
 */
export function VerdictPanel({ result }: { result: ScreenResponse }) {
  const trojan = result.verdict === 'TROJAN'
  return (
    <div className={`verdict ${trojan ? 'trojan' : 'clean'}`} role="status">
      <div className="headline">{trojan ? '⚠ TROJAN' : '✓ CLEAN'}</div>
      <div className="meta">
        {trojan
          ? <>confidence {result.confidence.toFixed(2)} · {result.findings.length} finding
              {result.findings.length === 1 ? '' : 's'} · screened in {result.elapsedMs} ms</>
          : <>No agent-directed manipulation found · screened in {result.elapsedMs} ms</>}
      </div>
    </div>
  )
}
