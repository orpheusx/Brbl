package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.MOMessage;
import com.enoughisasgoodasafeast.SharedConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.testcontainers.shaded.com.google.common.collect.Iterables.getFirst;
import static org.testcontainers.shaded.com.google.common.collect.Iterables.retainAll;

/**
 * The instructions for processing a message.
 * (Trying this as a Record for now. The state will have to be tracked separately.)
 *
 * @param id
 * @param text
 * @param next
 * @param previous
 * @param await
 */
public record Script(UUID id, String text, ScriptType type, List<Script> next, Script previous, String label, boolean await) {

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
        this(null, text, type, List.of(), previous, null, true);
    }

    public Script(String text, ScriptType type, Script previous, String label) {
        this(null, text, type, List.of(), previous, label, true);
    }

    public boolean hasNext() {
        return next != null && !next.isEmpty();
    }

    public boolean hasPrevious() {
        return null != previous;
    }

    public List<Script> next() {
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
    public Script evaluate(Session session, MOMessage moMessage) throws IOException {
        return switch (session.currentScript.type) {
            case PrintWithPrefix ->
                SimpleTestScript.SimpleEchoResponseScript.evaluate(session, moMessage);

            case ReverseText ->
                SimpleTestScript.ReverseTextResponseScript.evaluate(session, moMessage);

            case HelloGoodbye ->
                SimpleTestScript.HelloGoodbyeResponseScript.evaluate(session, moMessage);

        };
    }

}
