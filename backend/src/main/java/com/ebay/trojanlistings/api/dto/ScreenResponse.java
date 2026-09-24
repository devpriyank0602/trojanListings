package com.ebay.trojanlistings.api.dto;

import com.ebay.trojanlistings.detector.Detector;
import com.ebay.trojanlistings.detector.Finding;

import java.util.List;

/**
 * The screening verdict, shaped exactly as contracts/rest-api.md specifies.
 *
 * <p>{@code findings} is the "on what basis" the UI renders, ordered by score
 * descending. Empty findings means CLEAN.
 */
public record ScreenResponse(
        String verdict,
        double confidence,
        /** The exact text screened; finding offsets index into this. */
        String assembledText,
        String mode,
        String imageScreened,
        String imageNote,
        String extractedImageText,
        long elapsedMs,
        List<Finding> findings
) {
    public static ScreenResponse from(Detector.Verdict v) {
        return new ScreenResponse(
                v.verdict(),
                v.confidence(),
                v.assembledText(),
                v.mode().name(),
                v.imageScreened().name(),
                v.imageNote(),
                v.extractedImageText(),
                v.elapsedMs(),
                v.findings());
    }
}
