package com.enoughisasgoodasafeast.operator;

/**
 * An enumeration used to define the type of logic applied to the processing of a message.
 */
public enum ScriptType {
    EchoWithPrefix  (1, false), // FIXME get rid of these
    ReverseText     (2, false), // FIXME get rid of these
    HelloGoodbye    (3, false), // FIXME get rid of these
    PresentMulti    (4, false),
    ProcessMulti    (5, true)
    ;
//    Pivot           (6, false),
//    TopicSelection  (7, false);

    private final int value;
    private final boolean awaitsInput;

    ScriptType(int value, boolean awaitsInput) {
        this.value = value;
        this.awaitsInput = awaitsInput;
    }

    int value() {
        return value;
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
