package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.PersistenceManager;
import com.enoughisasgoodasafeast.operator.MessageProcessor;
import com.enoughisasgoodasafeast.operator.PostgresPersistenceManager;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

public class Sndr implements MessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Sndr.class);

    private QueueConsumer queueConsumer;
    private PersistenceManager persistenceManager;
    private HttpMTHandler httpMtHandler;

    public void init(Properties properties) throws IOException, TimeoutException, PersistenceManagerException {
        LOG.info("Initializing SNDR");
        httpMtHandler = (HttpMTHandler) HttpMTHandler.newHandler(properties);
        queueConsumer = RabbitQueueConsumer.createQueueConsumer(
                properties, this);
        persistenceManager = PostgresPersistenceManager.createPersistenceManager(properties);
    }

    @Override
    public boolean process(Message message) {
        LOG.info("Received outbound message: {}", message);
        boolean delivered = httpMtHandler.handle(message); // TODO using record method temporarily. Gateways will expect their own format.
//        if (delivered) {
//            LOG.info("Delivered message? {}: {}", delivered, message);
//            boolean isInsertOk = postgresPersistenceManager.insertDeliveredMT(message);
//            if (!isInsertOk) {
//                 TODO increment a database specific error counter metric in Prometheus?
//                LOG.error("Failed to log enqueued message, {}", message);
//            }
//        }
        return delivered;
    }

    public boolean log(/*Session session,*/ Message message) {
        boolean isInserted = persistenceManager.insertDeliveredMT(message);
        if(isInserted){
            LOG.info("Delivered {}", message);
        }

        return isInserted;
    }

    public static void main(String[] args) throws IOException, TimeoutException, PersistenceManagerException {
        Sndr sndr = new Sndr();
        final Properties properties = ConfigLoader.readConfig("sndr.properties");
        sndr.init(properties);

        // Test send to verify we can reach the platform
        // sndr.process(new Message(MessageType.MT, "00000", "17816629773"/* FIXME */, "Reachability Test"));
    }
}
