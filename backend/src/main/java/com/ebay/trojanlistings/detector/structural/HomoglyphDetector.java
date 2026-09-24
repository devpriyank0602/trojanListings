package com.ebay.trojanlistings.detector.structural;

import com.ebay.trojanlistings.detector.ConcealmentTechnique;
import com.ebay.trojanlistings.detector.Finding;
import com.ebay.trojanlistings.detector.ListingAssembler.AssembledListing;
import org.springframework.stereotype.Component;

import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flags look-alike characters from other alphabets substituted into Latin words.
 *
 * <p>Cyrillic а (U+0430) and Latin a (U+0061) are visually identical and byte-wise
 * different. Write {@code rаnk} and a blocklist for "rank" sees nothing.
 *
 * <p><b>The critical design constraint</b> is the false-alarm side. This must key on
 * script mixing <em>within a single token</em>, never on the mere presence of Cyrillic
 * or Greek -- otherwise every Russian-language listing on the platform gets flagged,
 * and SC-006's 10% false-alarm cap is blown by a rule that is simply wrong. The corpus
 * carries a genuine Cyrillic listing (bn-plain-04) specifically to hold this line.
 */
@Component
public class HomoglyphDetector implements StructuralDetector {

    /** Confusables that matter: ones that map onto Latin letters used in English words. */
    private static final Map<Character, Character> CONFUSABLES = new HashMap<>();
    static {
        // Cyrillic -> Latin
        CONFUSABLES.put('а', 'a'); CONFUSABLES.put('е', 'e'); CONFUSABLES.put('о', 'o');
        CONFUSABLES.put('р', 'p'); CONFUSABLES.put('с', 'c'); CONFUSABLES.put('х', 'x');
        CONFUSABLES.put('у', 'y'); CONFUSABLES.put('ѕ', 's'); CONFUSABLES.put('і', 'i');
        CONFUSABLES.put('ј', 'j'); CONFUSABLES.put('һ', 'h'); CONFUSABLES.put('ԁ', 'd');
        CONFUSABLES.put('А', 'A'); CONFUSABLES.put('В', 'B'); CONFUSABLES.put('Е', 'E');
        CONFUSABLES.put('К', 'K'); CONFUSABLES.put('М', 'M'); CONFUSABLES.put('Н', 'H');
        CONFUSABLES.put('О', 'O'); CONFUSABLES.put('Р', 'P'); CONFUSABLES.put('С', 'C');
        CONFUSABLES.put('Т', 'T'); CONFUSABLES.put('Х', 'X');
        // Greek -> Latin
        CONFUSABLES.put('ο', 'o'); CONFUSABLES.put('α', 'a'); CONFUSABLES.put('ν', 'v');
        CONFUSABLES.put('ρ', 'p'); CONFUSABLES.put('τ', 't'); CONFUSABLES.put('υ', 'u');
        CONFUSABLES.put('Ο', 'O'); CONFUSABLES.put('Α', 'A'); CONFUSABLES.put('Β', 'B');
        CONFUSABLES.put('Ε', 'E'); CONFUSABLES.put('Ζ', 'Z'); CONFUSABLES.put('Η', 'H');
        CONFUSABLES.put('Ι', 'I'); CONFUSABLES.put('Κ', 'K'); CONFUSABLES.put('Μ', 'M');
        CONFUSABLES.put('Ν', 'N'); CONFUSABLES.put('Ρ', 'P'); CONFUSABLES.put('Τ', 'T');
    }

    @Override
    public List<Finding> detect(AssembledListing assembled) {
        String text = assembled.text();
        List<Finding> findings = new ArrayList<>();

        int i = 0;
        while (i < text.length()) {
            if (!Character.isLetter(text.charAt(i))) { i++; continue; }

            int start = i;
            while (i < text.length() && Character.isLetter(text.charAt(i))) i++;
            int end = i;

            String token = text.substring(start, end);
            if (!isMixedScript(token)) continue;

            findings.add(new Finding(
                    Finding.Layer.STRUCTURAL,
                    ConcealmentTechnique.HOMOGLYPH,
                    assembled.fieldAt(start),
                    token, start, end,
                    reveal(token),
                    1.0,
                    "The word \"" + normaliseToken(token) + "\" contains look-alike letters "
                            + "from another alphabet (" + foreignScripts(token) + "). "
                            + "Visually identical to a buyer, a different string to any filter."));
        }
        return findings;
    }

    @Override
    public String normalise(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            sb.append(CONFUSABLES.getOrDefault(c, c));
        }
        return sb.toString();
    }

    /**
     * True only when a single token mixes Latin with a confusable from another script.
     * A wholly Cyrillic word is ordinary Russian, not an attack.
     */
    private static boolean isMixedScript(String token) {
        boolean hasLatin = false;
        boolean hasConfusable = false;
        for (char c : token.toCharArray()) {
            if (UnicodeScript.of(c) == UnicodeScript.LATIN) hasLatin = true;
            else if (CONFUSABLES.containsKey(c)) hasConfusable = true;
        }
        return hasLatin && hasConfusable;
    }

    static String reveal(String token) {
        StringBuilder sb = new StringBuilder();
        for (char c : token.toCharArray()) {
            Character latin = CONFUSABLES.get(c);
            if (latin != null) {
                sb.append('[').append(c).append('→').append(latin).append(' ')
                  .append(shortScript(c)).append(']');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String normaliseToken(String token) {
        StringBuilder sb = new StringBuilder();
        for (char c : token.toCharArray()) sb.append(CONFUSABLES.getOrDefault(c, c));
        return sb.toString();
    }

    private static String foreignScripts(String token) {
        java.util.Set<String> scripts = new java.util.LinkedHashSet<>();
        for (char c : token.toCharArray()) {
            if (CONFUSABLES.containsKey(c)) scripts.add(UnicodeScript.of(c).name().toLowerCase());
        }
        return String.join(", ", scripts);
    }

    private static String shortScript(char c) {
        return switch (UnicodeScript.of(c)) {
            case CYRILLIC -> "CYR";
            case GREEK -> "GRK";
            default -> "?";
        };
    }
}
