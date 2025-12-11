package com.enoughisasgoodasafeast.operator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
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
 *  @param id the identifier used within the Brbl ecosystem.
 *
 * @param platformIds           the identifiers for this User on other messaging platforms.
 * @param platformCreationTimes the creation time for this User for each messaging platform.
 * @param countryCode           the ISO country of the nation where the User lives.
 * @param languages             the list of ISO language codes spoken by the User.
 * @param customerId            the Customer that acquired this User instance.
 * @param platformNickNames     the optional nicknames for this User on other messaging platforms.
 * @param profile               the optional Profile that provides the User's identity.
 */

public record User(
        UUID id, // aka group_id
        Map<Platform, String> platformIds,
        Map<Platform, Instant> platformCreationTimes,
        String countryCode,
        List<String> languages,
        UUID customerId,
        Map<Platform, String> platformNickNames,
        Profile profile) implements Serializable
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

        if (platformCreationTimes == null || platformCreationTimes.isEmpty()) {
            fail("platformCreationTimes cannot be null or empty.");
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

        if (customerId == null) {
            fail("customerId cannot be null");
        }

        // NB: Perfectly fair for the User to not have any nicknames or be associated with a Profile.

        LOG.debug("Created new User (id:{})", id);
    }

    // Convenience constructor.
    // TODO Create a countryCode enum class, matching our schema type already defines (US, CA, MX)
    public User(Map<Platform, String> platformIds, Map<Platform, Instant> platformCreationTimes, List<String> languages, UUID customerId) {
        this(UUID.randomUUID(), platformIds, platformCreationTimes, "US", languages, customerId, Map.of(), null);
    }

    void fail(String message) {
        throw new IllegalArgumentException(message);
    }
}
