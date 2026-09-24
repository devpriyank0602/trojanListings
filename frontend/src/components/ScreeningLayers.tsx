import type { ScreenResponse } from '../api/client'

/**
 * The four screening layers, each reporting its own result (FR-045).
 *
 * All four rows are always rendered, even when a layer found nothing. An absent row
 * reads as "not applicable" when it actually means "found nothing", and that
 * difference matters when someone is reading the screen over your shoulder.
 *
 * This is also where degraded mode now lives (FR-048): the Classifier row shows
 * "unavailable" beside the three layers that did run, instead of a full-width amber
 * band eating the top of every screen.
 *
 * FR-047: the state word carries the meaning. Colour only reinforces it.
 */
type State = 'hit' | 'clear' | 'off' | 'na'

interface Row { name: string; sub: string; state: State; text: string }

function rows(result: ScreenResponse): Row[] {
  const count = (layer: string) => result.findings.filter(f => f.layer === layer).length
  const hits = (n: number): Row['state'] => (n > 0 ? 'hit' : 'clear')
  const label = (n: number) => (n > 0 ? `${n} finding${n === 1 ? '' : 's'}` : 'clear')

  const structural = count('STRUCTURAL')
  const pattern = count('PATTERN')
  const classifier = count('CLASSIFIER')
  const image = count('IMAGE_TEXT')

  const classifierRow: Row =
    result.mode === 'DEGRADED_NO_CLASSIFIER'
      ? { name: 'Classifier', sub: 'transformer, scored per sentence',
          state: 'off', text: 'unavailable' }
      : { name: 'Classifier', sub: 'transformer, scored per sentence',
          state: hits(classifier), text: label(classifier) }

  const photoRow: Row =
    result.imageScreened === 'NO_IMAGE'
      ? { name: 'Photo', sub: 'text rendered into the image', state: 'na', text: 'no photo' }
      : result.imageScreened === 'NOT_SCREENED'
      ? { name: 'Photo', sub: 'text rendered into the image', state: 'off', text: 'not screened' }
      : { name: 'Photo', sub: 'text rendered into the image', state: hits(image), text: label(image) }

  return [
    { name: 'Structure', sub: 'zero-width, look-alikes, encoding, hidden markup',
      state: hits(structural), text: label(structural) },
    { name: 'Pattern', sub: 'known instruction phrasings',
      state: hits(pattern), text: label(pattern) },
    classifierRow,
    photoRow,
  ]
}

const ICON: Record<State, string> = { hit: '▲', clear: '✓', off: '!', na: '–' }

export function ScreeningLayers({ result }: { result: ScreenResponse }) {
  return (
    <>
      <h3 className="sub">Screening layers</h3>
      <div className="layers">
        {rows(result).map((r, i) => (
          <div className={`layer ${r.state} rise rise-${i + 1}`} key={r.name}>
            <span className="dot" aria-hidden="true" />
            <span>
              <span className="name">{r.name}</span>
              <br />
              <span className="sub">{r.sub}</span>
            </span>
            <span className="state">
              <span aria-hidden="true">{ICON[r.state]}</span> {r.text}
            </span>
          </div>
        ))}
      </div>

      {result.mode === 'DEGRADED_NO_CLASSIFIER' && (
        <p className="note" style={{ marginTop: -6, marginBottom: 14 }}>
          Structure and pattern detection are still running — obfuscation and known
          phrasings are caught. Run <code>scripts/download-model.sh</code> to enable the
          classifier.
        </p>
      )}
    </>
  )
}
