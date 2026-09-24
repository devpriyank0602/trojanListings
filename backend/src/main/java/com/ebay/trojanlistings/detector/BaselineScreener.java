package com.ebay.trojanlistings.detector;

import com.ebay.trojanlistings.corpus.Listing;
import com.ebay.trojanlistings.detector.classifier.OnnxInjectionClassifier;
import com.ebay.trojanlistings.detector.pattern.InstructionPatternDetector;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A stand-in for general-purpose prompt-injection screening (FR-022).
 *
 * <p>It runs the classifier and pattern layers but <b>deliberately omits the
 * structural layer</b>. That is not a handicap invented to flatter the comparison --
 * it is what general-purpose tooling actually does. The survey in research.md §1.2
 * found that off-the-shelf options are either semantic classifiers trained on
 * user-typed prompts, or pattern matchers for direct injection. None of them carry
 * detectors for zero-width splicing, homoglyph substitution, base64 payloads or
 * forged chat delimiters in a document.
 *
 * <p>The comparison therefore answers a specific question: how much of the listing
 * threat does general-purpose screening leave on the table? Running both over the same
 * evaluation set makes that a measurement rather than an assertion.
 */
@Component
public class BaselineScreener implements Screener {

    private final ListingAssembler assembler;
    private final InstructionPatternDetector patterns;
    private final OnnxInjectionClassifier classifier;

    public BaselineScreener(ListingAssembler assembler,
                            InstructionPatternDetector patterns,
                            OnnxInjectionClassifier classifier) {
        this.assembler = assembler;
        this.patterns = patterns;
        this.classifier = classifier;
    }

    @Override
    public Detector.Verdict screen(Listing listing) {
        long started = System.currentTimeMillis();

        // No OCR either: a general-purpose text screener does not read listing photos.
        ListingAssembler.AssembledListing assembled = assembler.assemble(listing, null);

        if (assembled.text().isBlank()) {
            return new Detector.Verdict("CLEAN", 0.0, assembled.text(),
                    Detector.Mode.FULL, Detector.ImageStatus.NO_IMAGE, null, null,
                    System.currentTimeMillis() - started, List.of());
        }

        // Crucially: the raw text, NOT structurally normalised. A general-purpose tool
        // has no structural layer, so it never gets to see through the obfuscation.
        List<Finding> findings = new java.util.ArrayList<>(patterns.detect(assembled));
        findings.addAll(classifier.detect(assembled, assembled.text()));
        findings.sort(Finding::byRelevance);

        double confidence = findings.stream().mapToDouble(Finding::score).max().orElse(0.0);

        return new Detector.Verdict(
                findings.isEmpty() ? "CLEAN" : "TROJAN",
                confidence,
                assembled.text(),
                Detector.Mode.FULL,
                Detector.ImageStatus.NO_IMAGE,
                null, null,
                System.currentTimeMillis() - started,
                List.copyOf(findings));
    }
}
