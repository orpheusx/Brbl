package com.enoughisasgoodasafeast.operator;

import java.util.List;
import java.util.UUID;

/**
 *
 * @param id
 * @param text
 * @param matchText
 * @param script
 */
public record ResponseLogic(UUID id, String text, List<String> matchText, Script script) {

    public ResponseLogic(List<String> matchText, Script script) {
        this(UUID.randomUUID(), null, matchText, script);
    }

    public ResponseLogic(List<String> matchText, String text, Script script) {
        this(UUID.randomUUID(), text, matchText, script);
    }

    public ResponseLogic(UUID id, List<String> matchText, String text) {
        this(id, text, matchText, null);
    }

    public ResponseLogic copyReplacing(Script script) {
        return new ResponseLogic(this.id, this.text, this.matchText, script);
    }

    @Override
    public String toString() {
        if (script == null) {
            return String.format("ResponseLogic[id=%s, text='%s', matchText=%s, script=%s]", id, text, matchText(), null);
        } else {
            return String.format("ResponseLogic[id=%s, text='%s', matchText=%s, script=%s]", id, text, matchText(), script.id());
        }
    }
}

