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
     * Execute the node in the context of the given session and message.
     * Most simply this can result in the creation of one more MTMessages.
     * There are a variety of possible side effects including:
     *  - inserts/updates to the database
     *  - schedule new messages
     *  - invoke an ML operation
     * @param session the user context
     * @param moMessage the MO message being processed
     * @return the next Node in the conversation (or null if the conversation is complete?)
     * FIXME Maybe instead of null we return a symbolic Node that indicates the end of Node?
     */
    public Node evaluate(/*Session*/ ScriptContext session, Message moMessage) throws IOException {
        return switch (session.getCurrentNode().type) {
            case EchoWithPrefix ->
                SimpleTestScript.SimpleEchoResponseScript.evaluate(session, moMessage);

            case ReverseText ->
                SimpleTestScript.ReverseTextResponseScript.evaluate(session, moMessage);

            case HelloGoodbye ->
                SimpleTestScript.HelloGoodbyeResponseScript.evaluate(session, moMessage);

            // NOTE: practically speaking there's no reason to have any of the above. Most Scripts should
            // be of the following types or more specific versions thereof. Simple chaining conversations can
            // simply have a single logic list.
            case PresentMulti ->
                    Multi.Present.evaluate(session, moMessage); // Could re-use SendMessage logic while keeping the type difference

            case ProcessMulti ->
                    Multi.Process.evaluate(session, moMessage);

            // TODO Behaves like a SendMessage albeit with the expectation that there's no "next" node so we could replace impl
            case EndOfChat -> SendMessage.evaluate(session, moMessage); //EndOfSession? 'request' that the session be cleared?

            // TODO Even easier to replace with SendMessage.evaluate(). The Editor will always pair it with an Input.Process
            case RequestInput ->
                    Input.Request.evaluate(session, moMessage);

            case ProcessInput ->
                    Input.Process.evaluate(session, moMessage);

            case SendMessage ->
                    SendMessage.evaluate(session, moMessage);

        };

        // If we didn't advance to a different node then we're not done evaluating this one.
        //        if (!this.equals(next)) {
        //            session.registerEvaluated(this);
        //        }
        //        return next;

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

            if (startNode != childNode) {
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
}
