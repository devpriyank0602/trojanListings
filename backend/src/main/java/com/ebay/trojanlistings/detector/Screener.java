package com.ebay.trojanlistings.detector;

import com.ebay.trojanlistings.corpus.Listing;

/**
 * Anything that can screen a listing.
 *
 * <p>Exists so the full four-layer {@link Detector} and a general-purpose baseline can
 * be run over the same evaluation set and compared like-for-like (FR-022).
 */
public interface Screener {
    Detector.Verdict screen(Listing listing);
}
