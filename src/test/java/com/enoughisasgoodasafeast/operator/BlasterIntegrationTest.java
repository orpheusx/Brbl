package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.InMemoryQueueProducer;
import io.jenetics.util.NanoClock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

public class BlasterIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(BlasterIntegrationTest.class);

    PersistenceManager persistenceManager;
    Blaster blaster;

    final UUID modelCampaignId = UUID.fromString("019bd1ff-c890-7a28-9758-7ce559af5e0b");
    final UUID modelCustomerId = UUID.fromString("8285d1a8-2dc0-6752-3758-0076224bc839");
    final String description = "Created by BlasterIntegrationTest";
    final UUID modelScriptId = UUID.fromString("019c1ee9-49cc-7d59-a430-f050612acd72");
    final UUID modelRouteId = UUID.fromString("019bf69d-a08a-7c3a-a4ca-ed70d35327fc");

    final static List<UUID> modelUserIds = new ArrayList<>();

    @BeforeAll
    static void oneTimeSetUp() {
        String [] knownUserIds = {
                "8204383c-7a73-7ba0-1c8c-83be6886ef90", // 1
                "89a96c80-f1a1-e921-6355-4437bd156334", // 2
                "3ba13c74-a873-e03c-a706-9fb0e8ede5c9", // 3
                "1bfde246-a9e5-4f4d-2dd8-55ca87b2f2ee", // 4
                "19f1efc1-133b-fc2f-12e9-1383d40d31a7", // 5
                "02b1ba61-398f-ccc7-d555-5a86db7e3e00", // 6
                "acaf439e-9f0e-100b-7117-598b1465b49f", // 7
                "33cd32a1-6925-c163-2e8c-6ce32ae90c04", // 8
                "456853c5-f065-d21d-545c-ed981f94a316", // 9
                "dcb7ca2f-ecf6-dfc0-f0b3-b8f191f59a23", // 10
                "845b5658-8dce-746f-f870-d1c82df357ee", // 11
                "ba203f27-7f5a-a65e-e302-19f81b96e3a4", // 12
                "cf36d602-1a71-4354-b3a6-7c3aad0a6880", // 13
                "91532e46-52e5-7d54-584b-74fb4ae4ecd0", // 14

                "4a5dd67e-a452-ec0c-09d1-8aa69ab0f698", // 15 user.status = OUT
                "cca32bc5-90e5-0432-9def-f4ea8b6b17e6", // 16 user.status = OUT
                "22e76807-0750-d0f1-125c-6189102055d6", // 17 user.status = OUT
                "daa0b4ce-255b-7f25-b633-42727bb202aa", // 18 user.status = OUT
                "6bbec927-ff32-1f1a-ff69-a4fe7000ac8c", // 19 user.status = KNOWN
                "c4fc974a-cfb6-8f38-25f9-ca39e01d79e9"  // 20 user.status = KNOWN
        };
        for (String knownUserId : knownUserIds) {
            modelUserIds.add(UUID.fromString(knownUserId));
        }

    }

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
            var testStart = NanoClock.utcInstant();

            var pcId = persistenceManager.createPushCampaign(modelCustomerId, description, modelScriptId, modelRouteId);
            LOG.info("Push campaign lifecycle started. New campaignId: {}", pcId);

            // Verify campaign creation.
            final var pushCampaign = persistenceManager.getPushCampaign(pcId);
            assertNotNull(pushCampaign);
            assertEquals(ScriptStatus.PROD, pushCampaign.scriptStatus());
            assertEquals(RouteStatus.ACTIVE, pushCampaign.routeStatus());

            // Create a new user segment with the same set of users as an existing one.
            final boolean ok = persistenceManager.insertCampaignUserSegment(pcId, modelUserIds);
            assertTrue(ok);

            // Fetch the new user segment and verify status of each campaign user.
            final var newCampaignUsers = persistenceManager.getPushCampaignUsers(pushCampaign.id(), DeliveryStatus.PENDING);
            assertEquals(modelUserIds.size(), newCampaignUsers.size());

            for(CampaignUser campaignUser : newCampaignUsers) {
                assertNotNull(campaignUser);
                assertEquals(DeliveryStatus.PENDING, campaignUser.deliveryStatus());
            }

            // Consider all the sessions expired
            blaster.setExpirationSessionLifetime(Duration.ofSeconds(0));

            // Exec the campaign.
            final var pushReport = blaster.exec(pcId);

            assertFalse(pushReport.campaignUsersEmpty);
            assertEquals(modelUserIds.size(), pushReport.numUsers);
            assertFalse(pushReport.routeStatusNotActive);
            assertFalse(pushReport.campaignUsersStatusUpdateFail);
            assertFalse(pushReport.nodeNotFound);
            assertFalse(pushReport.campaignNotFound);
            assertFalse(pushReport.customerStatusNotActive);
            assertFalse(pushReport.invalidUsersSkipped.isEmpty());
            assertEquals(6, pushReport.invalidUsersSkipped.size(), "Unexpected number of invalid users skipped");
            assertEquals(14, pushReport.processedUsers.size(), "Unexpected number of processed users"); // See knownUserIds list.

            // Fetch campaign and check its status.
            final var postExecPushCampaign = persistenceManager.getPushCampaign(pcId);
            assertNotNull(postExecPushCampaign);
            assertNotNull(postExecPushCampaign.completedAt());

            // Update the campaign with a completion time.
            final var completedAt = NanoClock.utcInstant();
            final boolean completedOk = persistenceManager.completePushCampaign(pcId, completedAt);
            assertTrue(completedOk);

            // Fetch it back and verify the completion timestamp was set.
            final var completedPushCampaign = persistenceManager.getPushCampaign(pcId);
            assertNotNull(completedPushCampaign);
            // There are some differences based on the truncation of nanos so just check at the seconds level
            assertEquals(completedAt.getEpochSecond(), completedPushCampaign.completedAt().getEpochSecond());

            // Check the Sessions for each of the successfully processed users.
            for (UUID processedUserId : pushReport.processedUsers) {
                var session = persistenceManager.loadSession(processedUserId);
                assertNotNull(session, "Null session for user " + processedUserId);
                assertTrue(testStart.isBefore(session.getLastUpdatedNanos())); // make sure this isn't just an old session
                // Check the initial node was executed. NB: this assumes the selected script only advances by a single Node.
                // This is dependent on the script, however.
                assertEquals(pushCampaign.nodeId(), session.previousScript().id(),
                        "Session script " + pushCampaign.scriptId() + " not in expected state for user " + processedUserId);
            }

        });
    }

    @Test
    void getPushCampaignUsers() throws SQLException {
        // NB: Assumes we never alter the campaign user segment associated with the push campaign.
        final Collection<CampaignUser> pushCampaignUsers = persistenceManager.getPushCampaignUsers(modelCampaignId, DeliveryStatus.PENDING);
        assertEquals(14, pushCampaignUsers.size());
        pushCampaignUsers
                .forEach(campaignUser -> {
                    assertEquals(modelCampaignId, campaignUser.campaignId());
                    assertEquals(DeliveryStatus.PENDING, campaignUser.deliveryStatus());
                });
    }

    // @Test // Leave inactive until we figure out how to make it repeatable while also meaningful?
    void updateCampaignUsersStatus() {
        assertDoesNotThrow(() -> {
            // create user segment for existing campaign, update the status,
            final Instant completedAt = NanoClock.utcInstant();
            final boolean completedOk = persistenceManager.completePushCampaign(modelCampaignId, completedAt);
        });

    }

    // @Test
