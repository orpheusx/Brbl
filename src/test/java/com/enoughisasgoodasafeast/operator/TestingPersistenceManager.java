package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import io.jenetics.util.NanoClock;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public
class TestingPersistenceManager implements PersistenceManager {

    public static final UUID KEYWORD_ID = UUID.randomUUID();
    public static final UUID SCRIPT_ID = UUID.fromString("89eddcb8-7fe5-4cd1-b18b-78858f0789fb");
    public static final String USER_ID = OperatorTest.MOBILE_MX;
    public static final UUID CUSTOMER_ID = UUID.randomUUID();

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
    public boolean insertUser(User user) {
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

//    @Override
//    public Node getScriptForKeyword(Platform platform, String keyword) {
//        return null;
//    }

    @Override
    public User getUser(SessionKey sessionKey) {
        Map<Platform, String> platformIds = new HashMap<>();
        platformIds.put(Platform.SMS, USER_ID);
        Map<Platform, Instant> platformCreatedAt = new HashMap<>();
        platformCreatedAt.put(Platform.SMS, NanoClock.utcInstant());
        Map<Platform, String> userNickNames = new LinkedHashMap<>();
        userNickNames.put(Platform.SMS, "Bozo");
        return new User(UUID.randomUUID(), platformIds, platformCreatedAt, "US", List.of("ENG"), CUSTOMER_ID, userNickNames, null);
    }
}
