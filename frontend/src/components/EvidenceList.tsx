import { CONCEALMENT_LABEL, LAYER_LABEL, fieldLabel, type Finding } from '../api/client'

/**
 * "Why this is a Trojan" -- one card per finding (FR-019).
 *
 * This is the "on what basis" the reviewer asked for. Enum values are never rendered
 * raw: INVISIBLE_MARKUP shows as "Hidden markup". Nobody should have to decode an
 * identifier to understand a verdict (SC-008).
 */
export function EvidenceList({ findings }: { findings: Finding[] }) {
  if (findings.length === 0) return null

  return (
    <>
      <h3>Why this is a Trojan</h3>
      {findings.map((f, i) => (
        <div className="finding" key={i}>
          <div className="head">
            <span className="technique">
              {i + 1}. {CONCEALMENT_LABEL[f.concealment]}
            </span>
            <span className="score">score {f.score.toFixed(2)}</span>
          </div>

          <div className="where">
            found in <strong>{fieldLabel(f.sourceField)}</strong> · {LAYER_LABEL[f.layer]}
          </div>

          <div className="span">{truncate(f.span, 300)}</div>

          {f.revealedSpan && f.revealedSpan !== f.span && (
            <div className="span" style={{ marginTop: 6, background: '#f2f7ff', borderColor: '#d7e4fb' }}>
              <strong style={{ fontSize: 11, letterSpacing: '0.06em' }}>REVEALED: </strong>
              {truncate(f.revealedSpan, 300)}
            </div>
          )}

          <div className="why">{f.explanation}</div>
        </div>
      ))}
    </>
  )
}

function truncate(s: string, n: number) {
  return s.length <= n ? s : `${s.slice(0, n)}…`
}
