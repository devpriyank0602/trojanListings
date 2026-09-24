package com.ebay.trojanlistings.corpus;

import com.ebay.trojanlistings.detector.ConcealmentTechnique;
import com.ebay.trojanlistings.detector.ListingAssembler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enforces the corpus coverage rules from contracts/fixture-schema.md.
 *
 * <p>These are not stylistic preferences. Every rate the project reports is computed
 * over this corpus, so a gap here silently changes the denominator of the headline
 * finding.
 *
 * <p><b>These assertions were deliberately weakened on 2026-09-24</b>, when the
 * 38-fixture evaluation corpus was replaced by a 7-fixture demonstration corpus
 * (5 hostile, 2 benign) at the project owner's instruction. The bars below no longer
 * enforce SC-001 (>=25 hostile), SC-006 (>=10 benign, >=3 trigger-like) or SC-007
 * (every concealment technique labelled), because seven fixtures cannot satisfy them.
 *
 * <p>What this costs is worth stating plainly rather than burying in a diff:
 * <ul>
 *   <li>Rates over 7 fixtures have coarse resolution. A single benign control now moves
 *       the false-alarm rate by 50 percentage points, so the number is an illustration
 *       rather than a measurement.</li>
 *   <li>Per-technique catch rates are single-fixture observations, not rates.</li>
 *   <li>INVISIBLE_MARKUP is no longer a labelled concealment. It is still physically
 *       present, stacked inside {@code h-extract-01}, but nothing asserts that.</li>
 * </ul>
 *
 * <p>The superseded corpus remains in git history and is the basis of every number
 * published before that date. See research.md §9.4.
 */
class CorpusValidationTest {

    private static final FixtureLoader LOADER = new FixtureLoader("../corpus");
    private static final ListingAssembler ASSEMBLER = new ListingAssembler();

    private static List<Fixture> all() { return LOADER.load(); }
    private static List<Fixture> hostile() { return LOADER.hostile(); }
    private static List<Fixture> benign() { return LOADER.benignControls(); }

    @Test
    @DisplayName("At least 5 hostile fixtures (SC-001's >=25 no longer holds -- see class note)")
    void atLeastFiveHostile() {
        assertTrue(hostile().size() >= 5,
                "Need >=5 hostile fixtures, found " + hostile().size());
    }

    @Test
    @DisplayName("SC-001: all four attack techniques present")
    void allTechniquesPresent() {
        Set<AttackTechnique> present = hostile().stream()
                .map(Fixture::technique).collect(Collectors.toSet());
        assertEquals(EnumSet.allOf(AttackTechnique.class), present,
                "Missing techniques: " + missing(EnumSet.allOf(AttackTechnique.class), present));
    }

    @Test
    @DisplayName("SC-001: all four attacker goals present")
    void allGoalsPresent() {
        Set<AttackerGoal> present = hostile().stream()
                .map(Fixture::goal).collect(Collectors.toSet());
        assertEquals(EnumSet.allOf(AttackerGoal.class), present,
                "Missing goals: " + missing(EnumSet.allOf(AttackerGoal.class), present));
    }

    /**
     * Superseded by the 7-fixture corpus: with four techniques across five hostile
     * fixtures, at most one technique can carry two goals. Retained as a disabled
     * record of the bar the 38-fixture corpus met, so restoring that corpus restores
     * the check rather than requiring it be reinvented.
     */
    @Test
    @Disabled("7-fixture corpus cannot satisfy this; see class note")
    @DisplayName("SC-001: every technique carries at least two distinct goals")
    void techniquesCoverMultipleGoals() {
        Map<AttackTechnique, Set<AttackerGoal>> byTechnique = hostile().stream()
                .collect(Collectors.groupingBy(Fixture::technique,
                        Collectors.mapping(Fixture::goal, Collectors.toSet())));

        List<String> thin = new ArrayList<>();
        byTechnique.forEach((t, goals) -> {
            if (goals.size() < 2) thin.add(t + " covers only " + goals);
        });
        assertTrue(thin.isEmpty(),
                "Each technique must exercise multiple goals, else the per-technique rate "
                        + "reflects one goal rather than the technique: " + thin);
    }

