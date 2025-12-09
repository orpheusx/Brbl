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
        sk = new SessionKey(Platform.SMS, "17817299468", "21249", "keyword");

    }

    @Test
    void getUser() {
        final User user = pm.getUser(sk);
        assertNotNull(user);
    }

    @Test
    void findCustomerIdByRoute() {
        final UUID customerId = op.findCustomerIdByRoute(sk);
        assertNotNull(customerId);
        assertEquals("4d351c0e-5ce5-456e-8de0-70e04bd5c0fd", customerId.toString());
    }

    @Test
    void findDefaultScriptByRoute() {
        final Node node = op.findDefaultScriptByRoute(sk);
        assertNotNull(node);
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