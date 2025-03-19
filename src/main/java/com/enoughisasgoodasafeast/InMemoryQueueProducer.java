package com.enoughisasgoodasafeast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * A QueueProducer that actually just adds Messages to an internal list
 * which can be retrieved by test code for verification.
 */
public class InMemoryQueueProducer implements QueueProducer {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryQueueProducer.class);

    private final List<Message> queuedMessages = new ArrayList<>();

    public InMemoryQueueProducer() {
        LOG.info("Creating InMemoryQueueProducer");
    }

    @Override
    public void enqueue(Object event) throws IOException {
        LOG.info("Enqueuing message: {}", event);
        queuedMessages.add((Message) event);
    }

    public List<Message> getQueuedMessages() {
        return queuedMessages;
    }

}
