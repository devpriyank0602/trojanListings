import { useState } from 'react'
import { ScreenListing } from './pages/ScreenListing'
import { ExposureReportPage } from './pages/ExposureReportPage'
import { EMPTY_DRAFT, type ListingDraft } from './components/ListingInput'
import './styles.css'

export default function App() {
  const [tab, setTab] = useState<'screen' | 'exposure'>('screen')
  const [draft, setDraft] = useState<ListingDraft>(EMPTY_DRAFT)
  const [draftKey, setDraftKey] = useState(0)

  function screenThis(d: ListingDraft) {
    setDraft(d)
    setDraftKey(k => k + 1)   // remount the page with the new draft
    setTab('screen')
  }

  return (
    <>
      {/* Ambient background glow — decorative only, pointer-events none, sits behind
          everything. Purely visual; never carries information (FR-047 unaffected). */}
      <div className="ambient-bg" aria-hidden="true">
        <span className="blob blob-a" />
        <span className="blob blob-b" />
        <span className="blob blob-c" />
        <span className="grid-overlay" />
      </div>

      <header className="topbar">
        <span className="brand">
          {/* Four dots in OUR palette — an echo, deliberately not a reproduction. */}
          <span className="brand-dots" aria-hidden="true"><i /><i /><i /><i /></span>
          TROJAN LISTINGS
        </span>

        {/*
          FR-048: the synthetic-content statement is always visible and never
          dismissible, but it is a header chip rather than a full-width band. Two
          stacked banners previously consumed the top ~120px and pushed the product
          below the fold (SC-018).
        */}
        <span className="synthetic-chip" title="No live marketplace data is used anywhere in this tool">
          ⚗ Synthetic research data
        </span>

        <nav className="nav">
          <button className={tab === 'screen' ? 'active' : ''} onClick={() => setTab('screen')}>
            Screen a listing
          </button>
          <button className={tab === 'exposure' ? 'active' : ''} onClick={() => setTab('exposure')}>
            Exposure report
          </button>
        </nav>
      </header>

      {tab === 'screen'
        ? <ScreenListing key={draftKey} initialDraft={draft} />
        : <ExposureReportPage onScreenListing={screenThis} />}
    </>
  )
}
