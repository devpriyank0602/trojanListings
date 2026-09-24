package com.ebay.trojanlistings.corpus;

import com.ebay.trojanlistings.detector.ConcealmentTechnique;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A fabricated listing used as a test case, plus everything needed to measure and
 * grade it. One JSON file per fixture under {@code corpus/fixtures/}.
 *
 * <p>Every fixture is entirely synthetic. No real seller, buyer or live item is ever
 * named (FR-006), and no code path exists that could publish one to a live surface.
 *
 * @see <a href="../../../../../../specs/001-listing-injection-defense/contracts/fixture-schema.md">fixture-schema.md</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Fixture(
        String id,
        boolean hostile,
        AttackTechnique technique,
        AttackerGoal goal,
        ConcealmentTechnique concealment,
        String note,
        Listing listing,
        String expectedSpan,
        Condition fullComplianceCondition,
        Condition partialComplianceCondition,

        /**
         * An optional second, entirely benign listing presented alongside this one.
         *
         * <p>Required by ORDERED_BEFORE conditions: proving the agent ranked the
         * attacker's item first is only possible if it had something to rank it
         * against. Without this, a ranking fixture can only show that the agent
         * mentioned the item, which is not the same claim.
         */
        Listing comparisonListing
) {
    public Fixture {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Fixture.id is required");
        }
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException(id + ": note is required (what is this trying to do?)");
        }
        if (listing == null) {
            throw new IllegalArgumentException(id + ": listing is required");
        }

        if (hostile) {
            requirePresent(id, "technique", technique);
            requirePresent(id, "goal", goal);
            requirePresent(id, "concealment", concealment);
            requirePresent(id, "fullComplianceCondition", fullComplianceCondition);
            if (expectedSpan == null || expectedSpan.isBlank()) {
                throw new IllegalArgumentException(id + ": hostile fixtures require expectedSpan");
            }
            if (fullComplianceCondition.type() == Condition.Type.ORDERED_BEFORE
                    && comparisonListing == null) {
                throw new IllegalArgumentException(id
                        + ": ORDERED_BEFORE proves ranking, which requires a comparisonListing "
                        + "for the agent to rank against");
            }
        } else {
            // Benign controls carry no attack metadata at all. Anything else present is
            // a corpus authoring error, not a shrug -- it would silently skew the
            // false-alarm rate that SC-006 caps at 10%.
            requireAbsent(id, "technique", technique);
            requireAbsent(id, "goal", goal);
            requireAbsent(id, "concealment", concealment);
            requireAbsent(id, "fullComplianceCondition", fullComplianceCondition);
            requireAbsent(id, "partialComplianceCondition", partialComplianceCondition);
            requireAbsent(id, "expectedSpan", expectedSpan);
        }
    }

    private static void requirePresent(String id, String field, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(id + ": hostile fixtures require " + field);
        }
    }

    private static void requireAbsent(String id, String field, Object value) {
        if (value != null) {
            throw new IllegalArgumentException(
                    id + ": benign controls must not declare " + field);
        }
    }
}
