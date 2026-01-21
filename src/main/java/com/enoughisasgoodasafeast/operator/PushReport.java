package com.enoughisasgoodasafeast.operator;

import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
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

    boolean campaignUsersEmpty;

    // Number of users in the campaign users segment
    int numUsers;

    // Number of users skipped because they were not opted in at time of push.
    int numInvalidUsers;


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

    public void scriptStatusNotProdFail(ScriptStatus status) {
        this.scriptStatusNotProd = true;
        LOG.warn("Script {} status is not PROD ({})", scriptId, status.name());
        end();
    }

    public void nodeNotFoundFail(UUID nodeId) {
        this.nodeNotFound = true;
        LOG.error("No node found for {}", nodeId);
        end();
    }

    public void end() {
        this.endPush = NanoClock.utcInstant();
    }

}
