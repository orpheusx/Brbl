package com.enoughisasgoodasafeast.operator;

import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * A summary of the push operation execution.
 * Each of the boolean properties is a possible cause for the push op to fail.
 */
public class PushReport {

    private static final Logger LOG = LoggerFactory.getLogger(PushReport.class);

    public Instant startPush;
    public Instant endPush;

    UUID campaignId;
    boolean campaignNotFound;

    UUID customerId;
    boolean customerStatusNotActive;

    UUID scriptId;

    boolean nodeNotFound;
    boolean scriptStatusNotProd;
    boolean routeStatusNotActive;

    // True if there were no user associated with the PushCampaign
    boolean campaignUsersEmpty;

    // Number of users in the campaign users segment
    int numUsers;

    // The users skipped because they were not opted in at time of push.
    ArrayList<UUID> invalidUsersSkipped = new ArrayList<>(); // amalgam.group_id

    // The users skipped because they were already actively talking to the platform.
    ArrayList<UUID> activeUsersSkipped = new ArrayList<>(); // amalgam.group_id

    // The users that had errors while processing their script
    ArrayList<UUID> usersSkippedDueToScriptErrors = new ArrayList<UUID>();

    ArrayList<UUID> processedUsers = new ArrayList<UUID>();

    private boolean campaignAndUserStatusUpdateFail;


    public PushReport(UUID campaignId) {
        this.startPush = NanoClock.utcInstant();
        this.campaignId = campaignId;
    }

    public void campaignNotFoundFail() {
        this.campaignNotFound = true;
        LOG.error("No push campaign found for {}", campaignId);
        end();
    }

    public void campaignStatusNotActiveFail(CustomerStatus status) {
        this.customerStatusNotActive = true;
        LOG.error("Customer ({}) owning campaign is not ACTIVE ({}.) Skipping execution.",
                this.customerId, status.name());
        end();
    }

    public void nodeNotFoundFail(UUID nodeId) {
        this.nodeNotFound = true;
        LOG.error("No node found for {}", nodeId);
        end();
    }

    public void scriptStatusNotProdFail(ScriptStatus status) {
        this.scriptStatusNotProd = true;
        LOG.warn("Script {} status is not PROD ({})", scriptId, status.name());
        end();
    }

    public void routeStatusNotActive(RouteStatus routeStatus) {
        this.routeStatusNotActive = true;
        LOG.warn("Route status is not ACTIVE ({}.)", routeStatus.name());
        end();
    }

    public void campaignUsersEmptyFail() {
        this.campaignUsersEmpty = true;
        LOG.error("No campaign users found for campaign {}", campaignId);
        end();
    }

    public void campaignAndUserStatusUpdateFail() {
        this.campaignAndUserStatusUpdateFail = true;
    }

    public void end() {
        this.endPush = NanoClock.utcInstant();
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", PushReport.class.getSimpleName() + "[", "]")
                .add("campaignId=" + campaignId)
                .add("startPush=" + startPush)
                .add("endPush=" + endPush)
                .add("campaignNotFound=" + campaignNotFound)
                .add("customerId=" + customerId)
                .add("customerStatusNotActive=" + customerStatusNotActive)
                .add("scriptId=" + scriptId)
                .add("nodeNotFound=" + nodeNotFound)
                .add("scriptStatusNotProd=" + scriptStatusNotProd)
                .add("campaignUsersEmpty=" + campaignUsersEmpty)
                .add("numUsers=" + numUsers)
                .add("invalidUsersSkipped=" + invalidUsersSkipped)
                .add("activeUsersSkipped=" + activeUsersSkipped)
                .add("usersSkippedDueToScriptErrors=" + usersSkippedDueToScriptErrors)
                .add("campaignAndUserStatusUpdateFail=" + campaignAndUserStatusUpdateFail)
                .toString();
    }

}
