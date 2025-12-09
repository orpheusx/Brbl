package com.enoughisasgoodasafeast.operator;

import io.jenetics.util.NanoClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class UserTest {

    final static UUID id = UUID.randomUUID();
    final static UUID customerId = UUID.randomUUID();
    final static Map<Platform, String> platformIds = new HashMap<>();
    final static Map<Platform, Instant> platformsCreated = new LinkedHashMap<>();
    final static Map<Platform, String> userNickNames = new LinkedHashMap<>();
    final static String countryCode = Locale.getDefault().getCountry();
    final static List<String> languages = new ArrayList<>(1);

    static {
        platformIds.put(Platform.SMS, "17815551234");
        platformsCreated.put(Platform.SMS, NanoClock.systemUTC().instant());
        languages.add("SPA");
        languages.add("FRA");
        languages.add("ENG");
        userNickNames.put(Platform.SMS, "Boo");
    }

    @Test
    void idNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(null, platformIds, platformsCreated, userNickNames, countryCode, languages, customerId);
        });

        assertTrue(exception.getMessage().contains("id"));
    }

    @Test
    void platformIdsNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, null, platformsCreated, userNickNames, countryCode, languages, customerId);
        });
        assertTrue(exception.getMessage().contains("platformIds"));
    }

    @Test
    void platformIdsEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, new HashMap<>(),platformsCreated, userNickNames, countryCode, languages, customerId);
        });
        assertTrue(exception.getMessage().contains("platformIds"));
    }

    @Test
    void countryCodeNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, platformIds, platformsCreated, userNickNames, null, languages, customerId);
        });
        assertTrue(exception.getMessage().contains("countryCode"));
    }

    @Test
    void countryCodeUnsupported() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, platformIds, platformsCreated, userNickNames, "RU", languages, customerId);
        });
        assertTrue(exception.getMessage().contains("countryCode"));
    }

    @Test
    void languagesNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, platformIds, platformsCreated, userNickNames, countryCode, null, customerId);
        });
        assertTrue(exception.getMessage().contains("language"));
    }

    @Test
    void languagesEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, platformIds, platformsCreated, userNickNames, countryCode, new ArrayList<>(), customerId);
        });
        assertTrue(exception.getMessage().contains("language"));
    }

    @Test
    void languagesUnsupported() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            List<String> unsupported = new ArrayList<>();
            unsupported.add("ru");
            new User(id, platformIds, platformsCreated, userNickNames, countryCode, unsupported, customerId);
        });
        assertTrue(exception.getMessage().contains("language"));
    }

}