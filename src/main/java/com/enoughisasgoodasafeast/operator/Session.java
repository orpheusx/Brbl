package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.QueueProducer;
import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IO;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The Session tracks and persists state for a single User
 * Won't work as a Record since we need to update the currentScript field
 * and maintain state
 */
public class Session implements ScriptContext {
    private static final Logger LOG = LoggerFactory.getLogger(Session.class);
    public static final int MAX_INPUT_HISTORY = 10;

    final long startTimeNanos;
    final UUID id;
    final User user;
    final QueueProducer producer;
    final PersistenceManager persistenceManager;

    Script currentScript;

    private final Queue<Message> outputBuffer = new LinkedList<>();
    private final SequencedSet<Message> inputs = new LinkedHashSet<>();
    private final SequencedSet<Message> inputHistory = new LinkedHashSet<Message>(MAX_INPUT_HISTORY) {
        @Override
        public void addLast(Message message) {
            if (1 + this.size() > MAX_INPUT_HISTORY) {
                removeFirst();
            }
            super.addLast(message);
        }
    };
    private final List<Script> evaluatedScripts = new ArrayList<>(); // TODO make this a stack instead?

    /**
     * Creates a new, fully configured Session object.
     * @param id the unique identifier
     * @param currentScript the starting Script in the graph
     * @param user the unique User
     * @param producer the sink for messages created on behalf of this Session
     * @param persistenceManager the object that writes artifacts created for this Session
     */
    public Session(UUID id, Script currentScript, User user, QueueProducer producer, PersistenceManager persistenceManager) {
        startTimeNanos = NanoClock.systemUTC().nanos();
        this.id = Objects.requireNonNull(id);
        this.currentScript = Objects.requireNonNull(currentScript);
        this.user = Objects.requireNonNull(user);
        this.producer = Objects.requireNonNull(producer);
        this.persistenceManager = persistenceManager;
        LOG.info("Created Session {} for User {}", id, user.id());
    }

    public int currentInputsCount() {
        return inputs.size();
    }

    public Message previousInput() {
        return inputHistory.isEmpty() ? null : inputHistory.getLast();
    }

    public SequencedSet<Message> getInputHistory() {
        return inputHistory;
    }

    public Queue<Message> getOutputBuffer() {
        return outputBuffer;
    }

    public Script previousScript() {
        return evaluatedScripts.getLast();
    }

    @Override
    public List<Script> getEvaluatedScripts() {
        return evaluatedScripts;
    }

    public void registerInput(Message moMessage) {
        inputs.addLast(moMessage);
        LOG.info("Registered input message {}", moMessage);
    }

    @Override
    public void registerOutput(Message mtMessage) {
        outputBuffer.add(mtMessage);
        LOG.info("Registered output message {}", mtMessage);
    }
    
    public void registerEvaluated(Script script) {
        evaluatedScripts.addLast(script);
        LOG.info("Registered evaluated script {}", script.id());
    }

    /**
     * Since multiple Scripts may be evaluated in response to a single MO we need a way of
     * finding the one that prompted it. Used when logging the processed MO.
     * @return the Script that prompted the User's latest MO.
     */

    public Script getScriptForProcessedMO() {

        if (currentScript != null) { // FIXME should
            return currentScript;
        }

        for (int i = evaluatedScripts.size() - 1; i >= 0; i--) {
            Script evaluated = evaluatedScripts.get(i);
            if (evaluated.type().isAwaitInput()) {
                return evaluated;
            }
        }

        return evaluatedScripts.getFirst();
    }

    public void flush() throws IOException {
        int numInBuffer = outputBuffer.size();
        LOG.info("flushOutput: outputBuffer size = {}", numInBuffer);
        for (int i = 0; i < numInBuffer; i++) {
            Message mtMessage = outputBuffer.poll();
            producer.enqueue(mtMessage);
            persistenceManager.insertMT(mtMessage, this);
        }

        outputBuffer.clear();

        inputs.forEach(inputHistory::addLast);

        inputs.clear();
    }

    public User getUser() {
        return user;
    }

    @Override
    public Script getCurrentScript() {
        return currentScript;
    }

    public void setCurrentScript(Script script) {
        currentScript = script;
    }
}