//    void exec() throws ExecutionException, InterruptedException, SQLException {
//        // Customers: id, email, status
//        // 8285d1a8-2dc0-6752-3758-0076224bc839 | theron.witting@yahoo.com | ACTIVE
//
//        // Scripts: id, name, customer_id, status
//        // 0cdbd272-4916-4a88-9826-d43623443fb2 | Script 0   | 8285d1a8-2dc0-6752-3758-0076224bc839 | PROD
//
//         // Push Campaign: campaign_id
//        // 019bd1ff-c890-7a28-9758-7ce559af5e0b
//
//        var uuid = UUID.fromString("019bd1ff-c890-7a28-9758-7ce559af5e0b");
//        final PushReport report = blaster.exec(uuid);
//
//        assertFalse(report.nodeNotFound, "Missing specified Node");
//        assertFalse(report.scriptStatusNotProd,  "Specified Script status was not PROD.");
//        assertFalse(report.routeStatusNotActive, "Specified Route status was not ACTIVE.");
//        assertFalse(report.campaignUsersEmpty, "Missing expected campaign users.");
//        assertEquals(0, report.invalidUsersSkipped.size());
//        assertEquals(0, report.usersSkippedDueToScriptErrors.size());
//        assertEquals(14, report.processedUsers.size(), "Unexpected number of processed users"); // See knownUserIds list.
//        assertFalse(report.campaignUsersStatusUpdateFail, "Push campaign and/or campaign user status update failed.");
//
//    }

    @Test
    void isSessionExpired() throws InterruptedException {
        var now = NanoClock.utcInstant().minus(Duration.of(1, ChronoUnit.SECONDS));
        assertFalse(blaster.isSessionExpired(now));

        final Instant pastExpiration = Instant.ofEpochSecond(blaster.sessionLifetime.toMillis() + 100);
        assertTrue(blaster.isSessionExpired(pastExpiration));
    }

}