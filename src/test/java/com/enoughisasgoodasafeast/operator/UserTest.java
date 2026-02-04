package com.enoughisasgoodasafeast.operator;

import io.jenetics.util.NanoClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static com.enoughisasgoodasafeast.operator.LanguageCode.*;
import static org.junit.jupiter.api.Assertions.*;

public class UserTest {

    final static UUID id = randomUUID();
    final static UUID groupId = randomUUID();
    final static UUID customerId = randomUUID();
    final static Map<Platform, String> platformIds = new HashMap<>();
    final static Map<Platform, Instant> platformsCreated = new LinkedHashMap<>();
    final static Map<Platform, String> userNickNames = new LinkedHashMap<>();
    final static String countryCode = Locale.getDefault().getCountry();
    final static Set<LanguageCode> languages = new HashSet<>();
    final static Map<Platform, UserStatus> platformStatuses = new LinkedHashMap<>();

    static {
        platformIds.put(Platform.SMS, "17815551234");
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
            new User(null, null, platformIds, platformsCreated, countryCode, languages, customerId, userNickNames, null, platformStatuses);
        });

        assertTrue(exception.getMessage().contains("id"));
    }

    @Test
    void platformIdsNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, groupId, null, platformsCreated, countryCode, languages, customerId, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("platformIds"));
    }

    @Test
    void platformIdsEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, groupId, new HashMap<>(),platformsCreated, countryCode, languages, customerId, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("platformIds"));
    }

    @Test
    void countryCodeNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, groupId, platformIds, platformsCreated, null, languages, customerId, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("countryCode"));
    }

    @Test
    void countryCodeUnsupported() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, groupId, platformIds, platformsCreated, "RU", languages, customerId, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("countryCode"));
    }

    @Test
    void languagesNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, groupId, platformIds, platformsCreated, countryCode, null, customerId, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("language"));
    }

    @Test
    void languagesEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, groupId, platformIds, platformsCreated, countryCode, Set.of(), customerId, userNickNames, null, platformStatuses);
        });
        assertTrue(exception.getMessage().contains("language"));
    }

    // Not useful since we've migrated language specification to a Set of
    //@Test
    //void languagesUnsupported() {
    //    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
    //        List<String> unsupported = new ArrayList<>();
    //        unsupported.add("ru");
    //        new User(id, groupId, platformIds, platformsCreated, countryCode, unsupported, customerId, userNickNames, null, platformStatuses);
    //    });
    //    assertTrue(exception.getMessage().contains("language"));
    //}

    @Test
    void platformStatusesNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, groupId, platformIds, platformsCreated, countryCode, languages, customerId, userNickNames, null, null);
        });
        assertTrue(exception.getMessage().contains("platformStatus"));
    }

    @Test
    void platformStatusesEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, groupId, platformIds, platformsCreated, countryCode, languages, customerId, userNickNames, null, Map.of());
        });
        assertTrue(exception.getMessage().contains("platformStatus"));
    }

}