package com.ebay.trojanlistings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Trojan Listings -- listing-borne prompt injection measurement and defence.
 *
 * <p>Runs entirely locally. Makes no call to any internal organisational service
 * (FR-024); the only outbound call anywhere in the system is to an external agent
 * during a measurement run, and every user-facing capability reads persisted results
 * instead (FR-040).
 *
 * <p>All corpus content is synthetic adversarial research material. No code path
 * publishes to any live marketplace surface.
 */
@SpringBootApplication
public class TrojanListingsApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrojanListingsApplication.class, args);
    }
}
