package com.ebay.trojanlistings.api;

/**
 * A subsystem that may or may not be available at runtime, reported by
 * {@code GET /api/health}.
 *
 * <p>Both current implementations -- the ONNX classifier and the OCR extractor --
 * are allowed to be unavailable. FR-030 and FR-037 require the service to start and
 * keep screening without them, in a clearly reported degraded mode. Health exposes
 * that state truthfully rather than hiding it behind a green tick.
 */
public interface Capability {

    /** Key used in the health response, e.g. {@code classifierLoaded}. */
    String healthKey();

    /** Whether this subsystem actually loaded. */
    boolean available();
}
