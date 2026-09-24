package com.ebay.trojanlistings.detector.classifier;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.onnxruntime.*;
import com.ebay.trojanlistings.detector.ConcealmentTechnique;
import com.ebay.trojanlistings.detector.Finding;
import com.ebay.trojanlistings.detector.ListingAssembler.AssembledListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores each sentence of a listing for injection using the in-process ONNX classifier.
 *
 * <p>Two hazards inherited with the ONNX-Runtime-in-Java pattern, both handled here
 * because both are known to bite rather than hypothetical:
 *
 * <ol>
 *   <li><b>Native memory leak.</b> ONNX Runtime 1.16+ returns {@link OnnxValue}
 *       results whose native memory is <em>not</em> freed by reading them. Every
 *       inference below is wrapped in try-with-resources, including the input tensors.
 *       LIVERANK-259 is a production JVM crash from exactly this omission.</li>
 *   <li><b>Thread safety.</b> ORT sessions are not safe for concurrent use. The
 *       scoring method is {@code synchronized}. At this scale -- one reviewer, one
 *       listing at a time -- a lock is simpler and safer than a session pool.</li>
 * </ol>
 */
@Component
public class OnnxInjectionClassifier {

    private static final Logger log = LoggerFactory.getLogger(OnnxInjectionClassifier.class);

    private final ModelLoader models;
    private final SentenceSplitter splitter;
    private final double threshold;
    private final int maxSentenceChars;

    public OnnxInjectionClassifier(ModelLoader models, SentenceSplitter splitter,
                                   @Value("${trojan.classifier.threshold:0.5}") double threshold,
                                   @Value("${trojan.classifier.max-sentence-chars:2000}") int maxSentenceChars) {
        this.models = models;
        this.splitter = splitter;
        this.threshold = threshold;
        this.maxSentenceChars = maxSentenceChars;
    }

    public boolean available() { return models.available(); }

    /**
     * @param normalisedText the listing after structural normalisation -- zero-width
     *                       characters stripped, homoglyphs folded to Latin, spaced
     *                       letters collapsed. Scoring the raw text would hand the
     *                       classifier the mangled tokens the attacker intended.
     */
    public List<Finding> detect(AssembledListing assembled, String normalisedText) {
        if (!available()) return List.of();

        List<Finding> findings = new ArrayList<>();
        for (SentenceSplitter.Sentence sentence : splitter.split(normalisedText)) {
            double score;
            try {
                score = scoreSentence(sentence.text());
            } catch (Exception e) {
                // One unscoreable sentence must not fail the whole verdict. The
                // structural and pattern layers still stand.
                log.warn("Classifier failed on a sentence ({}); continuing", e.toString());
                continue;
            }
            if (score < threshold) continue;

            // Offsets index the normalised text. Length is preserved by every
            // normaliser except the zero-width stripper, so map back conservatively
            // by locating the sentence in the original.
            int start = assembled.text().indexOf(sentence.text());
            int end = start >= 0 ? start + sentence.text().length() : sentence.endOffset();
            if (start < 0) start = sentence.startOffset();

            findings.add(new Finding(
                    Finding.Layer.CLASSIFIER,
                    ConcealmentTechnique.NONE,
                    assembled.fieldAt(start),
                    sentence.text(), start, end,
                    sentence.text(),
                    round(score),
                    "A prompt-injection classifier scored this sentence " + round(score)
                            + " (threshold " + threshold + "). It reads as an instruction "
                            + "to a model rather than a description for a buyer."));
        }
        return findings;
    }

    /** @return probability that this sentence is an injection, in [0,1] */
    synchronized double scoreSentence(String sentence) throws OrtException {
        String input = sentence.length() > maxSentenceChars
                ? sentence.substring(0, maxSentenceChars)
                : sentence;

        Encoding encoding = models.tokenizer().encode(input);
        long[] ids = encoding.getIds();
        long[] mask = encoding.getAttentionMask();

        OrtEnvironment env = models.environment();
        OrtSession session = models.session();
        Set<String> expected = session.getInputNames();

        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            long[] shape = {1, ids.length};
            inputs.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape));
            if (expected.contains("attention_mask")) {
                inputs.put("attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape));
            }
            if (expected.contains("token_type_ids")) {
                // Some exports require it even when the tokenizer does not emit one.
                inputs.put("token_type_ids",
                        OnnxTensor.createTensor(env, LongBuffer.wrap(new long[ids.length]), shape));
            }

            // try-with-resources on the Result is what prevents the native leak.
            try (OrtSession.Result result = session.run(inputs)) {
                float[][] logits = (float[][]) result.get(0).getValue();
                return injectionProbability(logits[0]);
            }
        } finally {
            // Input tensors hold native memory too, and are not freed by run().
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    /**
     * Softmax over the two-class head; index 1 is the injection class, matching the
     * ProtectAI/Horizon-Labs convention (0 = benign, 1 = injection).
     */
    private static double injectionProbability(float[] logits) {
        if (logits.length == 1) {
            return 1.0 / (1.0 + Math.exp(-logits[0]));   // single-logit head
        }
        double max = Math.max(logits[0], logits[1]);
        double e0 = Math.exp(logits[0] - max);
        double e1 = Math.exp(logits[1] - max);
        return e1 / (e0 + e1);
    }

    private static double round(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
}
