package com.enoughisasgoodasafeast.operator;

/**
 * An enumeration used to define the type of logic applied to the processing of a message.
 */
public enum NodeType {
    PRESENT_MULTI(false),
    PROCESS_MULTI(true),
    END_OF_CHAT(false),
    REQUEST_INPUT(false),
    PROCESS_INPUT(true),
    SEND_MESSAGE(false);

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
