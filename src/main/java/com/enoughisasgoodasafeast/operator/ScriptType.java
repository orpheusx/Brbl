package com.enoughisasgoodasafeast.operator;

/**
 * An enumeration used to define the type of logic applied to the processing of a message.
 */
public enum ScriptType {
    EchoWithPrefix(1),
    ReverseText(2),
    HelloGoodbye(3),
    PresentMulti(4),
    ProcessMulti(5),
    Pivot(6),
    TopicSelection(7);

    private final int value;

    ScriptType(int value) {
        this.value = value;
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
