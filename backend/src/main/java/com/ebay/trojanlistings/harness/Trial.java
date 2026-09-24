package com.ebay.trojanlistings.harness;

import com.ebay.trojanlistings.corpus.AttackTechnique;
import com.ebay.trojanlistings.corpus.AttackerGoal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * One presentation of one fixture to one agent.
 *
 * <p>Written once, never mutated. Re-running a fixture produces a new Trial under a
 * new runId. Persisted as a single JSONL line the moment it completes (FR-039).
 *
 * <p>{@code technique} and {@code goal} are denormalised deliberately: a JSONL line
 * has to be self-contained so a verdict can be audited by reading one line, without
 * joining back to the corpus.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Trial(
        String runId,
        String fixtureId,
        AttackTechnique technique,
        AttackerGoal goal,
        boolean hostile,
        String agentModel,

        /** Verbatim and never truncated -- it is the evidence (FR-010). */
        String agentResponse,

        ComplianceOutcome outcome,

        /** Plain-English description of the condition that decided this (FR-043). */
        String conditionEvaluated,

        /** What in the response satisfied or failed that condition (FR-043, SC-016). */
        String matchedText,

        Instant recordedAt,

        /** Set when the trial failed. The run continues regardless (FR-011). */
        String error
) {
    /** A trial that could not be measured. Excluded from every rate, reported separately. */
    public static Trial failed(String runId, String fixtureId, AttackTechnique technique,
                               AttackerGoal goal, boolean hostile, String agentModel,
                               String error) {
        return new Trial(runId, fixtureId, technique, goal, hostile, agentModel,
                "", ComplianceOutcome.NOT_MEASURED,
                "not evaluated -- trial errored", "", Instant.now(), error);
    }

    /** True when this trial counts toward a compliance rate. */
    public boolean isMeasured() {
        return outcome != ComplianceOutcome.NOT_MEASURED;
    }

    /** Full and partial both count as the attack having worked. */
    public boolean complied() {
        return outcome == ComplianceOutcome.FULL_COMPLIANCE
                || outcome == ComplianceOutcome.PARTIAL_COMPLIANCE;
    }
}
