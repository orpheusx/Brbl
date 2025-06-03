package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * The instructions for processing a message.
 * (Trying this as a Record for now. The state will have to be tracked separately.)
 *
 * @param id
 * @param text
 * @param next
 * @param label
 */
public record Script(UUID id, String text, ScriptType type, SequencedSet<ResponseLogic> next/*, Script previous*/, String label) {

    private static final Logger LOG = LoggerFactory.getLogger(Script.class);

    public Script {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text cannot be null or empty.");
        }
        if (next == null) {
            next = new LinkedHashSet<>();
        }
        if (label == null) {
            LOG.info("Created Script (type:{}, id:{}", type, id);
        } else {
            LOG.info("Created Script (type:{}, label:'{}', id:{}", type, label, id);
        }
    }

    public Script(UUID id, String text, ScriptType type) {
        this(id, text, type, null, null);
    }

    public Script(String text, ScriptType type) {
        this(null, text, type, null, null);
    }

    public Script(String text, ScriptType type, String label) {
        this(null, text, type, null, label);
    }


    public boolean hasNext() {
        return next != null && !next.isEmpty();
    }

    public SequencedSet<ResponseLogic> next() {
        return next;
    }

    /**
     * Execute the script in the context of the given session and message.
     * Most simply this can result in the creation of one more MTMessages.
     * There are a variety of possible side effects including:
     *  - inserts/updates to the database
     *  - schedule new messages
     *  - invoke an ML operation
     * @param session the user context
     * @param moMessage the MO message being processed
     * @return the next Script in the conversation (or null if the conversation is complete?)
     * FIXME Maybe instead of null we return a symbolic Script that indicates the end of Script?
     */
    public Script evaluate(Session session, Message moMessage) throws IOException {
        Script next =  switch (session.currentScript().type) {
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
                    Multi.Present.evaluate(session, moMessage);

            case ProcessMulti ->
                    Multi.Process.evaluate(session, moMessage);

//            case Pivot ->
//                    Pivot.evaluate(session);
//
//            case TopicSelection ->
//                    TopicSelection.evaluate(session, moMessage);
        };

        // If we didn't advance to a different script then we're not done evaluating this one.
        if (!this.equals(next)) {
            session.registerEvaluated(this);
        }

        return next;
    }

    /**
     * A convenience function that displays the structure of a script graph
     * @param startScript the origin of the graph
     * @param script the "current" script in the recursive call
     * @param indent the current level of recursion
     */
    public static void printGraph(Script startScript, Script script, int indent) {
        printIndent(script, indent);
        int level = indent + 1;
        for (ResponseLogic edge : script.next()) {
            printIndent(edge, level);
            Script childScript = edge.script();

            if (startScript != childScript) {
                printGraph(startScript, childScript, level + 1);
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
