package com.ebay.trojanlistings.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Append-only JSONL persistence for trials, one file per measurement run.
 *
 * <p>Chosen over a database because FR-039 requires each trial durable the moment it
 * completes -- an append plus flush is the simplest correct implementation of that --
 * and because FR-043/SC-016 require any verdict auditable on demand. A JSONL line
 * carrying the fixture id, the declared condition, the agent's verbatim response and
 * the matched evidence can be grepped in front of a judge. No schema, no daemon.
 */
@Component
public class TrialStore {

    private static final Logger log = LoggerFactory.getLogger(TrialStore.class);

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Path resultsDir;

    public TrialStore(@Value("${trojan.results.path}") String resultsPath) {
        this.resultsDir = Path.of(resultsPath).toAbsolutePath().normalize();
    }

    /** Summary of a recorded run, for {@code GET /api/runs}. */
    public record RunSummary(String runId, String recordedAt, String agentModel, int trialCount) {}

    /**
     * Appends one trial and flushes immediately. A run interrupted part-way keeps
     * everything already written (FR-039).
     */
    public synchronized void append(Trial trial) {
        Path file = fileFor(trial.runId());
        try {
            Files.createDirectories(resultsDir);
            String line = mapper.writeValueAsString(trial) + System.lineSeparator();
            // CREATE + APPEND + SYNC: durable on return, not merely buffered.
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not persist trial " + trial.fixtureId()
                    + " of run " + trial.runId(), e);
        }
    }

    /** All trials of a run, in the order they were recorded. */
    public List<Trial> read(String runId) {
        Path file = fileFor(runId);
        if (!Files.isRegularFile(file)) return List.of();

        List<Trial> trials = new ArrayList<>();
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.filter(l -> !l.isBlank()).forEach(l -> {
                try {
                    trials.add(mapper.readValue(l, Trial.class));
                } catch (Exception e) {
                    // One malformed line must not lose the rest of the run. This can
                    // happen if the process died mid-write.
                    log.warn("Skipping unreadable trial line in run {}: {}", runId, e.getMessage());
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read run " + runId, e);
        }
        return trials;
    }

    /** Recorded runs, newest first. Reads from disk -- never contacts an agent (FR-040). */
    public List<RunSummary> listRuns() {
        if (!Files.isDirectory(resultsDir)) return List.of();

        try (Stream<Path> files = Files.list(resultsDir)) {
            return files
                    .filter(p -> p.getFileName().toString().startsWith("trials-"))
                    .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .map(this::summarise)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(RunSummary::recordedAt).reversed())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list runs in " + resultsDir, e);
        }
    }

    public boolean exists(String runId) {
        return Files.isRegularFile(fileFor(runId));
    }

    private RunSummary summarise(Path file) {
        String name = file.getFileName().toString();
        String runId = name.substring("trials-".length(), name.length() - ".jsonl".length());

        List<Trial> trials = read(runId);
        if (trials.isEmpty()) return null;

        return new RunSummary(
                runId,
                trials.get(0).recordedAt().toString(),
                trials.get(0).agentModel(),
                trials.size());
    }

    private Path fileFor(String runId) {
        // Guard the path: runId reaches this from a URL path variable.
        if (!runId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("Invalid runId: " + runId);
        }
        return resultsDir.resolve("trials-" + runId + ".jsonl");
    }
}
