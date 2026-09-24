package com.ebay.trojanlistings.detector.classifier;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the assembled listing into sentences, preserving offsets.
 *
 * <p>This is Sentence Fragment Extraction, borrowed from StackOne Defender (see
 * research.md 1.3). It earns its place twice over:
 *
 * <ul>
 *   <li><b>Detection.</b> A two-line injection inside a 400-word description is
 *       diluted below threshold if the whole listing is scored as one sequence.
 *       Scored alone, the injection stands out.</li>
 *   <li><b>Explanation.</b> The highest-scoring sentence <em>is</em> the span the UI
 *       highlights. No separate span-localisation step is needed.</li>
 * </ul>
 */
@Component
public class SentenceSplitter {

    /** A sentence plus where it came from in the assembled text. */
    public record Sentence(String text, int startOffset, int endOffset) {}

    private static final int MIN_SENTENCE_CHARS = 12;

    public List<Sentence> split(String text) {
        List<Sentence> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;

        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean terminator = c == '.' || c == '!' || c == '?' || c == '\n';
            if (!terminator && i != text.length() - 1) continue;

            int end = Math.min(i + 1, text.length());
            emit(out, text, start, end);
            start = end;
        }
        if (start < text.length()) emit(out, text, start, text.length());

        return out;
    }

    private void emit(List<Sentence> out, String text, int start, int end) {
        // Trim whitespace while keeping offsets pointing at real content, so a
        // highlight never spans a leading newline.
        int s = start;
        while (s < end && Character.isWhitespace(text.charAt(s))) s++;
        int e = end;
        while (e > s && Character.isWhitespace(text.charAt(e - 1))) e--;

        if (e - s < MIN_SENTENCE_CHARS) return;   // fragments score noisily
        out.add(new Sentence(text.substring(s, e), s, e));
    }
}
