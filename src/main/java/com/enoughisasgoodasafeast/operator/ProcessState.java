package com.enoughisasgoodasafeast.operator;

/**
 * An enum that provides additional state context for the script processing system.
 */
public enum ProcessState {
    OK, // script processing was either successful or can be retried. "Bad" input is expected and OK.
    ERROR, // processing failed and we cannot continue evaluating the script.
}
