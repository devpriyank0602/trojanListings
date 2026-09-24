import { useState } from 'react'
import { api, BackendUnreachable, type ScreenResponse } from '../api/client'
import { ListingInput, EMPTY_DRAFT, type ListingDraft } from '../components/ListingInput'
import { VerdictPanel } from '../components/VerdictPanel'
import { EvidenceList } from '../components/EvidenceList'
import { HighlightedSpan } from '../components/HighlightedSpan'
import { ImageEvidence } from '../components/ImageEvidence'

/**
 * Screen 1 -- paste a seller listing, learn whether it is a Trojan and exactly why
 * (FR-018 to FR-020, FR-038).
 */
export function ScreenListing({ initialDraft }: { initialDraft?: ListingDraft }) {
  const [draft, setDraft] = useState<ListingDraft>(initialDraft ?? EMPTY_DRAFT)
  const [result, setResult] = useState<ScreenResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [revealed, setRevealed] = useState(false)

  async function analyse() {
    setBusy(true); setError(null); setRevealed(false)
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
      setResult(null)
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
    <div className="split">
      <ListingInput draft={draft} onChange={setDraft} onAnalyse={analyse} busy={busy} />

      <div className="panel">
        <h2>Verdict</h2>

        {error && <div className="error">{error}</div>}

        {!result && !error && (
          <p className="note">
            Paste a seller listing on the left and press <strong>Analyse listing</strong>.
            Anything written to manipulate an AI shopping agent will be flagged, with the
            responsible text marked and the technique named.
          </p>
        )}

        {busy && !result && <p className="note">Screening…</p>}

        {result && (
          <>
            <VerdictPanel result={result} />
            <EvidenceList findings={result.findings} />
            <ImageEvidence result={result} imageDataUrl={draft.imageBase64} />
            <HighlightedSpan
              assembled={result.assembledText}
              findings={result.findings}
              revealed={revealed}
              onToggleRevealed={setRevealed}
            />
          </>
        )}
      </div>
    </div>
  )
}
