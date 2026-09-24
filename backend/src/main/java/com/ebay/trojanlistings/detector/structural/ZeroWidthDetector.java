package com.ebay.trojanlistings.detector.structural;

import com.ebay.trojanlistings.detector.ConcealmentTechnique;
import com.ebay.trojanlistings.detector.Finding;
import com.ebay.trojanlistings.detector.ListingAssembler.AssembledListing;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Flags zero-width codepoints spliced inside words.
 *
 * <p>The attack: write {@code ig<U+200B>nore} and the literal token "ignore" never
 * appears, so a keyword blocklist sees nothing. A human sees "ignore" -- the character
 * has no width. A model reads it as the instruction it is.
 *
 * <p>Deliberately requires the character to sit <em>between two letters</em>. Zero-width
 * joiners appear legitimately in emoji sequences and in Indic and Arabic scripts; a
 * blanket flag would fire on ordinary listings in those languages.
 */
@Component
public class ZeroWidthDetector implements StructuralDetector {

    /** ZWSP, ZWNJ, ZWJ, word joiner, BOM/ZWNBSP, and the invisible-operator block. */
    private static final String ZERO_WIDTH = "​‌‍⁠﻿⁡⁢⁣⁤";

    private static boolean isZeroWidth(char c) {
        return ZERO_WIDTH.indexOf(c) >= 0;
    }

    @Override
    public List<Finding> detect(AssembledListing assembled) {
        String text = assembled.text();
        List<Finding> findings = new ArrayList<>();

        int i = 0;
        while (i < text.length()) {
            if (!isZeroWidth(text.charAt(i))) { i++; continue; }

            // Intra-word only: letter on both sides. Emoji ZWJ sequences and script
            // shaping joiners do not satisfy this.
            boolean intraWord = i > 0 && i < text.length() - 1
                    && Character.isLetter(text.charAt(i - 1))
                    && Character.isLetter(text.charAt(i + 1));
            if (!intraWord) { i++; continue; }

            int start = wordStart(text, i);
            int end = wordEnd(text, i);
            String span = text.substring(start, end);

            findings.add(new Finding(
                    Finding.Layer.STRUCTURAL,
                    ConcealmentTechnique.ZERO_WIDTH,
                    assembled.fieldAt(start),
                    span, start, end,
                    reveal(span),
                    1.0,
                    "Zero-width character inserted inside the word \"" + stripZeroWidth(span)
                            + "\", so the word never appears as a literal token to a "
                            + "keyword filter. Invisible to a human reader."));

            i = end;   // one finding per affected word
        }
        return findings;
    }

    @Override
    public String normalise(String text) {
        return stripZeroWidth(text);
    }

    /** Substitutes visible sentinels so the reader can see what was hidden. */
    static String reveal(String span) {
        StringBuilder sb = new StringBuilder(span.length() + 16);
        for (char c : span.toCharArray()) {
            if (isZeroWidth(c)) {
                sb.append("␣").append(nameOf(c)).append("␣");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String nameOf(char c) {
        return switch (c) {
            case '​' -> "ZWSP";
            case '‌' -> "ZWNJ";
            case '‍' -> "ZWJ";
            case '⁠' -> "WJ";
            case '﻿' -> "BOM";
            default -> String.format("U+%04X", (int) c);
        };
    }

    static String stripZeroWidth(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (!isZeroWidth(c)) sb.append(c);
        }
        return sb.toString();
    }

    private static int wordStart(String text, int i) {
        int s = i;
        while (s > 0 && (Character.isLetterOrDigit(text.charAt(s - 1)) || isZeroWidth(text.charAt(s - 1)))) s--;
        return s;
    }

    private static int wordEnd(String text, int i) {
        int e = i;
        while (e < text.length() && (Character.isLetterOrDigit(text.charAt(e)) || isZeroWidth(text.charAt(e)))) e++;
        return e;
    }
}
