package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.SharedConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CustomerTest {
    final static UUID id = UUID.randomUUID();
    final static Map<Platform, String> platformIds = Map.of(Platform.SMS, "17815551234");
    final static String countryCode = Locale.getDefault().getCountry();
    final static List<String> languages = List.of("es", "fr");

    final static User user = new User(id, platformIds, countryCode, languages);

    final String firstName = "Fred";
    final String surname = "Flintstone";
    final String companyName = "Hanna-Barbera";

    @Test
    public void createOk() {
        assertDoesNotThrow(() -> new Customer(user, firstName, surname, companyName));
    }

    @Test
    public void createOkNoCompanyName() {
        assertDoesNotThrow(() -> {
            Customer c = new Customer(user, firstName, surname, null);
            assertEquals(SharedConstants.NO_COMPANY, c.companyName());
        });
    }

    @Test
    public void userNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new Customer(null, firstName, surname, companyName);
        });
        assertTrue(exception.getMessage().contains("user"));
    }

    @Test
    public void firstNameNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
            new Customer(user, null, "Flintstone", companyName)
        );
        assertTrue(exception.getMessage().contains("firstName"));
    }

    @Test
    public void surnameNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
            new Customer(user, firstName, null, companyName)
        );
        assertTrue(exception.getMessage().contains("surname"));
    }

}