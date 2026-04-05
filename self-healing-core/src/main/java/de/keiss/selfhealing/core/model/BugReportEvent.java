package de.keiss.selfhealing.core.model;

/**
 * Published by AppBugReporter when a bug in the app under test is detected. Consumers may react by creating issues,
 * notifying engineers, or failing CI.
 */
public record BugReportEvent(BugReport report) {
}
