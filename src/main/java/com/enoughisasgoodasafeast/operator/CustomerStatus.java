package com.enoughisasgoodasafeast.operator;

/**
 * Denotes the state of a given Customer.
 * The Brbl runtime (Operator and Blaster) will only interact with Customers that are ACTIVE.
 */
public enum CustomerStatus {
    REQUESTED,
    ACTIVE,
    SUSPENDED,
    LAPSED
}
