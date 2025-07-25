package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import io.jenetics.util.NanoClock;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public
class TestingPersistenceManager implements PersistenceManager {

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
        Map<Pattern, Keyword> keywordMap = new HashMap<>();

        Keyword keyword = new Keyword(UUID.randomUUID(), "Color quiz", Platform.SMS,
                UUID.fromString("89eddcb8-7fe5-4cd1-b18b-78858f0789fb"), null);

        keywordMap.put(Pattern.compile(keyword.wordPattern()), keyword);

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
        platformIds.put(Platform.BRBL, "completely_bullshit_brbl_id");
        Map<Platform, Instant> platformCreatedAt = new HashMap<>();
        platformCreatedAt.put(Platform.BRBL, NanoClock.utcInstant());
        return new User(UUID.randomUUID(), platformIds, platformCreatedAt, "US", List.of("ENG"));
    }
}
