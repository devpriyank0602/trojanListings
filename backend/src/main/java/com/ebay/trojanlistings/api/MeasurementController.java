package com.ebay.trojanlistings.api;

import com.ebay.trojanlistings.corpus.AttackTechnique;
import com.ebay.trojanlistings.harness.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Measurement endpoints, per contracts/rest-api.md.
 *
 * <p>The GET endpoints read persisted JSONL and <b>never</b> contact the agent
 * (FR-040). That is what makes the three-minute demo safe to perform: the before/after
 * story is served entirely from recorded results, so a flaky network or an unavailable
 * model cannot break it in front of judges.
 *
 * <p>{@code POST /api/runs} is available but never required for anything else to work
 * (FR-041).
 */
@RestController
@RequestMapping("/api")
public class MeasurementController {

    private final MeasurementRunner runner;
    private final TrialStore store;
    private final ExposureReportBuilder reportBuilder;

    public MeasurementController(MeasurementRunner runner, TrialStore store,
                                 ExposureReportBuilder reportBuilder) {
        this.runner = runner;
        this.store = store;
        this.reportBuilder = reportBuilder;
    }

    public record StartRunRequest(List<String> fixtureIds, String agentModel) {}

    /** Starts a fresh measurement run. Optional -- see FR-041. */
    @PostMapping("/runs")
    public ResponseEntity<Map<String, Object>> startRun(@RequestBody(required = false) StartRunRequest request) {
        if (!runner.agentConfigured()) {
            // 503 rather than 500: the service is healthy, this one optional capability
            // is not configured. Everything else keeps working.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AGENT_API_KEY is not set, so a new measurement run cannot start. "
                            + "Recorded results remain available at GET /api/runs.");
        }

        List<String> ids = request == null ? null : request.fixtureIds();
        String runId = runner.startRun(ids);

        return ResponseEntity.accepted().body(Map.of(
                "runId", runId,
                "message", "Run started. Poll GET /api/runs/" + runId + "/trials for progress."));
    }

    /** Recorded runs, newest first. No agent call. */
    @GetMapping("/runs")
    public Map<String, Object> listRuns() {
        return Map.of("runs", store.listRuns());
    }

    /** The folded exposure report for a run. No agent call. */
    @GetMapping("/runs/{runId}/report")
    public ExposureReport report(@PathVariable String runId) {
        requireRun(runId);
        return reportBuilder.build(runId, store.read(runId));
    }

    /**
     * Individual trials, for auditing a verdict (FR-043, SC-016). No agent call.
     *
     * <p>Filters are additive; omitting all of them returns the whole run.
     */
    @GetMapping("/runs/{runId}/trials")
    public Map<String, Object> trials(@PathVariable String runId,
                                      @RequestParam(required = false) String fixtureId,
                                      @RequestParam(required = false) AttackTechnique technique,
                                      @RequestParam(required = false) ComplianceOutcome outcome) {
        requireRun(runId);

        List<Trial> trials = store.read(runId).stream()
                .filter(t -> fixtureId == null || fixtureId.equals(t.fixtureId()))
                .filter(t -> technique == null || technique == t.technique())
                .filter(t -> outcome == null || outcome == t.outcome())
                .toList();

        return Map.of("trials", trials, "progress", runner.progressOf(runId));
    }

    private void requireRun(String runId) {
        if (!store.exists(runId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such run: " + runId);
        }
    }
}
