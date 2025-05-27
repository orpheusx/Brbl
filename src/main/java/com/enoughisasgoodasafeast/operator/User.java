package com.enoughisasgoodasafeast.operator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A User lives in a single country but may speak multiple languages.
 * They may be identified differently by the Platforms they use to communicate.
 * Their primary id is their Brbl defined id.
 * <p>
 * The validation of country codes is limited to what the platform supports.
 * TODO Currently, this is hardcoded and we need to change it.
 *
 * @param id the identifier used within the Brbl ecosystem.
 * @param platformIds the identifiers for this User on other messaging platforms.
 * @param platformCreationTimes the creation time for this User for each messaging platform.
 * @param countryCode the ISO country of the nation where the User lives.
 * @param languages the list of ISO language codes spoken by the User.
 */

public record User(
        UUID id,
        Map<Platform, String> platformIds,
        Map<Platform, Instant> platformCreationTimes,
        String countryCode,
        List<String> languages)
{
    private static final Logger LOG = LoggerFactory.getLogger(User.class);

    public User {
        // validations
        if (id == null) {
            fail("id cannot be null.");
        }

        if (platformIds == null || platformIds.isEmpty()) {
            fail("platformIds cannot be null or empty.");
        }

        if (countryCode == null) {
            fail("countryCode cannot be null");
        }

        switch (countryCode) { // TODO read supported list from a .properties?
            case "CA","MX","US" -> {} // TODO create a Country enum and use it here somehow? This would mirror the type definition in the database...
            default -> fail("Unsupported countryCode");
        }

        if (languages == null || languages.isEmpty()) {
            fail("languages cannot be null or empty.");
        }

        for (String language : languages) {
            switch (language) {
                case "SPA","FRA","ENG" -> {} // TODO create a Language enum and use it here somehow?
                default -> fail("Unsupported language.");
            }
        }

        LOG.info("Created new User (id:{})", id);
    }

    void fail(String message) {
        throw new IllegalArgumentException(message);
    }
}
