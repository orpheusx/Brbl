package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.MOMessage;
import com.enoughisasgoodasafeast.MTMessage;
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

    final UUID id;
    final User user;
    final long startTimeNanos;
    Script currentScript;
    Queue<MTMessage> outputBuffer = new LinkedList<>();
    Integer seqNum = 0;
    SequencedSet<MOMessage> inputs = new LinkedHashSet<>();

    // processing resources
    QueueProducer producer; // different ones depending on the Platform
    // Db manager goes here...

    public Session(UUID id, Script currentScript, User user, QueueProducer producer) {
        startTimeNanos = NanoClock.systemUTC().nanos();
        this.id = id;
        this.currentScript = currentScript;
        this.user = user;
        this.producer = producer;
        LOG.info("Created Session {} for User {}", id, user.id());
    }

    public void addOutput(MTMessage mtMessage) {
        outputBuffer.add(mtMessage);
    }

    public void flushOutput() throws IOException {
        int numInBuffer = outputBuffer.size();
        for (int i = 0; i < numInBuffer; i++) {
            MTMessage mtMessage = outputBuffer.poll();
            producer.enqueue(mtMessage);
        }
        outputBuffer.clear();
    }

    public User getUser() {
        return user;
    }

    public Script currentScript() {
        return currentScript;
    }

    public void addInput(MOMessage message) {
        inputs.add(message);
    }
}
