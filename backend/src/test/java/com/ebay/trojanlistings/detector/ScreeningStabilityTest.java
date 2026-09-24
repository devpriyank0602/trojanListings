package com.ebay.trojanlistings.detector;

import com.ebay.trojanlistings.corpus.Fixture;
import com.ebay.trojanlistings.corpus.FixtureLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sustained-load checks for the two hazards research.md §2 inherited with the
 * ONNX-Runtime-in-Java pattern.
 *
 * <p>Native memory is the important one: ONNX Runtime results hold off-heap memory
 * that {@code getValue()} does not release. LIVERANK-259 is a production JVM crash
 * from exactly that omission, and the failure mode is invisible in a single-request
 * test — it only shows up under repetition. Hence 200 screenings.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "trojan.corpus.path=../corpus",
        "trojan.model.path=../models/prompt-injection-guard-small",
        "trojan.tessdata.path=../models/tessdata"
})
class ScreeningStabilityTest {

    @Autowired Detector detector;
    @Autowired FixtureLoader fixtures;

    @Test
    @DisplayName("200 screenings do not leak native or heap memory")
    void sustainedScreeningIsStable() {
        List<Fixture> corpus = fixtures.load();

        // Warm up so class loading and lazy init are not counted as growth.
        for (int i = 0; i < 20; i++) {
            detector.screen(corpus.get(i % corpus.size()).listing(), null);
        }
        long before = committedMemory();

        for (int i = 0; i < 200; i++) {
            detector.screen(corpus.get(i % corpus.size()).listing(), null);
        }

        System.gc();
        try { Thread.sleep(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        long after = committedMemory();

        long growthMb = (after - before) / (1024 * 1024);
        System.out.printf("  Memory after 200 screenings: %+d MB (before %d MB, after %d MB)%n",
                growthMb, before / 1024 / 1024, after / 1024 / 1024);

        // A leak of the LIVERANK-259 shape grows without bound; 64 MB of slack
        // tolerates ordinary JIT and heap noise while still catching that.
        assertTrue(growthMb < 64,
                "Memory grew " + growthMb + " MB over 200 screenings, which suggests "
                        + "an ONNX result or tensor is not being closed. Check that every "
                        + "inference call uses try-with-resources (research.md §2).");
    }

    @Test
    @DisplayName("Screening is deterministic — the same listing yields the same verdict")
    void screeningIsDeterministic() {
        Fixture f = fixtures.hostile().get(0);

        Detector.Verdict first = detector.screen(f.listing(), null);
        for (int i = 0; i < 25; i++) {
            Detector.Verdict again = detector.screen(f.listing(), null);
            assertTrue(first.verdict().equals(again.verdict())
                            && first.findings().size() == again.findings().size(),
                    "Screening must be reproducible — a demo that flickers is worse than "
                            + "one that fails");
        }
    }

    private static long committedMemory() {
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }
}
