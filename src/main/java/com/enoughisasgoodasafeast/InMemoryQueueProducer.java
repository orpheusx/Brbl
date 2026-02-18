package com.enoughisasgoodasafeast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

/**
 * A QueueProducer that actually just adds Messages to an internal list
 * which can be retrieved by test code for verification.
 */
public class InMemoryQueueProducer implements QueueProducer {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryQueueProducer.class);

    private final List<Message> queuedMessages = new ArrayList<>();

    public InMemoryQueueProducer() {
        LOG.debug("Creating InMemoryQueueProducer");
    }

    @Override
    public boolean enqueue(Message event) {
        LOG.info("Enqueuing message: {}", event);
        boolean ok = queuedMessages.add((Message) event);
        LOG.info("Number messages in queue: {}", queuedMessages.size());
        return ok;
    }

    @Override
    public void shutdown() throws IOException, TimeoutException {
        // no op
        LOG.info("Shutdown called.");
    }

    public int enqueuedCount() {
        return queuedMessages.size();
    }

    public List<Message> enqueued() {
        return queuedMessages;
    }

}
