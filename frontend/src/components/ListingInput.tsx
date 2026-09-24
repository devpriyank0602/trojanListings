import { useEffect, useState } from 'react'
import { api, type Sample, TECHNIQUE_LABEL } from '../api/client'

export interface ListingDraft {
  title: string
  description: string
  category: string
  condition: string
  specifics: Array<{ key: string; value: string }>
  imageBase64: string | null
  imageName: string | null
}

export const EMPTY_DRAFT: ListingDraft = {
  title: '', description: '', category: '', condition: '',
  specifics: [{ key: '', value: '' }], imageBase64: null, imageName: null,
}

interface Props {
  draft: ListingDraft
  onChange: (d: ListingDraft) => void
  onAnalyse: () => void
  busy: boolean
}

const CONDITIONS = ['', 'New', 'New other', 'Used — Excellent', 'Used — Good', 'For parts']

/**
 * The seller-listing surface (FR-044).
 *
 * Borrows the seller-flow vocabulary — photo first, content grouped into titled
 * section cards, a category/condition row, one primary call to action — without
 * becoming a multi-step dedicated flow. A faithful multi-step flow would put the
 * hostile listing and its verdict on separate screens, and that relationship is the
 * only thing this interface exists to show.
 *
 * Category and condition are collected because a real sell form has them and their
 * absence is conspicuous. They are not screened: the backend contract takes title,
 * description, item specifics and photo, and adding fields to it would change what
 * the measured numbers refer to.
 */
export function ListingInput({ draft, onChange, onAnalyse, busy }: Props) {
  const [samples, setSamples] = useState<Sample[]>([])

  useEffect(() => {
    // The sample loader removes all typing from the live demo and guarantees the
    // pasted content is exactly what was measured.
    api.samples().then(r => setSamples(r.samples)).catch(() => setSamples([]))
  }, [])

  function set<K extends keyof ListingDraft>(key: K, value: ListingDraft[K]) {
    onChange({ ...draft, [key]: value })
  }

  function setSpecific(i: number, field: 'key' | 'value', value: string) {
    onChange({
      ...draft,
      specifics: draft.specifics.map((s, j) => (j === i ? { ...s, [field]: value } : s)),
    })
  }

  function readImage(file: File) {
    const reader = new FileReader()
    reader.onload = () =>
      onChange({ ...draft, imageBase64: String(reader.result), imageName: file.name })
    reader.readAsDataURL(file)
  }

  function loadSample(id: string) {
    const s = samples.find(x => x.id === id)
    if (!s) return
    onChange({
      title: s.listing.title,
      description: s.listing.description,
      category: '',
      condition: '',
      specifics: Object.entries(s.listing.itemSpecifics ?? {}).map(([key, value]) => ({ key, value })),
      imageBase64: null,
      imageName: s.listing.imagePath,
    })
  }

  const grouped = samples.reduce<Record<string, Sample[]>>((acc, s) => {
    ;(acc[s.technique] ??= []).push(s)
    return acc
  }, {})

  return (
    <div className="panel">
      <div className="panel-head">
        <h2>Seller listing</h2>
        <span className="note" style={{ marginLeft: 'auto' }}>as a seller would submit it</span>
      </div>

      <div className="panel-body">
        {/* Photo first, as on a real sell form */}
        <div className="section">
          <div className="overline">📷 Photo</div>
          <label
            className={`dropzone ${draft.imageBase64 ? 'has-image' : ''}`}
            onDragOver={e => e.preventDefault()}
            onDrop={e => {
              e.preventDefault()
              const f = e.dataTransfer.files?.[0]
              if (f) readImage(f)
            }}
          >
            {draft.imageBase64 ? (
              <img src={draft.imageBase64} alt="Listing photo to be screened" />
            ) : (
              <>
                <span className="icon" aria-hidden="true">🖼</span>
                <span className="hint">Drop a listing photo, or click to browse</span>
              </>
            )}
            <input type="file" accept="image/png,image/jpeg" style={{ display: 'none' }}
                   onChange={e => { const f = e.target.files?.[0]; if (f) readImage(f) }} />
          </label>
          {draft.imageBase64 && (
            <button className="link" type="button" style={{ marginTop: 8 }}
                    onClick={() => onChange({ ...draft, imageBase64: null, imageName: null })}>
              remove photo
            </button>
          )}
        </div>

        <div className="section">
          <div className="overline">🏷 Item details</div>

          <label htmlFor="title">Title</label>
          <input id="title" type="text" value={draft.title}
                 onChange={e => set('title', e.target.value)}
                 placeholder="e.g. Omega Seamaster 1968 — Serviced" />

          <div className="row-2">
            <div>
              <label htmlFor="cat">Category</label>
              <input id="cat" type="text" value={draft.category}
                     onChange={e => set('category', e.target.value)}
                     placeholder="Watches & Parts" />
            </div>
            <div>
              <label htmlFor="cond">Condition</label>
              <select id="cond" value={draft.condition}
                      onChange={e => set('condition', e.target.value)}>
                {CONDITIONS.map(c => (
                  <option key={c} value={c}>{c || 'Select condition'}</option>
                ))}
              </select>
            </div>
          </div>
        </div>

        <div className="section">
          <div className="overline">📝 Description</div>
          <textarea value={draft.description}
                    onChange={e => set('description', e.target.value)}
                    aria-label="Listing description"
                    placeholder="Paste the seller's description, including any HTML" />
        </div>

        <div className="section">
          <div className="overline">📋 Item specifics</div>
          {draft.specifics.map((s, i) => (
            <div className="specifics-row" key={i}>
              <input type="text" value={s.key} placeholder="Name" aria-label="Specific name"
                     onChange={e => setSpecific(i, 'key', e.target.value)} />
              <input type="text" value={s.value} placeholder="Value" aria-label="Specific value"
                     onChange={e => setSpecific(i, 'value', e.target.value)} />
              <button type="button" aria-label="Remove this specific"
                      onClick={() => onChange({ ...draft, specifics: draft.specifics.filter((_, j) => j !== i) })}>
                ×
              </button>
            </div>
          ))}
          <button className="link" type="button"
                  onClick={() => onChange({ ...draft, specifics: [...draft.specifics, { key: '', value: '' }] })}>
            + add specific
          </button>
        </div>

        {/* One primary CTA per screen, blue only */}
        <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginTop: 4 }}>
          <button className="primary" onClick={onAnalyse} disabled={busy}>
            {busy ? 'Analysing…' : '🛡  Analyse listing'}
          </button>

          <select defaultValue="" aria-label="Load a sample listing"
                  onChange={e => { loadSample(e.target.value); e.target.value = '' }}
                  style={{ width: 'auto', flex: 1 }}>
            <option value="">load a sample listing…</option>
            {Object.entries(grouped).map(([technique, items]) => (
              <optgroup key={technique} label={TECHNIQUE_LABEL[technique] ?? technique}>
                {items.map(s => <option key={s.id} value={s.id}>{s.id}</option>)}
              </optgroup>
            ))}
          </select>
        </div>

        {draft.imageName && !draft.imageBase64 && (
          <p className="note" style={{ marginTop: 10 }}>
            This sample has a photo at <code>{draft.imageName}</code>. Drop it into the
            photo box above to screen the image too.
          </p>
        )}
      </div>
    </div>
  )
}
