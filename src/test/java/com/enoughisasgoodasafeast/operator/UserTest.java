package com.enoughisasgoodasafeast.operator;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class UserTest {

    final static UUID id = UUID.randomUUID();
    final static Map<Platform, String> platformIds = new HashMap<>();
    final static String countryCode = Locale.getDefault().getCountry();
    final static List<String> languages = new ArrayList<>(1);

    static {
        platformIds.put(Platform.SMS, "17815551234");
        languages.add("es");
        languages.add("fr");
        languages.add("en");
    }

    @Test
    void idNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(null, platformIds, countryCode, languages);
        });

        assertTrue(exception.getMessage().contains("id"));
    }

    @Test
    void platformIdsNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, null, countryCode, languages);
        });
        assertTrue(exception.getMessage().contains("platformIds"));
    }

    @Test
    void platformIdsEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, new HashMap<>(), countryCode, languages);
        });
        assertTrue(exception.getMessage().contains("platformIds"));
    }

    @Test
    void countryCodeNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, platformIds, null, languages);
        });
        assertTrue(exception.getMessage().contains("countryCode"));
    }

    @Test
    void countryCodeUnsupported() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, platformIds, "RU", languages);
        });
        assertTrue(exception.getMessage().contains("countryCode"));
    }

    @Test
    void languagesNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, platformIds, countryCode, null);
        });
        assertTrue(exception.getMessage().contains("language"));
    }

    @Test
    void languagesEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, platformIds, countryCode, new ArrayList<>());
        });
        assertTrue(exception.getMessage().contains("language"));
    }

    @Test
    void languagesUnsupported() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            List<String> unsupported = new ArrayList<>();
            unsupported.add("ru");
            new User(id, platformIds, countryCode, unsupported);
        });
        assertTrue(exception.getMessage().contains("language"));
    }
}