package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public interface PersistenceManager {

    // Called by Rcvr
    boolean insertMO(Message message);

    // Called by Operator
    boolean insertProcessedMO(Message message, Session session);

    // Called by Session
    boolean insertMT(Message message, Session session);

    // Called by Sndr
    boolean insertDeliveredMT(Message message);

    // Called by Operator
    User getUser(SessionKey sessionKey);

    // Called by Operator
    boolean insertUser(User user);

    // Called by Operator
    Map<Pattern, Keyword> getKeywords();

    // Called by Operator
    Node getScript(UUID scriptId);

    Node getScriptForKeyword(Platform platform, String keyword);

    /*
     * This exception exists simply to slightly abstract the internal details involved.
     */
    public static class PersistenceManagerException extends Exception {
        public PersistenceManagerException(String message, Exception e) {
            super(message, e);
        }
    }


}
