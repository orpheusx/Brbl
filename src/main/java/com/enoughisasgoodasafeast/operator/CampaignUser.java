package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.QueueProducer;

import java.time.Instant;
import java.util.UUID;

/*
 * The record class corresponding to the CAMPAIGN_USERS table joined with USERS.
 * This could just be a composition of User and Profile records?
 */
public record CampaignUser(UUID id,
                           // User
                           String platformId,
                           UserStatus userStatus,
                           CountryCode countryCode,
                           String nickname,
                           LanguageCode languageCode,
                           // CampaignUser
                           DeliveryStatus deliveryStatus,
                           // Profile
                           String givenName,
                           String surname,
                           // Session
                           Instant sessionLastUpdatedAt,
                           // Node
                           Node node,
                           // non-value support components
                           QueueProducer queueProducer,
                           PersistenceManager persistenceManager) {
}
