package com.enoughisasgoodasafeast.operator;

import java.util.List;
import java.util.UUID;

public record ResponseLogic(UUID id, String text, List<String> matchText, Script script) {

    public ResponseLogic(List<String> matchText, Script script) {
        this(UUID.randomUUID(), null, matchText, script);
    }

    public ResponseLogic(List<String> matchText, String text, Script script) {
        this(UUID.randomUUID(), text, matchText, script);
    }

    @Override
    public String toString() {
        return String.format("ResponseLogic[id=%s, text='%s', matchText=%s, script=%s]", id, text, matchText(), script.id());
    }
}

