package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;

import java.util.List;

public interface ScriptContext {

    Script getCurrentScript();

    void registerOutput(Message moMessage);

    List<Script> getEvaluatedScripts();

}
