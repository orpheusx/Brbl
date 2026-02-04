package com.enoughisasgoodasafeast.operator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The User model contains all the channel specific incarnations of a human user, the customer of our Customer.
 * These incarnations are distinguished by the Platform through which they communicate.
 * We materialize the data for all the rows linked through the amalgams table so that Scripts can make use of it.
 * A User lives in a single country but may speak multiple languages.
 * They may be identified differently by the Platforms they use to communicate.
 * Their primary id is their Brbl defined id.
 * <p>
 * The validation of country codes is limited to what the platform supports.
 * TODO Currently, this is hardcoded and we need to change it.
 *
 * @param id                    the identifier used within the Brbl ecosystem.
 * @param groupId               the identifier used to link the same user on different platforms together.
 * @param platformIds           the identifiers for this User on other messaging platforms.
 * @param platformCreationTimes the creation time for this User for each messaging platform.
 * @param countryCode           the ISO country of the nation where the User lives.
 * @param languages             the list of ISO language codes spoken by the User.
 * @param customerId            the Customer that acquired this User instance.
 * @param platformNickNames     the optional nicknames for this User on other messaging platforms.
 * @param platformProfiles      the optional Profiles associated with this User for each messaging platform.
 * @param platformStatus        the User controlled opt-in status for each Platform.
 */

// TODO convert the type of 'languages' to LanguageCode and 'countryCode' to CountryCode.
//  Also, make 'countryCode' a map like the other properties that are implicitly collections.
public record User(
        UUID id,
        UUID groupId,
        Map<Platform, String> platformIds,
        Map<Platform, Instant> platformCreationTimes,
        String countryCode, // FIXME why isn't this a map as well?
        Set<LanguageCode> languages, // FIXME make this a Set of LanguageCode.
        UUID customerId, // no map because the grouping of User rows is on the basis of the Customer claiming the User.
        Map<Platform, String> platformNickNames,
        Map<Platform, Profile> platformProfiles,
        Map<Platform,UserStatus> platformStatus
    ) implements Serializable {

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

        for (LanguageCode language : languages) {
            switch (language) {
                case FRA, ENG, RUS, SPA, CMN, YUE, HAT, POR -> {} // convert the string
                default -> fail("Unsupported language.");
            }
        }

        if (customerId == null) {
            fail("customerId cannot be null");
        }

        if (platformStatus == null || platformStatus.isEmpty()) {
            fail("platformStatus cannot be null or empty.");
        }

        // NB: Perfectly fair for the User to not have any nicknames or be associated with a Profile.

        LOG.debug("Created new User (id:{})", id);
    }

    // Convenience constructor.
    // TODO Create a countryCode enum class, matching our schema type already defines (US, CA, MX)
//    public User(Map<Platform, String> platformIds, Map<Platform, Instant> platformCreationTimes, Set<String> languages,
//                UUID customerId, Map<Platform, UserStatus> platformStatus) {
//        this(randomUUID(), randomUUID(), platformIds, platformCreationTimes,
//                "US", languages, customerId, Map.of(), null, platformStatus);
//    }

    void fail(String message) {
        throw new IllegalArgumentException(message);
    }

    public void merge(User other) {
        this.platformIds.putAll(other.platformIds());
        this.platformCreationTimes.putAll(other.platformCreationTimes());
        this.platformNickNames.putAll(other.platformNickNames());
        this.platformProfiles.putAll(other.platformProfiles());
        this.platformStatus.putAll(other.platformStatus());
        this.languages.addAll(other.languages());
    }

}
