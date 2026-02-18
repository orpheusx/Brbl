package com.enoughisasgoodasafeast;

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Defines the interface for putting messages/events into a queue or queue-like system.
 */
public interface QueueProducer {

    public boolean enqueue(@NonNull Message event);

    public void shutdown() throws IOException, TimeoutException;

}
