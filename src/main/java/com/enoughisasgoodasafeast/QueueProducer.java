package com.enoughisasgoodasafeast;

import java.io.IOException;

/**
 * Defines the interface for putting messages/events into a queue or queue-like system.
 */
public interface QueueProducer {

    // FIXME define a general message type class to use here
    public void enqueue(Object event) throws IOException;

}
