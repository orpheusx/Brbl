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
}
