package com.ebay.trojanlistings.detector.structural;

import com.ebay.trojanlistings.detector.ConcealmentTechnique;
import com.ebay.trojanlistings.detector.Finding;
import com.ebay.trojanlistings.detector.ListingAssembler.AssembledListing;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Flags counterfeit chat-template delimiters planted in seller content.
 *
 * <p>These tokens mark turn boundaries in a model's own serialisation format. A seller
 * who writes {@code <|im_start|>system} into a description is attempting to convince
 * the model that a new, higher-authority turn has begun. If the serving stack does not
 * strip them, the model may genuinely act on it.
 *
 * <p>No legitimate listing contains these. They are not thresholded.
 */
@Component
public class ChatTemplateDetector implements StructuralDetector {

    /** marker -> what it is, for the explanation shown to a reviewer. */
    private static final Map<String, String> MARKERS = Map.ofEntries(
            Map.entry("<|im_start|>", "ChatML turn-start token"),
            Map.entry("<|im_end|>", "ChatML turn-end token"),
            Map.entry("<|system|>", "system-role token"),
            Map.entry("<|user|>", "user-role token"),
            Map.entry("<|assistant|>", "assistant-role token"),
            Map.entry("<|endoftext|>", "end-of-text token"),
            Map.entry("[inst]", "Llama instruction-start token"),
            Map.entry("[/inst]", "Llama instruction-end token"),
            Map.entry("<<sys>>", "Llama system-block token"),
            Map.entry("<</sys>>", "Llama system-block close token"),
            Map.entry("### system", "system-role section header"),
            Map.entry("### instruction", "instruction section header"),
            Map.entry("[system]", "bracketed system-role marker"),
            Map.entry("human:", "conversation-role prefix"),
            Map.entry("assistant:", "conversation-role prefix")
    );

    @Override
    public List<Finding> detect(AssembledListing assembled) {
        String text = assembled.text();
        String lower = text.toLowerCase(Locale.ROOT);
        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<String, String> entry : MARKERS.entrySet()) {
            String marker = entry.getKey();
            int from = 0;
            while (true) {
                int at = lower.indexOf(marker, from);
                if (at < 0) break;

                // Report the marker plus the sentence it introduces -- the instruction
                // is what a reviewer needs to see, not the delimiter on its own.
                int end = Math.min(text.length(), at + marker.length() + 160);
                int sentenceEnd = text.indexOf('\n', at + marker.length());
                if (sentenceEnd > 0 && sentenceEnd < end) end = sentenceEnd;

                String span = text.substring(at, end);
                findings.add(new Finding(
                        Finding.Layer.STRUCTURAL,
                        ConcealmentTechnique.CHAT_TEMPLATE,
                        assembled.fieldAt(at),
                        span, at, end,
                        span,
                        1.0,
                        "Contains a " + entry.getValue() + " (\"" + text.substring(at, at + marker.length())
                                + "\"), which imitates the start of a new instruction turn. "
                                + "No legitimate listing contains this."));
                from = at + marker.length();
            }
        }
        return findings;
    }
}
