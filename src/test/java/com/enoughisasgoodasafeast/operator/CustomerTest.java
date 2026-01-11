package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.SharedConstants;
import io.jenetics.util.NanoClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CustomerTest {
    final static UUID id = UUID.randomUUID();
    final static UUID groupId = UUID.randomUUID();
    final static Map<Platform, String> platformIds = Map.of(Platform.SMS, "17815551234");
    final static Map<Platform, Instant> platformCreatedMap = Map.of(Platform.SMS, NanoClock.utcInstant());
    final static Map<Platform, String> userNickNames = new LinkedHashMap<>();
    final static String countryCode = Locale.getDefault().getCountry();
    final static List<String> languages = List.of("SPA", "FRA");
    final static UUID customerId = UUID.randomUUID();
    final static Map<Platform, UserStatus> userStatuses = Map.of(Platform.SMS, UserStatus.IN);

    final static User user = new User(id, groupId, platformIds, platformCreatedMap, countryCode, languages, customerId, userNickNames, null, userStatuses);

    // final String firstName = "Fred";
    // final String surname = "Flintstone";
    // final String companyName = "Hanna-Barbera";

    @Test
    public void createOk() {
        assertDoesNotThrow(() -> new Customer(null, user, null, null));
    }

    @Test
    public void createOkNoCompanyName() {
        assertDoesNotThrow(() -> {
            Customer c = new Customer(null, user, null, null);
            assertEquals(SharedConstants.NO_COMPANY, c.companyName());
        });
    }

    @Test
    public void userNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new Customer(null, null, null, null);
        });
        assertTrue(exception.getMessage().contains("user"));
    }

    //@Test
    //public void firstNameNull() {
    //    Exception exception = assertThrows(IllegalArgumentException.class, () ->
    //            new Customer(null, user, null, null)
    //    );
    //    assertTrue(exception.getMessage().contains("firstName"));
    //}

    //@Test
    //public void surnameNull() {
    //    Exception exception = assertThrows(IllegalArgumentException.class, () ->
    //        new Customer(null, user, /*firstName, null, companyName,*/ null, null)
    //    );
    //    assertTrue(exception.getMessage().contains("surname"));
    //}

}