package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * The instructions for processing a message.
 * (Trying this as a Record for now. The state will have to be tracked separately.)
 *
 * @param id
 * @param text
 * @param edges
 * @param label
 */
public record Node(UUID id, String text, NodeType type, SequencedSet<Edge> edges, String label) implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(Node.class);

    public Node {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text cannot be null or empty.");
        }
        if (edges == null) {
            edges = new LinkedHashSet<>();
        }
        if (label == null) {
            LOG.debug("Created Node (type:{}, id:{}", type, id);
        } else {
            LOG.debug("Created Node (type:{}, label:'{}', id:{}", type, label, id);
        }
    }

    public Node(String text, NodeType type, SequencedSet<Edge> edges, String label) {
        this(UUID.randomUUID(), text, type, edges, label);
    }

    public Node(UUID id, String text, NodeType type) {
        this(id, text, type, null, null);
    }

    public Node(String text, NodeType type) {
        this(null, text, type, null, null);
    }

    public Node(String text, NodeType type, String label) {
        this(null, text, type, null, label);
    }


    public boolean hasNext() {
        return edges != null && !edges.isEmpty();
    }

    public SequencedSet<Edge> edges() {
        return edges;
    }

    // NB It's painful creating and connecting Nodes and Edges. Trying to make it less so with this pseudo-DSL-like method?
    public void addEdge(List<String> matchText, String text) {
        edges.add(new Edge(matchText, text, this));
    }

    /**
     * A convenience function that displays the structure of a node graph
     * @param startNode the origin of the graph
     * @param node the "current" node in the recursive call
     * @param indent the current level of recursion
     */
    public static void printGraph(Node startNode, Node node, int indent) {
        printIndent(node, indent);
        int level = indent + 1;
        for (Edge edge : node.edges()) {
            printIndent(edge, level);
            Node childNode = edge.targetNode();

            if (childNode != null && startNode != childNode) {
                printGraph(startNode, childNode, level + 1);
            }
        }
    }

    private static void printIndent(Object object, int level) {
        System.out.print(level + " ");
        for (int i = 0; i < level; i++) {
            System.out.print("\t");
        }
        System.out.println(object);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Node.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("text='" + text + "'")
                .add("type=" + type)
                .add("edges=" + edges.size())
                .add("label='" + label + "'")
                .toString();
    }
}
