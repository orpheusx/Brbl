package com.enoughisasgoodasafeast.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class Functions {
    private static final Logger LOG = LoggerFactory.getLogger(Functions.class);

    public static void waitSeconds(int num) {
        try {
            Thread.sleep(Duration.ofSeconds(num));
        } catch (InterruptedException e) {
            LOG.error("waitSeconds was interrupted", e);
        }
    }

}
