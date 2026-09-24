/**
 * A speedometer/compass-style semicircular dial for the threat score, replacing the
 * flat progress bar with something that reads as instrumentation rather than a form
 * field — the request was specifically "make it like a bike speedometer".
 *
 * FR-047 still holds: colour is never the sole carrier of meaning. The needle and
 * banded arc are decoration on top of three things that already carry the full
 * answer in text — the SAFE/THREAT end labels drawn directly on the dial, the
 * numeral rendered beside it, and the TROJAN/CLEAN badge in the parent VerdictPanel.
 * A viewer who cannot distinguish the green/yellow/red bands still reads the same
 * verdict from the labels and the number.
 */
interface Props {
  /** 0..1 */
  value: number
  trojan: boolean
}

const CX = 100
const CY = 102
const R = 78
const BAND_STROKE = 15

/** t in [0,1]: 0 = leftmost (SAFE), 1 = rightmost (THREAT), 0.5 = top of the arc. */
function pointAt(t: number, radius: number) {
  const angle = Math.PI * (1 - t)
  return { x: CX + radius * Math.cos(angle), y: CY - radius * Math.sin(angle) }
}

function arcPath(t1: number, t2: number, radius: number) {
  const p1 = pointAt(t1, radius)
  const p2 = pointAt(t2, radius)
  const large = t2 - t1 > 0.5 ? 1 : 0
  return `M ${p1.x} ${p1.y} A ${radius} ${radius} 0 ${large} 1 ${p2.x} ${p2.y}`
}

const TICKS = [0, 0.25, 0.5, 0.75, 1]

export function ThreatGauge({ value, trojan }: Props) {
  const clamped = Math.max(0, Math.min(1, value))
  const needleDeg = clamped * 180
  const tipColor = trojan ? 'var(--threat-glow)' : 'var(--safe-glow)'

  return (
    <div className="threat-gauge">
      <svg viewBox="0 0 200 122" width="196" height="120" role="img"
           aria-label={`Threat score ${clamped.toFixed(2)} out of 1.00, dial reading ${trojan ? 'toward threat' : 'toward safe'}`}>
        {/* banded arc — safe / caution / threat, purely decorative reinforcement */}
        <path d={arcPath(0, 0.34, R)} className="gauge-band gauge-band-safe" />
        <path d={arcPath(0.34, 0.67, R)} className="gauge-band gauge-band-caution" />
        <path d={arcPath(0.67, 1, R)} className="gauge-band gauge-band-threat" />

        {/* tick marks, like a real dial */}
        {TICKS.map(t => {
          const inner = pointAt(t, R - BAND_STROKE / 2 - 3)
          const outer = pointAt(t, R + BAND_STROKE / 2 + 5)
          return (
            <line key={t} x1={inner.x} y1={inner.y} x2={outer.x} y2={outer.y}
                  className="gauge-tick" />
          )
        })}

        {/* end labels — the text meaning, never colour alone */}
        <text x={pointAt(0.02, R + 24).x} y={pointAt(0.02, R + 24).y}
              className="gauge-end-label" textAnchor="start">SAFE</text>
        <text x={pointAt(0.98, R + 24).x} y={pointAt(0.98, R + 24).y}
              className="gauge-end-label" textAnchor="end">THREAT</text>

        {/* needle */}
        <g className="needle-group"
           style={{ transform: `rotate(${needleDeg}deg)`, transformOrigin: `${CX}px ${CY}px` }}>
          <line x1={CX} y1={CY} x2={CX - 60} y2={CY} className="gauge-needle"
                style={{ stroke: tipColor }} />
          <circle cx={CX - 60} cy={CY} r={4.5} className="gauge-needle-tip"
                  style={{ fill: tipColor }} />
        </g>
        <circle cx={CX} cy={CY} r={9} className="gauge-hub" />
        <circle cx={CX} cy={CY} r={3.5} className="gauge-hub-dot" />
      </svg>

      <div className="gauge-readout">
        <div className="label">Threat score</div>
        <div className={`value ${trojan ? 'trojan' : 'clean'}`}>{clamped.toFixed(2)}</div>
      </div>
    </div>
  )
}
