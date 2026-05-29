package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.MessageProcessor;
import com.enoughisasgoodasafeast.operator.Session;

public interface SessionAwareMessageProcessor extends MessageProcessor {

    /**
     * Process the given Message.
     *
     * @param message the message being processed.
     * @return a tuple containing a flag indicating if processing was successful and the Session context.
     * If false, the Session may be null.
     * The latter can be used to call log(session, message).
     */
    BooleanSession process(Message message);

    /**
     * Log the processed Message using the Session context.
     */
    boolean log(Session session, Message message);
}
