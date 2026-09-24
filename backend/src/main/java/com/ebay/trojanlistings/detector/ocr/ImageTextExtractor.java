package com.ebay.trojanlistings.detector.ocr;

import com.ebay.trojanlistings.api.Capability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Extracts text rendered into a listing photo, so in-image attacks can be screened
 * with the same detectors as text (FR-035).
 *
 * <p><b>Why the CLI rather than the Tess4J JNA bindings.</b> research.md §3 chose
 * Tess4J and flagged macOS arm64 native extraction as known friction, with the
 * Tesseract CLI as the documented fallback. On this machine the friction was real:
 * Tess4J 5.11.0 ships {@code darwin-x86-64/libtesseract.dylib} only, so on Apple
 * Silicon it fails with {@code UnsatisfiedLinkError} and then
 * {@code NoClassDefFoundError: TessAPI}. The fallback is now the primary path. It
 * costs one process spawn per image — irrelevant at one listing per review — and buys
 * a binary that actually exists on both architectures.
 *
 * <p>Runs entirely locally with no network call. <b>Degrades rather than failing</b>:
 * if the binary is absent or extraction yields nothing readable, the text verdict is
 * still returned and the image is reported {@code NOT_SCREENED} — explicitly
 * <em>not</em> clean (FR-037). Silently treating an unreadable image as safe is the
 * exact failure the attack relies on.
 *
 * <p>Preprocessing is not optional: the corpus deliberately includes low-contrast
 * overlay text (FR-032), which Tesseract misses without a grayscale-plus-contrast
 * pass. {@code --psm 11} (sparse text) is used because overlaid instructions are not
 * page-structured prose.
 */
@Component
public class ImageTextExtractor implements Capability {

    private static final Logger log = LoggerFactory.getLogger(ImageTextExtractor.class);

    /** Common install locations, so a PATH that excludes Homebrew still works. */
    private static final List<String> CANDIDATE_BINARIES = List.of(
            "tesseract", "/opt/homebrew/bin/tesseract", "/usr/local/bin/tesseract",
            "/usr/bin/tesseract");

    private final Path tessdata;
    private final int timeoutSeconds;

    private volatile String binary;
    private volatile boolean available;
    private volatile String unavailableReason;

    public ImageTextExtractor(@Value("${trojan.tessdata.path}") String tessdataPath,
                              @Value("${trojan.ocr.timeout-seconds:20}") int timeoutSeconds) {
        this.tessdata = Path.of(tessdataPath).toAbsolutePath().normalize();
        this.timeoutSeconds = timeoutSeconds;
        probe();
    }

    /** Result of an extraction attempt. {@code screened=false} means "could not read". */
    public record Extraction(String text, boolean screened, String note) {
        public static Extraction notScreened(String note) { return new Extraction(null, false, note); }
        public static Extraction of(String text) { return new Extraction(text, true, null); }
    }

    @Override public String healthKey() { return "ocrAvailable"; }
    @Override public boolean available() { return available; }
    public String unavailableReason() { return unavailableReason; }

    /**
     * Probes by actually running the binary, not merely by constructing an object.
     *
     * <p>An earlier version reported {@code ocrAvailable: true} because the engine
     * object constructed fine while the natives were missing — health lying about a
     * capability is worse than the capability being absent, because it removes the
     * signal that would have prompted a fix.
     */
    private void probe() {
        if (!Files.isRegularFile(tessdata.resolve("eng.traineddata"))) {
            unavailableReason = "eng.traineddata not found under " + tessdata
                    + " -- run scripts/download-tessdata.sh";
            log.warn("OCR unavailable: {}. Images will be reported NOT_SCREENED.", unavailableReason);
            return;
        }

        for (String candidate : CANDIDATE_BINARIES) {
            try {
                Process p = new ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true).start();
                if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    binary = candidate;
                    available = true;
                    log.info("OCR available via '{}', tessdata at {}", candidate, tessdata);
                    return;
                }
            } catch (Exception ignored) {
                // try the next candidate
            }
        }

        unavailableReason = "tesseract binary not found (tried " + CANDIDATE_BINARIES
                + "). Install with: brew install tesseract";
        log.warn("OCR unavailable: {}. Images will be reported NOT_SCREENED.", unavailableReason);
    }

    public Extraction extract(Path imageFile) {
        if (!available) return Extraction.notScreened(unavailableReason);
        try {
            return extract(Files.readAllBytes(imageFile));
        } catch (IOException e) {
            return Extraction.notScreened("could not read image file: " + e.getMessage());
        }
    }

    public Extraction extract(byte[] imageBytes) {
        if (!available) return Extraction.notScreened(unavailableReason);

        Path input = null;
        Path outputBase = null;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) return Extraction.notScreened("image could not be decoded");

            input = Files.createTempFile("trojan-ocr-", ".png");
            outputBase = Files.createTempFile("trojan-ocr-out-", "");
            ImageIO.write(enhance(image), "png", input.toFile());

            String text = run(input, outputBase);
            // No text found is a legitimate SCREENED outcome -- most product photos
            // genuinely contain none, and those are benign controls.
            return Extraction.of(text == null ? "" : text.trim());

        } catch (Exception e) {
            log.warn("OCR failed on an image: {}", e.toString());
            return Extraction.notScreened("extraction failed: " + e.getClass().getSimpleName());
        } finally {
            deleteQuietly(input);
            deleteQuietly(outputBase);
            if (outputBase != null) deleteQuietly(Path.of(outputBase + ".txt"));
        }
    }

    private String run(Path input, Path outputBase) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
                binary, input.toString(), outputBase.toString(),
                "--tessdata-dir", tessdata.toString(),
                "-l", "eng",
                "--oem", "1",     // LSTM
                "--psm", "11"));  // sparse text

        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("tesseract timed out after " + timeoutSeconds + "s");
        }
        if (p.exitValue() != 0) {
            throw new IOException("tesseract exited " + p.exitValue() + ": " + stdout.trim());
        }

        Path produced = Path.of(outputBase + ".txt");
        return Files.isRegularFile(produced) ? Files.readString(produced, StandardCharsets.UTF_8) : "";
    }

    /**
     * Grayscale plus contrast stretch. Without this the deliberately faint overlay text
     * in the low-contrast fixtures is invisible to Tesseract.
     *
     * <p>scale 2.2, offset -110 pushes light greys apart without crushing black text.
     */
    private static BufferedImage enhance(BufferedImage source) {
        BufferedImage gray = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        gray.getGraphics().drawImage(source, 0, 0, null);

        RescaleOp contrast = new RescaleOp(2.2f, -110f, null);
        return contrast.filter(gray, null);
    }

    private static void deleteQuietly(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
    }
}
