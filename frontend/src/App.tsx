import { useEffect, useState } from 'react'
import { api, type Health } from './api/client'
import { ScreenListing } from './pages/ScreenListing'
import { ExposureReportPage } from './pages/ExposureReportPage'
import { EMPTY_DRAFT, type ListingDraft } from './components/ListingInput'
import './styles.css'

export default function App() {
  const [tab, setTab] = useState<'screen' | 'exposure'>('screen')
  const [health, setHealth] = useState<Health | null>(null)
  const [draft, setDraft] = useState<ListingDraft>(EMPTY_DRAFT)
  const [draftKey, setDraftKey] = useState(0)

  useEffect(() => { api.health().then(setHealth).catch(() => setHealth(null)) }, [])

  function screenThis(d: ListingDraft) {
    setDraft(d)
    setDraftKey(k => k + 1)   // force the page to remount with the new draft
    setTab('screen')
  }

  return (
    <>
      <header className="topbar">
        <span className="brand">TROJAN LISTINGS</span>
        <nav className="nav">
          <button className={tab === 'screen' ? 'active' : ''} onClick={() => setTab('screen')}>
            Screen a listing
          </button>
          <button className={tab === 'exposure' ? 'active' : ''} onClick={() => setTab('exposure')}>
            Exposure report
          </button>
        </nav>
      </header>

      {/* FR-028: every screen states plainly that this is synthetic research content. */}
      <div className="safety-banner">
        Synthetic adversarial research — no live eBay data, no real seller, nothing published.
      </div>

      {/* FR-030: degraded mode is reported, never hidden. */}
      {health && !health.classifierLoaded && (
        <div className="degraded-banner">
          <strong>Classifier unavailable</strong> — screening is running with structural and
          pattern detection only. Obfuscation and known phrasings are still caught; novel
          plain-language attacks may be missed. Run <code>scripts/download-model.sh</code>.
        </div>
      )}
      {health && !health.ocrAvailable && (
        <div className="degraded-banner">
          <strong>OCR unavailable</strong> — listing photos will be reported as
          <em> not screened</em> rather than treated as clean. Install with
          <code> brew install tesseract</code>.
        </div>
      )}

      {tab === 'screen'
        ? <ScreenListing key={draftKey} initialDraft={draft} />
        : <ExposureReportPage onScreenListing={screenThis} />}
    </>
  )
}
