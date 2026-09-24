package com.ebay.trojanlistings.harness;

import com.ebay.trojanlistings.corpus.Fixture;
import com.ebay.trojanlistings.corpus.FixtureLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.Map;

/**
 * Runs the corpus against the agent and persists every trial as it completes.
 *
 * <p>Benign controls are run exactly like hostile fixtures but graded differently:
 * they have no attack to comply with, so they are always recorded as refusals and
 * reported in their own bucket. Running them matters -- a near-zero control rate is
 * what proves the measured compliance reflects manipulation rather than ordinary
 * agent behaviour (SC-004).
 */
@Component
public class MeasurementRunner {

    private static final Logger log = LoggerFactory.getLogger(MeasurementRunner.class);

    private final FixtureLoader fixtures;
    private final AgentClient agent;
    private final ComplianceJudge judge;
    private final TrialStore store;

    /** Live progress for in-flight runs, so the UI can show a run advancing. */
    private final Map<String, Progress> progress = new ConcurrentHashMap<>();
    private final Executor executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "measurement-runner");
        t.setDaemon(true);
        return t;
    });

    public record Progress(int completed, int total, boolean finished, String error) {}

    public MeasurementRunner(FixtureLoader fixtures, AgentClient agent,
                             ComplianceJudge judge, TrialStore store) {
        this.fixtures = fixtures;
        this.agent = agent;
        this.judge = judge;
        this.store = store;
    }

    public boolean agentConfigured() { return agent.isConfigured(); }

    public Progress progressOf(String runId) {
        return progress.getOrDefault(runId, new Progress(0, 0, false, null));
    }

    /**
     * Starts a run in the background and returns its id immediately.
     *
     * @param fixtureIds specific fixtures to run, or null/empty for the whole corpus
     */
    public String startRun(List<String> fixtureIds) {
        if (!agent.isConfigured()) {
            throw new IllegalStateException(
                    "AGENT_API_KEY is not set, so a new measurement run cannot start. "
                            + "Recorded results remain viewable (FR-040).");
        }

        List<Fixture> selected = (fixtureIds == null || fixtureIds.isEmpty())
                ? fixtures.load()
                : fixtures.load().stream().filter(f -> fixtureIds.contains(f.id())).toList();

        if (selected.isEmpty()) {
            throw new IllegalArgumentException("No fixtures matched " + fixtureIds);
        }

        String runId = UUID.randomUUID().toString();
        progress.put(runId, new Progress(0, selected.size(), false, null));
        executor.execute(() -> execute(runId, selected));
        return runId;
    }

    private void execute(String runId, List<Fixture> selected) {
        log.info("Measurement run {} starting over {} fixtures", runId, selected.size());
        int completed = 0;

        for (Fixture fixture : selected) {
            try {
                store.append(runTrial(runId, fixture));
            } catch (Exception e) {
                // A failing trial must never abort the run (FR-011). Record it as
                // NOT_MEASURED so it is excluded from rates and surfaced separately,
                // rather than silently counted as a refusal.
                log.warn("Trial {} failed: {}", fixture.id(), e.toString());
                store.append(Trial.failed(runId, fixture.id(), fixture.technique(),
                        fixture.goal(), fixture.hostile(), agent.model(), e.toString()));
            }
            completed++;
            progress.put(runId, new Progress(completed, selected.size(), false, null));
        }

        progress.put(runId, new Progress(completed, selected.size(), true, null));
        log.info("Measurement run {} complete: {} trials recorded", runId, completed);
    }

    private Trial runTrial(String runId, Fixture fixture) throws Exception {
        Path image = fixtures.imagePath(fixture);
        String response = agent.ask(fixture, image);

        if (!fixture.hostile()) {
            // Controls declare no condition, so there is nothing to evaluate. They are
            // recorded as refusals and reported in their own bucket.
            return new Trial(runId, fixture.id(), null, null, false, agent.model(),
                    response, ComplianceOutcome.REFUSAL,
                    "benign control -- no attack to comply with", "",
                    Instant.now(), null);
        }

        ComplianceJudge.Judgement j = judge.judge(fixture, response);
        return new Trial(runId, fixture.id(), fixture.technique(), fixture.goal(), true,
                agent.model(), response, j.outcome(),
                j.conditionEvaluated(), j.matchedText(), Instant.now(), null);
    }
}
