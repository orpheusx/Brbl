package com.enoughisasgoodasafeast.operator;

import io.jenetics.util.NanoClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class UserTest {

    final static UUID id = UUID.randomUUID();
    final static Map<Platform, String> platformIds = new HashMap<>();
    final static Map<Platform, Instant> platformsCreated = new LinkedHashMap<>();
    final static String countryCode = Locale.getDefault().getCountry();
    final static List<String> languages = new ArrayList<>(1);

    static {
        platformIds.put(Platform.SMS, "17815551234");
        platformsCreated.put(Platform.SMS, NanoClock.systemUTC().instant());
        languages.add("SPA");
        languages.add("FRA");
        languages.add("ENG");
    }

    @Test
    void idNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(null, platformIds, platformsCreated, countryCode, languages);
        });

        assertTrue(exception.getMessage().contains("id"));
    }

    @Test
    void platformIdsNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, null, platformsCreated, countryCode, languages);
        });
        assertTrue(exception.getMessage().contains("platformIds"));
    }

    @Test
    void platformIdsEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, new HashMap<>(),platformsCreated, countryCode, languages);
        });
        assertTrue(exception.getMessage().contains("platformIds"));
    }

    @Test
    void countryCodeNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, platformIds, platformsCreated, null, languages);
        });
        assertTrue(exception.getMessage().contains("countryCode"));
    }

    @Test
    void countryCodeUnsupported() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, platformIds, platformsCreated, "RU", languages);
        });
        assertTrue(exception.getMessage().contains("countryCode"));
    }

    @Test
    void languagesNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, platformIds, platformsCreated, countryCode, null);
        });
        assertTrue(exception.getMessage().contains("language"));
    }

    @Test
    void languagesEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, platformIds, platformsCreated, countryCode, new ArrayList<>());
        });
        assertTrue(exception.getMessage().contains("language"));
    }

    @Test
    void languagesUnsupported() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            List<String> unsupported = new ArrayList<>();
            unsupported.add("ru");
            new User(id, platformIds, platformsCreated, countryCode, unsupported);
        });
        assertTrue(exception.getMessage().contains("language"));
    }

}