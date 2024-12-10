package com.enoughisasgoodasafeast;

import java.util.logging.Logger;
import java.util.logging.LogManager;
import java.io.IOException;

public class LoggingExample {

    private static final Logger LOGGER = Logger.getLogger(LoggingExample.class.getName());

    public static void main(String[] args) {

        try {
            LogManager.getLogManager().readConfiguration(
                    LoggingExample.class.getResourceAsStream("/logging.properties"));
        } catch (IOException e) {
            System.err.println("Could not load logging.properties file");
        }

        LOGGER.info("This is an informational message.");
        LOGGER.finest("fine won't be shown.");
        LOGGER.severe("severe should always be shown.");
        LOGGER.warning("This is a warning message.");
    }
}