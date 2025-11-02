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

    private Map<Pattern, Keyword> keywordMap = new HashMap<>();

    public TestingPersistenceManager() {
        Keyword keyword = new Keyword(KEYWORD_ID, "Color quiz", Platform.SMS,
                SCRIPT_ID, null);
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

    @Override
    public Node getScript(UUID scriptId) {
        return null;
    }

    @Override
    public Node getScriptForKeyword(Platform platform, String keyword) {
        return null;
    }

    @Override
    public User getUser(SessionKey sessionKey) {
        Map<Platform, String> platformIds = new HashMap<>();
        platformIds.put(Platform.SMS, USER_ID);
        Map<Platform, Instant> platformCreatedAt = new HashMap<>();
        platformCreatedAt.put(Platform.SMS, NanoClock.utcInstant());
        return new User(UUID.randomUUID(), platformIds, platformCreatedAt, "US", List.of("ENG"));
    }
}
