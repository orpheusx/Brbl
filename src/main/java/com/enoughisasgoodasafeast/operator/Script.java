package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The instructions for processing a message.
 * (Trying this as a Record for now. The state will have to be tracked separately.)
 *
 * @param id
 * @param text
 * @param next
 * @param previous
 */
public record Script(UUID id, String text, ScriptType type, List<ResponseLogic> next, Script previous, String label) {

    private static final Logger LOG = LoggerFactory.getLogger(Script.class);

    public Script {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text cannot be null or empty.");
        }
        if (next == null) {
            next = new ArrayList<>();
        }
        if (label == null) {
            LOG.info("Created Script (type:{}, id:{}", type, id);
        } else {
            LOG.info("Created Script (type:{}, label:'{}', id:{}", type, label, id);
        }
    }

    public Script(String text, ScriptType type, Script previous) {
        this(null, text, type, null, previous, null);
    }

    public Script(String text, ScriptType type, Script previous, String label) {
        this(null, text, type, null, previous, label);
    }

    public boolean hasNext() {
        return next != null && !next.isEmpty();
    }

    public boolean hasPrevious() {
        return null != previous;
    }

    public List<ResponseLogic> next() {
        return next;
    }

    public Script previous() {
        return previous;
    }

    public boolean isStart() {
        return previous == null;
    }

    /**
     * Execute the script in the context of the given session and message.
     * Most simply this can result in the creation of one more MTMessages.
     * There are a variety of possible side effects including:
     *  - inserts/updates to the database
     *  - schedule new messages
     *  - invoke an ML operation
     * @param session the user context
     * @param moMessage the message being processed
     * @return the next Script in the conversation (or null if the conversation is complete?)
     * FIXME Maybe instead of null we return a symbolic Script that indicates the end of Script.
     */
    public Script evaluate(Session session, Message moMessage) throws IOException {
        return switch (session.currentScript().type) {
            case PrintWithPrefix ->
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

            case Pivot ->
                    Pivot.evaluate(session);

            case TopicSelection ->
                    TopicSelection.evaluate(session, moMessage);
        };
    }

    public int expectedInputCount() {
        return 1;
    }
}
