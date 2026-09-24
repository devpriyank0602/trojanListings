import { TECHNIQUE_LABEL, type ExposureReport } from '../api/client'

/**
 * Attack-success-rate by technique, with the benign control row always last and
 * visually separated.
 *
 * Showing 0% controls next to a high attack rate is what proves the numbers track
 * manipulation rather than ordinary agent chattiness (SC-004).
 */
export function ExposureTable({ report }: { report: ExposureReport }) {
  const rows = Object.entries(report.byTechnique)
    .filter(([, r]) => r.total > 0)
    .sort((a, b) => b[1].complianceRate - a[1].complianceRate)

  return (
    <>
      <h3>Attack success rate by technique</h3>

      {rows.length === 0 && <p className="note">No measured trials in this run.</p>}

      {rows.map(([technique, r]) => (
        <div className="bar-row" key={technique}>
          <span>{TECHNIQUE_LABEL[technique] ?? technique}</span>
          <div className="bar"><span style={{ width: `${r.complianceRate * 100}%` }} /></div>
          <span className="figure">
            {(r.complianceRate * 100).toFixed(1)}% <span className="note">({r.total})</span>
          </span>
        </div>
      ))}

      <div className="bar-row" style={{ marginTop: 14, paddingTop: 12, borderTop: '1px solid var(--line)' }}>
        <span>Benign controls</span>
        <div className="bar control">
          <span style={{ width: `${report.benignControlRate.complianceRate * 100}%` }} />
        </div>
        <span className="figure">
          {(report.benignControlRate.complianceRate * 100).toFixed(1)}%{' '}
          <span className="note">({report.benignControlRate.total})</span>
        </span>
      </div>

      {report.notMeasured > 0 && (
        <p className="note" style={{ marginTop: 10 }}>
          {report.notMeasured} trial{report.notMeasured === 1 ? '' : 's'} could not be
          measured and {report.notMeasured === 1 ? 'is' : 'are'} excluded from every rate
          above — never counted as refusals.
        </p>
      )}
    </>
  )
}
