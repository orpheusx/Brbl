package com.enoughisasgoodasafeast;

/**
 * Defines routing keys used to denote the different retry delay intervals.
 * Exponential increases in interval is too much but maybe doubling isn't?
 * Remember that the total wait time is the sum of all previous delays.
 */
public enum RetryDelayRoutingKey {
    DELAY_5S ("_5s",    5_000),    // PT5S
    DELAY_10S("_10s",   10_000),   // PT15S
    DELAY_30S("_30s",   30_000),   // PT30S
    DELAY_1M ("_1m",    60_000),   // PT1M
    DELAY_2M ("_2m",    120_000),  // PT2M
    DELAY_5M ("_5m",    300_000),  // PT5M
    DELAY_10M("_10m",   600_000),  // PT10M,
    DELAY_20M("_20m",   1_200_000);// PT20M

    private final String suffix;
    private final long delayMs;

    RetryDelayRoutingKey(String suffix, long delayMs) {
        this.suffix = suffix;
        this.delayMs = delayMs;
    }

    public  String suffix() {
        return suffix;
    }

    public long delayMs() {
        return this.delayMs;
    }

}
