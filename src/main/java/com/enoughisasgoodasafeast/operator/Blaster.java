package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.Functions;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.Functions.randomUUID;

/**
 * A (mostly) standalone service that sends out
 */
public class Blaster {

    private static final Logger LOG = LoggerFactory.getLogger(Blaster.class);

    private PersistenceManager persistenceManager;
    private QueueProducer queueProducer;
    public Duration sessionLifetime;

    public Blaster() {
    }

    public Blaster(PostgresPersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public Blaster(QueueProducer queueProducer) {
        this.queueProducer = queueProducer;
    }

    public void init(Properties props) throws PersistenceManagerException, IOException, TimeoutException {
        if (persistenceManager == null) {
            persistenceManager = PostgresPersistenceManager.createPersistenceManager(props);
            LOG.info("ppm created.");
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
        return sessionLifetime.compareTo(Duration.between(lastUpdateAt, Instant.now())) <= 0;
    }

    public static boolean processUser(CampaignUser campaignUser) {
        LOG.info("Call back for " + campaignUser.id());

        // Create a new Session but don't write to database yet. Need the Node, User, & PersistenceManager
//        var session = new Session(randomUUID(), campaignUser.node(), , campaignUser.queueProducer(), campaignUser.persistenceManager());
        LOG.info("Creating new Session");

        // Execute the first node of the script, using the associated session, generating an MT and pushing it onto the regular, rate-limited output queue with a lower priority.
        LOG.info("Executing first Node");

        // BEGIN TRANSACTION
        // Persist the Session so the Operator can fetch it if/when the User responds
        // ...
        // Update the delivered column of the CampaignUser record (CAMPAIGN_USERS table)
        // ...

        return true; // FIXME obviously use a computed value here.
    }

    /**
     * Assumes the caller (Quartz or similar) knows which campaign id to execute.
     *
     * @param campaignId the id of the campaign to be executed.
     * @throws InterruptedException if one of the StructuredTaskScope tasks throws.
     * @throws ExecutionException   if one of the StructuredTaskScope tasks throws.
     */
    public PushReport exec(@NonNull UUID campaignId) throws InterruptedException, ExecutionException {
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

        LOG.info("Executing: {} (text: {}}", node.id(), node.text());
        LOG.info("Audience:");

//        PushSupport payload = new PushSupport(node, this.queueProducer, persistenceManager);

//        final var campaignUserReport =
//                persistenceManager.processPushCampaignUsers(
//                        campaignId, Blaster::processUser);
//        report.setCampaignUserReport(campaignUserReport);

//        for (var campaignUser : campaignUsers) { // FIXME can we avoid materializing all the rows into a list of records and simply process them as we go? Does this help?
//
//            LOG.info(campaignUser.toString());
//            report.numUsers++;
//
//            // NB: SESSIONS is keyed by the group_id. So there's only one (currently) for any/all channels visited by the Customer's user.
//            // FIXME Should we add user id to the SESSIONS table's primary key to allow more flexibility wrt session scope?
//            final var lastSessionActivity = campaignUser.sessionLastUpdatedAt();
//
//            // NB: A missing Session may simply indicate that they've been inactive for a while and the Session row was deleted in a prior cleanup.
//            final var isExpired = (null == lastSessionActivity) || isSessionExpired(lastSessionActivity);
//
//            // Check User status, skip if their status is not IN. If KNOWN or OUT, log this for the report.
//            // If not IN, this indicates that the user transitioned to not-IN status since the partition was created.
//            // In these cases we want to log this so there aren't questions about why the user wasn't included in the blast.
//            // Record this in a report-like object?
//            if (!campaignUser.userStatus().equals(UserStatus.IN)) {
//                // add to report's list of excluded campaign users
//                report.numInvalidUsers++;
//            }
//
//            // Create a new Session but don't write to database yet.
//
//            // Execute the first node of the script, using the associated session, generating an MT and pushing it onto the regular, rate-limited output queue with a lower priority.
//
//            // Update the delivered column of the CampaignUser record (CAMPAIGN_USERS table)
//
//        }

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
        Blaster blaster = new Blaster();
        blaster.init(ConfigLoader.readConfig("persistence_manager_test.properties"));
        blaster.exec(UUID.fromString("eb7aa81a-b314-420c-8f3d-df4755faa9bb"));
    }

}
