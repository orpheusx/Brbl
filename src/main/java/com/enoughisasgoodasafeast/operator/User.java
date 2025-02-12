package com.enoughisasgoodasafeast.operator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @param countryCode the ISO country of the nation where the User lives.
 * @param languages the list of ISO language codes spoken by the User.
 */

public record User(
        UUID id,
        Map<Platform, String> platformIds,
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
            case "CA","MX","US" -> {}
            default -> fail("Unsupported countryCode");
        }

        if (languages == null || languages.isEmpty()) {
            fail("languages cannot be null or empty.");
        }

        for (String language : languages) {
            switch (language) {
                case "es","fr","en" -> {}
                default -> fail("Unsupported language.");
            }
        }

        LOG.info("Created User (id:{})", id);
    }

    void fail(String message) {
        throw new IllegalArgumentException(message);
    }
}
