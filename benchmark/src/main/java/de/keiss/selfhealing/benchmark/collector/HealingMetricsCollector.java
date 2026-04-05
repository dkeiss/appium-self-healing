package de.keiss.selfhealing.benchmark.collector;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import de.keiss.selfhealing.benchmark.config.BenchmarkProperties;
import de.keiss.selfhealing.benchmark.model.BenchmarkRun;
import de.keiss.selfhealing.benchmark.model.BenchmarkRun.BenchmarkSummary;
import de.keiss.selfhealing.benchmark.model.BenchmarkRun.HealingMetrics;
import de.keiss.selfhealing.core.model.HealingEvent;
import de.keiss.selfhealing.core.model.HealingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Listens for {@link HealingEvent}s emitted by the orchestrator and accumulates them into a {@link BenchmarkRun}
 * fragment that is written to disk when the Spring context closes (typically the end of a Cucumber test run).
 *
 * <p>
 * One collector instance per test run. The fragment path follows the pattern
 * {@code <outputDir>/<provider>-<trackName>.json} so multiple provider runs can be aggregated later by
 * {@code BenchmarkRunner}.
 *
 * <p>
 * Activation is gated by {@code benchmark.enabled=true} — standard test runs (no benchmark configuration) pay no
 * overhead and produce no fragments.
 */
@Slf4j
public class HealingMetricsCollector {

    private final BenchmarkProperties properties;
    private final ObjectMapper jsonMapper;
    private final List<HealingEvent> events = new CopyOnWriteArrayList<>();
    private final Instant startTime = Instant.now();

    public HealingMetricsCollector(BenchmarkProperties properties) {
        this.properties = properties;
        this.jsonMapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        log.info("HealingMetricsCollector active — track='{}' difficulty={} outputDir={}", properties.trackName(),
                properties.difficulty(), properties.outputDir());
    }

    @EventListener
    public void onHealingEvent(HealingEvent event) {
        events.add(event);
        log.debug("Collected healing event #{}: {} [{}] success={}", events.size(), event.originalLocator(),
                event.category(), event.healingResult().success());
    }

    @EventListener(ContextClosedEvent.class)
    public void flushOnContextClose() {
        Path fragment = flush();
        if (fragment != null) {
            log.info("Benchmark fragment persisted: {}", fragment.toAbsolutePath());
        }
    }

    /**
     * Writes the accumulated events as a {@link BenchmarkRun} JSON fragment. Returns the output path, or {@code null}
     * if nothing was written (no events or IO failure). Public for direct invocation from tests.
     */
    public Path flush() {
        if (events.isEmpty()) {
            log.warn("No healing events collected — skipping fragment write for track '{}'", properties.trackName());
            return null;
        }

        String provider = events.stream().map(HealingEvent::llmProvider).filter(Objects::nonNull).findFirst()
                .orElse("unknown");

        List<HealingMetrics> metrics = events.stream().map(HealingMetricsCollector::toMetrics).toList();

        Instant endTime = Instant.now();
        BenchmarkSummary summary = BenchmarkSummary.fromMetrics(metrics, provider);

        BenchmarkRun run = new BenchmarkRun(provider, properties.trackName(), properties.difficulty(), startTime,
                endTime, Duration.between(startTime, endTime).toMillis(), metrics, summary);

        try {
            Path outDir = Path.of(properties.outputDir());
            Files.createDirectories(outDir);
            Path outFile = outDir.resolve(buildFilename(provider, properties.trackName()));
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(outFile.toFile(), run);
            log.info("Wrote benchmark fragment [{} events] to {}", metrics.size(), outFile.toAbsolutePath());
            return outFile;
        } catch (IOException e) {
            log.error("Failed to write benchmark fragment for provider={} track={}", provider, properties.trackName(),
                    e);
            return null;
        }
    }

    /**
     * @return immutable snapshot of currently collected events (for tests/diagnostics)
     */
    public List<HealingEvent> collectedEvents() {
        return List.copyOf(events);
    }

    private static HealingMetrics toMetrics(HealingEvent event) {
        HealingResult result = event.healingResult();
        return new HealingMetrics(event.scenarioName(), event.originalLocator(), result.healedLocatorExpression(),
                result.success(), result.healingDurationMs(), result.tokensUsed(), result.explanation());
    }

    private static String buildFilename(String provider, String trackName) {
        String safeTrack = trackName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (safeTrack.isBlank())
            safeTrack = "unknown";
        return provider + "-" + safeTrack + ".json";
    }
}
