package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.InMemoryQueueProducer;
import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.MessageType;
import com.enoughisasgoodasafeast.datagen.KnownData;
import io.jenetics.util.NanoClock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.datagen.KnownData.*;
import static org.junit.jupiter.api.Assertions.*;

public class BlasterIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(BlasterIntegrationTest.class);

    PersistenceManager persistenceManager;
    PersistenceManager adminPersistenceManager;
    private InMemoryQueueProducer queueProducer;
    Blaster blaster;

    final UUID modelCampaignId = UUID.fromString("019bd1ff-c890-7a28-9758-7ce559af5e0b");
    final UUID modelCustomerId = UUID.fromString("8285d1a8-2dc0-6752-3758-0076224bc839");
    // Corresponding company id for modelCustomerId
    final UUID modelCompanyId =  UUID.fromString(knownCompanyId);

    final String description = "Created by BlasterIntegrationTest";
    final UUID modelScriptId = UUID.fromString(knownScriptData[0][0]);
    final UUID modelNodeId = UUID.fromString(knownRootNodeIds[0]);

    final UUID modelRouteId = UUID.fromString(KnownData.knownRouteIdsAndChannels[0][0]);

    public static final int EXPECTED_SUCCESSES = 10; // matched with the elements in knownUserIds
    public static final int EXPECTED_SKIPPED = 7; // matched with the elements in knownUserIds
    final static List<UUID> modelUserIds = new ArrayList<>();

    @BeforeAll
    static void oneTimeSetUp() {
        for (String knownUserId : knownUserIds) {
            modelUserIds.add(UUID.fromString(knownUserId));
        }
        // TODO Add users that are OUT?
    }

    @BeforeEach
    void setUp() throws IOException, PersistenceManager.PersistenceManagerException, TimeoutException {
        final var props = ConfigLoader.readConfig("blaster_integration_test.properties");
        persistenceManager = PostgresPersistenceManager.createPersistenceManager(props);

        // For changing states, etc that brbl_pushr doesn't have privileges to do.
        adminPersistenceManager = PostgresPersistenceManager.createPersistenceManager(ConfigLoader.readConfig("migrations.properties"));

        queueProducer = new InMemoryQueueProducer();
        blaster = new Blaster(persistenceManager, queueProducer);
        blaster.init(props);
    }

    @Test
    void pushCampaignLifecycle() {
        assertDoesNotThrow(() -> {
            var testStart = NanoClock.utcInstant();

            var pcId = persistenceManager.createPushCampaign(modelCompanyId, description, modelScriptId, modelRouteId);
            LOG.info("Push campaign lifecycle started. New campaignId: {}", pcId);

            // Verify campaign creation.
            final var pushCampaign = persistenceManager.getPushCampaign(pcId);
            assertNotNull(pushCampaign);
            assertEquals(ScriptStatus.PROD, pushCampaign.scriptStatus());
            assertEquals(RouteStatus.ACTIVE, pushCampaign.routeStatus());
            assertEquals(CompanyStatus.ACTIVE, pushCampaign.companyStatus());

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
            assertFalse(pushReport.companyStatusNotActive);
            // TODO address skipped users by enhancing datagen
            //assertFalse(pushReport.invalidUsersSkipped.isEmpty());

            // TODO address skipped users by enhancing datagen
            // The EXPECTED_SKIPPED would only be 6 if we properly avoided creating campaign user segments with the more than a single Platform ...
            // Likewise, the value of EXPECTED_SUCCESSES would be one greater.
            // assertEquals(EXPECTED_SKIPPED, pushReport.invalidUsersSkipped.size(), "Unexpected number of invalid users skipped");
            assertEquals(EXPECTED_SUCCESSES, pushReport.processedUsers.size(), "Unexpected number of processed users"); // See knownUserIds list.

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
            
            // Check the queue producer for the MTs we expect to have been generated
            final var enqueued = queueProducer.enqueued();
            assertFalse(enqueued.isEmpty());
            assertEquals(EXPECTED_SUCCESSES, enqueued.size());

            // Check messages_mt table as well
            var messageIds = enqueued.stream().map(Message::id).toList();
            var messagesCreated = getMessages(messageIds);
            assertEquals(EXPECTED_SUCCESSES, messagesCreated.size());
        });
    }

    private List<Message> getMessages(List<UUID> messageIds) throws SQLException {
        try (var connection = adminPersistenceManager.fetchConnection();
             var ps = connection.prepareStatement(
                     "SELECT id, sent_at, _from, _to, _text, session_id, script_id FROM messages_mt WHERE id = ANY (?)"
             )) {
            ps.setArray(1, connection.createArrayOf("uuid", messageIds.toArray()));

            final ResultSet rs = ps.executeQuery();
            List<Message> messages = new ArrayList<>();
            while(rs.next()) {
                final UUID id = (UUID) rs.getObject("id");
                final Instant sentAt = rs.getTimestamp("sent_at").toInstant();
                final String from = rs.getString("_from");
                final String to = rs.getString("_to");
                final String text = rs.getString("_text");

                messages.add(
                        new Message(id, sentAt, MessageType.MT, Platform.SMS, from, to, text)
                );
            }

            return messages;
        }
    }

   //@Test
   //void fetchMessagesById() throws SQLException {
   //    var messageIds = List.of(UUID.fromString("019c76a8-47d5-7fab-b0c1-b870cbfeb3b8"),
   //            UUID.fromString("019c76a8-47d7-7bef-92d2-4fe6a2843ff2"),
   //            UUID.fromString("019c76a8-47d9-7c8c-b3d6-c02e5892340e"),
   //            UUID.fromString("019c76a8-47db-788a-821c-29ec153baa4a"),
   //            UUID.fromString("019c76a8-47dd-7951-b02b-3b3c5a5dca2d"),
   //            UUID.fromString("019c76a8-47df-7386-8c6d-b1d7a372e55e"),
   //            UUID.fromString("019c76a8-47e0-7f32-a8ae-3655033acae9"),
   //            UUID.fromString("019c76a8-47e2-751b-8781-ee7b5e178402"),
   //            UUID.fromString("019c76a8-47e4-7197-9bff-e0f08bd00b6e"),
   //            UUID.fromString("019c76a8-47e6-7cc7-86f1-f0efc48d8b50"),
   //            UUID.fromString("019c76a8-47e7-7651-9aab-29fc385650dc"));
   //    var messages = getMessages(messageIds);
   //    assertEquals(11, messages.size());
   //    for (Message message : messages) {
   //        LOG.info(message.toString());
   //    }
   //}

    //@Test
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

    //@Test
    void getPushCampaignUsers2() throws SQLException {
        UUID campaignId = UUID.fromString("019cca9f-4cad-7e37-835c-0b950eca2eea");
        final Collection<CampaignUser> pushCampaignUsers = persistenceManager.getPushCampaignUsers(campaignId, DeliveryStatus.PENDING);
        // FIXME we're expecting 20 CampaignUsers where the nested User have a single Platform;
        //  It's expected that the Blaster.exec method knows what platform is targeted by the PushCampaign
        //  (via it's specified Route) and only sends for the matching ones.
        //  The test data should challenge this more by including Users that
        //  have multiple platforms.
        assertEquals(20, pushCampaignUsers.size());
        pushCampaignUsers
                .forEach(campaignUser -> {
                    assertEquals(campaignId, campaignUser.campaignId());
                    assertEquals(DeliveryStatus.PENDING, campaignUser.deliveryStatus());
                    LOG.info(campaignUser.user().platformNumbers().toString());
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

    @Test
    void testExec_ActiveSessionUsersSkipped() throws SQLException, PersistenceManager.PersistenceManagerException {
        final UUID userForTestUserGroupId = modelUserIds.getFirst();//UUID.fromString("8204383c-7a73-7ba0-1c8c-83be6886ef90"); // This is the first model user in knownUserIds
        final UUID testUserGroupId = UUID.fromString(KnownData.knownAmalgamIds[0]);//UUID.fromString("34237d77-b16e-9251-c90f-1a99b7b7555b"); // and the corresponding group_id in amalgams.

        // Create a new push campaign
        final UUID pcId = persistenceManager.createPushCampaign(modelCompanyId, "testExec_ActiveSessionUsersSkipped", modelScriptId, modelRouteId);
        assertNotNull(pcId);

        // Add only the test user to the campaign
        final boolean usersAdded = persistenceManager.insertCampaignUserSegment(pcId, List.of(userForTestUserGroupId));
        assertTrue(usersAdded);

        // Fetch a User object for the test user group ID (this is a placeholder, a real user would be fetched differently)
        // For testing, we can create a dummy user since the actual fields don't matter much for session expiration logic
        final User dummyUser = new User(
                Map.of(Platform.SMS, UUID.randomUUID()),
                testUserGroupId,
                Map.of(Platform.SMS, "1234567890"),
                Map.of(Platform.SMS, NanoClock.utcInstant()),
                "US",
                Set.of(LanguageCode.ENG),
                UUID.randomUUID(),
                modelCustomerId,
                Map.of(Platform.SMS, "TestUser"),
                Map.of(),
                Map.of(Platform.SMS, UserStatus.IN)
        );

        // Get a Node for the session
        final var node = persistenceManager.getNodeGraph(modelNodeId);
        assertNotNull(node, "Node graph for modelScriptId should not be null.");

        // Create and save an active session for this user
        final Session activeSession = new Session(testUserGroupId, node, dummyUser, new InMemoryQueueProducer(), persistenceManager);
        persistenceManager.saveSession(activeSession);

        // Ensure blaster's session lifetime is long enough to consider the session active
        blaster.setExpirationSessionLifetime(Duration.ofDays(1)); // Make session effectively never expire within test runtime

        final var pushReport = blaster.exec(pcId);

        // LOG.error("Looking for skipped user by group id: {}", testUserGroupId);
        assertTrue(pushReport.usersWithActiveSessionsSkipped.contains(testUserGroupId), "PushReport should indicate the user with active session (by groupId) was skipped.");
        assertFalse(pushReport.processedUsers.contains(testUserGroupId), "User with active session should not be processed.");

        // Restore default session lifetime for other tests
        blaster.setExpirationSessionLifetime(Duration.ofMinutes(20));
    }

    @Test
    void testExec_CampaignUsersEmpty() throws SQLException {
        // Create a push campaign without adding any users to it
        final UUID pcId = persistenceManager.createPushCampaign(modelCompanyId, "testExec_CampaignUsersEmpty", modelScriptId, modelRouteId);
        assertNotNull(pcId);

        final var pushReport = blaster.exec(pcId);

        assertTrue(pushReport.campaignUsersEmpty, "PushReport should indicate campaignUsersEmpty for a campaign with no users.");
        assertTrue(pushReport.isPushComplete(), "PushReport should be complete when campaign users are empty.");
    }

    @Test
    void testExec_RouteStatusNotActive() throws SQLException {
        UUID pcId = null;
        try {
            // Create a push campaign linked to modelRouteId (which is assumed to be ACTIVE initially)
            pcId = persistenceManager.createPushCampaign(modelCompanyId, "testExec_RouteStatusNotActive", modelScriptId, modelRouteId);
            assertNotNull(pcId);

            // Set the route status to INACTIVE
            updateRouteStatus(modelRouteId, RouteStatus.SUSPENDED);

            final var pushReport = blaster.exec(pcId);

            assertTrue(pushReport.routeStatusNotActive, "PushReport should indicate routeStatusNotActive.");
            assertTrue(pushReport.isPushComplete(), "PushReport should be complete when route is not active.");
        } finally {
            // Revert route status back to ACTIVE for subsequent tests
            updateRouteStatus(modelRouteId, RouteStatus.ACTIVE);
        }
    }

    private void updateRouteStatus(UUID routeId, RouteStatus status) throws SQLException {
        // FIXME brbl_logic_write_role doesn't have update access on routes table.
        try (var connection = adminPersistenceManager.fetchConnection();
             var ps = connection.prepareStatement("UPDATE routes SET status = ?::brbl_logic.route_status WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setObject(2, routeId);
            ps.executeUpdate();
        }
    }

    private void updateScriptStatus(UUID scriptId, ScriptStatus status) throws SQLException {
        try (var connection = adminPersistenceManager.fetchConnection();
             var ps = connection.prepareStatement("UPDATE scripts SET status = ?::script_status WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setObject(2, scriptId);
            ps.executeUpdate();
        }
    }

    private void updateCompanyStatus(UUID customerId, CompanyStatus status) throws SQLException {
        try (var connection = adminPersistenceManager.fetchConnection();
             var ps = connection.prepareStatement("UPDATE companies SET status = ?::brbl_logic.company_status WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setObject(2, customerId);
            ps.executeUpdate();
        }
    }

    @Test
    void testExec_CompanyStatusNotActive() throws SQLException {
        UUID pcId = null;
        try {
            // Create a push campaign linked to modelCustomerId (which is assumed to be ACTIVE initially)
            pcId = persistenceManager.createPushCampaign(modelCompanyId, "Inactive Customer Test", modelScriptId, modelRouteId);
            assertNotNull(pcId);

            // Set the customer status to INACTIVE
            updateCompanyStatus(modelCompanyId, CompanyStatus.SUSPENDED);

            final var pushReport = blaster.exec(pcId);

            assertTrue(pushReport.companyStatusNotActive, "PushReport should indicate companyStatusNotActive.");
            assertTrue(pushReport.isPushComplete(), "PushReport should be complete when Company is not active.");
        } finally {
            // Revert customer status back to ACTIVE for subsequent tests
            updateCompanyStatus(modelCompanyId, CompanyStatus.ACTIVE);
            // Cleanup the created campaign if necessary. For now, we assume campaigns are transient.
        }
    }

    @Test
    void testExec_CampaignNotFound() throws SQLException {
        // Use a UUID that is highly unlikely to exist in the database
        final UUID nonExistentCampaignId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        final var pushReport = blaster.exec(nonExistentCampaignId);

        assertTrue(pushReport.campaignNotFound, "PushReport should indicate campaignNotFound for a non-existent campaign.");
        assertTrue(pushReport.isPushComplete(), "PushReport should be complete when campaign is not found.");
    }


}