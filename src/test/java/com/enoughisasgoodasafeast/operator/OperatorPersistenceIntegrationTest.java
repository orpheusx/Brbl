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
        sk = new SessionKey(Platform.SMS, "13054379229", "21249", "keyword");

    }

    @Test
    void getUser() {
        final User user = pm.getUser(sk);
        assertNotNull(user);
    }

    @Test
    void getScript() {

        final UUID scriptId = UUID.fromString("23900613-af65-48de-b7f4-b310b738eb8e"); // 'What is the capital of Australia?'
        final Node node = pm.getScript(scriptId);
        assertNotNull(node);
        assertEquals(node.id(), scriptId);
        // Node.printGraph(node, node, 1);
        assertEquals(3, node.edges().size()); // Canberra, Sydney, and Melbourne.
    }

    @Test
    void findCustomerIdByRoute() {
        final UUID customerId = op.findCustomerIdByRoute(sk);
        assertNotNull(customerId);
        assertEquals("762a353b-0597-0a15-b57d-389b21686463", customerId.toString());
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
        Instant before = Instant.now();
        SessionKey unknown = new SessionKey(Platform.SMS, randomUserNumber(), "21249", "keyword");
        final User createdUser = op.findOrCreateUser(unknown);
        assertNotNull(createdUser);
        LOG.info("{}", createdUser);
        assertTrue(createdUser.platformCreationTimes().get(Platform.BRBL).isAfter(before));
    }

    static String randomUserNumber() {
        Random gen = new Random();
        return String.valueOf(
                gen.nextInt(9)) +
                gen.nextInt(999999999);
    }

}