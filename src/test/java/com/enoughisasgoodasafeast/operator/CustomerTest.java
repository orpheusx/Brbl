package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.SharedConstants;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static org.junit.jupiter.api.Assertions.*;

public class CustomerTest {
    static final Map<Platform, UUID> platformIds = Map.of(Platform.SMS, randomUUID());
    static final UUID groupId = randomUUID();
    static final Map<Platform, String> platformNumbers = Map.of(Platform.SMS, "17815551234");
    static final Map<Platform, Instant> platformCreatedMap = Map.of(Platform.SMS, Instant.now());
    static final Map<Platform, String> userNickNames = new LinkedHashMap<>();
    static final String countryCode = Locale.getDefault().getCountry();
    static final Set<LanguageCode> languages = Set.of(LanguageCode.SPA, LanguageCode.FRA);
    static final UUID claimantId = randomUUID();
    static final Map<Platform, UserStatus> userStatuses = Map.of(Platform.SMS, UserStatus.IN);

    static final User user = new User(platformIds, groupId, platformNumbers, platformCreatedMap, countryCode, languages, claimantId, null, userNickNames, null, userStatuses);

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

}