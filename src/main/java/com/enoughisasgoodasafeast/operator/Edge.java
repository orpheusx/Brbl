package com.enoughisasgoodasafeast.operator;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;

/**
 *
 * @param id the unique identifier of this record.
 * @param responseText the text that may be sent when an Edge is selected.
 * @param matchText the list of inputs used to match on this Edge.
 * @param targetNode the Node this Edge connects to.
 */
public record Edge(UUID id, String responseText, List<String> matchText, Node targetNode) implements Serializable {

    public Edge() {
        this(randomUUID(), null, null, null);
    }

    public Edge(Node targetNode) {
        this(randomUUID(), null, null, targetNode);
    }

    public Edge(String responseText, Node targetNode) {
        this(randomUUID(), responseText, null, targetNode);
    }

    public Edge(List<String> matchText, Node targetNode) {
        this(randomUUID(), null, matchText, targetNode);
    }

    public Edge(List<String> matchText, String responseText, Node targetNode) {
        this(randomUUID(), responseText, matchText, targetNode);
    }

    public Edge(UUID id, List<String> matchText, String text) {
        this(id, text, matchText, null);
    }

    public Edge copyReplacing(Node targetNode) {
        return new Edge(this.id, this.responseText, this.matchText, targetNode);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Edge edge)) return false;
        return Objects.equals(id, edge.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

