import type { ScreenResponse } from '../api/client'

/**
 * The TROJAN / CLEAN badge with a threat gauge (FR-019, FR-047).
 *
 * Colour never carries the meaning alone: the verdict word and an icon are always
 * present, and the gauge always renders its numeral beside the bar. A viewer who
 * cannot distinguish red from green still gets the full answer (SC-017).
 */
export function VerdictPanel({ result }: { result: ScreenResponse }) {
  const trojan = result.verdict === 'TROJAN'
  const pct = Math.round(result.confidence * 100)

  return (
    <div className={`verdict ${trojan ? 'trojan' : 'clean'} rise`} role="status">
      <div>
        <div className="badge">{trojan ? '⚠ TROJAN' : '✓ CLEAN'}</div>
        <div className="meta">
          {trojan ? (
            <>
              {result.findings.length} finding{result.findings.length === 1 ? '' : 's'} across{' '}
              {new Set(result.findings.map(f => f.sourceField)).size} field
              {new Set(result.findings.map(f => f.sourceField)).size === 1 ? '' : 's'} ·
              screened in {result.elapsedMs} ms
            </>
          ) : (
            <>No agent-directed manipulation found · screened in {result.elapsedMs} ms</>
          )}
        </div>
      </div>

      <div className="gauge">
        <div className="label">Threat score</div>
        <div className="value">{result.confidence.toFixed(2)}</div>
        <div className="track" role="img"
             aria-label={`Threat score ${result.confidence.toFixed(2)} out of 1.00`}>
          <span style={{ width: `${pct}%` }} />
        </div>
      </div>
    </div>
  )
}
