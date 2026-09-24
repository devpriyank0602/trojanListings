import { Fragment, useEffect, useState } from 'react'
import {
  api, BackendUnreachable, OUTCOME_LABEL, TECHNIQUE_LABEL,
  type ExposureReport, type RunSummary, type Trial,
} from '../api/client'
import { ExposureTable } from '../components/ExposureTable'
import type { ListingDraft } from '../components/ListingInput'

/**
 * Screen 2 -- the "before" half of the story: what agents actually did when they read
 * these listings.
 *
 * No agent call ever fires from this screen (FR-040). Everything here is recorded
 * results read from disk, which is what makes the three-minute demo safe to perform.
 */
export function ExposureReportPage({ onScreenListing }: { onScreenListing: (d: ListingDraft) => void }) {
  const [runs, setRuns] = useState<RunSummary[]>([])
  const [runId, setRunId] = useState<string | null>(null)
  const [report, setReport] = useState<ExposureReport | null>(null)
  const [trials, setTrials] = useState<Trial[]>([])
  const [expanded, setExpanded] = useState<string | null>(null)
  const [filter, setFilter] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.listRuns()
      .then(r => { setRuns(r.runs); if (r.runs[0]) setRunId(r.runs[0].runId) })
      .catch(e => setError(e instanceof BackendUnreachable ? e.message : String(e)))
  }, [])

  useEffect(() => {
    if (!runId) return
    api.report(runId).then(setReport).catch(e => setError(String(e)))
    api.trials(runId, filter ? { technique: filter } : {})
      .then(r => setTrials(r.trials)).catch(e => setError(String(e)))
  }, [runId, filter])

  if (error) return <div className="wrap"><div className="panel"><div className="panel-body"><div className="error">{error}</div></div></div></div>

  if (runs.length === 0) {
    return (
      <div className="split">
        <div className="panel">
          <div className="panel-head"><h2>Exposure report</h2></div>
          <div className="panel-body">
          <p className="note">
            No measurement runs recorded yet. Produce one with:
          </p>
          <pre className="assembled">{`export AGENT_API_KEY=...
curl -XPOST localhost:8080/api/runs -H 'Content-Type: application/json' -d '{}'`}</pre>
          <p className="note">
            Screening on the other tab works without this — it never needs an agent.
          </p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="wrap">
      <div className="panel">
        <div className="panel-head"><h2>Exposure report</h2></div>
        <div className="panel-body">

        <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 12 }}>
          <select value={runId ?? ''} onChange={e => setRunId(e.target.value)} style={{ width: 'auto' }}>
            {runs.map(r => (
              <option key={r.runId} value={r.runId}>
                {r.recordedAt.slice(0, 16).replace('T', ' ')} · {r.trialCount} trials · {r.agentModel}
              </option>
            ))}
          </select>
          <span className="note">recorded results — no agent is contacted by this screen</span>
        </div>

        {report && <ExposureTable report={report} />}
        </div>
      </div>

      <div className="panel">
        <div className="panel-head"><h2>Trials</h2></div>
        <div className="panel-body">

        <select value={filter} onChange={e => setFilter(e.target.value)}
                style={{ width: 'auto', marginBottom: 10 }} aria-label="Filter by technique">
          <option value="">all techniques</option>
          {['FREE_TEXT', 'STRUCTURED_FIELD', 'OBFUSCATED', 'IN_IMAGE'].map(t => (
            <option key={t} value={t}>{TECHNIQUE_LABEL[t]}</option>
          ))}
        </select>

        <table className="trials">
          <thead>
            <tr>
              <th>Fixture</th><th>Technique</th><th>Outcome</th><th>What the agent said</th><th />
            </tr>
          </thead>
          <tbody>
            {trials.map(t => (
              <Fragment key={t.fixtureId}>
                <tr className={t.hostile ? '' : 'control'}>
                  <td className="mono">{t.fixtureId}</td>
                  <td>{t.technique ? TECHNIQUE_LABEL[t.technique] : 'Benign control'}</td>
                  <td><span className={`pill ${pillClass(t.outcome)}`}>{OUTCOME_LABEL[t.outcome]}</span></td>
                  <td>{truncate(t.agentResponse, 160)}</td>
                  <td>
                    <button className="link"
                            onClick={() => setExpanded(expanded === t.fixtureId ? null : t.fixtureId)}>
                      {expanded === t.fixtureId ? 'hide' : 'why?'}
                    </button>
                  </td>
                </tr>

                {expanded === t.fixtureId && (
                  <tr>
                    <td colSpan={5}>
                      {/* SC-016: any verdict explainable on demand, no re-run, no second model. */}
                      <dl className="audit">
                        <dt>Condition evaluated</dt>
                        <dd>{t.conditionEvaluated}</dd>
                        <dt>What decided it</dt>
                        <dd className="mono">{t.matchedText || '—'}</dd>
                        <dt>Agent response, verbatim</dt>
                        <dd><pre className="assembled" style={{ maxHeight: 220 }}>{t.agentResponse}</pre></dd>
                        {t.error && (<><dt>Error</dt><dd className="mono">{t.error}</dd></>)}
                      </dl>
                      <button className="link" style={{ marginTop: 8 }}
                              onClick={() => onScreenListing(draftFor(t))}>
                        → Screen this listing
                      </button>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
        </div>
      </div>
    </div>
  )
}

/** FR-021: jump from a recorded trial straight to screening the same listing. */
function draftFor(t: Trial): ListingDraft {
  return {
    title: '', description: '', category: '', condition: '',
    specifics: [{ key: '', value: '' }],
    imageBase64: null, imageName: t.fixtureId,
  }
}

function pillClass(outcome: string) {
  return outcome === 'FULL_COMPLIANCE' ? 'full'
    : outcome === 'PARTIAL_COMPLIANCE' ? 'partial'
    : outcome === 'REFUSAL' ? 'refusal' : 'notmeasured'
}

function truncate(s: string, n: number) {
  return !s ? '—' : s.length <= n ? s : `${s.slice(0, n)}…`
}
