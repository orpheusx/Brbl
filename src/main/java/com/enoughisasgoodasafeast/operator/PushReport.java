package com.enoughisasgoodasafeast.operator;

import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
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
    boolean companyStatusNotActive;

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
    ArrayList<UUID> usersWithActiveSessionsSkipped = new ArrayList<>(); // amalgam.group_id

    // The users that had errors while processing their script
    ArrayList<UUID> usersSkippedDueToScriptErrors = new ArrayList<UUID>();

    ArrayList<UUID> processedUsers = new ArrayList<UUID>();

    public boolean campaignUsersStatusUpdateFail;

    public boolean campaignCompletionTimeUpdateFail;


    public PushReport(UUID campaignId) {
        this.startPush = NanoClock.utcInstant();
        this.campaignId = campaignId;
    }

    public void campaignNotFoundFail() {
        this.campaignNotFound = true;
        LOG.error("No push campaign found for {}", campaignId);
        end();
    }

    public void campaignStatusNotActiveFail(CompanyStatus status) {
        this.companyStatusNotActive = true;
        LOG.error("Company ({}) owning campaign is not ACTIVE ({}.) Skipping execution.",
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

    /*
     * This is probably the worst kind of error. How do we handle it?
     */
    public void campaignUsersStatusUpdateFail() {
        this.campaignUsersStatusUpdateFail = true;
        LOG.error("Failed to update campaign users status for campaign {}", campaignId);
        end();
    }

    public void campaignCompletionTimeUpdateFail() {
        this.campaignCompletionTimeUpdateFail = true;
        LOG.error("Failed to update campaign completion time for campaign {}", campaignId);
        end();
    }

    /* Business method to evaluate the push's various metrics.
     * If true, the campaign was "complete" and should not be re-tried. (This is a little ambiguous. Could we simplify it?)
     * False, if there were any retriable failures. Such failures could be a reflection of transient service problems or
     * in-flight changes to user/script/route/customer states.
     * To be clear, "complete" does not imply "successful."
     */
    public boolean isPushComplete() {
        return campaignNotFound || campaignUsersEmpty || companyStatusNotActive || nodeNotFound || scriptStatusNotProd || routeStatusNotActive;
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
                .add("customerStatusNotActive=" + companyStatusNotActive)
                .add("scriptId=" + scriptId)
                .add("nodeNotFound=" + nodeNotFound)
                .add("scriptStatusNotProd=" + scriptStatusNotProd)
                .add("campaignUsersEmpty=" + campaignUsersEmpty)
                .add("numUsers=" + numUsers)
                .add("invalidUsersSkipped=" + invalidUsersSkipped)
                .add("activeUsersSkipped=" + usersWithActiveSessionsSkipped)
                .add("usersSkippedDueToScriptErrors=" + usersSkippedDueToScriptErrors)
                .add("campaignAndUserStatusUpdateFail=" + campaignUsersStatusUpdateFail)
                .toString();
    }

}
