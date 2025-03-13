package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;

public interface MessageProcessor {

    /**
     * Process the given Message
     *
     * @param message the message being processed.
     * @return true if processing was complete, false if incomplete.
     */
    boolean process(Message message);

}
