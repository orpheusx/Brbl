package com.enoughisasgoodasafeast.operator;

/**
 * An enumeration used to define the type of logic applied to the processing of a message.
 */
public enum NodeType {
    PRESENT_MULTI(false), // 3
    PROCESS_MULTI(true),  // 4
    END_OF_CHAT(false),   // 5
    REQUEST_INPUT(false), // 1
    PROCESS_INPUT(true),  // 2
    SEND_MESSAGE(false),  // 6
    OPT_IN(true),         // 7
    OPT_OUT(false);       // 8

    private final boolean awaitInput;

    NodeType(boolean awaitInput) {
        this.awaitInput = awaitInput;
    }

    boolean isAwaitInput() {
        return awaitInput;
    }

    public static NodeType forValue(int value) {
        for (NodeType t : values()) {
            if (t.ordinal() == value) {
                return t;
            }
        }
        return null;
    }
}
