package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.MessageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

public class Sndr implements MessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Sndr.class);

    public QueueConsumer queueConsumer;
    HttpMTHandler httpMtHandler;

    public void init() throws IOException, TimeoutException {
        LOG.info("Initializing SNDR");
        final Properties properties = ConfigLoader.readConfig("sndr.properties");
        this.init(properties);
    }

    public void init(Properties properties) throws IOException, TimeoutException {
        httpMtHandler = (HttpMTHandler) HttpMTHandler.newHandler(properties);
        queueConsumer = RabbitQueueConsumer.createQueueConsumer(
                properties, this);
    }

    @Override
    public boolean process(Message message) {
        LOG.info("Received outbound message: {}", message);
        boolean delivered = httpMtHandler.handle(message.toString()); // TODO using record method temporarily. Gateways will expect their own format.
        LOG.info("Delivered message? {}: {}", delivered, message);
        return delivered;
    }

    public static void main(String[] args) throws IOException, TimeoutException {
        Sndr sndr = new Sndr();
        sndr.init();

        // Test send to verify we can reach the platform
        // sndr.process(new Message(MessageType.MT, "00000", "17816629773"/* FIXME */, "Reachability Test"));
    }
}
