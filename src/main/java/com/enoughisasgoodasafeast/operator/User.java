package com.enoughisasgoodasafeast.operator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
 * They may be identified differently by the Platforms they use to communicate. SMS and WhatsApp use a phone number while
 *  other platforms use opaque identifiers. By convention, we call these platform specific identifiers, platformNumbers.
 * Their primary id is their Brbl defined id, a UUID.
 * <p>
 * The validation of country codes is limited to what the platform supports.
 * TODO Currently, this is hardcoded and we need to change it.
 *
 * @param platformIds           the identifier used within the Brbl ecosystem.
 * @param groupId               the identifier used to link the same user on different platforms together.
 * @param platformNumbers       the identifiers for this User on other messaging platforms.
 * @param platformCreationTimes the creation time for this User for each messaging platform.
 * @param countryCode           the ISO country of the nation where the User lives.
 * @param languages             the list of ISO language codes spoken by the User.
 * @param claimantId            the identifier of the Customer that claims this User instance.
 * @param customerId            the optional Customer record. If not-null this indicates the User is also a Customer.
 * @param platformNickNames     the optional nicknames for this User on other messaging platforms.
 * @param platformProfiles      the optional Profiles associated with this User for each messaging platform.
 * @param platformStatus        the User controlled opt-in status for each Platform.
 */

// TODO make 'countryCode' a map like the other properties that are implicitly collections.
public record User(
        @NonNull Map<Platform, UUID> platformIds,
        @NonNull UUID groupId,
        @NonNull Map<Platform, String> platformNumbers,
        @NonNull Map<Platform, Instant> platformCreationTimes,
        @NonNull String countryCode,
        @NonNull Set<LanguageCode> languages,
        @NonNull UUID claimantId, // Not a map because the grouping of User rows is on the basis of the claimant_id, the Customer claiming the User.
        @Nullable UUID customerId, // Optional but, if present, same cardinality as groupId and claimantId
        @NonNull Map<Platform, String> platformNickNames,
        @Nullable Map<Platform, Profile> platformProfiles,
        @NonNull Map<Platform,UserStatus> platformStatus
    ) implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(User.class);

    public User {
        // validations (we still do this to help debug (de)serialization problems.)
        if (platformIds == null || platformIds.isEmpty()) {
            fail("platformIds cannot be null.");
        }

        if (platformNumbers == null || platformNumbers.isEmpty()) {
            fail("platformNumbers cannot be null or empty.");
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

        if (claimantId == null) {
            fail("claimantId cannot be null");
        }

        if (platformStatus == null || platformStatus.isEmpty()) {
            fail("platformStatus cannot be null or empty.");
        }

        // NB: Perfectly fair for the User to not have any nicknames or be associated with a Profile.

        LOG.debug("Created new User (id:{})", platformIds);
    }

    void fail(String message) {
        throw new IllegalArgumentException(message);
    }

    public void merge(User other) {
        // Only the paranoid survive...
        if(!this.groupId.equals(other.groupId) || !this.claimantId.equals(other.claimantId)) {
            throw new IllegalArgumentException("Cannot merge Users with different groupId or claimantId.");
        }
        if (this.customerId != null && other.customerId != null && !this.customerId.equals(other.customerId)) {
            throw new IllegalArgumentException("Cannot merge Users with different customerId.");
        }
        this.platformIds.putAll(other.platformIds);
        this.platformNumbers.putAll(other.platformNumbers());
        this.platformCreationTimes.putAll(other.platformCreationTimes());
        this.platformNickNames.putAll(other.platformNickNames());
        if (this.platformProfiles != null && other.platformProfiles() != null) {
            this.platformProfiles.putAll(other.platformProfiles());
        }
        this.platformStatus.putAll(other.platformStatus());
        this.languages.addAll(other.languages());
    }

}
