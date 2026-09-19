package com.enoughisasgoodasafeast;

/**
 * Signals a fundamental issue with an essential part of our configuration.
 * For example, the lack of any active Routes which would render the system unable to send messages.
 */
public class CriticalConfigException extends Exception {

    public CriticalConfigException(String message) {
        super(message);
    }

}
