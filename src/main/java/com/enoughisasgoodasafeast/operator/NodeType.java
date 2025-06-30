package com.enoughisasgoodasafeast.operator;

/**
 * An enumeration used to define the type of logic applied to the processing of a message.
 */
public enum NodeType {
    EchoWithPrefix  (false), // 0 // FIXME get rid of these
    ReverseText     (false), // 1 // FIXME get rid of these
    HelloGoodbye    (false), // 2// FIXME get rid of these

    PresentMulti    (false), // 3
    ProcessMulti    (true),  // 4
    EndOfChat       (false), // 5
    RequestInput    (false), // 6
    ProcessInput    (true),  // 7
    SendMessage     (false)  // 8
    ;
    //    Pivot           (6, false),
    //    TopicSelection  (7, false);

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
