package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.FakeQueueConsumer;
import com.enoughisasgoodasafeast.InMemoryQueueProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

import static com.enoughisasgoodasafeast.datagen.KnownData.knownRootNodeIds;
import static com.enoughisasgoodasafeast.datagen.KnownData.knownRouteIdsAndChannels;
import static com.enoughisasgoodasafeast.datagen.KnownData.*;
import static io.jenetics.util.NanoClock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This class requires a running instance of Postgres with actual data.
 * TODO The tests would be more resilient to reloading related errors if we didn't hard code the script id.
 * TODO Setup a prefab database that runs in a container.
 */
class OperatorPersistenceIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(OperatorPersistenceIntegrationTest.class);

    private PersistenceManager pm;
    private Operator op;
    private SessionKey sk;

    @BeforeEach
    void setUp() throws Exception {
        pm = PostgresPersistenceManager.createPersistenceManager(ConfigLoader.readConfig("persistence_manager_test.properties"));
        op = new Operator(new FakeQueueConsumer(), new InMemoryQueueProducer(), pm);
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
    void getScript() {
        final UUID scriptId = UUID.fromString(knownRootNodeIds[0]);
        final Node node = pm.getNodeGraph(scriptId);
        assertNotNull(node);
        assertEquals(node.id(), scriptId);
        // Node.printGraph(node, node, 1);
        assertEquals(1, node.edges().size()); // just a single connecting edge expected for PRESENT_MULTI nodes.
    }

    @Test
    void findOwningCompanyIdByRouteChannel() {
        // sk: SessionKey(Platform.SMS, from: "13052020804", to: "119839196677", keyword: "keyword");
        final UUID companyId = op.findOwningCompanyIdByRouteChannel(sk);
        assertNotNull(companyId);
        assertEquals(knownCompanyId, companyId.toString());
    }

    @Test
    void findDefaultScriptByRoute() {
        final Node node = op.findDefaultScriptByRoute(sk);
        assertNotNull(node);
        // Node.printGraph(node, node, 1);
        assertEquals("ColorQuiz", node.label());
    }

    @Test
    void findOrCreateUser() {
        // FIXME this test creates new users every time it runs.
        Instant before = utcInstant();
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
    void findAllKeywords() {
        // Note: assumes system under test contains (at least) data contained in the generated known_keywords.tsv file.
        var keywordsByPattern = pm.getKeywords();
        assertFalse(keywordsByPattern.isEmpty());
        assertEquals(5, keywordsByPattern.size()); // keywords w/out route_id value are excluded
        assertTrue(keywordsByPattern.entrySet().stream().anyMatch(entry -> {
            return entry.getValue().wordPattern().equals("foo");
        }));
    }

    static String randomUserNumber() {
        Random gen = new Random();
        return "1" + gen.nextInt(9) +
                gen.nextInt(999999999);
    }

}