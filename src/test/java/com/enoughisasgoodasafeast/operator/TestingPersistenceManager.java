package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.datagen.KnownData;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

import static com.enoughisasgoodasafeast.Functions.randomUUID;

public class TestingPersistenceManager implements PersistenceManager {

    private static final Logger LOG = LoggerFactory.getLogger(TestingPersistenceManager.class);

    public static final UUID KEYWORD_ID = randomUUID();
    public static final UUID SCRIPT_ID = UUID.fromString(KnownData.knownRootNodeIds[0]); // "89eddcb8-7fe5-4cd1-b18b-78858f0789fb");
    public static final String USER_ID = OperatorTest.MOBILE_MX;
    public static final UUID CLAIMANT_ID = randomUUID();

    private final Map<Pattern, Keyword> keywordMap = new HashMap<>();
    private final Map<UUID, Node> nodesById = new HashMap<>();

    private Route[] routes;
    private boolean isFailInsertNewUser;
    private boolean isFailLoadSession = false;

    public TestingPersistenceManager() {
        LOG.info("no-arg constructor called");
//        Keyword keyword = new Keyword(
//                KEYWORD_ID,
//                "(color|colour|colr).*(quiz|q|kwiz)",
//                Platform.SMS,
//                SCRIPT_ID,
//                OperatorTest.SHORT_CODE_4); // channel
//        keywordMap.put(Pattern.compile(keyword.wordPattern()), keyword);
    }

    // TODO
    // Add constructor that takes a list of Keywords?
    // ...

    private final Set<UUID> processedMoIds = new HashSet<>();

    @Override
    public boolean isMOProcessed(UUID moId) {
        LOG.info("isMOProcessed check for {}", moId);
        return processedMoIds.contains(moId);
    }

    @Override
    public boolean commitSessionState(Message moMessage, Session session, boolean isNewUser, UserStatus updatedUserStatus) throws PersistenceManagerException {
        LOG.info("commitSessionState for MO {}", moMessage.id());
        processedMoIds.add(moMessage.id());
        if (isNewUser) {
            if (!insertNewUser(session.getUser())) {
                throw new PersistenceManagerException("commitSessionState failed for message " + moMessage.id(),
                        new SQLException("Failed to insert new user: " + moMessage.id()));
            }
        }
        if (updatedUserStatus != null) {
            updateUserStatus(session.getUser(), moMessage.platform(), updatedUserStatus);
        }
        insertProcessedMO(moMessage, session);
        for (Message mtMessage : session.getOutputBuffer()) {
            insertMT(mtMessage, session);
        }
        if (session.getCurrentNode() == null) {
            clearSession(session);
        } else {
            saveSession(session);
        }
        return true;
    }

    @Override
    public boolean insertMO(Message message) {
        LOG.info("insertMO");
        return true;
    }

    @Override
    public boolean insertProcessedMO(Message message, Session session) {
        LOG.info("insertProcessedMO");
        return true;
    }

    @Override
    public boolean insertMT(Message message, Session session) {
        LOG.info("insertMT");
        return true;
    }

    @Override
    public boolean insertDeliveredMT(Message message) {
        LOG.info("insertDeliveredMT");
        return true;
    }

    public void failInsertNewUser(boolean isFail) {
        this.isFailInsertNewUser = isFail;
    }

    @Override
    public boolean insertNewUser(User user) { //declare possible PersistenceManagerException?
        if (this.isFailInsertNewUser) {
            LOG.info("Failing insertNewUser");
            return false;
        }
        LOG.info("insertNewUser");
        return true;
    }

    @Override
    public Map<Pattern, Keyword> getKeywords() {
        LOG.info("getKeywords: size = {}", keywordMap.size());
        return keywordMap;
    }

    public void addKeyword(Pattern pattern, Keyword keyword) {
        keywordMap.put(pattern, keyword);
    }

    public void addNodeGraph(UUID scriptId, Node presentQuestion) {
        LOG.info("addScript");
        nodesById.put(scriptId, presentQuestion);
    }

    @Override
    public Node getNodeGraph(UUID scriptId) {
        LOG.info("getNodeGraph");
        return nodesById.get(scriptId);
    }

    @Override
    public Route[] getActiveRoutes() {
        LOG.info("getActiveRoutes called");
        return routes;
    }

    @Override
    public boolean updateUserStatus(User user, Platform platform, UserStatus status) {
        LOG.info("updateUserStatus");
        return true;
    }

