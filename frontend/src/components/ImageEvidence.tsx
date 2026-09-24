import type { ScreenResponse } from '../api/client'

/**
 * The photo beside the text extracted from it (FR-038).
 *
 * NOT_SCREENED must never read as clean (FR-037). An image we could not read is an
 * unscreened image, and saying so is the difference between an honest tool and one
 * that hides its blind spot.
 */
interface Props {
  result: ScreenResponse
  imageDataUrl: string | null
}

export function ImageEvidence({ result, imageDataUrl }: Props) {
  if (result.imageScreened === 'NO_IMAGE') return null

  return (
    <>
      <h3>Listing photo</h3>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, alignItems: 'start' }}>
        {imageDataUrl && (
          <img src={imageDataUrl} alt="The listing photo that was screened"
               style={{ maxWidth: '100%', borderRadius: 8, border: '1px solid var(--line)' }} />
        )}

        <div>
          {result.imageScreened === 'SCREENED' ? (
            <>
              <div className="note" style={{ marginBottom: 6 }}>Text read from this photo:</div>
              <pre className="assembled" style={{ maxHeight: 200 }}>
                {result.extractedImageText?.trim() || '(no text found in this photo)'}
              </pre>
            </>
          ) : (
            <div className="note amber">
              <strong>Text could not be extracted from this image — it has not been screened.</strong>
              <div style={{ marginTop: 6, fontWeight: 400 }}>{result.imageNote}</div>
            </div>
          )}
        </div>
      </div>
    </>
  )
}