    @Test
    @DisplayName("At least four of the five concealment techniques are labelled")
    void mostConcealmentTechniquesCovered() {
        Set<ConcealmentTechnique> present = hostile().stream()
                .map(Fixture::concealment).collect(Collectors.toSet());
        present.remove(ConcealmentTechnique.NONE);

        // Full SC-007 coverage needs five labels and the corpus has four non-IN_IMAGE
        // hostile fixtures to carry them. INVISIBLE_MARKUP is the one that lost its
        // label; it is still stacked inside h-extract-01.
        assertTrue(present.size() >= 4,
                "Expected >=4 labelled concealment techniques, found " + present);
    }

    @Test
    @DisplayName("At least 2 benign controls, at least 1 of them trigger-like")
    void benignControlsPresent() {
        assertTrue(benign().size() >= 2,
                "Need >=2 benign controls, found " + benign().size());

        long triggerLike = benign().stream()
                .filter(f -> f.note().contains("TRIGGER-LIKE"))
                .count();
        assertTrue(triggerLike >= 1,
                "Need >=1 trigger-like benign control, else the false-alarm rate says "
                        + "nothing about vocabulary-driven over-flagging, found " + triggerLike);
    }

    @Test
    @DisplayName("Every fixture id is unique")
    void idsUnique() {
        Set<String> seen = new HashSet<>();
        List<String> dupes = all().stream()
                .map(Fixture::id).filter(id -> !seen.add(id)).toList();
        assertTrue(dupes.isEmpty(), "Duplicate fixture ids: " + dupes);
    }

    @Test
    @DisplayName("expectedSpan is a real substring of the assembled listing")
    void expectedSpanIsSubstring() {
        List<String> broken = new ArrayList<>();
        for (Fixture f : hostile()) {
            if (f.technique() == AttackTechnique.IN_IMAGE) continue;   // lives in the photo
            String assembled = ASSEMBLER.assemble(f.listing()).text();
            if (!assembled.contains(f.expectedSpan())) {
                broken.add(f.id());
            }
        }
        assertTrue(broken.isEmpty(),
                "expectedSpan must be verbatim substring of the assembled listing "
                        + "(it scores span accuracy). Broken: " + broken);
    }

    @Test
    @DisplayName("FR-031: in-image instructions appear in no text field")
    void inImageInstructionsAbsentFromText() {
        List<String> leaked = new ArrayList<>();
        for (Fixture f : hostile()) {
            if (f.technique() != AttackTechnique.IN_IMAGE) continue;

            String assembled = ASSEMBLER.assemble(f.listing()).text().toLowerCase();
            if (assembled.contains(f.expectedSpan().toLowerCase())) {
                leaked.add(f.id());
            }
        }
        assertTrue(leaked.isEmpty(),
                "IN_IMAGE fixtures must carry the instruction ONLY in the photo, "
                        + "otherwise they are secretly text fixtures and SC-014 is meaningless. "
                        + "Leaked: " + leaked);
    }

    @Test
    @DisplayName("Every in-image fixture has an image file that exists")
    void inImageFilesExist() {
        List<String> missing = all().stream()
                .filter(f -> f.listing().imagePath() != null)
                .filter(f -> !java.nio.file.Files.exists(LOADER.imagePath(f)))
                .map(Fixture::id).toList();
        assertTrue(missing.isEmpty(),
                "Missing image files (run scripts/render_fixture_images.py): " + missing);
    }

    @Test
    @DisplayName("Every hostile fixture declares an auditable full-compliance condition")
    void conditionsAreAuditable() {
        List<String> bad = hostile().stream()
                .filter(f -> f.fullComplianceCondition().description().isBlank())
                .map(Fixture::id).toList();
        assertTrue(bad.isEmpty(),
                "SC-016 requires every condition to carry a plain-English description: " + bad);
    }

    private static <T> Set<T> missing(Set<T> required, Set<T> present) {
        Set<T> gap = new HashSet<>(required);
        gap.removeAll(present);
        return gap;
    }
}
