package com.enoughisasgoodasafeast.operator;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/*
 * A record class roughly corresponding to the CAMPAIGN_USERS table joined with a bunch of others.
 * Used to encapsulate components for each target of a push campaign.
 */
public record CampaignUser(UUID campaignId,
                           UUID groupId,
                           User user,
                           DeliveryStatus deliveryStatus,
                           Instant sessionLastUpdatedAt) implements Serializable {

    // Only the collection properties of a record can be updated.
    // That works for our purposes here but we if we need to do more we have to return a new instance.
    public CampaignUser merge(CampaignUser other) {
        this.user.merge(other.user);
        return this;
    }

}
