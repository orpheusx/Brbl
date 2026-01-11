package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import org.jline.utils.Log;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

public class Blaster {

    private static final Logger LOG = LoggerFactory.getLogger(Blaster.class);

    private PersistenceManager persistenceManager;

    public Blaster() {
    }

    public Blaster(PostgresPersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public void init(Properties props) throws PersistenceManagerException {
        if (persistenceManager == null) {
            persistenceManager = PostgresPersistenceManager.createPersistenceManager(props);
            LOG.info("ppm created.");
        }
    }

    public void execAndReport(@NonNull UUID campaignId) throws InterruptedException, ExecutionException {
        // Collect needed data:
        //   Read the Campaign from PUSH_CAMPAIGNS
        final PushCampaign campaign = persistenceManager.getPushCampaign(campaignId);

        LOG.info("Fetched: {}", campaign);

        if (!campaign.status().equals(CustomerStatus.ACTIVE)) {
            Log.error("Customer ({}) owning ");
        }

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // Read the specified script graph (Call scriptCache.refresh(scriptID) to insure we have the latest variant?)
            var suppliedNode = scope.fork(() -> persistenceManager.getScript(campaign.nodeId()));

            // Read CampaignUsers (CAMPAIGN_USERS joined with data from USERS) from associated (possibly in batches)
            // FIXME Need to think about using a Stream here instead of a List...
            var suppliedUsers = scope.fork(() -> persistenceManager.getUsersForPushCampaign(campaignId));

            scope.join().throwIfFailed();

            Node node = suppliedNode.get();

            List<CampaignUser> campaignUsers = suppliedUsers.get();

            LOG.info("Executing: {}", node);
            LOG.info("Audience:");
            assert campaignUsers != null;
            //campaignUsers.forEach(campaignUser -> LOG.info(campaignUser.toString()));

            for (var campaignUser : campaignUsers) {

                // Check if user has an active session (in cache). Configurable decision: clear the session or skip this user.
                //    Construct a SessionKey (minus keyword).
                //    The downside of accessing the sessionCache normally is that doing so resets the LRU. Is there a way to check for its existence without doing so?

                // Check User status, skip if their status is not IN. If KNOWN or OUT, log this for the report.

                // Populate a new instance of Session without adding it to the cache.

                // Execute the first node of the script, using the associated session, generating an MT and pushing it onto the regular, rate-limited output queue with a lower priority.

                // Update the delivered column of the CampaignUser record (CAMPAIGN_USERS table)

            }

            // Generate the run report. Is this a table? push_campaign_report?
            // ...

        }

        //    |
        //    |--> for each CampaignUsers
        //    |  |--> check if user has an active session (in cache). Configurable decision: clear the session or skip this user.
        //    |  |--> check User status, skip if their status is not IN. If KNOWN or OUT, log this for the report.
        //    |  |--> populate a new instance of Session but don't add it to the cache.
        //    |  |--> execute the first node of the script, using the associated session, generating an MT and pushing it onto the regular, rate-limited output queue with a lower priority.
        //    |  |--> update the delivered column of the CampaignUser record (CAMPAIGN_USERS table)
        //    |--> update the completed_at column of PUSH_CAMPAIGNS table:
        //    |--> generate the run report. Is this a table? push_campaign_report?

    }

    static void main() throws Exception {
        Blaster blaster = new Blaster();
        blaster.init(ConfigLoader.readConfig("persistence_manager_test.properties"));
        blaster.execAndReport(UUID.fromString("eb7aa81a-b314-420c-8f3d-df4755faa9bb"));
    }

}
