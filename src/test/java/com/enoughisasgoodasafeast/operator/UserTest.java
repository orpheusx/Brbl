package com.enoughisasgoodasafeast.operator;

import io.jenetics.util.NanoClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static com.enoughisasgoodasafeast.operator.LanguageCode.*;
import static org.junit.jupiter.api.Assertions.*;

public class UserTest {

    final static Map<Platform, UUID> platformIds = Map.of(Platform.SMS, randomUUID());
    final static UUID groupId = randomUUID();
    final static UUID customerId = randomUUID();
    final static Map<Platform, String> platformNumbers = new HashMap<>();
    final static Map<Platform, Instant> platformsCreated = new LinkedHashMap<>();
    final static Map<Platform, String> userNickNames = new LinkedHashMap<>();
    final static String countryCode = Locale.getDefault().getCountry();
    final static Set<LanguageCode> languages = new HashSet<>();
    final static Map<Platform, UserStatus> platformStatuses = new LinkedHashMap<>();

    static {
        platformNumbers.put(Platform.SMS, "17815551234");
        platformsCreated.put(Platform.SMS, NanoClock.systemUTC().instant());
        languages.add(SPA);
        languages.add(FRA);
        languages.add(ENG);
        userNickNames.put(Platform.SMS, "Boo");
        platformStatuses.put(Platform.SMS, UserStatus.IN);

    }

    @Test
    void idNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(null, null, platformNumbers, platformsCreated, countryCode, languages, customerId, userNickNames, null, platformStatuses);
        });

        assertTrue(exception.getMessage().contains("platformIds cannot be null"));
    }

    @Test
    void platformIdsNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, null, platformsCreated, countryCode, languages, customerId, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("platformNumbers"));
    }

    @Test
    void platformIdsEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, new HashMap<>(),platformsCreated, countryCode, languages, customerId, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("platformNumbers"));
    }

    @Test
    void countryCodeNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, platformNumbers, platformsCreated, null, languages, customerId, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("countryCode"));
    }

    @Test
    void countryCodeUnsupported() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, platformNumbers, platformsCreated, "RU", languages, customerId, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("countryCode"));
    }

    @Test
    void languagesNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, platformNumbers, platformsCreated, countryCode, null, customerId, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("language"));
    }

    @Test
    void languagesEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, platformNumbers, platformsCreated, countryCode, Set.of(), customerId, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("language"));
    }

    // Not useful since we've migrated language specification to a Set of
    //@Test
    //void languagesUnsupported() {
    //    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
    //        List<String> unsupported = new ArrayList<>();
    //        unsupported.add("ru");
    //        new User(id, groupId, platformNumbers, platformsCreated, countryCode, unsupported, customerId, userNickNames, null, platformStatuses);
    //    });
    //    assertTrue(exception.getMessage().contains("language"));
    //}

    @Test
    void platformStatusesNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, platformNumbers, platformsCreated, countryCode, languages, customerId, userNickNames, null, null);
        });
        assertTrue(exception.getMessage().contains("platformStatus"));
    }

    @Test
    void platformStatusesEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(platformIds, groupId, platformNumbers, platformsCreated, countryCode, languages, customerId, userNickNames, null, Map.of());
        });
        assertTrue(exception.getMessage().contains("platformStatus"));
    }

}