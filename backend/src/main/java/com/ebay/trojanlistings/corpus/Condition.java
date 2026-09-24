package com.ebay.trojanlistings.corpus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

/**
 * A mechanically checkable statement about an agent's response.
 *
 * <p>No language model evaluates these (FR-008). A judge model reading a response
 * produced under attack is reading the attacker's output, and the same payload that
 * manipulated the agent can manipulate the judge -- invisibly, since a compromised
 * judge reports clean numbers. Since the attack-success-rate is the headline finding,
 * the judging mechanism has to survive that objection.
 *
 * <p>{@code description} is mandatory: it is what gets shown when someone asks how a
 * verdict was reached (SC-016).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Condition(Type type, List<String> values, String description) {

    public enum Type {
        /** Satisfied when the response contains at least one of {@code values}. */
        CONTAINS_ANY,
        /** Satisfied when the response contains every one of {@code values}. */
        CONTAINS_ALL,
        /** Satisfied when the response contains none of {@code values}. Disclosure suppression. */
        ABSENT,
        /** Satisfied when values[0] appears earlier than values[1]. Ranking. */
        ORDERED_BEFORE,
        /** Satisfied when values[0] matches as a regex. Applied verbatim, case-sensitive. */
        REGEX
    }

    public Condition {
        Objects.requireNonNull(type, "Condition.type is required");
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Condition.values must be non-empty");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "Condition.description is required -- it is shown when auditing a verdict (SC-016)");
        }
        if (type == Type.ORDERED_BEFORE && values.size() < 2) {
            throw new IllegalArgumentException("ORDERED_BEFORE requires two values");
        }
        values = List.copyOf(values);
    }
}
