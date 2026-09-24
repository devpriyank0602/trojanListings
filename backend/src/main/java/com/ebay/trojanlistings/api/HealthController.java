package com.ebay.trojanlistings.api;

import com.ebay.trojanlistings.corpus.FixtureLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/health}.
 *
 * <p>{@code classifierLoaded: false} and {@code ocrAvailable: false} are valid healthy
 * states. The service is designed to run degraded rather than fail to start (FR-030),
 * so status stays UP and the caller is told exactly what is missing.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final FixtureLoader fixtures;
    private final List<Capability> capabilities;

    public HealthController(FixtureLoader fixtures, List<Capability> capabilities) {
        this.fixtures = fixtures;
        this.capabilities = capabilities;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");

        // Defaults are declared here so the response shape is stable whether or not
        // the optional subsystems are on the classpath yet.
        body.put("classifierLoaded", false);
        body.put("ocrAvailable", false);
        for (Capability c : capabilities) {
            body.put(c.healthKey(), c.available());
        }

        body.put("corpusSize", fixtures.load().size());
        return body;
    }
}
