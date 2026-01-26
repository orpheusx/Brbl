package com.enoughisasgoodasafeast.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CampaignUserReport {
    List<UUID> campaignUserIds;
    List<DeliveryStatus> deliveryStatuses;

    public CampaignUserReport() {
        this.campaignUserIds = new ArrayList<>();
        this.deliveryStatuses = new ArrayList<>();
    }
}
