package com.ebay.trojanlistings.corpus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Loads every fixture JSON under {@code corpus/fixtures/}.
 *
 * <p>Fails fast and names the offending file on malformed JSON. A silently skipped
 * fixture would quietly change the denominator of every rate we report, which is worse
 * than a startup failure.
 */
@Component
public class FixtureLoader {

    private static final Logger log = LoggerFactory.getLogger(FixtureLoader.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path corpusRoot;
    private volatile List<Fixture> cache;

    public FixtureLoader(@Value("${trojan.corpus.path}") String corpusPath) {
        this.corpusRoot = Path.of(corpusPath).toAbsolutePath().normalize();
    }

    /** All fixtures, sorted by id so ordering is stable across runs. */
    public List<Fixture> load() {
        List<Fixture> local = cache;
        if (local != null) return local;

        Path dir = corpusRoot.resolve("fixtures");
        if (!Files.isDirectory(dir)) {
            log.warn("Corpus directory not found at {} -- running with an empty corpus", dir);
            return cache = List.of();
        }

        try (Stream<Path> files = Files.list(dir)) {
            local = files
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .map(this::read)
                    .sorted(Comparator.comparing(Fixture::id))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list corpus directory " + dir, e);
        }

        log.info("Loaded {} fixtures from {}", local.size(), dir);
        return cache = local;
    }

    public List<Fixture> hostile() {
        return load().stream().filter(Fixture::hostile).toList();
    }

    public List<Fixture> benignControls() {
        return load().stream().filter(f -> !f.hostile()).toList();
    }

    public Optional<Fixture> byId(String id) {
        return load().stream().filter(f -> f.id().equals(id)).findFirst();
    }

    /** Absolute path to a fixture's image, for OCR and for the vision agent. */
    public Path imagePath(Fixture fixture) {
        String rel = fixture.listing().imagePath();
        return rel == null ? null : corpusRoot.resolve(rel).normalize();
    }

    private Fixture read(Path file) {
        try {
            return mapper.readValue(Files.readString(file), Fixture.class);
        } catch (Exception e) {
            // Name the file. "Cannot deserialize" with no filename is useless when 35
            // fixtures are being authored in parallel.
            throw new IllegalStateException(
                    "Invalid fixture " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }
}
