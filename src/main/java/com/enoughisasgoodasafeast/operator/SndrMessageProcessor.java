package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.StatusException;
import com.enoughisasgoodasafeast.sndr.ProcessStateRoutingKey;

public interface SndrMessageProcessor extends MessageProcessor {


    /**
     * Process the given Message.
     *
     * @param message the message being processed.
     * @return duple of the ProcessState and, if a retry is needed, the routing key to be used.
     */
    ProcessStateRoutingKey process(Message message);

    /**
     * Log the processed Message as appropriate.
     */
    boolean log(Message message);

    // TODO Obviously we need to build a real, read-through cache implementation in Sndr.
    default int getRouteRetryLimit(Message message) {
        return 1; // two retry attempts (we start at zero) in addition to the initial non-retry attempt.
    };
}
