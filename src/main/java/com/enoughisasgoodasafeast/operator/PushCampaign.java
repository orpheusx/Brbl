package com.enoughisasgoodasafeast.operator;

import java.time.Instant;
import java.util.UUID;

/**
 * Contains the metadata needed for a "push" messaging campaign.
 *
 * @param id
 * @param customerId the Customer that created this campaign.
 * @param description explains the intent of the campaign.
 * @param scriptId the script that will be used for the execution of the campaign.
 * @param createdAt when the push campaign definition was first created.
 * @param updatedAt the latest change made to an incomplete push campaign.
 * @param completedAt set only after we've completed sending the script's initial message. Null until then.
 * @param customerStatus the enum denoting the status of the identified Customer.
 */
public record PushCampaign(UUID id,
                           UUID customerId,
                           String description,
                           UUID scriptId,
                           Instant createdAt,
                           Instant updatedAt,
                           Instant completedAt,
                           CustomerStatus customerStatus,
                           ScriptStatus scriptStatus,
                           UUID nodeId) {
}
