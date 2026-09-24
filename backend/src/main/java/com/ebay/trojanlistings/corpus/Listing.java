package com.ebay.trojanlistings.corpus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A seller listing -- the unit of analysis. Both a corpus fixture and a listing pasted
 * into the UI are Listings.
 *
 * <p>Nulls are normalised away in the compact constructor so every downstream detector
 * can assume non-null fields. An entirely empty Listing is legal and must produce a
 * defined verdict rather than an error (FR-017).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Listing(
        String title,
        String description,
        Map<String, String> itemSpecifics,
        String imagePath,
        String sellerName
) {
    public Listing {
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        // LinkedHashMap preserves declaration order, so assembled offsets are stable
        // across runs -- which matters because the UI highlights by offset.
        itemSpecifics = itemSpecifics == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(itemSpecifics));
    }

    /** Convenience for tests and the screening endpoint, which often have text only. */
    public static Listing ofText(String title, String description) {
        return new Listing(title, description, Map.of(), null, null);
    }

    /** True when every field a detector could read is empty. */
    public boolean isEmpty() {
        return title.isBlank()
                && description.isBlank()
                && itemSpecifics.isEmpty()
                && (imagePath == null || imagePath.isBlank());
    }
}
