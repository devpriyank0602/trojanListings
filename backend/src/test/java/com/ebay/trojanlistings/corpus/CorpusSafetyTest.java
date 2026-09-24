package com.ebay.trojanlistings.corpus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces the project's non-negotiable safety rules against the corpus itself.
 *
 * <p>These exist because the corpus is adversarial content by design, and the single
 * thing that must never happen is a real seller being named in a fixture that says
 * they sell counterfeits. Safety Constraint 3 is not advisory; this test is how it is
 * actually held.
 */
class CorpusSafetyTest {

    private static final FixtureLoader LOADER = new FixtureLoader("../corpus");

    /** Every fabricated seller must be unmistakably fabricated. */
    private static final Pattern FICTIONAL = Pattern.compile(".*_fictional$");

    /**
     * eBay item ids are 12-digit numbers. A fixture containing one is referencing
     * something that may really exist, which is exactly what FR-006 forbids.
     */
    private static final Pattern ITEM_ID = Pattern.compile("\\b\\d{12}\\b");

    /**
     * Real marketplace hostnames. Brand names in a title are fine and unavoidable --
     * "Omega Seamaster" is a product, not a party. Links to live listings are not.
     */
    private static final List<String> LIVE_SURFACE_HOSTS = List.of(
            "ebay.com", "ebay.co.uk", "ebay.de", "amazon.com", "etsy.com"
    );

    @Test
    @DisplayName("FR-006: every sellerName is explicitly fabricated")
    void sellerNamesAreFictional() {
        List<String> bad = new ArrayList<>();
        for (Fixture f : LOADER.load()) {
            String seller = f.listing().sellerName();
            if (seller == null || seller.isBlank()) continue;
            if (!FICTIONAL.matcher(seller).matches()) {
                bad.add(f.id() + " -> '" + seller + "'");
            }
        }
        assertTrue(bad.isEmpty(),
                "Seller names must end '_fictional' so no fixture can be mistaken for a "
                        + "real party -- competitor-disparagement fixtures accuse sellers of "
                        + "counterfeiting (Safety Constraint 3). Offenders: " + bad);
    }

    @Test
    @DisplayName("FR-006: no fixture references a live item id")
    void noLiveItemIds() {
        List<String> bad = new ArrayList<>();
        for (Fixture f : LOADER.load()) {
            if (ITEM_ID.matcher(serialise(f)).find()) bad.add(f.id());
        }
        assertTrue(bad.isEmpty(),
                "A 12-digit number looks like a real marketplace item id: " + bad);
    }

    @Test
    @DisplayName("No fixture links to a live marketplace surface")
    void noLiveSurfaceLinks() {
        List<String> bad = new ArrayList<>();
        for (Fixture f : LOADER.load()) {
            String text = serialise(f).toLowerCase(Locale.ROOT);
            for (String host : LIVE_SURFACE_HOSTS) {
                if (text.contains(host)) {
                    bad.add(f.id() + " -> " + host);
                }
            }
        }
        assertTrue(bad.isEmpty(),
                "Fixtures must not link to live marketplace surfaces: " + bad);
    }

    @Test
    @DisplayName("Disparagement fixtures name only fabricated competitors")
    void disparagementTargetsAreFabricated() {
        List<String> suspicious = new ArrayList<>();
        for (Fixture f : LOADER.hostile()) {
            if (f.goal() != AttackerGoal.COMPETITOR_DISPARAGEMENT) continue;

            // The fixture must make clear the target is invented. Either it names no
            // seller at all (describing "other listings" generically), or any named
            // seller carries the fabricated marker.
            String text = serialise(f);
            if (text.contains("seller '") && !text.contains("_fictional")
                    && !text.contains("_direct")) {
                suspicious.add(f.id());
            }
        }
        assertTrue(suspicious.isEmpty(),
                "Competitor-disparagement fixtures must target fabricated sellers only "
                        + "(Safety Constraint 3): " + suspicious);
    }

    private static String serialise(Fixture f) {
        StringBuilder sb = new StringBuilder();
        sb.append(f.id()).append(' ').append(f.note()).append(' ')
          .append(f.listing().title()).append(' ')
          .append(f.listing().description()).append(' ')
          .append(f.listing().sellerName());
        f.listing().itemSpecifics().forEach((k, v) -> sb.append(' ').append(k).append(' ').append(v));
        return sb.toString();
    }
}
