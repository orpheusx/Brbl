package com.enoughisasgoodasafeast.operator;

import java.time.Instant;
import java.util.UUID;

/**
 * Contains the metadata needed for a "push" messaging campaign.
 *
 * @param id
 * @param customer_id the Customer that created this campaign.
 * @param description explains the intent of the campaign.
 * @param script_id the script that will be used for the execution of the campaign.
 * @param created_at when the push campaign definition was first created.
 * @param updated_at the latest change made to an incomplete push campaign.
 * @param completed_at set only after we've completed sending the script's initial message. Null until then.
 */
public record PushCampaign(UUID id,
                           UUID customer_id,
                           String description,
                           UUID script_id,
                           Instant created_at,
                           Instant updated_at,
                           Instant completed_at) {
}
