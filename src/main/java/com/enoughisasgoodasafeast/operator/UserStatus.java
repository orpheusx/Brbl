package com.enoughisasgoodasafeast.operator;

/**
 * Denotes the state of a given User on a given Platform.
 * The Brbl runtime (Operator and Blaster) will only deliver messages to Users that are IN (meaning "opted in.")
 */
public enum UserStatus {
    KNOWN,
    IN,
    OUT
}
