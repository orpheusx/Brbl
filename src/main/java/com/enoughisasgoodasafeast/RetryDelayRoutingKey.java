package com.enoughisasgoodasafeast;

/**
 * Defines routing keys used to denote the different retry delay intervals.
 */
public enum RetryDelayRoutingKey {
    DELAY_5S (5_000),
    DELAY_10S(10_000),
    DELAY_30S(30_000),
    DELAY_1m (60_000),
    DELAY_2m (120_000),
    DELAY_5m (300_000),
    DELAY_10m(600_000),
    DELAY_30m(1_800_000);

    private final long delayMs;

    RetryDelayRoutingKey(long delayMs) {
        this.delayMs = delayMs;
    }

    public long delayMs() {
        return this.delayMs;
    }
}
