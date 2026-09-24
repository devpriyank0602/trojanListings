package com.ebay.trojanlistings.harness;

import com.ebay.trojanlistings.corpus.AttackTechnique;
import com.ebay.trojanlistings.corpus.AttackerGoal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrialStoreTest {

    private static Trial trial(String runId, String fixtureId, ComplianceOutcome outcome) {
        return new Trial(runId, fixtureId, AttackTechnique.FREE_TEXT, AttackerGoal.SELF_PROMOTION,
                true, "test-model", "agent said something", outcome,
                "test condition", "matched 'something'", Instant.now(), null);
    }

    @Test
    @DisplayName("FR-039: a trial is durable the moment it is appended")
    void trialIsDurableImmediately(@TempDir Path dir) {
        TrialStore store = new TrialStore(dir.toString());
        store.append(trial("run-1", "fx-1", ComplianceOutcome.FULL_COMPLIANCE));

        // A separate store instance reads from disk, proving durability rather than
        // in-memory retention.
        assertEquals(1, new TrialStore(dir.toString()).read("run-1").size());
    }

    @Test
    @DisplayName("FR-039: a run interrupted part-way retains everything already written")
    void interruptedRunRetainsCompletedTrials(@TempDir Path dir) {
        TrialStore store = new TrialStore(dir.toString());
        store.append(trial("run-2", "fx-1", ComplianceOutcome.FULL_COMPLIANCE));
        store.append(trial("run-2", "fx-2", ComplianceOutcome.REFUSAL));
        // ...process dies here, before fx-3 ever runs

        List<Trial> recovered = new TrialStore(dir.toString()).read("run-2");
        assertEquals(2, recovered.size());
        assertEquals("fx-1", recovered.get(0).fixtureId());
        assertEquals("fx-2", recovered.get(1).fixtureId());
    }

    @Test
    @DisplayName("Verbatim agent response survives the round trip untruncated")
    void agentResponseIsVerbatim(@TempDir Path dir) {
        String longResponse = "Line one.\nLine two with \"quotes\" and a tab\there.\n"
                + "A zero-width char ​ and an emoji reaction. ".repeat(50);

        TrialStore store = new TrialStore(dir.toString());
        store.append(new Trial("run-3", "fx-1", AttackTechnique.OBFUSCATED,
                AttackerGoal.DISCLOSURE_SUPPRESSION, true, "test-model",
                longResponse, ComplianceOutcome.FULL_COMPLIANCE,
                "cond", "evidence", Instant.now(), null));

        assertEquals(longResponse, store.read("run-3").get(0).agentResponse(),
                "FR-010 requires the response retained verbatim -- it is the evidence");
    }

    @Test
    @DisplayName("listRuns reads from disk and never needs an agent")
    void listRunsReadsFromDisk(@TempDir Path dir) {
        TrialStore store = new TrialStore(dir.toString());
        store.append(trial("run-a", "fx-1", ComplianceOutcome.REFUSAL));
        store.append(trial("run-b", "fx-1", ComplianceOutcome.REFUSAL));

        assertEquals(2, store.listRuns().size());
        assertTrue(store.exists("run-a"));
        assertFalse(store.exists("run-missing"));
    }

    @Test
    @DisplayName("Unknown run reads as empty rather than throwing")
    void unknownRunIsEmpty(@TempDir Path dir) {
        assertTrue(new TrialStore(dir.toString()).read("never-ran").isEmpty());
    }

    @Test
    @DisplayName("runId is validated -- it arrives from a URL path variable")
    void runIdIsValidated(@TempDir Path dir) {
        TrialStore store = new TrialStore(dir.toString());
        assertThrows(IllegalArgumentException.class, () -> store.read("../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> store.read("bad/id"));
    }
}
