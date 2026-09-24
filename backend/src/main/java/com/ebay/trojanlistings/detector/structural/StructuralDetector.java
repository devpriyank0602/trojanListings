package com.ebay.trojanlistings.detector.structural;

import com.ebay.trojanlistings.detector.Finding;
import com.ebay.trojanlistings.detector.ListingAssembler.AssembledListing;

import java.util.List;

/**
 * A detector for one family of concealment.
 *
 * <p>These run before the classifier for two reasons. First, obfuscation is a string
 * problem rather than a semantic one -- a classifier reading text with zero-width
 * characters spliced through it sees mangled tokens, while a short scanner sees the
 * attack immediately. Second, running structural first means the classifier scores
 * normalised text, which materially improves its hit rate.
 */
public interface StructuralDetector {

    /** Findings in this listing, or an empty list. Never null. */
    List<Finding> detect(AssembledListing assembled);

    /**
     * A copy of the text with this detector's concealment neutralised, handed to the
     * next layer. Default is a no-op for detectors that have nothing to normalise.
     */
    default String normalise(String text) {
        return text;
    }
}
