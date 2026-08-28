package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.PersistenceManager;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import com.enoughisasgoodasafeast.operator.PostgresPersistenceManager;
import com.enoughisasgoodasafeast.operator.SndrMessageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

public class Sndr implements SndrMessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Sndr.class);

    private QueueConsumer queueConsumer;
    private PersistenceManager persistenceManager;
    private HttpMTSender httpMtHandler;

    public Sndr() {
    }

    public Sndr(QueueConsumer queueConsumer ) {
        this.queueConsumer = queueConsumer;
    }

    public Sndr(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }
    public Sndr(QueueConsumer queueConsumer, PersistenceManager persistenceManager) {
        this.queueConsumer = queueConsumer;
        this.persistenceManager = persistenceManager;
    }

    public void init(Properties properties) throws IOException, TimeoutException, PersistenceManagerException {
        LOG.info("Initializing SNDR");
        httpMtHandler = (HttpMTSender) HttpMTSender.newHandler(properties);
        if(queueConsumer == null) {
            queueConsumer = RabbitQueueConsumer.createQueueConsumer(properties, this);
        }
        if(persistenceManager == null) {
            persistenceManager = PostgresPersistenceManager.createPersistenceManager(properties);
        }
    }

    @Override
    public StatusException process(Message message) {
        LOG.info("Processing outbound message: {}", message);
        StatusException delivered = httpMtHandler.send(message);
        LOG.info("Message delivery: {}: {}", delivered, message);
        return delivered;
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

    public static void main(String[] args) throws IOException, TimeoutException, PersistenceManagerException {
        Sndr sndr = new Sndr();
        final Properties properties = ConfigLoader.readConfig("sndr.properties");
        sndr.init(properties);

        // Test send to verify we can reach the platform
        // sndr.process(new Message(MessageType.MT, "00000", "17816629773"/* FIXME */, "Reachability Test"));
    }
}