    public void setActiveRoutes(Route[] routes) {
        LOG.info("setActiveRoutes");
        this.routes = routes;
    }

    private final Map<UUID, byte[]> savedSessions = new HashMap<>();

    @Override
    public boolean saveSession(Session session) throws PersistenceManagerException {
        LOG.info("saveSession");
        try {
            savedSessions.put(session.getId(), SessionSerde.sessionToBytes(session));
            return true;
        } catch (IOException e) {
            LOG.error("Failed to serialize session for {}", session.getId(), e);
            throw new PersistenceManagerException(e);
        }
    }

    public void failLoadSession(boolean isFailLoadSession) {
        this.isFailLoadSession = isFailLoadSession;
    }

    @Override
    public @Nullable Session loadSession(UUID id) throws PersistenceManagerException {
        LOG.info("loadSession");

        if (isFailLoadSession) {
            throw new PersistenceManagerException("Simulated Load Session failure", null, true);
        }

        final byte[] bytes = savedSessions.get(id);
        if (bytes == null) {
            LOG.error("Session {} not found.", id);
            // throw new PersistenceManagerException("No session data for id: " + id.toString());
            return null;
        }
        try {
            return SessionSerde.bytesToSession(bytes);
        } catch (IOException | ClassNotFoundException e) {
            LOG.error("Failed to deserialize session for {}", id, e);
            throw new PersistenceManagerException(e);
        }
    }

    @Override
    public boolean clearSession(@NonNull Session session) throws PersistenceManagerException {
        LOG.info("clearSession");
        savedSessions.remove(session.getId());
        return true;
    }

    @Override
    public PushCampaign getPushCampaign(@NonNull UUID campaignId) {
        LOG.info("getPushCampaign");
        return null; // FIXME implement!
    }

//    @Override
//    public Node getScriptForKeyword(Platform platform, String keyword) {
//        return null;
//    }

    boolean isUserNotNew = false;

    public boolean isUserNotNew() {
        return isUserNotNew;
    }

    public void setUserNotNew(boolean userNotNew) {
        isUserNotNew = userNotNew;
    }

    public Instant getUserCreatedInstant() {
        if (isUserNotNew) {
            return  Instant.now().minus(1, ChronoUnit.HOURS);
        } else {
            return Instant.now();
        }
    }

    @Override
    public User getUser(SessionKey sessionKey) throws PersistenceManagerException {
        // FIXME seems like it would make more sense to create the User using the properties of the provided SessionKey, no?
        LOG.info("getUser");
        Map<Platform, UUID> platformIds = Map.of(Platform.SMS, randomUUID());
        Map<Platform, String> platformNumbers = new HashMap<>();
//        platformNumbers.put(Platform.SMS, USER_ID);
        platformNumbers.put(sessionKey.platform(), sessionKey.from());
        Map<Platform, Instant> platformCreatedAt = new HashMap<>();
        platformCreatedAt.put(sessionKey.platform(), getUserCreatedInstant());
        Map<Platform, String> userNickNames = new LinkedHashMap<>();
        userNickNames.put(Platform.SMS, "Bozo");
        Map<Platform, UserStatus> userStatuses = new LinkedHashMap<>();
        userStatuses.put(sessionKey.platform(), UserStatus.IN);
        return new User(platformIds, randomUUID(), platformNumbers, platformCreatedAt, "US", Set.of(LanguageCode.ENG), CLAIMANT_ID, null, userNickNames, null, userStatuses);
    }

    public Collection<CampaignUser> getPushCampaignUsers(@NonNull UUID campaignId, DeliveryStatus byStatus) {
        LOG.info("getPushCampaignUsers");
        return new ArrayList<>();
    }

    public boolean updatePushCampaignUsersStatus(@NonNull PushReport report) throws SQLException {
        LOG.info("updatePushCampaignUsersStatus");
        return true;
    }

    public UUID createPushCampaign(@NonNull UUID customerId, String description, @NonNull UUID scriptId, @NonNull UUID routeId) throws SQLException {
        LOG.info("createPushCampaign");
        return null;
    }

    public boolean insertCampaignUserSegment(@NonNull UUID campaignId, @NonNull List<UUID> userIds) {
        LOG.info("insertCampaignUserSegment");
        return true;
    }

    public boolean completePushCampaign(@NonNull UUID campaignId, Instant completionTime) throws SQLException {
        LOG.info("completePushCampaign");
        return false;
    }

    @Override
    public @NonNull Connection fetchConnection() throws SQLException {
        return null; // NB: This is intentional.
    }
}
