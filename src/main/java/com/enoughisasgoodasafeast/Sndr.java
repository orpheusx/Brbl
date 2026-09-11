package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.PersistenceManager;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import com.enoughisasgoodasafeast.operator.PostgresPersistenceManager;
import com.enoughisasgoodasafeast.operator.SndrMessageProcessor;
import com.enoughisasgoodasafeast.sndr.ProcessStateRoutingKey;
import com.enoughisasgoodasafeast.sndr.TelnyxSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

public class Sndr implements SndrMessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Sndr.class);

    private QueueConsumer queueConsumer;
    private PersistenceManager persistenceManager;
    private TelnyxSender telnyxSender;

    public Sndr() {
    }

    public Sndr(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        this.telnyxSender = new TelnyxSender(persistenceManager);
    }

    public Sndr(QueueConsumer queueConsumer, PersistenceManager persistenceManager) {
        this.queueConsumer = queueConsumer;
        this.persistenceManager = persistenceManager;
        this.telnyxSender = new TelnyxSender(persistenceManager);
    }

    public void init(Properties properties) throws IOException, TimeoutException, PersistenceManagerException {
        LOG.info("Initializing SNDR");

        if (queueConsumer == null) {
            queueConsumer = RabbitQueueConsumer.createQueueConsumer(properties, this);
        }

        if (persistenceManager == null) {
            persistenceManager = PostgresPersistenceManager.createPersistenceManager(properties);
        }

        if (telnyxSender == null) {
            telnyxSender = new TelnyxSender(persistenceManager);
        }
    }

    @Override
    public ProcessStateRoutingKey process(Message message) {
        LOG.info("Processing outbound message: {}", message);
        // TODO Check creation date of the Message. We don't want to try sending messages that are outside their window of relevance.
        //  Requires a lookup of the customer's preference from the database. TBD
        return telnyxSender.send(message);
    }

    public boolean log(Message message) {
        boolean isInserted = persistenceManager.insertDeliveredMT(message);
        if (isInserted) {
            LOG.info("Delivered {}", message);
        }

        return isInserted;
    }

    public void shutdown() throws IOException, TimeoutException {
        if (queueConsumer != null) {
            queueConsumer.shutdown();
            LOG.info("Shutdown queueConsumer.");
        }
        LOG.info("Shutdown Sndr");
    }

    // Called by Brbl.main
    public static void main(String[] args) throws IOException, TimeoutException, PersistenceManagerException {
        final Sndr sndr = new Sndr();
        final Properties properties = ConfigLoader.readConfig("sndr.properties");
        sndr.init(properties);
    }
}
