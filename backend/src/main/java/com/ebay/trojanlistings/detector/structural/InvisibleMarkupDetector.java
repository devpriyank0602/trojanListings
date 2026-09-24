package com.ebay.trojanlistings.detector.structural;

import com.ebay.trojanlistings.detector.ConcealmentTechnique;
import com.ebay.trojanlistings.detector.Finding;
import com.ebay.trojanlistings.detector.ListingAssembler.AssembledListing;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags HTML that hides text from a buyer while leaving it fully legible to a model.
 *
 * <p>eBay descriptions accept HTML, so a seller can write white-on-white text, 1px
 * fonts, or {@code display:none} blocks. The buyer sees an ordinary listing; the model
 * reads the payload in full. This is the attack that makes the "reveal hidden
 * characters" toggle land on stage.
 *
 * <p>Keys on <em>invisibility</em>, never on the presence of CSS. Ordinary styled
 * listings are common and must not fire (the corpus carries bn-plain-03 for this).
 */
@Component
public class InvisibleMarkupDetector implements StructuralDetector {

    private static final Pattern STYLED_ELEMENT = Pattern.compile(
            "<([a-z]+)\\b[^>]*\\bstyle\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>(.*?)</\\1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private record Rule(Pattern pattern, String label) {}

    private static final List<Rule> INVISIBILITY_RULES = List.of(
            new Rule(Pattern.compile("color\\s*:\\s*(#fff(fff)?\\b|white\\b|rgb\\(\\s*255\\s*,\\s*255\\s*,\\s*255\\s*\\))",
                    Pattern.CASE_INSENSITIVE), "white text"),
            new Rule(Pattern.compile("font-size\\s*:\\s*0*(\\.\\d+)?(px|pt|em|rem)?\\s*(;|$)",
                    Pattern.CASE_INSENSITIVE), "zero font size"),
            new Rule(Pattern.compile("font-size\\s*:\\s*1px", Pattern.CASE_INSENSITIVE), "1px font size"),
            new Rule(Pattern.compile("display\\s*:\\s*none", Pattern.CASE_INSENSITIVE), "display:none"),
            new Rule(Pattern.compile("visibility\\s*:\\s*hidden", Pattern.CASE_INSENSITIVE), "visibility:hidden"),
            new Rule(Pattern.compile("opacity\\s*:\\s*0(\\.0+)?\\s*(;|$)", Pattern.CASE_INSENSITIVE), "zero opacity"),
            new Rule(Pattern.compile("text-indent\\s*:\\s*-\\d{3,}", Pattern.CASE_INSENSITIVE), "off-screen text indent")
    );

    @Override
    public List<Finding> detect(AssembledListing assembled) {
        String text = assembled.text();
        List<Finding> findings = new ArrayList<>();

        Matcher m = STYLED_ELEMENT.matcher(text);
        while (m.find()) {
            String style = m.group(2);
            String inner = stripTags(m.group(3)).trim();
            if (inner.isEmpty()) continue;

            String reason = invisibilityReason(style);
            if (reason == null) continue;

            int start = m.start(3);
            int end = m.end(3);

            findings.add(new Finding(
                    Finding.Layer.STRUCTURAL,
                    ConcealmentTechnique.INVISIBLE_MARKUP,
                    assembled.fieldAt(start),
                    inner, start, end,
                    "[HIDDEN via " + reason + " ▸ " + inner + "]",
                    1.0,
                    "Text hidden with " + reason + " -- invisible to a human buyer, "
                            + "fully legible to a model reading the listing."));
        }
        return findings;
    }

    private static String invisibilityReason(String style) {
        boolean whiteText = false, whiteBackground = false;
        String reason = null;

        for (Rule rule : INVISIBILITY_RULES) {
            if (!rule.pattern().matcher(style).find()) continue;
            if (rule.label().equals("white text")) { whiteText = true; continue; }
            reason = rule.label();
        }

        if (whiteText) {
            whiteBackground = Pattern.compile("background(-color)?\\s*:\\s*(#fff(fff)?\\b|white\\b)",
                    Pattern.CASE_INSENSITIVE).matcher(style).find();
            // White on an unspecified background is still hidden on eBay's white page.
            return whiteBackground ? "white-on-white styling" : "white text on a white page";
        }
        return reason;
    }

    private static String stripTags(String html) {
        return html.replaceAll("<[^>]+>", "");
    }
}
