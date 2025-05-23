package com.enoughisasgoodasafeast.operator;

/**
 * An enumeration used to define the type of logic applied to the processing of a message.
 * TODO Add an ordinal for use with database reporting.
 */
public enum ScriptType {
    EchoWithPrefix,
    ReverseText,
    HelloGoodbye,
    PresentMulti,
    ProcessMulti,
    Pivot,
    TopicSelection;
}
