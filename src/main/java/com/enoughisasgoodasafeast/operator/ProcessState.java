package com.enoughisasgoodasafeast.operator;

/**
 * An enum that provides additional state context for the script processing system.
 */
public enum ProcessState {
    OK,     // script processing was successful.
    ERROR,  // processing failed and we cannot continue evaluating the script.
    RETRY,  // processing failed temporarily
    NOOP    // no processing was performed
}
