package com.enoughisasgoodasafeast.operator;

/**
 * Denotes the state of a given Script.
 * The Brbl runtime (Operator and Blaster) will only process instances of Script that are in
 * the STAGE (if the current environment is stage) or PROD (if the current environment is production)
 * states.
 */
public enum ScriptStatus {
    DRAFT,
    TEST,
    VALID,
    STAGE,
    PROD,
    INACTIVE
}
