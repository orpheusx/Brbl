package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.InMemoryQueueProducer;
import io.jenetics.util.NanoClock;
import org.jline.builtins.Nano;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IO;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.io.IO.println;
import static org.junit.jupiter.api.Assertions.*;

class BlasterIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(BlasterIntegrationTest.class);

    Blaster blaster;

    @BeforeEach
    void setUp() throws IOException, PersistenceManager.PersistenceManagerException, TimeoutException {
        blaster = new Blaster(new InMemoryQueueProducer());
        blaster.init(ConfigLoader.readConfig("persistence_manager_test.properties"));
    }

    @Test
    void exec() throws ExecutionException, InterruptedException {
        // Customers: id, email, status
        // 8285d1a8-2dc0-6752-3758-0076224bc839 | theron.witting@yahoo.com | ACTIVE

        // Scripts: id, name, customer_id, status
        // 0cdbd272-4916-4a88-9826-d43623443fb2 | Script 0   | 8285d1a8-2dc0-6752-3758-0076224bc839 | PROD

         // INSERT INTO brbl_logic.push_campaigns
         //  (id, customer_id, description, script_id, created_at, updated_at, completed_at)
         //  VALUES
         //  (uuidv7(), '8285d1a8-2dc0-6752-3758-0076224bc839', 'Test Campaign 1', '0cdbd272-4916-4a88-9826-d43623443fb2', NOW(), NOW(), null);

        // We have 13 users on the WhatsApp platform with Profiles:
        // select u.id, u.platform_code, u.platform_id, u.status, p.given_name from amalgams a inner join profiles p on p.id = a.profile_id inner join users u on u.id = a.user_id where u.platform_code = 'W';

        // INSERT INTO CAMPAIGN_USERS(campaign_id, user_id, delivered)
        // SELECT '019bd1ff-c890-7a28-9758-7ce559af5e0b', u.id, 'PENDING'
        // FROM amalgams a
        //      INNER JOIN profiles p ON p.id = a.profile_id
        //      INNER JOIN users u ON u.id = a.user_id
        // WHERE u.platform_code = 'W';
        // 13 records inserted.
        var uuid = UUID.fromString("019bd1ff-c890-7a28-9758-7ce559af5e0b");
        final PushReport report = blaster.exec(uuid);

        assertEquals(0, report.numInvalidUsers);
//        assertEquals(13, report.numUsers);
    }

    @Test
    void isSessionExpired() throws InterruptedException {
        var now = NanoClock.utcInstant(); Thread.sleep(1000);
        assertFalse(blaster.isSessionExpired(now));

        final Instant pastExpiration = Instant.ofEpochSecond(blaster.sessionLifetime.toMillis() + 100);
        assertTrue(blaster.isSessionExpired(pastExpiration));
    }

}