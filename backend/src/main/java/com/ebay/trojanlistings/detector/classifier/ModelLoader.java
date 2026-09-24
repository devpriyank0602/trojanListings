package com.ebay.trojanlistings.detector.classifier;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.ebay.trojanlistings.api.Capability;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the ONNX prompt-injection classifier once at application startup and keeps the
 * session for the lifetime of the process.
 *
 * <p>This follows the established internal pattern for in-process inference
 * (nsfw-classifier-server, cpcguidesvc, HomeSplice): resolve artifacts at startup,
 * create one ORT session, reuse it per request. Never load per request.
 *
 * <p><b>Failure is a supported state.</b> If the model is missing or will not load, the
 * service still starts and screening continues with the structural and pattern layers
 * in clearly-reported degraded mode (FR-030). A missing model file on demo morning
 * must not be the difference between a working demo and a stack trace.
 *
 * <p>Model: Horizon-Labs/prompt-injection-guard-small, Apache-2.0, int8 quantized.
 * Chosen because it is trained on documents with planted injections rather than on
 * user-typed prompts -- see research.md 1.1.
 */
@Component
public class ModelLoader implements Capability {

    private static final Logger log = LoggerFactory.getLogger(ModelLoader.class);

    private final Path modelDir;
    private OrtEnvironment environment;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    private String unavailableReason;

    public ModelLoader(@Value("${trojan.model.path}") String modelPath) {
        this.modelDir = Path.of(modelPath).toAbsolutePath().normalize();
        load();
    }

    private void load() {
        Path model = modelDir.resolve("model_quantized.onnx");
        Path tokenizerJson = modelDir.resolve("tokenizer.json");

        if (!Files.isRegularFile(model) || !Files.isRegularFile(tokenizerJson)) {
            unavailableReason = "model assets not found under " + modelDir
                    + " -- run scripts/download-model.sh";
            log.warn("Classifier unavailable: {}. Screening continues in degraded mode "
                    + "(structural + pattern layers).", unavailableReason);
            return;
        }

        try {
            long started = System.currentTimeMillis();
            environment = OrtEnvironment.getEnvironment();
            session = environment.createSession(model.toString(), new OrtSession.SessionOptions());
            tokenizer = HuggingFaceTokenizer.newInstance(tokenizerJson);
            log.info("Classifier loaded from {} in {} ms", modelDir,
                    System.currentTimeMillis() - started);
        } catch (OrtException | RuntimeException e) {
            unavailableReason = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("Classifier failed to load ({}). Screening continues in degraded mode.",
                    unavailableReason);
            closeQuietly();
        } catch (java.io.IOException e) {
            unavailableReason = "tokenizer load failed: " + e.getMessage();
            log.warn("Classifier failed to load ({}). Screening continues in degraded mode.",
                    unavailableReason);
            closeQuietly();
        }
    }

    @Override public String healthKey() { return "classifierLoaded"; }
    @Override public boolean available() { return session != null && tokenizer != null; }

    public String unavailableReason() { return unavailableReason; }
    public OrtEnvironment environment() { return environment; }
    public OrtSession session() { return session; }
    public HuggingFaceTokenizer tokenizer() { return tokenizer; }

    @PreDestroy
    public void close() { closeQuietly(); }

    private void closeQuietly() {
        try { if (session != null) session.close(); } catch (Exception ignored) { }
        try { if (tokenizer != null) tokenizer.close(); } catch (Exception ignored) { }
        session = null;
        tokenizer = null;
    }
}
