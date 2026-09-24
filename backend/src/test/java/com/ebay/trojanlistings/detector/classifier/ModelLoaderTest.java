package com.ebay.trojanlistings.detector.classifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FR-030: a classifier that cannot load is a supported state, never a startup failure.
 *
 * <p>These cover the two ways it fails and assert they are reported *differently*. A
 * partial download used to surface as a raw ORT parse error, which reads like a corrupt
 * or wrong model rather than "the transfer was interrupted, run the script again" --
 * exactly the wrong diagnosis to be forming on demo morning.
 */
class ModelLoaderTest {

    @TempDir
    Path modelDir;

    @Test
    void absentAssetsAreReportedAsMissingAndNameTheScript() {
        ModelLoader loader = new ModelLoader(modelDir.toString());

        assertFalse(loader.available(), "no assets on disk -- classifier must be absent");
        assertNotNull(loader.unavailableReason());
        assertTrue(loader.unavailableReason().contains("not found"),
                "expected a 'not found' reason, got: " + loader.unavailableReason());
        assertTrue(loader.unavailableReason().contains("download-model.sh"),
                "the reason must name the remedy, got: " + loader.unavailableReason());
    }

    @Test
    void truncatedModelIsReportedAsPartialRatherThanAsAnOrtParseError() throws IOException {
        // Both files present, so the existence check passes -- but the model is a stub,
        // which is what an interrupted 256 MB transfer leaves behind.
        Files.write(modelDir.resolve("model_quantized.onnx"), new byte[1024]);
        Files.writeString(modelDir.resolve("tokenizer.json"), "{}".repeat(100_000));

        ModelLoader loader = new ModelLoader(modelDir.toString());

        assertFalse(loader.available(), "a truncated model must not be treated as loaded");
        assertNotNull(loader.unavailableReason());
        assertTrue(loader.unavailableReason().contains("partial download"),
                "expected a 'partial download' reason, got: " + loader.unavailableReason());
        assertTrue(loader.unavailableReason().contains("1024"),
                "the reason should name the actual size, got: " + loader.unavailableReason());
    }

    @Test
    void truncatedTokenizerIsAlsoCaught() throws IOException {
        Files.write(modelDir.resolve("model_quantized.onnx"), new byte[300_000_000]);
        Files.writeString(modelDir.resolve("tokenizer.json"), "{}");

        ModelLoader loader = new ModelLoader(modelDir.toString());

        assertFalse(loader.available());
        assertTrue(loader.unavailableReason().contains("tokenizer.json"),
                "expected the tokenizer to be named, got: " + loader.unavailableReason());
    }

    @Test
    void constructionNeverThrows() {
        // The whole point of FR-030: the service starts regardless.
        assertDoesNotThrow(() -> new ModelLoader(modelDir.resolve("nope").toString()));
    }
}
