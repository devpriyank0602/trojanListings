package com.ebay.trojanlistings.api;

import com.ebay.trojanlistings.api.dto.ScreenRequest;
import com.ebay.trojanlistings.api.dto.ScreenResponse;
import com.ebay.trojanlistings.corpus.Fixture;
import com.ebay.trojanlistings.corpus.FixtureLoader;
import com.ebay.trojanlistings.detector.Detector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.ebay.trojanlistings.api.GlobalExceptionHandler.PayloadTooLargeException;
import static com.ebay.trojanlistings.api.GlobalExceptionHandler.UnsupportedMediaTypeException;

/**
 * {@code POST /api/screen} -- the endpoint the UI calls.
 *
 * <p>This is the whole product from a reviewer's point of view: paste a seller
 * listing, get TROJAN or CLEAN plus the specific reason. It never contacts an agent
 * and never leaves the machine.
 */
@RestController
@RequestMapping("/api")
public class ScreeningController {

    private final Detector detector;
    private final FixtureLoader fixtures;
    private final int maxImageBytes;

    public ScreeningController(Detector detector, FixtureLoader fixtures,
                               @Value("${trojan.screening.max-image-bytes:5242880}") int maxImageBytes) {
        this.detector = detector;
        this.fixtures = fixtures;
        this.maxImageBytes = maxImageBytes;
    }

    @PostMapping("/screen")
    public ScreenResponse screen(@RequestBody(required = false) ScreenRequest request) {
        // A wholly absent body is treated as an empty listing, which FR-017 requires
        // to produce a defined verdict rather than a 400.
        ScreenRequest req = request == null
                ? new ScreenRequest(null, null, null, null)
                : request;

        byte[] image = decodeImage(req.imageBase64());
        return ScreenResponse.from(detector.screen(req.toListing(), image));
    }

    /**
     * Corpus fixtures for the UI's sample loader, so the live demo needs no typing and
     * the pasted content is provably what was measured.
     */
    @GetMapping("/samples")
    public Map<String, Object> samples() {
        List<Map<String, Object>> samples = fixtures.load().stream()
                .map(ScreeningController::toSample)
                .toList();
        return Map.of("samples", samples);
    }

    private static Map<String, Object> toSample(Fixture f) {
        return Map.of(
                "id", f.id(),
                "hostile", f.hostile(),
                "technique", f.technique() == null ? "BENIGN" : f.technique().name(),
                "goal", f.goal() == null ? "" : f.goal().name(),
                "note", f.note(),
                "listing", f.listing(),
                "hasImage", f.listing().imagePath() != null);
    }

    private byte[] decodeImage(String base64) {
        if (base64 == null || base64.isBlank()) return null;

        String payload = base64;
        String declaredType = null;

        if (payload.startsWith("data:")) {
            int comma = payload.indexOf(',');
            if (comma < 0) {
                throw new UnsupportedMediaTypeException("Malformed data URI for image");
            }
            declaredType = payload.substring(5, payload.indexOf(';') > 0 ? payload.indexOf(';') : comma);
            payload = payload.substring(comma + 1);
        }

        if (declaredType != null
                && !declaredType.equals("image/png")
                && !declaredType.equals("image/jpeg")
                && !declaredType.equals("image/jpg")) {
            throw new UnsupportedMediaTypeException("PNG or JPEG only, got " + declaredType);
        }

        // Check the encoded length before decoding, so an oversized payload is
        // rejected without allocating it.
        if ((long) payload.length() * 3 / 4 > maxImageBytes) {
            throw new PayloadTooLargeException(
                    "Image exceeds the " + (maxImageBytes / 1024 / 1024) + " MB limit");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new UnsupportedMediaTypeException("Image is not valid base64");
        }

        if (bytes.length > maxImageBytes) {
            throw new PayloadTooLargeException(
                    "Image exceeds the " + (maxImageBytes / 1024 / 1024) + " MB limit");
        }
        if (!isPngOrJpeg(bytes)) {
            // Trust the bytes, not the declared type -- the magic number is the truth.
            throw new UnsupportedMediaTypeException("PNG or JPEG only");
        }
        return bytes;
    }

    private static boolean isPngOrJpeg(byte[] b) {
        if (b.length < 4) return false;
        boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean jpeg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
        return png || jpeg;
    }
}
