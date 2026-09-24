package com.ebay.trojanlistings;

import com.ebay.trojanlistings.corpus.AttackTechnique;
import com.ebay.trojanlistings.corpus.Fixture;
import com.ebay.trojanlistings.corpus.FixtureLoader;
import com.ebay.trojanlistings.detector.BaselineScreener;
import com.ebay.trojanlistings.detector.Detector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gap analysis (FR-022, SC-010): what does general-purpose prompt-injection
 * screening miss on listing content?
 *
 * <p>Both screeners run over the identical evaluation set, so the comparison is
 * like-for-like. The per-technique breakdown is printed, and the specific fixtures the
 * baseline missed are named — "it misses obfuscation" is an assertion, "it missed
 * these eight fixtures" is a finding.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "trojan.corpus.path=../corpus",
        "trojan.model.path=../models/prompt-injection-guard-small",
        "trojan.tessdata.path=../models/tessdata"
})
class ComparisonTest {

    @Autowired Detector detector;
    @Autowired BaselineScreener baseline;
    @Autowired FixtureLoader fixtures;

    private record Tally(int caught, int total) {
        double rate() { return total == 0 ? 0.0 : (double) caught / total; }
    }

    @Test
    @DisplayName("FR-022 / SC-010: report per-technique coverage for both screeners")
    void compareAgainstGeneralPurposeBaseline() {
        Map<AttackTechnique, int[]> full = new EnumMap<>(AttackTechnique.class);
        Map<AttackTechnique, int[]> base = new EnumMap<>(AttackTechnique.class);
        List<String> baselineMissed = new ArrayList<>();
        List<String> bothMissed = new ArrayList<>();

        int benignTotal = 0, fullFalseAlarms = 0, baseFalseAlarms = 0;

        for (Fixture f : fixtures.load()) {
            byte[] image = readImage(f);

            boolean fullFlagged = "TROJAN".equals(detector.screen(f.listing(), image).verdict());
            boolean baseFlagged = "TROJAN".equals(baseline.screen(f.listing()).verdict());

            if (!f.hostile()) {
                benignTotal++;
                if (fullFlagged) fullFalseAlarms++;
                if (baseFlagged) baseFalseAlarms++;
                continue;
            }

            full.computeIfAbsent(f.technique(), k -> new int[2])[fullFlagged ? 0 : 1]++;
            base.computeIfAbsent(f.technique(), k -> new int[2])[baseFlagged ? 0 : 1]++;

            if (!baseFlagged) (fullFlagged ? baselineMissed : bothMissed).add(f.id());
        }

        System.out.println();
        System.out.println("============ COMPARISON: listing-aware vs general-purpose ============");
        System.out.printf("  %-18s %18s %18s%n", "TECHNIQUE", "LISTING-AWARE", "GENERAL-PURPOSE");

        for (AttackTechnique t : AttackTechnique.values()) {
            int[] f = full.get(t);
            int[] b = base.get(t);
            if (f == null) continue;
            Tally ft = new Tally(f[0], f[0] + f[1]);
            Tally bt = new Tally(b[0], b[0] + b[1]);
            System.out.printf("  %-18s %13.1f%% %4s %13.1f%% %4s%n",
                    t, ft.rate() * 100, "(" + ft.total() + ")", bt.rate() * 100, "(" + bt.total() + ")");
        }

        System.out.printf("%n  False alarms on benign : listing-aware %d/%d, general-purpose %d/%d%n",
                fullFalseAlarms, benignTotal, baseFalseAlarms, benignTotal);

        System.out.println("\n  GAP — caught by listing-aware screening, missed by general-purpose:");
        System.out.println("    " + (baselineMissed.isEmpty() ? "(none)" : baselineMissed));
        if (!bothMissed.isEmpty()) {
            System.out.println("\n  Missed by BOTH (honest reporting): " + bothMissed);
        }
        System.out.println("======================================================================");
        System.out.println();

        // The claim the project makes on stage: general-purpose screening leaves
        // listing-specific attacks on the table. If this ever stops being true, the
        // pitch needs rewriting, so assert it rather than merely printing it.
        assertTrue(!baselineMissed.isEmpty(),
                "Expected the general-purpose baseline to miss attacks that the "
                        + "listing-aware detector catches. If it caught everything, the "
                        + "structural layer is no longer the differentiator.");
    }

    private byte[] readImage(Fixture f) {
        Path p = fixtures.imagePath(f);
        if (p == null || !Files.isRegularFile(p)) return null;
        try {
            return Files.readAllBytes(p);
        } catch (Exception e) {
            return null;
        }
    }
}
