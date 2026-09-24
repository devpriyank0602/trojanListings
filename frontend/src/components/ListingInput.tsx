import { useEffect, useState } from 'react'
import { api, type Sample, TECHNIQUE_LABEL } from '../api/client'

export interface ListingDraft {
  title: string
  description: string
  specifics: Array<{ key: string; value: string }>
  imageBase64: string | null
  imageName: string | null
}

export const EMPTY_DRAFT: ListingDraft = {
  title: '', description: '', specifics: [{ key: '', value: '' }],
  imageBase64: null, imageName: null,
}

interface Props {
  draft: ListingDraft
  onChange: (d: ListingDraft) => void
  onAnalyse: () => void
  busy: boolean
}

/** Seller listing input: title, description, item specifics, and photo (FR-018, FR-038). */
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
    const next = draft.specifics.map((s, j) => (j === i ? { ...s, [field]: value } : s))
    onChange({ ...draft, specifics: next })
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
      specifics: Object.entries(s.listing.itemSpecifics ?? {}).map(([key, value]) => ({ key, value })),
      // Fixture images live on disk under corpus/images; fetch and inline so the
      // request looks exactly like a user upload.
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
      <h2>Seller listing</h2>

      <label htmlFor="title">Title</label>
      <input id="title" type="text" value={draft.title}
             onChange={e => set('title', e.target.value)}
             placeholder="e.g. Omega Seamaster 1968 — Serviced" />

      <label htmlFor="desc">Description</label>
      <textarea id="desc" value={draft.description}
                onChange={e => set('description', e.target.value)}
                placeholder="Paste the seller's description, including any HTML" />

      <h3>Item specifics</h3>
      {draft.specifics.map((s, i) => (
        <div className="specifics-row" key={i}>
          <input type="text" value={s.key} placeholder="Name"
                 onChange={e => setSpecific(i, 'key', e.target.value)} />
          <input type="text" value={s.value} placeholder="Value"
                 onChange={e => setSpecific(i, 'value', e.target.value)} />
          <button type="button" aria-label="Remove specific"
                  onClick={() => onChange({ ...draft, specifics: draft.specifics.filter((_, j) => j !== i) })}>
            ×
          </button>
        </div>
      ))}
      <button className="link" type="button"
              onClick={() => onChange({ ...draft, specifics: [...draft.specifics, { key: '', value: '' }] })}>
        + add specific
      </button>

      <h3>Photo</h3>
      <label
        className={`dropzone ${draft.imageBase64 ? 'has-image' : ''}`}
        onDragOver={e => e.preventDefault()}
        onDrop={e => {
          e.preventDefault()
          const f = e.dataTransfer.files?.[0]
          if (f) readImage(f)
        }}
      >
        {draft.imageBase64
          ? <img src={draft.imageBase64} alt="Listing photo to be screened" />
          : <span>Drop a listing photo here, or click to browse</span>}
        <input type="file" accept="image/png,image/jpeg" style={{ display: 'none' }}
               onChange={e => { const f = e.target.files?.[0]; if (f) readImage(f) }} />
      </label>
      {draft.imageBase64 && (
        <button className="link" type="button"
                onClick={() => onChange({ ...draft, imageBase64: null, imageName: null })}>
          remove photo
        </button>
      )}

      <div style={{ marginTop: 18, display: 'flex', gap: 12, alignItems: 'center' }}>
        <button className="primary" onClick={onAnalyse} disabled={busy}>
          {busy ? 'Analysing…' : 'Analyse listing'}
        </button>

        <select defaultValue="" onChange={e => { loadSample(e.target.value); e.target.value = '' }}
                aria-label="Load a sample listing" style={{ width: 'auto', flex: 1 }}>
          <option value="">load a sample…</option>
          {Object.entries(grouped).map(([technique, items]) => (
            <optgroup key={technique} label={TECHNIQUE_LABEL[technique] ?? technique}>
              {items.map(s => <option key={s.id} value={s.id}>{s.id}</option>)}
            </optgroup>
          ))}
        </select>
      </div>

      {draft.imageName && !draft.imageBase64 && (
        <p className="note" style={{ marginTop: 8 }}>
          This sample has a photo at <code>{draft.imageName}</code>. Upload it above to
          screen the image too.
        </p>
      )}
    </div>
  )
}
