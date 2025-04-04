package com.enoughisasgoodasafeast;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Defines the interface for putting messages/events into a queue or queue-like system.
 */
public interface QueueProducer {

    public void enqueue(Object event) throws IOException;

    public void shutdown() throws IOException, TimeoutException;

}
