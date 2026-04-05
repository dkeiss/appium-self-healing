package de.keiss.selfhealing.core.model;

import java.time.Instant;

/**
 * Published after a self-healing auto-fix PR has been created on GitHub. Downstream listeners (CI notification, test
 * reports) can react to this event.
 */
public record PullRequestEvent(String prUrl, String branchName, String pageObjectClassName, String commitMessage,
        Instant createdAt) {
}
