package com.enoughisasgoodasafeast.operator;

import java.time.Instant;
import java.util.UUID;

/**
 * Contains the metadata needed for a "push" messaging campaign.
 *
 * @param id the primary key for the row in the underlying table.
 * @param customerId the id of the Customer that created this campaign.
 * @param description explains the intent of the campaign.
 * @param scriptId the id of the script that will be used for the execution of the campaign.
 * @param createdAt when the push campaign definition was first created.
 * @param updatedAt when the latest change was made to an incomplete push campaign.
 * @param completedAt timestamp set only after we've completed sending the script's initial message. Null until then.
 * @param customerStatus the enum denoting the status of the identified Customer.
 * @param scriptStatus the status of the Script referenced by the campaign.
 * @param nodeId the id of the starting Node referenced by the Script.
 * @param channel the channel for the produced MTs.
 * @param platform the platform of the channel.
 * @param routeStatus the state of the associated Route.
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
                           UUID nodeId,
                           String channel,
                           Platform platform,
                           RouteStatus routeStatus) {
}
