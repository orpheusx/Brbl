package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;

public interface PersistenceManager {

    // Called by Rcvr
    boolean insertMO(Message message);

    // Called by Operator
    boolean insertProcessedMO(Message message, Session session);

    // Called by Session
    boolean insertMT(Message message, Session session);

    // Called by Sndr
    boolean insertDeliveredMT(Message message);

    /*
     * This exception exists simply to slightly abstract the internal details involved.
     */
    public static class PersistenceManagerException extends Exception {
        public PersistenceManagerException(String message, Exception e) {
            super(message, e);
        }
    }
}
