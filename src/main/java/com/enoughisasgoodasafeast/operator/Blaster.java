package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IO;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * A standalone service that manages 'blasting' out push campaigns.
 */
public class Blaster {

    private static final Logger LOG = LoggerFactory.getLogger(Blaster.class);

    private PersistenceManager persistenceManager;
    private QueueProducer queueProducer;
    private ScriptEngine scriptEngine;
    public Duration sessionLifetime;

    public Blaster() {
    }

    public Blaster(PostgresPersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        this.scriptEngine = new ScriptEngine(persistenceManager);
    }

    public Blaster(QueueProducer queueProducer) {
        this.queueProducer = queueProducer;
    }

    public void init(Properties props) throws PersistenceManagerException, IOException, TimeoutException {
        if (persistenceManager == null) {
            persistenceManager = PostgresPersistenceManager.createPersistenceManager(props);
            LOG.info("ppm created.");
        }

        if(scriptEngine == null) {
            scriptEngine = new ScriptEngine(persistenceManager);
        }

        if(queueProducer == null) {
            queueProducer = RabbitQueueProducer.createQueueProducer(props);
        }

        if (sessionLifetime == null) {
            sessionLifetime = Duration.ofMinutes(20); // FIXME get the global default from props;
        }
    }

    /*
     * The updatedAt column of a Session cannot be null (per SQL constraint) but if the sessions table is included as an
     * outer join on the query then we may get a null value when there's no Session present. If a Session doesn't exist
     * then it doesn't make much sense to ask if it has expired. */
    public boolean isSessionExpired(@NonNull Instant lastUpdateAt) {
        // FIXME whether an not expired session can be overwritten should be a push campaign param.
        return sessionLifetime.compareTo(Duration.between(lastUpdateAt, Instant.now())) <= 0;
    }

