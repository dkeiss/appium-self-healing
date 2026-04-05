package de.keiss.selfhealing.core.healing;

import de.keiss.selfhealing.core.config.SelfHealingProperties;
import de.keiss.selfhealing.core.model.EnvironmentReport;
import de.keiss.selfhealing.core.model.EnvironmentReport.ServiceStatus;
import de.keiss.selfhealing.core.model.FailureContext;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for {@code ENVIRONMENT_ISSUE} triage results.
 *
 * Performs HTTP health checks against configured endpoints (backend, Appium server, optional custom probes) and an
 * empty-page-source heuristic. Produces an {@link EnvironmentReport} that tells the orchestrator whether the failure is
 * potentially recoverable (e.g. transient network blip → retry) or terminal (e.g. backend down → abort with
 * diagnostic).
 */
@Slf4j
public class EnvironmentChecker {

    private final SelfHealingProperties.EnvironmentCheck config;
    private final HttpClient httpClient;

    public EnvironmentChecker(SelfHealingProperties.EnvironmentCheck config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.connectTimeoutMs())).build();
    }

    public EnvironmentReport check(FailureContext context) {
        log.info("Running environment health checks...");
        List<ServiceStatus> statuses = new ArrayList<>();

        if (config.backendUrl() != null && !config.backendUrl().isBlank()) {
            statuses.add(probe("backend", config.backendUrl()));
        }
        if (config.appiumUrl() != null && !config.appiumUrl().isBlank()) {
            statuses.add(probe("appium", config.appiumUrl() + "/status"));
        }

        boolean pageSourceEmpty = context.pageSourceXml() == null || context.pageSourceXml().isBlank();
        if (pageSourceEmpty) {
            statuses.add(new ServiceStatus("page-source", "driver.getPageSource()", false, 0,
                    "Page source empty — emulator may be disconnected or app crashed"));
        }

        boolean allReachable = statuses.stream().allMatch(ServiceStatus::reachable);
        boolean recoverable = allReachable && !pageSourceEmpty;
        String summary = buildSummary(statuses, recoverable);

        log.info("Environment check complete: healthy={}, recoverable={}", allReachable, recoverable);
        return new EnvironmentReport(Instant.now(), allReachable, recoverable, statuses, summary);
    }

    /**
     * Blocks for {@link SelfHealingProperties.EnvironmentCheck#retryBackoffMs()} milliseconds before returning — used
     * by the orchestrator between retries when an environment issue looks transient.
     */
    public void waitBeforeRetry() {
        try {
            Thread.sleep(config.retryBackoffMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ServiceStatus probe(String name, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofMillis(config.requestTimeoutMs())).GET().build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 500;
            return new ServiceStatus(name, url, ok, response.statusCode(),
                    ok ? "reachable" : "HTTP " + response.statusCode());
        } catch (Exception e) {
            log.warn("Health check failed for {} at {}: {}", name, url, e.getMessage());
            return new ServiceStatus(name, url, false, 0, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String buildSummary(List<ServiceStatus> statuses, boolean recoverable) {
        if (statuses.isEmpty()) {
            return "No endpoints configured for environment check";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(recoverable ? "Environment appears healthy — failure may be transient. "
                : "Environment issues detected: ");
        for (ServiceStatus s : statuses) {
            sb.append(s.name()).append("=").append(s.reachable() ? "OK" : "DOWN").append("; ");
        }
        return sb.toString().trim();
    }
}
