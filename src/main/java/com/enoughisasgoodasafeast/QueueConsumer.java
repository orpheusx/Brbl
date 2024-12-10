package com.enoughisasgoodasafeast;

import java.io.IOException;

/**
 * An interface for components that process messages from a queue.
 * FIXME Check SQS API. Does it require polling or does it, like RabbitMQ, implement
 * FIXME a callback for handling messages? If the latter and the impl is HTTP based then
 * FIXME the dequeue() method may still makes sense.
 */
public interface QueueConsumer {
    /**
     * FIXME Probably need to make the return type less vague.
     * @return the object taken from the queue
     */
    public Object dequeue() throws IOException;

    long getPollIntervalMs();

    QueueConsumer setPollIntervalMs(long pollIntervalMs);



}
