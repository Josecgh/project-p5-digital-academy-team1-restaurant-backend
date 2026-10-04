package dev.team1.automation;

import java.time.Instant;

public record CloudAutomationStatus(
    boolean online,
    Instant lastSyncAt,
    Instant lastAttemptAt,
    String lastResult,
    String lastError
) {}
