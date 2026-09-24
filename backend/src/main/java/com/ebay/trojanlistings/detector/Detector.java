package com.ebay.trojanlistings.detector;

import com.ebay.trojanlistings.corpus.Listing;
import com.ebay.trojanlistings.detector.classifier.OnnxInjectionClassifier;
import com.ebay.trojanlistings.detector.ocr.ImageTextExtractor;
import com.ebay.trojanlistings.detector.pattern.InstructionPatternDetector;
import com.ebay.trojanlistings.detector.structural.StructuralDetector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the four screening layers and produces the verdict.
 *
 * <p>Order matters and is not arbitrary:
 * <ol>
 *   <li><b>OCR</b> first, so any text hidden in the photo joins the assembled listing
 *       and is screened by every subsequent layer rather than by a special case.</li>
 *   <li><b>Structural</b> next. Obfuscation is a string problem; a short scanner beats
 *       a classifier at it outright. Running it first also lets it hand normalised
 *       text forward, so the classifier sees real words instead of the mangled tokens
 *       the attacker intended.</li>
 *   <li><b>Pattern</b> for known plain-language phrasings.</li>
 *   <li><b>Classifier</b> last, scoring per sentence, for everything not enumerable.</li>
 * </ol>
 *
 * <p><b>Thresholding.</b> Structural findings are not thresholded — a legitimate
 * listing does not contain a zero-width character spliced mid-word, so presence is
 * proof. Classifier findings are thresholded, because a probability is not.
 */
@Component
public class Detector implements Screener {

    /** How screening ran, so the UI can be honest about what was and wasn't checked. */
    public enum Mode { FULL, DEGRADED_NO_CLASSIFIER }

    /** Whether the listing photo was actually screened (FR-037). */
    public enum ImageStatus { SCREENED, NOT_SCREENED, NO_IMAGE }

    public record Verdict(
            String verdict,              // "TROJAN" or "CLEAN"
            double confidence,
            /** The exact string the detectors scanned. Finding offsets index into this. */
            String assembledText,
            Mode mode,
            ImageStatus imageScreened,
            String imageNote,            // why an image was not screened, if it wasn't
            String extractedImageText,   // what OCR read, for the evidence panel
            long elapsedMs,
            List<Finding> findings
    ) {}

    private final ListingAssembler assembler;
    private final List<StructuralDetector> structural;
    private final InstructionPatternDetector patterns;
    private final OnnxInjectionClassifier classifier;
    private final ImageTextExtractor ocr;

    public Detector(ListingAssembler assembler,
                    List<StructuralDetector> structural,
                    InstructionPatternDetector patterns,
                    OnnxInjectionClassifier classifier,
                    ImageTextExtractor ocr) {
        this.assembler = assembler;
        this.structural = structural;
        this.patterns = patterns;
        this.classifier = classifier;
        this.ocr = ocr;
    }

    @Override
    public Verdict screen(Listing listing) {
        return screen(listing, null);
    }

    /**
     * @param imageBytes the listing photo, or null. When present its text is extracted
     *                   and appended to the assembled listing before screening.
     */
    public Verdict screen(Listing listing, byte[] imageBytes) {
        long started = System.currentTimeMillis();

        // --- Layer 4 (first in order): text rendered into the photo ---
        ImageStatus imageStatus = ImageStatus.NO_IMAGE;
        String imageNote = null;
        String imageText = null;

        if (imageBytes != null && imageBytes.length > 0) {
            ImageTextExtractor.Extraction extraction = ocr.extract(imageBytes);
            if (extraction.screened()) {
                imageStatus = ImageStatus.SCREENED;
                imageText = extraction.text();
            } else {
                // Explicitly not "clean". An unreadable image is an unscreened image.
                imageStatus = ImageStatus.NOT_SCREENED;
                imageNote = extraction.note();
            }
        }

        ListingAssembler.AssembledListing assembled = assembler.assemble(listing, imageText);

        // An entirely empty listing is a defined CLEAN verdict, never an error (FR-017).
        if (assembled.text().isBlank()) {
            return new Verdict("CLEAN", 0.0, assembled.text(), modeOf(), imageStatus,
                    imageNote, imageText, System.currentTimeMillis() - started, List.of());
        }

        List<Finding> findings = new ArrayList<>();

        // --- Layer 1: structural obfuscation, and normalisation for what follows ---
        String normalised = assembled.text();
        for (StructuralDetector detector : structural) {
            findings.addAll(detector.detect(assembled));
            normalised = detector.normalise(normalised);
        }

        // --- Layer 2: known instruction phrasings ---
        findings.addAll(patterns.detect(assembled));

        // --- Layer 3: the classifier, per sentence, over normalised text ---
        findings.addAll(classifier.detect(assembled, normalised));

        // Attribute image-sourced findings to their own layer, so the UI can show them
        // beside the photo rather than mixed into the seller's text.
        List<Finding> attributed = findings.stream()
                .map(f -> "image".equals(f.sourceField())
                        ? new Finding(Finding.Layer.IMAGE_TEXT, f.concealment(), f.sourceField(),
                                      f.span(), f.startOffset(), f.endOffset(), f.revealedSpan(),
                                      f.score(), f.explanation())
                        : f)
                .sorted(Finding::byRelevance)
                .toList();

        double confidence = attributed.stream().mapToDouble(Finding::score).max().orElse(0.0);

        return new Verdict(
                attributed.isEmpty() ? "CLEAN" : "TROJAN",
                confidence,
                assembled.text(),
                modeOf(),
                imageStatus,
                imageNote,
                imageText,
                System.currentTimeMillis() - started,
                attributed);
    }

    private Mode modeOf() {
        return classifier.available() ? Mode.FULL : Mode.DEGRADED_NO_CLASSIFIER;
    }
}