    /**
     * Assumes the caller (Quartz or similar) knows which campaign id to execute.
     *
     * @param campaignId the id of the campaign to be executed.
     * @throws InterruptedException if one of the StructuredTaskScope tasks throws.
     * @throws ExecutionException   if one of the StructuredTaskScope tasks throws.
     */
    public PushReport exec(@NonNull UUID campaignId) throws InterruptedException, ExecutionException, SQLException {
        // Collect needed data:
        //  Read the Campaign from PUSH_CAMPAIGNS
        //  Fetch the associated graph of Nodes for the campaign's script id. (use the standard method for this.)
        //
        var report = new PushReport(campaignId);

        final var campaign = persistenceManager.getPushCampaign(campaignId);
        if (campaign == null) {
            report.campaignNotFoundFail();
            return report;
        }

        LOG.info("Fetched: {}", campaign);

        final var customerStatus = campaign.customerStatus();
        if (!customerStatus.equals(CustomerStatus.ACTIVE)) {
            report.campaignStatusNotActiveFail(customerStatus);
            return report;
        }

        final var scriptStatus = campaign.scriptStatus();
        if (!scriptStatus.equals(ScriptStatus.PROD)) {
            report.scriptStatusNotProdFail(scriptStatus);
            return report;
        }

        // Read the specified script graph (Call scriptCache.refresh(scriptID) to insure we have the latest variant?)
        var node = persistenceManager.getNodeGraph(campaign.nodeId());

        if (node == null) { // this shouldn't be possible due to FK from scripts to nodes
            report.nodeNotFoundFail(campaign.nodeId());
            return report;
        }

        Node.printGraph(node, node, 0);

        LOG.info("Executing: {} (text: {}}", node.id(), node.text());
        LOG.info("Audience:");

        PushSupport support = new PushSupport(campaignId, node, this.queueProducer, persistenceManager); // FIXME don't need this anymore, I think.

        final var campaignUsers = persistenceManager.getPushCampaignUsers(campaignId);
        if(campaignUsers == null) {
            report.campaignUsersEmptyFail();
            return report;
        }

        report.numUsers = campaignUsers.size();

        // FIXME I wanted to avoid materializing all the rows into a list of records and simply process them as we go but this wasn't workable
        //  The query returns rows that may need to be merged (the User model is a aggregation of database records) so I can't simply fetch result set in
        //  batches. It may be simpler to add a secondary batching structure to the campaign_users table definition.
        for (var campaignUser : campaignUsers) {

            LOG.info(campaignUser.toString());

            // Check User status, skip if their status is not IN. If KNOWN or OUT, log this for the report.
            // If not IN, this indicates that the user transitioned to not-IN status since the partition was created.
            // In these cases we want to log this so there aren't questions about why the user wasn't included in the blast.
            // FIXME Record this in a report-like object.

            final var campaignDefinedPlatform = Platform.WAP; // FIXME need to add this to the campaign metadata even though it is duplicated (more or less) by the User's platform_code.

            if (!campaignUser.user().platformStatus().get(campaignDefinedPlatform).equals(UserStatus.IN)) {
                report.invalidUsersSkipped.add(campaignUser.groupId());
            }

            // NB: SESSIONS is keyed _only_ by the group_id. So there's only one (currently) for any/all channels visited by the Customer's user.
            // FIXME Should we add user id to the SESSIONS table's primary key to allow more flexibility wrt session scope?
            final var lastSessionActivity = campaignUser.sessionLastUpdatedAt();
            // NB: A missing Session may simply indicate that they've been inactive for a while and the Session row was deleted in a prior cleanup.
            final var isExpired = (null == lastSessionActivity) || isSessionExpired(lastSessionActivity);
            if (!isExpired) {
                // session is considered active so don't clobber it out-of-the-blue.
                report.activeUsersSkipped.add(campaignUser.groupId());
            }

            // Create a new Session
            final var sessionForCampaignUser = new Session(
                    campaignUser.groupId(),
                    support.startNode(),
                    campaignUser.user(),
                    support.queueProducer(),
                    support.persistenceManager()
            );

            // FIXME where should we get the route channel? Should we add it to the PUSH_CAMPAIGNS table? Or include it in the method signature?
            final var initialMessage = new Message(MessageType.MO, campaignUser.user().platformIds().get(campaignDefinedPlatform), "routeChannel", "#");

            // Execute the first node of the script, using the associated session, generating an MT and pushing it onto the regular, rate-limited output queue, possibly with a lower priority.
            final boolean ok = scriptEngine.process(sessionForCampaignUser, initialMessage);
            if (!ok) {
                report.usersSkippedDueToScriptErrors.add(campaignUser.user().id());
            } else {
                report.processedUsers.add(campaignUser.user().id());
            }
        }

        // Update the delivered column of the CampaignUser record (CAMPAIGN_USERS table)
        // FIXME move this outside of the campaignUser processing loop.
        // FIXME to avoid the cost of group updates we could have a results table that could provide historic info about successive attempts to push to a user.
        if (!persistenceManager.updatePushCampaignUsersStatus(report)) {
            LOG.error("Failed to update push campaign {} and users!", campaignId);
        }

        // Generate the run report. Is this a table? push_campaign_report?

        //    |
        //    |--> for each CampaignUsers
        //    |  |--> check if user has an active session (in cache). Configurable decision: clear the session or skip this user.
        //    |  |--> check User status, skip if their status is not IN. If KNOWN or OUT, log this for the report.
        //    |  |--> populate a new instance of Session but don't add it to the cache.
        //    |  |--> execute the first node of the script, using the associated session, generating an MT and pushing it onto the regular, rate-limited output queue with a lower priority.
        //    |  |--> update the delivered column of the CampaignUser record (CAMPAIGN_USERS table)
        //    |--> update the completed_at column of PUSH_CAMPAIGNS table:
        //    |--> generate the run report. Is this a table? push_campaign_report?
        return report;
    }

    static void main() throws Exception {
        var queueProducer = new InMemoryQueueProducer();
        Blaster blaster = new Blaster(queueProducer);
        blaster.init(ConfigLoader.readConfig("persistence_manager_test.properties"));
        final var report = blaster.exec(UUID.fromString("019bd1ff-c890-7a28-9758-7ce559af5e0b"));
        queueProducer.enqueued().forEach( message -> {
            IO.println(message);
        });
        IO.println(report);
    }

}
