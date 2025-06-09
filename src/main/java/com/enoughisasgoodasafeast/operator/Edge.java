package com.enoughisasgoodasafeast.operator;

import java.util.List;
import java.util.UUID;

/**
 *
 * @param id
 * @param text
 * @param matchText
 * @param node
 */
public record Edge(UUID id, String text, List<String> matchText, Node node) {

    public Edge(List<String> matchText, Node node) {
        this(UUID.randomUUID(), null, matchText, node);
    }

    public Edge(List<String> matchText, String text, Node node) {
        this(UUID.randomUUID(), text, matchText, node);
    }

    public Edge(UUID id, List<String> matchText, String text) {
        this(id, text, matchText, null);
    }

    public Edge copyReplacing(Node node) {
        return new Edge(this.id, this.text, this.matchText, node);
    }

    @Override
    public String toString() {
        if (node == null) {
            return String.format("Edge[id=%s, text='%s', matchText=%s, node=%s]", id, text, matchText(), null);
        } else {
            return String.format("Edge[id=%s, text='%s', matchText=%s, node=%s]", id, text, matchText(), node.id());
        }
    }
}

