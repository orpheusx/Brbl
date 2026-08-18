package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.StatusException;

public interface SndrMessageProcessor extends MessageProcessor {


    /**
     * Process the given Message.
     *
     * @param message the message being processed.
     * @return true if processing was complete, false if incomplete.
     */
    StatusException process(Message message);

    /**
     * Log the processed Message as appropriate.
     */
    boolean log(Message message);
}
