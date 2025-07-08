package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.SharedConstants;
import io.jenetics.util.NanoClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CustomerTest {
    final static UUID id = UUID.randomUUID();
    final static Map<Platform, String> platformIds = Map.of(Platform.SMS, "17815551234");
    final static Map<Platform, Instant> platformCreatedMap = Map.of(Platform.SMS, NanoClock.utcInstant());
    final static String countryCode = Locale.getDefault().getCountry();
    final static List<String> languages = List.of("SPA", "FRA");

    final static User user = new User(id, platformIds, platformCreatedMap, countryCode, languages);

    final String firstName = "Fred";
    final String surname = "Flintstone";
    final String companyName = "Hanna-Barbera";

    @Test
    public void createOk() {
        assertDoesNotThrow(() -> new Customer(null, user, firstName, surname, companyName));
    }

    @Test
    public void createOkNoCompanyName() {
        assertDoesNotThrow(() -> {
            Customer c = new Customer(null, user, firstName, surname, null);
            assertEquals(SharedConstants.NO_COMPANY, c.companyName());
        });
    }

    @Test
    public void userNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new Customer(null, null, firstName, surname, companyName);
        });
        assertTrue(exception.getMessage().contains("user"));
    }

    @Test
    public void firstNameNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
            new Customer(null, user, null, "Flintstone", companyName)
        );
        assertTrue(exception.getMessage().contains("firstName"));
    }

    @Test
    public void surnameNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
            new Customer(null, user, firstName, null, companyName)
        );
        assertTrue(exception.getMessage().contains("surname"));
    }

}