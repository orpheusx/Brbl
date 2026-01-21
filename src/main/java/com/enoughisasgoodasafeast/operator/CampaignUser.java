package com.enoughisasgoodasafeast.operator;

import java.time.Instant;
import java.util.UUID;

/*
 * The record class corresponding to the CAMPAIGN_USERS table joined with USERS.
 * This could just be a composition of User and Profile records?
 */
public record CampaignUser(UUID id, String platformId, UserStatus userStatus,CountryCode countryCode, String nickname,
                           LanguageCode languageCode, DeliveryStatus deliveryStatus, String givenName, String surname,
                           Instant sessionLastUpdatedAt) {
}
