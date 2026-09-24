package com.ebay.trojanlistings.api.dto;

import com.ebay.trojanlistings.corpus.Listing;

import java.util.Map;

/**
 * A listing submitted for screening. Every field is optional -- an empty request is a
 * defined CLEAN verdict, not an error (FR-017).
 *
 * @param imageBase64 a data URI or bare base64 PNG/JPEG. Capped by
 *                    {@code trojan.screening.max-image-bytes}.
 */
public record ScreenRequest(
        String title,
        String description,
        Map<String, String> itemSpecifics,
        String imageBase64
) {
    public Listing toListing() {
        return new Listing(title, description, itemSpecifics, null, null);
    }
}
