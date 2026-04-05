package de.keiss.selfhealing.core.model;

import java.time.Instant;
import java.util.List;

/**
 * Result of an environment health check — produced by EnvironmentChecker when triage classifies a failure as
 * ENVIRONMENT_ISSUE.
 *
 * If {@link #recoverable} is true, the orchestrator may retry after waiting. Otherwise the failure is terminal and
 * needs human intervention.
 */
public record EnvironmentReport(Instant checkedAt, boolean healthy, boolean recoverable, List<ServiceStatus> services,
        String summary) {

    public record ServiceStatus(String name, String endpoint, boolean reachable, int statusCode, String detail) {
    }
}
