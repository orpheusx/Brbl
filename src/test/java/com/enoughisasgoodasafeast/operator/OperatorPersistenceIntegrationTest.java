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
        sk = new SessionKey(Platform.SMS, "13052020804", "119839196677", "keyword");
    }

    @Test
    void findUserExisting() {
        final User user = pm.getUser(
                new SessionKey(Platform.SMS, "18484242144", "113052034955", "random keyword")
        );
        assertNotNull(user);
        assertNotNull(user.platformProfiles());
        assertEquals(1, user.platformProfiles().size());
        assertEquals(2, user.platformStatus().size()); // TODO create/find an example where there are grouped
        LOG.info(user.platformCreationTimes().get(Platform.SMS).toString());
    }

    @Test
    void getScript() {
        final UUID scriptId = UUID.fromString("23900613-af65-48de-b7f4-b310b738eb8e"); // 'What is the capital of Australia?'
//        final UUID scriptId = UUID.fromString("0fc4ef6c-082f-4e90-b2f4-e14dbac78623"); // 'What kind of food? 1) vegetables 2) meat 3) fruit'
        final Node node = pm.getNodeGraph(scriptId);
        assertNotNull(node);
        assertEquals(node.id(), scriptId);
        // Node.printGraph(node, node, 1);
        assertEquals(3, node.edges().size()); // Canberra, Sydney, and Melbourne.
    }

    @Test
    void findOwningCompanyIdByRouteChannel() {
        // sk: SessionKey(Platform.SMS, "13052020804", "119839196677", "keyword");
        final UUID companyId = op.findOwningCompanyIdByRouteChannel(sk);
        assertNotNull(companyId);
        assertEquals("8410c710-6986-e350-d3d5-28428a640e5f", companyId.toString());
    }

    @Test
    void findDefaultScriptByRoute() {
        final Node node = op.findDefaultScriptByRoute(sk);
        assertNotNull(node);
        // Node.printGraph(node, node, 1);
        assertEquals(" a quote that inspires yo", node.label());
    }

    @Test
    void findOrCreateUser() {
        Instant before = utcInstant();
        SessionKey unknown = new SessionKey(Platform.SMS, randomUserNumber(), "119839196677", "keyword");
        final User createdUser = op.findOrCreateUser(unknown);
        assertNotNull(createdUser);
        LOG.info("{}", createdUser);
        assertTrue(createdUser.platformCreationTimes().get(Platform.SMS).isAfter(before));
    }

    @Test
    void findAllKeywords() {
        var keywordsByPattern = pm.getKeywords();
        assertFalse(keywordsByPattern.isEmpty()); // FIXME assumes system under test is in a known state.
        assertEquals(8, keywordsByPattern.size()); // keywords w/out route_id value are excluded
        assertTrue(keywordsByPattern.entrySet().stream().anyMatch(entry -> {
            return entry.getValue().wordPattern().equals("foo"); // FIXME also not a great test
        }));
    }

    static String randomUserNumber() {
        Random gen = new Random();
        return "1" + gen.nextInt(9) +
                gen.nextInt(999999999);
    }

}