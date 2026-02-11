package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.InMemoryQueueProducer;
import io.jenetics.util.NanoClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

public class BlasterIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(BlasterIntegrationTest.class);

    PersistenceManager persistenceManager;
    Blaster blaster;

    final UUID customerId = UUID.fromString("8285d1a8-2dc0-6752-3758-0076224bc839");
    final String description = "Created by BlasterIntegrationTest";
    final UUID scriptId = UUID.fromString("019c1ee9-49cc-7d59-a430-f050612acd72");
    final UUID routeId = UUID.fromString("019bf69d-a08a-7c3a-a4ca-ed70d35327fc");

    @BeforeEach
    void setUp() throws IOException, PersistenceManager.PersistenceManagerException, TimeoutException {
        final var props = ConfigLoader.readConfig("persistence_manager_test.properties");
        persistenceManager = PostgresPersistenceManager.createPersistenceManager(props);
        blaster = new Blaster(persistenceManager, new InMemoryQueueProducer());
        blaster.init(props);
    }

    @Test
    void pushCampaignLifecycle() {
        assertDoesNotThrow(() -> {
            var pcId = persistenceManager.createPushCampaign(customerId, description, scriptId, routeId);
            final var pushCampaign = persistenceManager.getPushCampaign(pcId);
            assertNotNull(pushCampaign);
            assertEquals(ScriptStatus.PROD, pushCampaign.scriptStatus());
            assertEquals(RouteStatus.ACTIVE, pushCampaign.routeStatus());

            // create a user segment

            // exec the campaign

            // fetch campaign and check its status

            // fetch user segment and check status of each campaign user
        });
    }

    @Test
    void updateCampaignUsersStatus() {
        // create user segment for existing campaign,
    }

    @Test
    void updatePushCampaignStatus() {
        // create a campaign, fetch it, check status, update status. fetch it, check status
    }

    @Test
    void exec() throws ExecutionException, InterruptedException, SQLException {
        // Customers: id, email, status
        // 8285d1a8-2dc0-6752-3758-0076224bc839 | theron.witting@yahoo.com | ACTIVE

        // Scripts: id, name, customer_id, status
        // 0cdbd272-4916-4a88-9826-d43623443fb2 | Script 0   | 8285d1a8-2dc0-6752-3758-0076224bc839 | PROD

         // Push Campaign: campaign_id
        // 019bd1ff-c890-7a28-9758-7ce559af5e0b

        var uuid = UUID.fromString("019bd1ff-c890-7a28-9758-7ce559af5e0b");
        final PushReport report = blaster.exec(uuid);

        assertFalse(report.nodeNotFound, "Missing specified Node");
        assertFalse(report.scriptStatusNotProd,  "Specified Script status was not PROD.");
        assertFalse(report.routeStatusNotActive, "Specified Route status was not ACTIVE.");
        assertFalse(report.campaignUsersEmpty, "Missing expected campaign users.");
        assertEquals(0, report.invalidUsersSkipped.size());
//        assertEquals(0, report.activeUsersSkipped.size());
        assertEquals(0, report.usersSkippedDueToScriptErrors.size());
//        assertEquals(report.numUsers, report.processedUsers.size(), "Not all users have been processed.");
        assertFalse(report.campaignAndUserStatusUpdateFail, "Push campaign and/or campaign user status update failed.");

    }

    @Test
    void isSessionExpired() throws InterruptedException {
        var now = NanoClock.utcInstant(); Thread.sleep(1000);
        assertFalse(blaster.isSessionExpired(now));

        final Instant pastExpiration = Instant.ofEpochSecond(blaster.sessionLifetime.toMillis() + 100);
        assertTrue(blaster.isSessionExpired(pastExpiration));
    }

}