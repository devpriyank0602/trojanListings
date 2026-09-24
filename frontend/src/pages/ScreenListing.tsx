import { useState } from 'react'
import { api, BackendUnreachable, type ScreenResponse } from '../api/client'
import { ListingInput, EMPTY_DRAFT, type ListingDraft } from '../components/ListingInput'
import { VerdictPanel } from '../components/VerdictPanel'
import { ScreeningLayers } from '../components/ScreeningLayers'
import { EvidenceList } from '../components/EvidenceList'
import { HighlightedSpan } from '../components/HighlightedSpan'
import { ImageEvidence } from '../components/ImageEvidence'

/**
 * Screen 1 — paste a seller listing, learn whether it is a Trojan and exactly why
 * (FR-018 to FR-021, FR-038, FR-044 to FR-046).
 *
 * While a screening request is in flight, the WHOLE screen goes into an "active"
 * state (`.split.active`) rather than just the result panel quietly loading: both
 * panels glow, and an energy beam sweeps left-to-right across the divide, so the
 * verdict reads as arriving *from* the analysis rather than just popping in. This
 * is still bounded by the real request duration (FR-046) -- the beam runs for as
 * long as `busy` is true, which is exactly as long as the fetch takes.
 */
export function ScreenListing({ initialDraft }: { initialDraft?: ListingDraft }) {
  const [draft, setDraft] = useState<ListingDraft>(initialDraft ?? EMPTY_DRAFT)
  const [result, setResult] = useState<ScreenResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [revealed, setRevealed] = useState(false)

  async function analyse() {
    setBusy(true); setError(null); setRevealed(false); setResult(null)
    try {
      const specifics = Object.fromEntries(
        draft.specifics.filter(s => s.key.trim()).map(s => [s.key.trim(), s.value])
      )
      setResult(await api.screen({
        title: draft.title,
        description: draft.description,
        itemSpecifics: specifics,
        imageBase64: draft.imageBase64,
      }))
    } catch (e) {
      const err = e as Error & { status?: number }
      if (err instanceof BackendUnreachable) setError(err.message)
      else if (err.status === 413) setError('That image is larger than the 5 MB limit. The text fields can still be analysed.')
      else if (err.status === 415) setError('PNG or JPEG only.')
      else setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={`split ${busy ? 'active' : ''}`}>
      {busy && <span className="energy-beam" aria-hidden="true" />}

      <ListingInput draft={draft} onChange={setDraft} onAnalyse={analyse} busy={busy} />

      <div className={`panel ${busy ? 'panel-active' : ''}`}>
        <div className="panel-head">
          <h2>Screening verdict</h2>
          {result && (
            <span className="note" style={{ marginLeft: 'auto' }}>
              {result.elapsedMs} ms
            </span>
          )}
        </div>

        <div className={`panel-body ${busy ? 'scanning' : ''}`}>
          {error && <div className="error">{error}</div>}

          {!result && !error && !busy && (
            <div className="empty-state">
              <div className="big" aria-hidden="true">🛡</div>
              <p>
                Paste a seller listing on the left and press <strong>Analyse listing</strong>.
              </p>
              <p className="note">
                Four independent layers check it — structure, known phrasings, a
                classifier, and any text rendered into the photo. Anything written to
                manipulate an AI shopping agent gets flagged, with the responsible text
                marked and the technique named.
              </p>
            </div>
          )}

          {busy && !result && (
            <div className="empty-state">
              <div className="big pulse-icon" aria-hidden="true">🔎</div>
              <p>Screening across four layers…</p>
            </div>
          )}

          {result && (
            <>
              <VerdictPanel result={result} />
              <ScreeningLayers result={result} />

              {/*
                A clean listing has nothing to evidence and nothing to mark up. Showing
                an empty "Why this is a Trojan" section, a listing with no highlights and
                a disabled reveal toggle asks the reviewer to read three panels to learn
                what the badge already said. So the detail is rendered only for TROJAN.

                The one exception is an image we could not read: FR-037 requires that it
                never reads as clean, so that warning stays regardless of the verdict.
              */}
              {result.verdict === 'TROJAN' ? (
                <>
                  <EvidenceList findings={result.findings} />
                  <ImageEvidence result={result} imageDataUrl={draft.imageBase64} />
                  <HighlightedSpan
                    assembled={result.assembledText}
                    findings={result.findings}
                    revealed={revealed}
                    onToggleRevealed={setRevealed}
                  />
                </>
              ) : (
                result.imageScreened === 'NOT_SCREENED' && (
                  <ImageEvidence result={result} imageDataUrl={draft.imageBase64} />
                )
              )}
            </>
          )}
        </div>
      </div>
    </div>
  )
}
