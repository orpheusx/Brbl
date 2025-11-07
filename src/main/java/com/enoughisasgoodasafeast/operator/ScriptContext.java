package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;

import java.util.List;

public interface ScriptContext {

    Node getCurrentNode();

    void registerOutput(Message moMessage);

    List<Node> getEvaluatedNodes();

    void registerEvaluated(Node node);
}
