package com.ebay.trojanlistings.detector.structural;

import com.ebay.trojanlistings.detector.ConcealmentTechnique;
import com.ebay.trojanlistings.detector.Finding;
import com.ebay.trojanlistings.detector.ListingAssembler.AssembledListing;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags payloads encoded so a model can read them but a filter cannot.
 *
 * <p>Two families:
 * <ul>
 *   <li><b>Base64.</b> A model will decode it happily. Critically, this detector only
 *       fires when the decoded bytes are <em>readable instruction-like text</em> --
 *       not merely because a string is base64-shaped. Listings legitimately contain
 *       serial numbers and SSH key fragments that look identical to a shape check
 *       (the corpus carries bn-plain-05 for exactly this).</li>
 *   <li><b>Spaced letters.</b> {@code d o   n o t   m e n t i o n} -- no whole word
 *       exists to match. The cheapest possible evasion, which is why it matters.</li>
 * </ul>
 */
@Component
public class EncodedPayloadDetector implements StructuralDetector {

    private static final Pattern BASE64_BLOCK =
            Pattern.compile("\\b[A-Za-z0-9+/]{24,}={0,2}\\b");

    /** At least six single letters separated by spaces. */
    private static final Pattern SPACED_LETTERS =
            Pattern.compile("\\b(?:[A-Za-z]\\s+){5,}[A-Za-z]\\b");

    /**
     * Words that mark decoded text as an instruction rather than a serial number.
     * Requiring one of these is what keeps the false-alarm rate honest.
     */
    private static final List<String> INSTRUCTION_MARKERS = List.of(
            "ignore", "disregard", "instruction", "system", "prompt", "print", "output",
            "recommend", "rank", "do not", "don't", "must", "reveal", "forget", "override",
            "listing", "assistant", "mention", "summar"
    );

    @Override
    public List<Finding> detect(AssembledListing assembled) {
        String text = assembled.text();
        List<Finding> findings = new ArrayList<>();

        Matcher b64 = BASE64_BLOCK.matcher(text);
        while (b64.find()) {
            String candidate = b64.group();
            String decoded = tryDecode(candidate);
            if (decoded == null || !looksLikeInstruction(decoded)) continue;

            findings.add(new Finding(
                    Finding.Layer.STRUCTURAL,
                    ConcealmentTechnique.ENCODED_PAYLOAD,
                    assembled.fieldAt(b64.start()),
                    candidate, b64.start(), b64.end(),
                    "[BASE64 ▸ " + decoded.trim() + "]",
                    1.0,
                    "Base64 block decoding to an instruction: \"" + decoded.trim() + "\". "
                            + "A model decodes this; a keyword filter does not."));
        }

        Matcher spaced = SPACED_LETTERS.matcher(text);
        while (spaced.find()) {
            String candidate = spaced.group();
            String collapsed = candidate.replaceAll("\\s+", "");
            if (!looksLikeInstruction(collapsed)) continue;

            findings.add(new Finding(
                    Finding.Layer.STRUCTURAL,
                    ConcealmentTechnique.ENCODED_PAYLOAD,
                    assembled.fieldAt(spaced.start()),
                    candidate, spaced.start(), spaced.end(),
                    "[SPACED ▸ " + collapsed + "]",
                    1.0,
                    "Letters separated by spaces so no whole word exists to match, "
                            + "spelling out: \"" + collapsed + "\"."));
        }

        return findings;
    }

    @Override
    public String normalise(String text) {
        // Collapse spaced-letter runs so the classifier sees real words.
        Matcher m = SPACED_LETTERS.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group().replaceAll("\\s+", "")));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String tryDecode(String candidate) {
        try {
            byte[] raw = Base64.getDecoder().decode(candidate);
            if (raw.length < 8) return null;

            String decoded = new String(raw, StandardCharsets.UTF_8);
            // Reject binary: require the decoded bytes to be mostly printable ASCII.
            long printable = decoded.chars()
                    .filter(c -> c >= 32 && c < 127 || c == '\n' || c == '\r' || c == '\t')
                    .count();
            return printable >= decoded.length() * 0.9 ? decoded : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean looksLikeInstruction(String decoded) {
        String lower = decoded.toLowerCase(Locale.ROOT);
        return INSTRUCTION_MARKERS.stream().anyMatch(lower::contains);
    }
}
