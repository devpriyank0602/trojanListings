package com.ebay.trojanlistings.detector;

import com.ebay.trojanlistings.corpus.Listing;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Flattens a {@link Listing} into the single string the detectors scan, while keeping
 * a map back from any character offset to the field it came from.
 *
 * <p>Screening assesses the listing as an assembled whole (FR-015) so that manipulation
 * split across several fields is not missed -- no single field of which is hostile on
 * its own. But a verdict still has to name the field responsible (FR-014), so the
 * assembler records a {@link Segment} per field with its offset range.
 */
@Component
public class ListingAssembler {

    /** A contiguous run of assembled text originating from one listing field. */
    public record Segment(String sourceField, int startOffset, int endOffset) {
        boolean contains(int offset) {
            return offset >= startOffset && offset < endOffset;
        }
    }

    public record AssembledListing(String text, List<Segment> segments) {

        /**
         * The field an offset came from, or {@code "unknown"} if it lands on a
         * structural marker rather than field content.
         */
        public String fieldAt(int offset) {
            for (Segment s : segments) {
                if (s.contains(offset)) return s.sourceField();
            }
            return "unknown";
        }

        /**
         * The field a whole span came from, resolved by overlap rather than by its
         * first character.
         *
         * <p>Sentence-level findings routinely start on the structural marker that
         * introduces a field -- {@code "[SPECIFIC:Condition] Used"} -- and markers sit
         * outside every segment by design, so {@link #fieldAt(int)} on the start offset
         * returns {@code "unknown"} for precisely the findings that do have a real field
         * behind them (FR-014).
         */
        public String fieldOverlapping(int startOffset, int endOffset) {
            for (Segment s : segments) {
                if (startOffset < s.endOffset() && endOffset > s.startOffset()) {
                    return s.sourceField();
                }
            }
            return "unknown";
        }
    }

    public AssembledListing assemble(Listing listing) {
        return assemble(listing, null);
    }

    /**
     * @param imageText text extracted from the listing photo by OCR, or null when there
     *                  is no image or extraction was unavailable. When present it is
     *                  appended as its own segment so a finding can be attributed to
     *                  the image rather than to the seller's text (FR-036).
     */
    public AssembledListing assemble(Listing listing, String imageText) {
        StringBuilder sb = new StringBuilder();
        List<Segment> segments = new ArrayList<>();

        appendSection(sb, segments, "[TITLE] ", listing.title(), "title");
        appendSection(sb, segments, "[DESCRIPTION] ", listing.description(), "description");

        for (Map.Entry<String, String> e : listing.itemSpecifics().entrySet()) {
            appendSection(sb, segments,
                    "[SPECIFIC:" + e.getKey() + "] ", e.getValue(),
                    "specific:" + e.getKey());
        }

        if (imageText != null && !imageText.isBlank()) {
            appendSection(sb, segments, "[IMAGE_TEXT] ", imageText, "image");
        }

        return new AssembledListing(sb.toString(), List.copyOf(segments));
    }

    private void appendSection(StringBuilder sb, List<Segment> segments,
                               String marker, String value, String fieldName) {
        if (value == null || value.isBlank()) return;

        sb.append(marker);
        int start = sb.length();      // offsets point at content, not at the marker
        sb.append(value);
        int end = sb.length();
        sb.append('\n');

        segments.add(new Segment(fieldName, start, end));
    }
}
