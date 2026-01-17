package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static io.jenetics.util.NanoClock.*;

public class TestingPersistenceManager implements PersistenceManager {

    private static final Logger LOG = LoggerFactory.getLogger(TestingPersistenceManager.class);

    public static final UUID KEYWORD_ID = randomUUID();
    public static final UUID SCRIPT_ID = UUID.fromString("89eddcb8-7fe5-4cd1-b18b-78858f0789fb");
    public static final String USER_ID = OperatorTest.MOBILE_MX;
    public static final UUID CUSTOMER_ID = randomUUID();

    private final Map<Pattern, Keyword> keywordMap = new HashMap<>();
    private final Map<UUID, Node> nodesByScriptId = new HashMap<>();

    public TestingPersistenceManager() {
        Keyword keyword = new Keyword(
                KEYWORD_ID,
                "(color|colour|colr).*(quiz|q|kwiz)",
                Platform.SMS,
                SCRIPT_ID,
                OperatorTest.SHORT_CODE_4); // channel
        keywordMap.put(Pattern.compile(keyword.wordPattern()), keyword);
    }

    @Override
    public boolean insertMO(Message message) {
        return true;
    }

    @Override
    public boolean insertProcessedMO(Message message, Session session) {
        return true;
    }

    @Override
    public boolean insertMT(Message message, Session session) {
        return true;
    }

    @Override
    public boolean insertDeliveredMT(Message message) {
        return true;
    }

    @Override
    public boolean insertNewUser(User user) {
        return true;
    }

    @Override
    public Map<Pattern, Keyword> getKeywords() {
        return keywordMap;
    }

    void addScript(UUID scriptId, Node presentQuestion) {
        nodesByScriptId.put(scriptId, presentQuestion);
    }

    @Override
    public Node getScript(UUID scriptId) {
        return nodesByScriptId.get(scriptId);
    }

    @Override
    public Route[] getActiveRoutes() {
        return null;
    }

    private final Map<UUID, byte[]> savedSessions = new HashMap<>();

    @Override
    public void saveSession(Session session) throws PersistenceManagerException {
        try {
            savedSessions.put(session.getId(), SessionSerde.sessionToBytes(session));
        } catch (IOException e) {
            LOG.error("Failed to serialize session for {}", session.getId(), e);
            throw new PersistenceManagerException(e);
        }
    }

    @Override
    public @Nullable Session loadSession(UUID id) throws PersistenceManagerException {
        final byte[] bytes = savedSessions.get(id);
        try {
            return SessionSerde.bytesToSession(bytes);
        } catch (IOException | ClassNotFoundException e) {
            LOG.error("Failed to deserialize session for {}", id, e);
            throw new PersistenceManagerException(e);
        }
    }

    @Override
    public List<CampaignUser> getUsersForPushCampaign(@NonNull UUID campaignId) {
        return List.of(); // FIXME implement!
    }

    @Override
    public PushCampaign getPushCampaign(@NonNull UUID campaignId) {
        return null; // FIXME implement!
    }

//    @Override
//    public Node getScriptForKeyword(Platform platform, String keyword) {
//        return null;
//    }

    @Override
    public User getUser(SessionKey sessionKey) {
        Map<Platform, String> platformIds = new HashMap<>();
        platformIds.put(Platform.SMS, USER_ID);
        Map<Platform, Instant> platformCreatedAt = new HashMap<>();
        platformCreatedAt.put(Platform.SMS, utcInstant());
        Map<Platform, String> userNickNames = new LinkedHashMap<>();
        userNickNames.put(Platform.SMS, "Bozo");
        Map<Platform, UserStatus> userStatuses = new LinkedHashMap<>();
        userStatuses.put(Platform.SMS, UserStatus.IN);
        return new User(randomUUID(), randomUUID(), platformIds, platformCreatedAt, "US", List.of("ENG"), CUSTOMER_ID, userNickNames, null, userStatuses);
    }
}
