package com.ebay.trojanlistings.detector;

/**
 * One reason a listing was called TROJAN.
 *
 * <p>This is the "on what basis" the UI renders. Every field exists to answer a
 * question a reviewer will actually ask: what kind of attack ({@code concealment}),
 * where it was ({@code sourceField}), what exactly ({@code span}), and why that is
 * suspicious ({@code explanation}).
 */
public record Finding(
        Layer layer,
        ConcealmentTechnique concealment,

        /** {@code title}, {@code description}, {@code specific:Care Instructions}, {@code image}. */
        String sourceField,

        /** The offending text, verbatim. */
        String span,

        /** Offsets into the assembled listing, for UI highlighting. */
        int startOffset,
        int endOffset,

        /**
         * The span with invisible content made visible -- zero-width codepoints
         * replaced by sentinels, homoglyphs marked, hidden markup unwrapped, base64
         * decoded. This is what the "Reveal hidden characters" toggle shows, and it is
         * what makes "a buyer sees nothing, the model sees everything" land as a
         * demonstration rather than a claim.
         */
        String revealedSpan,

        double score,

        /** Plain English. Shown directly to a non-technical reviewer. */
        String explanation
) {
    public enum Layer {
        /** Structural obfuscation. Near-deterministic, so not thresholded. */
        STRUCTURAL,
        /** Known instruction phrasings. */
        PATTERN,
        /** The ONNX sequence classifier, scored per sentence. */
        CLASSIFIER,
        /** Text extracted from the listing photo by OCR. */
        IMAGE_TEXT
    }

    /** Longest span first at equal score, so the UI highlights the most informative one. */
    public static int byRelevance(Finding a, Finding b) {
        int byScore = Double.compare(b.score(), a.score());
        if (byScore != 0) return byScore;
        return Integer.compare(b.span().length(), a.span().length());
    }
}
