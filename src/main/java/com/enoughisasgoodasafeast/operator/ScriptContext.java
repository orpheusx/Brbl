package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;

import java.util.List;

public interface ScriptContext {

    Node getCurrentScript();

    void registerOutput(Message moMessage);

    List<Node> getEvaluatedScripts();

}
