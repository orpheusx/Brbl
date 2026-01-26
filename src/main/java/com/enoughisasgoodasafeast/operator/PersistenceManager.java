package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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
    boolean insertNewUser(User user);

    // Called by Operator
    Map<Pattern, Keyword> getKeywords();

    // Called by Operator
    Node getNodeGraph(UUID scriptId);

    // Called by Operator
    Route[] getActiveRoutes();

    //Node getScriptForKeyword(Platform platform, String keyword);

    void saveSession(Session session) throws PersistenceManagerException;

    Session loadSession(UUID id) throws PersistenceManagerException;

    @Nullable PushCampaign getPushCampaign(@NonNull UUID campaignId);

    @Nullable CampaignUserReport processPushCampaignUsers
            (@NonNull UUID campaignId,
             @NonNull PushSupport pushSupport,
             @NonNull Function<PushSupport, Boolean> perUserProcessor)
            throws SQLException;

    /*
     * This exception exists simply to slightly abstract the internal details involved.
     */
    class PersistenceManagerException extends Exception {
        public PersistenceManagerException(Exception e) {
            super(e);
        }

        public PersistenceManagerException(String message, Exception e) {
            super(message, e);
        }
    }


}
