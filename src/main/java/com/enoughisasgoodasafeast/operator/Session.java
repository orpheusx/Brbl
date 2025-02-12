package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.QueueProducer;
import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Won't work as a Record since we need to update the currentScript field
 * and maintain state
 */
public class Session {
    private static final Logger LOG = LoggerFactory.getLogger(Session.class);

    final UUID id;
    final User user;
    final long startTimeNanos;
    Script currentScript;

    // processing resources
    QueueProducer producer; // different ones depending on the Platform
    // Db manager goes here...

    public Session(UUID id, Script currentScript, User user, QueueProducer producer) {
        startTimeNanos = NanoClock.systemUTC().nanos();
        this.id = id;
        this.currentScript = currentScript;
        this.user = user;
        this.producer = producer;
        LOG.info("Created Session {} for User {}", id, user.id());
    }

}
