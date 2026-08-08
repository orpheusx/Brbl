package com.enoughisasgoodasafeast.operator;

/**
 * An enum that provides additional state context for MessageProcessor impls.
 */
public enum ProcessState {
    OK,     // Processing was successful.
    ERROR,  // Processing failed and we cannot continue processing.
    RETRY,  // Processing failed temporarily
    NOOP    // No processing was performed
}
