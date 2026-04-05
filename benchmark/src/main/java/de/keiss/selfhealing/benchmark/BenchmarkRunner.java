package de.keiss.selfhealing.benchmark;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import de.keiss.selfhealing.benchmark.model.BenchmarkReport;
import de.keiss.selfhealing.benchmark.model.BenchmarkRun;
import de.keiss.selfhealing.benchmark.model.TestTrack;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Aggregates benchmark run fragments written by {@code HealingMetricsCollector} during per-provider test executions
 * into a single {@link BenchmarkReport}.
 *
 * <p>
 * Expected workflow:
 * <ol>
 * <li>For each LLM provider, run {@code :integration-tests:test} with
 * {@code -Dbenchmark.enabled=true -Dbenchmark.track-name=...} and {@code SPRING_PROFILES_ACTIVE=<provider>}. Each run
 * produces {@code build/reports/benchmark/<provider>-<track>.json}.</li>
 * <li>Run {@code :benchmark:bootRun} (or {@code :benchmarkAll} Gradle task) to load all fragments, aggregate, and write
 * the final comparison report.</li>
 * </ol>
 *
 * <p>
 * Configurable via system properties:
 * <ul>
 * <li>{@code benchmark.input-dir} — directory containing fragments (default {@code build/reports/benchmark})</li>
 * <li>{@code benchmark.output} — path for the aggregated report (default
 * {@code build/reports/benchmark-report.json})</li>
 * </ul>
 */
@SpringBootApplication
@Slf4j
public class BenchmarkRunner implements CommandLineRunner {

    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public BenchmarkRunner() {
        this.yamlMapper = YAMLMapper.builder().build();
        this.jsonMapper = JsonMapper.builder().build();
    }

    public static void main(String[] args) {
        SpringApplication.run(BenchmarkRunner.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("=== LLM Self-Healing Benchmark — Aggregation Mode ===");

        List<TestTrack> tracks = loadTestTracks();
        log.info("Loaded {} test track definitions (for metadata):", tracks.size());
        tracks.forEach(t -> log.info("  - {} ({})", t.name(), t.difficulty()));

        Path inputDir = Path.of(System.getProperty("benchmark.input-dir", "build/reports/benchmark"));
        List<BenchmarkRun> runs = loadRunFragments(inputDir);

        if (runs.isEmpty()) {
            log.warn("No benchmark fragments found in {} — run provider test tasks first to produce fragments.",
                    inputDir.toAbsolutePath());
            log.warn("Expected filenames: <provider>-<track>.json (e.g. anthropic-einfache-locator-anderungen.json)");
            return;
        }

        log.info("Loaded {} benchmark run fragment(s):", runs.size());
        runs.forEach(r -> log.info("  - {} / {} ({} healings, {} tokens)", r.llmProvider(), r.trackName(),
                r.summary().totalHealingAttempts(), r.summary().totalTokensUsed()));

        BenchmarkReport report = BenchmarkReport.generate(runs);
        log.info(report.toComparisonTable());

        Path outputPath = Path.of(System.getProperty("benchmark.output", "build/reports/benchmark-report.json"));
        Files.createDirectories(outputPath.getParent());
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), report);
        log.info("Aggregated report saved to: {}", outputPath.toAbsolutePath());
    }

    /**
     * Loads test track definitions from {@code classpath:tracks/*.yaml}. Kept for metadata enrichment and UI output;
     * tracks are not executed here anymore.
     */
    List<TestTrack> loadTestTracks() throws IOException {
        List<TestTrack> tracks = new ArrayList<>();
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:tracks/*.yaml");

        for (Resource resource : resources) {
            TestTrack track = yamlMapper.readValue(resource.getInputStream(), TestTrack.class);
            tracks.add(track);
            log.debug("Loaded track: {}", track.name());
        }
        return tracks;
    }

    /**
     * Loads all {@code *.json} files from {@code inputDir} and deserializes them as {@link BenchmarkRun} instances.
     * Non-matching or malformed files are skipped with a warning so a single bad file does not break aggregation.
     */
    List<BenchmarkRun> loadRunFragments(Path inputDir) throws IOException {
        if (!Files.isDirectory(inputDir)) {
            log.warn("Benchmark input directory does not exist: {}", inputDir.toAbsolutePath());
            return List.of();
        }

        List<BenchmarkRun> runs = new ArrayList<>();
        try (Stream<Path> stream = Files.list(inputDir)) {
            List<Path> fragments = stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().equals("benchmark-report.json"))
                    .sorted(Comparator.comparing(Path::getFileName)).toList();

            for (Path fragment : fragments) {
                try {
                    BenchmarkRun run = jsonMapper.readValue(fragment.toFile(), BenchmarkRun.class);
                    runs.add(run);
                } catch (Exception e) {
                    log.warn("Skipping malformed fragment {}: {}", fragment.getFileName(), e.getMessage());
                }
            }
        }
        return runs;
    }
}
