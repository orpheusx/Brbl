package com.enoughisasgoodasafeast.operator;

/**
 * An enumeration used to define the type of logic applied to the processing of a message.
 */
public enum ScriptType {
    EchoWithPrefix  (1, false), // FIXME get rid of these
    ReverseText     (2, false), // FIXME get rid of these
    HelloGoodbye    (3, false), // FIXME get rid of these

    PresentMulti    (4, false),
    ProcessMulti    (5, true),
    EndOfChat       (6, false),
    RequestInput    (7, false),
    ProcessInput    (8, true),
    SendMessage     (9, false)
    ;
    //    Pivot           (6, false),
    //    TopicSelection  (7, false);

    private final int value;
    private final boolean awaitInput;

    ScriptType(int value, boolean awaitInput) {
        this.value = value;
        this.awaitInput = awaitInput;
    }

    int value() {
        return value;
    }

    boolean isAwaitInput() {
        return awaitInput;
    }

    public static ScriptType forValue(int value) {
        for (ScriptType t : values()) {
            if (t.value == value) {
                return t;
            }
        }
        return null;
    }
}
