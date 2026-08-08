package com.enoughisasgoodasafeast.operator;

import io.jenetics.util.NanoClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static com.enoughisasgoodasafeast.operator.LanguageCode.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UserTest {

    static final Map<Platform, UUID> platformIds = Map.of(Platform.SMS, randomUUID());
    static final UUID groupId = randomUUID();
    static final UUID claimantId = randomUUID();
    static final Map<Platform, String> platformNumbers = Map.of(Platform.SMS, "17815551234");
    static final Map<Platform, Instant> platformsCreated = Map.of(Platform.SMS, NanoClock.systemUTC().instant());
    static final Map<Platform, String> userNickNames = Map.of(Platform.SMS, "Boo");
    static final String countryCode = Locale.getDefault().getCountry();
    static final Set<LanguageCode> languages = Set.of(SPA, FRA, ENG);
    static final Map<Platform, UserStatus> platformStatuses = Map.of(Platform.SMS, UserStatus.IN);

    @Test
    void idNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(null, null, platformNumbers, platformsCreated, countryCode, languages, claimantId, null, userNickNames, null, platformStatuses);
        });

        assertTrue(exception.getMessage().contains("platformIds cannot be null"));
    }

    @Test
    void platformIdsNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, null, platformsCreated, countryCode, languages, claimantId, null, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("platformNumbers"));
    }

    @Test
    void platformIdsEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, new HashMap<>(), platformsCreated, countryCode, languages, claimantId, null, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("platformNumbers"));
    }

    @Test
    void countryCodeNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, platformNumbers, platformsCreated, null, languages, claimantId, null, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("countryCode"));
    }

    @Test
    void countryCodeUnsupported() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, platformNumbers, platformsCreated, "RU", languages, claimantId, null, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("countryCode"));
    }

    @Test
    void languagesNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, platformNumbers, platformsCreated, countryCode, null, claimantId, null, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("language"));
    }

    @Test
    void languagesEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, platformNumbers, platformsCreated, countryCode, Set.of(), claimantId, null, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("language"));
    }

    @Test
    void platformStatusesNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, platformNumbers, platformsCreated, countryCode, languages, claimantId, null, userNickNames, null, null);
        });
        assertTrue(exception.getMessage().contains("platformStatus"));
    }

    @Test
    void platformStatusesEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, platformNumbers, platformsCreated, countryCode, languages, claimantId, null, userNickNames, null, Map.of());
        });
        assertTrue(exception.getMessage().contains("platformStatus"));
    }

}