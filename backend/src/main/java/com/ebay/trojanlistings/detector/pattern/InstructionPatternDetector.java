package com.ebay.trojanlistings.detector.pattern;

import com.ebay.trojanlistings.detector.ConcealmentTechnique;
import com.ebay.trojanlistings.detector.Finding;
import com.ebay.trojanlistings.detector.ListingAssembler.AssembledListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Matches known instruction phrasings aimed at an AI reader.
 *
 * <p>This layer is deliberately narrow. It catches plain-language attacks that the
 * structural layer cannot see and the classifier may score below threshold -- but it
 * is the layer most likely to produce false alarms, so every pattern requires an
 * imperative <em>and</em> a target. That constraint lives in the pattern file and is
 * asserted by the benign controls in the evaluation set.
 *
 * <p>Score is 0.9 rather than 1.0: a pattern match is strong evidence but, unlike a
 * zero-width character spliced mid-word, not proof. The distinction is honest and it
 * shows in the UI.
 */
@Component
public class InstructionPatternDetector {

    private static final Logger log = LoggerFactory.getLogger(InstructionPatternDetector.class);
    private static final double PATTERN_SCORE = 0.9;

    private final List<Pattern> patterns;

    public InstructionPatternDetector() {
        this.patterns = load();
        log.info("Loaded {} instruction patterns", patterns.size());
    }

    public List<Finding> detect(AssembledListing assembled) {
        String text = assembled.text();
        List<Finding> findings = new ArrayList<>();

        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                // Report the whole sentence containing the match. The matched fragment
                // alone reads as a phrase out of context; a reviewer needs the claim.
                int start = sentenceStart(text, m.start());
                int end = sentenceEnd(text, m.end());
                String span = text.substring(start, end).trim();

                findings.add(new Finding(
                        Finding.Layer.PATTERN,
                        classify(m.group()),
                        assembled.fieldAt(m.start()),
                        span, start, end,
                        span,
                        PATTERN_SCORE,
                        "Contains a known instruction phrasing aimed at an AI reader: \""
                                + m.group().trim() + "\"."));
                break;   // one finding per pattern; overlapping matches add noise
            }
        }
        return findings;
    }

    /**
     * Patterns mostly describe plain-language attacks. A bracketed role marker inside
     * the match means the seller also forged a delimiter, which is worth naming.
     */
    private static ConcealmentTechnique classify(String match) {
        String lower = match.toLowerCase();
        boolean forgedRole = lower.contains("[system]") || lower.contains("[inst]")
                || lower.contains("<|") || lower.contains("<<sys>>");
        return forgedRole ? ConcealmentTechnique.CHAT_TEMPLATE : ConcealmentTechnique.NONE;
    }

    private List<Pattern> load() {
        List<Pattern> compiled = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource("patterns/instruction-patterns.txt").getInputStream(),
                StandardCharsets.UTF_8))) {

            String line;
            int lineNo = 0;
            while ((line = r.readLine()) != null) {
                lineNo++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                try {
                    compiled.add(Pattern.compile(trimmed, Pattern.CASE_INSENSITIVE));
                } catch (PatternSyntaxException e) {
                    // Skip the broken line rather than failing startup -- one bad regex
                    // should not take down screening entirely. But say so loudly.
                    log.error("Invalid pattern at line {} ('{}'): {}", lineNo, trimmed, e.getDescription());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load instruction patterns", e);
        }
        return List.copyOf(compiled);
    }

    private static int sentenceStart(String text, int from) {
        for (int i = from; i > 0; i--) {
            char c = text.charAt(i - 1);
            if (c == '.' || c == '\n' || c == '!' || c == '?') return i;
        }
        return 0;
    }

    private static int sentenceEnd(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '\n' || c == '!' || c == '?') return Math.min(i + 1, text.length());
        }
        return text.length();
    }
}
