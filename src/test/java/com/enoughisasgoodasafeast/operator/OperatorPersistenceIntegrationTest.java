package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.FakeQueueConsumer;
import com.enoughisasgoodasafeast.InMemoryQueueProducer;
import com.enoughisasgoodasafeast.QueueProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Random;
import java.util.SequencedSet;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static com.enoughisasgoodasafeast.datagen.KnownData.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This class requires a running instance of Postgres with actual data.
 * TODO The tests would be more resilient to reloading related errors if we didn't hard code the script id.
 * TODO Setup a prefab database that runs in a container.
 */
class OperatorPersistenceIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(OperatorPersistenceIntegrationTest.class);

    private PersistenceManager pm;
    private QueueProducer qp;
    private Operator op;
    private SessionKey sk;

    @BeforeEach
    void setUp() throws Exception {
        pm = PostgresPersistenceManager.createPersistenceManager(ConfigLoader.readConfig("persistence_manager_test.properties"));
        qp = new InMemoryQueueProducer();
        op = new Operator(new FakeQueueConsumer(), qp, pm);
        // user.platform_id, route.channel, keyword.pattern
        sk = new SessionKey(Platform.SMS, "13052020804", knownRouteIdsAndChannels[0][1], "keyword");
    }

    @Test
    void findUserExisting() {
        final User user = pm.getUser(
                new SessionKey(Platform.SMS, knownNumbersForUsers[0], knownRouteIdsAndChannels[0][1], "random keyword")
        );
        assertNotNull(user);
        assertNotNull(user.platformProfiles());
        assertEquals(1, user.platformProfiles().size());
        assertEquals(2, user.platformStatus().size()); // TODO create/find an example where there are grouped
        LOG.info("Found {}", user);
    }

    @Test
    void getChangeTopicNodeGraph() {

        final UUID nodeId = UUID.fromString(knownRootNodeIds[0]);
        final Node stopNode = pm.getNodeGraph(nodeId);
        assertNotNull(stopNode);
        assertEquals(stopNode.id(), nodeId);
//        Node.printGraph(stopNode, stopNode, 2); // simple cycle

        // Fetch and validate the initial PRESENT node of the 'change topic' graph.
        final var startingChangeTopicNodeId = UUID.fromString(knownUnreferencedNodeIds[0]);
        final var presentChangeTopic = pm.getNodeGraph(startingChangeTopicNodeId);
        assertNotNull(presentChangeTopic);
        assertEquals(presentChangeTopic.id(), startingChangeTopicNodeId);
        assertSame(NodeType.PRESENT_MULTI, presentChangeTopic.type());
        assertTrue(presentChangeTopic.text().contains("Oh, you want to talk about something else?"));

//        Node.printGraph(stopNode, presentChangeTopic, 2); // explodes

        // Check the single Edge connecting initial PRESENT to PROCESS
        final SequencedSet<Edge> presentChangeEdges = presentChangeTopic.edges();
        assertNotNull(presentChangeEdges);
        assertFalse(presentChangeEdges.isEmpty());
        assertEquals(1, presentChangeEdges.size());
        assertEquals("n/a", presentChangeEdges.getFirst().responseText());

        // Check the paired PROCESS node
        final var processChangeTopic = presentChangeEdges.getFirst().targetNode();
        assertNotNull(processChangeTopic);
        assertSame(NodeType.PROCESS_MULTI, processChangeTopic.type());
        assertTrue(processChangeTopic.text().contains("Sorry. I'm confused. The options are"));

        // Check the two (and only two) Edges on the PROCESS
        final SequencedSet<Edge> processChangeEdges = processChangeTopic.edges();
        assertNotNull(processChangeEdges);
        assertFalse(processChangeEdges.isEmpty());
        assertEquals(2, processChangeEdges.size());

        // The first edge should point to a PRESENT_MULTI
        final Edge processEdgeFirst = processChangeEdges.getFirst();
        assertTrue(processEdgeFirst.responseText().contains("Sure, no problem"));
        final var yesChange = processEdgeFirst.targetNode();
        assertSame(NodeType.PRESENT_MULTI, yesChange.type());
        assertTrue(yesChange.text().contains("Here are the things we can talk about"));

        // Check each of the yesChange.edges
        final var linkChangeTopicPresentToProcess = yesChange.edges();
        assertEquals(1, linkChangeTopicPresentToProcess.size());
        var topicProcessNode = linkChangeTopicPresentToProcess.getFirst().targetNode();
        var topicSelectedEdges = topicProcessNode.edges();

        assertEquals("Great!", topicSelectedEdges.getFirst().responseText());
        assertEquals("My least favorite topic...", topicSelectedEdges.getLast().responseText());

        final Edge processEdgeSecond = processChangeEdges.getLast();
        assertTrue(processEdgeSecond.responseText().contains("Ok, I'll repeat"));
        // The second edge should point to an END_OF_CHAT node.
        final var noContinue = processEdgeSecond.targetNode();
        assertSame(NodeType.END_OF_CHAT, noContinue.type());


    }

    @Test
    void getNodeGraph() {
        final UUID nodeId = UUID.fromString(knownRootNodeIds[0]);
        final Node node = pm.getNodeGraph(nodeId);
        assertNotNull(node);
        assertEquals(node.id(), nodeId);
        //Node.printGraph(node, node, 1);
        assertEquals(1, node.edges().size()); // just a single connecting edge expected to connect PRESENT_MULTI node with PROCESS_MULI.

        // Dive into the graph and look for the Nodes we expect. Relies on KnownData.
        var processMultiNode = node.edges().getFirst().targetNode();
        assertEquals(NodeType.PROCESS_MULTI, processMultiNode.type(), "Type of node connected to root is unexpected.");

        // Check size and ordering.
        var edgesForProcessMulti = processMultiNode.edges();
        assertEquals(3, edgesForProcessMulti.size());
        assertEquals("Red is the color of life.", edgesForProcessMulti.getFirst().responseText());
        assertEquals("Flort is for the cool kids.", edgesForProcessMulti.getLast().responseText());

        // One more level into the graph. Again, totally reliant on KnownData.
        var firstNode = edgesForProcessMulti.getFirst().targetNode();
        var lastNode = edgesForProcessMulti.getLast().targetNode();
        assertNotNull(firstNode);
        assertNotNull(lastNode);
        assertEquals(NodeType.PRESENT_MULTI, firstNode.type(), "Type connected to firstNode is unexpected.");
        assertEquals(NodeType.PRESENT_MULTI, lastNode.type(), "Type connected to lastNode is unexpected.");

        // Find the next paired PROCESS_MULTI nodes and check ordering of its edges
        var shapeNode = firstNode.edges().getFirst().targetNode();
        var shapeEdges = shapeNode.edges();
        assertEquals(3, shapeEdges.size());
        LOG.info("Match text for shapes {}", shapeEdges.getFirst().matchText());
        assertTrue(shapeEdges.getFirst().matchText().getFirst().contains("1"));
        assertTrue(shapeEdges.getLast().matchText().getFirst().contains("3"));

        var stoogesNode = lastNode.edges().getFirst().targetNode();
        var stoogeEdges = stoogesNode.edges();
        assertEquals(3, stoogeEdges.size());
        LOG.info("Match text for stooges {}", stoogeEdges.getFirst().matchText());
        assertTrue(stoogeEdges.getFirst().matchText().getFirst().contains("1"));
        assertTrue(stoogeEdges.getLast().matchText().getFirst().contains("3"));

//        assertEquals(1, lastNodeEdges.size());
//
//        assertTrue(lastNodeEdges.getFirst().matchText().getFirst().contains("3"));

    }

    @Test
    void findOwningCompanyIdByRouteChannel() {
        // sk: SessionKey(Platform.SMS, from: "13052020804", to: "119839196677", keyword: "keyword");
        final UUID companyId = op.findOwningCompanyIdByRouteChannel(sk);
        assertNotNull(companyId);
        assertEquals(knownCompanyId, companyId.toString());
    }

    @Test
    void getActiveRoutes() {
        final Route[] activeRoutes = pm.getActiveRoutes();
        assertNotNull(activeRoutes);
        assertEquals(knownRouteIdsAndChannels.length, activeRoutes.length);
        // LOG.info("Found expected {} active routes", activeRoutes.length);
        for (Route activeRoute : activeRoutes) {
            assertNotNull(activeRoute.optInNodeId());
            assertNotNull(activeRoute.optOutNodeId());
        }
        var sk = new SessionKey(Platform.SMS, randomUserNumber(), knownRouteIdsAndChannels[0][1], "randomNonKeywordInput");
        final var optInScriptIdByRoute = op.findOptInScriptIdByRoute(sk);
        assertNotNull(optInScriptIdByRoute);
        assertEquals(NodeType.SEND_MESSAGE, optInScriptIdByRoute.type());
        assertTrue(optInScriptIdByRoute.text().contains("Welcome"));
        assertTrue(optInScriptIdByRoute.text().contains("opt-out"));

        final var optOutScriptIdByRoute = op.findOptOutScriptByRoute(sk);
        assertNotNull(optOutScriptIdByRoute);
        assertEquals(NodeType.OPT_OUT, optOutScriptIdByRoute.type());
        assertTrue(optOutScriptIdByRoute.text().contains("opted out"));
    }

    @Test
    void findDefaultScriptByRoute() {
        final var node = op.findDefaultScriptByRoute(sk);
        assertNotNull(node);
        // Node.printGraph(node, node, 1);
        assertEquals("ColorQuiz", node.label());
    }

    @Test
    void findInterruptConversationByRoute() {
        final var node = op.findInterruptConversationByRoute(sk);
        assertNotNull(node);
        assertEquals("Oh, you want to talk about something else? 1) yes 2) no, let's continue with the current conversation.", node.text());
    }

    @Test
    void findOrCreateUser() {
        // FIXME this test creates new users every time it runs.
        Instant before = Instant.now();
        SessionKey unknown = new SessionKey(Platform.SMS, randomUserNumber(), knownRouteIdsAndChannels[0][1], "colour");
        final User createdUser = op.findOrCreateUser(unknown);
        assertNotNull(createdUser);
        LOG.info("Created user: {}", createdUser);
        assertTrue(createdUser.platformCreationTimes().get(Platform.SMS).isAfter(before));

        // this time with an unknown keyword
        SessionKey unknown2 = new SessionKey(Platform.SMS, randomUserNumber(), knownRouteIdsAndChannels[0][1], "wlkewerj");
        final User createdUser2 = op.findOrCreateUser(unknown);
        assertNotNull(createdUser2);
        LOG.info("Created user: {}", createdUser2);
    }

    @Test
    void updateUserStatus() {
        var sk = new SessionKey(Platform.SMS, knownNumbersForUsers[0], knownRouteIdsAndChannels[0][1], "random keyword");
        var optedInUser = pm.getUser(sk);
        assertNotNull(optedInUser);
        assertEquals(UserStatus.IN, optedInUser.platformStatus().get(Platform.SMS));
        LOG.info("OptedIN? {}", optedInUser);

        // now test the updating of the status to OUT
        pm.updateUserStatus(optedInUser, Platform.SMS, UserStatus.OUT);
        var optedOutUser = pm.getUser(sk);
        assertNotEquals(optedInUser, optedOutUser);
        assertEquals(optedInUser.groupId(), optedOutUser.groupId());
        LOG.info("OptedOUT? {}", optedInUser);

        assertEquals(UserStatus.OUT, optedOutUser.platformStatus().get(Platform.SMS));

        // Restore the original status
        pm.updateUserStatus(optedOutUser, Platform.SMS, UserStatus.IN);
        var restoredStatusUser = pm.getUser(sk);
        LOG.info("Reverted status? {}", restoredStatusUser);
        assertEquals(UserStatus.IN, restoredStatusUser.platformStatus().get(Platform.SMS));
        assertEquals(optedInUser, restoredStatusUser);
    }

    @Test
    void findAllKeywords() {
        // Note: assumes system under test contains (at least) data contained in the generated known_keywords.tsv file.
        var keywordsByPattern = pm.getKeywords();
        assertFalse(keywordsByPattern.isEmpty());
        assertEquals(6, keywordsByPattern.size()); // keywords w/out route_id value are excluded
        assertTrue(keywordsByPattern.entrySet().stream().anyMatch(entry -> {
            return entry.getValue().wordPattern().equals("foo"); // at least one of the keywords should be "foo"
        }));
    }

    static String randomUserNumber() {
        Random gen = new Random();
        return "1" + gen.nextInt(9) +
                gen.nextInt(999_999_999);
    }

}