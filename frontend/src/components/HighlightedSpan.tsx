import { useMemo } from 'react'
import type { Finding } from '../api/client'

/**
 * Renders the screened listing with every finding's span marked, plus the toggle that
 * makes invisible content visible (FR-019, FR-020).
 *
 * The toggle is the demo's strongest beat: the same listing shown twice, and the
 * second one is obviously hostile.
 */
interface Props {
  assembled: string
  findings: Finding[]
  revealed: boolean
  onToggleRevealed: (v: boolean) => void
}

export function HighlightedSpan({ assembled, findings, revealed, onToggleRevealed }: Props) {
  const segments = useMemo(() => buildSegments(assembled, findings, revealed), [assembled, findings, revealed])
  const hasHidden = findings.some(f => f.revealedSpan && f.revealedSpan !== f.span)

  return (
    <>
      <h3>Marked-up listing</h3>
      <pre className="assembled">
        {segments.map((s, i) =>
          s.hit ? <mark className="hit" key={i}>{s.text}</mark> : <span key={i}>{s.text}</span>
        )}
      </pre>

      <label className="toggle">
        <input type="checkbox" checked={revealed} disabled={!hasHidden}
               onChange={e => onToggleRevealed(e.target.checked)} />
        Reveal hidden characters
        {!hasHidden && <span className="note"> (nothing hidden in this listing)</span>}
      </label>
    </>
  )
}

interface Segment { text: string; hit: boolean }

/** Merges overlapping spans so a character is never wrapped twice. */
function buildSegments(text: string, findings: Finding[], revealed: boolean): Segment[] {
  const ranges = findings
    .map(f => ({
      start: Math.max(0, f.startOffset),
      end: Math.min(text.length, f.endOffset),
      replacement: revealed ? f.revealedSpan : null,
    }))
    .filter(r => r.end > r.start)
    .sort((a, b) => a.start - b.start)

  const merged: typeof ranges = []
  for (const r of ranges) {
    const last = merged[merged.length - 1]
    if (last && r.start <= last.end) {
      last.end = Math.max(last.end, r.end)
      last.replacement = last.replacement ?? r.replacement
    } else {
      merged.push({ ...r })
    }
  }

  const out: Segment[] = []
  let cursor = 0
  for (const r of merged) {
    if (r.start > cursor) out.push({ text: text.slice(cursor, r.start), hit: false })
    out.push({ text: r.replacement ?? text.slice(r.start, r.end), hit: true })
    cursor = r.end
  }
  if (cursor < text.length) out.push({ text: text.slice(cursor), hit: false })
  return out
}
