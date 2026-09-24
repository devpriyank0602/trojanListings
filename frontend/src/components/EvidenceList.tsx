import { CONCEALMENT_LABEL, LAYER_LABEL, fieldLabel, type Finding } from '../api/client'

/**
 * "Why this is a Trojan" — one card per finding (FR-019).
 *
 * Given the spotlight treatment: this is the main deliverable of the whole
 * screening pipeline, so it gets a glowing hero frame that distinguishes it from
 * the more procedural Screening Layers / Marked-up Listing sections above and
 * below it. Each card is numbered, and the number keys back to the matching
 * highlight in the marked-up listing. Enum values are never rendered raw:
 * INVISIBLE_MARKUP shows as "Hidden markup". Nobody should have to decode an
 * identifier to understand a verdict (SC-008, FR-047).
 */
export function EvidenceList({ findings }: { findings: Finding[] }) {
  if (findings.length === 0) return null

  return (
    <div className="spotlight">
      <h3 className="spotlight-head">
        <span className="pulse-dot" aria-hidden="true" />
        Why this is a Trojan
      </h3>

      {findings.map((f, i) => (
        <div className={`finding rise rise-${Math.min(i + 1, 4)}`} key={i}>
          <div className="head">
            <span className="technique">
              <span className="num" aria-hidden="true">{i + 1}</span>
              {CONCEALMENT_LABEL[f.concealment]}
            </span>
            <span className="score">score {f.score.toFixed(2)}</span>
          </div>

          <div className="where">
            found in <strong>{fieldLabel(f.sourceField)}</strong> · detected by{' '}
            {LAYER_LABEL[f.layer]}
          </div>

          <div className="span">{truncate(f.span, 300)}</div>

          {f.revealedSpan && f.revealedSpan !== f.span && (
            <div className="span revealed">
              <strong style={{ fontSize: 10.5, letterSpacing: '.08em' }}>REVEALED&nbsp;</strong>
              {truncate(f.revealedSpan, 300)}
            </div>
          )}

          <div className="why">{f.explanation}</div>
        </div>
      ))}
    </div>
  )
}

function truncate(s: string, n: number) {
  return s.length <= n ? s : `${s.slice(0, n)}…`
}
