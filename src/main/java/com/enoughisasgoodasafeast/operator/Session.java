package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.QueueProducer;
import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * The Session tracks and persists state for a single User
 * Won't work as a Record since we need to update the currentScript field
 * and maintain state
 */
public class Session {
    private static final Logger LOG = LoggerFactory.getLogger(Session.class);
    public static final int MAX_INPUT_HISTORY = 10;

    final long startTimeNanos;
    final UUID id;
    final User user;
    final QueueProducer producer;

    Script currentScript;

    final Queue<Message> outputBuffer = new LinkedList<>();
    final SequencedSet<Message> inputs = new LinkedHashSet<>();
    final SequencedSet<Message> inputHistory = new LinkedHashSet<Message>(MAX_INPUT_HISTORY) {
        @Override
        public void addLast(Message message) {
            if (1 + this.size() > MAX_INPUT_HISTORY) {
                removeFirst();
            }
            super.addLast(message);
        }
    };

    List<Script> evaluatedScripts = new ArrayList<>();

    // Db manager goes here...
    // ...

    public Session(UUID id, Script currentScript, User user, QueueProducer producer) {
        startTimeNanos = NanoClock.systemUTC().nanos();
        this.id = Objects.requireNonNull(id);
        this.currentScript = Objects.requireNonNull(currentScript);
        this.user = Objects.requireNonNull(user);
        this.producer = Objects.requireNonNull(producer);
        LOG.info("Created Session {} for User {}", id, user.id());
    }

    public void addOutput(Message mtMessage) {
        outputBuffer.add(mtMessage);
    }

    public void flushOutput() throws IOException {
        int numInBuffer = outputBuffer.size();
        for (int i = 0; i < numInBuffer; i++) {
            Message mtMessage = outputBuffer.poll();
            producer.enqueue(mtMessage);
        }
        outputBuffer.clear();

        inputs.forEach(inputHistory::addLast);

        inputs.clear();
    }

    public User getUser() {
        return user;
    }

    public Script currentScript() {
        return currentScript;
    }

    public void addInput(Message message) {
        inputs.add(message);
    }

    public void addEvaluated(Script script) {
        evaluatedScripts.addLast(script);
    }
}
