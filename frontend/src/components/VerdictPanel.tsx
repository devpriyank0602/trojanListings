import type { ScreenResponse } from '../api/client'
import { ThreatGauge } from './ThreatGauge'

/**
 * The TROJAN / CLEAN badge with a compass-style threat gauge (FR-019, FR-047).
 *
 * Colour never carries the meaning alone: the verdict word and an icon are always
 * present, the gauge carries SAFE/THREAT text labels on its own face, and the
 * numeral is always rendered beside it. A viewer who cannot distinguish red from
 * green still gets the full answer from the words alone (SC-017).
 */
export function VerdictPanel({ result }: { result: ScreenResponse }) {
  const trojan = result.verdict === 'TROJAN'

  return (
    <div className={`verdict ${trojan ? 'trojan' : 'clean'} materialize`} role="status">
      <div className="verdict-text">
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

      <ThreatGauge value={result.confidence} trojan={trojan} />
    </div>
  )
}
