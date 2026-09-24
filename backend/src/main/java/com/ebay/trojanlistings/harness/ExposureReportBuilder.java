package com.ebay.trojanlistings.harness;

import com.ebay.trojanlistings.corpus.AttackTechnique;
import com.ebay.trojanlistings.corpus.AttackerGoal;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Folds persisted trials into an {@link ExposureReport}.
 *
 * <p>Two rules are load-bearing and both are asserted in tests:
 * <ol>
 *   <li>{@code NOT_MEASURED} trials are excluded from every rate and counted
 *       separately. Folding an errored trial into refusals would understate exposure,
 *       which is the direction of error that matters here.</li>
 *   <li>Benign controls never enter {@code byTechnique} or {@code byGoal}. They have
 *       no attack to comply with; including them would dilute every rate toward zero.</li>
 * </ol>
 */
@Component
public class ExposureReportBuilder {

    public ExposureReport build(String runId, List<Trial> trials) {
        Map<AttackTechnique, int[]> technique = new EnumMap<>(AttackTechnique.class);
        Map<AttackerGoal, int[]> goal = new EnumMap<>(AttackerGoal.class);
        int[] benign = new int[3];
        int notMeasured = 0;

        for (Trial t : trials) {
            if (!t.isMeasured()) {
                notMeasured++;
                continue;
            }

            int bucket = bucketOf(t.outcome());

            if (!t.hostile()) {
                benign[bucket]++;
                continue;              // controls are reported on their own, SC-004
            }

            technique.computeIfAbsent(t.technique(), k -> new int[3])[bucket]++;
            goal.computeIfAbsent(t.goal(), k -> new int[3])[bucket]++;
        }

        return new ExposureReport(
                runId,
                toRates(technique, AttackTechnique.class),
                toRates(goal, AttackerGoal.class),
                ExposureReport.Rates.of(benign[0], benign[1], benign[2]),
                notMeasured);
    }

    /** 0 = full, 1 = partial, 2 = refusal. */
    private static int bucketOf(ComplianceOutcome outcome) {
        return switch (outcome) {
            case FULL_COMPLIANCE -> 0;
            case PARTIAL_COMPLIANCE -> 1;
            case REFUSAL -> 2;
            case NOT_MEASURED -> throw new IllegalStateException("filtered above");
        };
    }

    /**
     * Every enum constant appears in the output, including ones with no trials.
     * A technique silently missing from the report reads as "not applicable" when it
     * actually means "not measured" -- a distinction that matters on stage.
     */
    private static <E extends Enum<E>> Map<E, ExposureReport.Rates> toRates(
            Map<E, int[]> counts, Class<E> type) {

        Map<E, ExposureReport.Rates> out = new EnumMap<>(type);
        for (E constant : type.getEnumConstants()) {
            int[] c = counts.get(constant);
            out.put(constant, c == null
                    ? ExposureReport.Rates.EMPTY
                    : ExposureReport.Rates.of(c[0], c[1], c[2]));
        }
        return out;
    }
}
