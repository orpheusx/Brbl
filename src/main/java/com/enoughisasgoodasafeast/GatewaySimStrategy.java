package com.enoughisasgoodasafeast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewaySimStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(GatewaySimStrategy.class);

    private final String ordinal;
    private final int targetCount;

    private int counter;

    // Create inner classes for different cases?

    public GatewaySimStrategy(int targetCount) {
        this.targetCount = targetCount;
        ordinal = ordinal(targetCount);
    }

    /**
     * Reject the first message we see.
     *
     * @param message
     * @return
     */
    public boolean canAccept(String message) {
        counter++;
        if (counter == targetCount) {
            LOG.info("Refusing {} message.", ordinal);
            return false;
        } else {
            return true;
        }
    }

    // Because we're that guy...
    private String ordinal(int num) {
        return num + switch (num) {
            case 1 ->  "st";
            case 2 ->  "nd";
            case 3 ->  "rd";
            default -> "th";
        };
    }
}
