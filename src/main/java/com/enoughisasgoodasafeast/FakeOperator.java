package com.enoughisasgoodasafeast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class FakeOperator {

    private static final Logger LOG = LoggerFactory.getLogger(FakeOperator.class);

    private QueueConsumer queueConsumer;
    private QueueProducer queueProducer;

    public void init() throws IOException, TimeoutException {
        LOG.info("Initializing FakeOperator");
        queueProducer = RabbitQueueProducer.createQueueProducer("sndr.properties");
        QueueProducerMTHandler mtHandler = new QueueProducerMTHandler(queueProducer);
        queueConsumer = RabbitQueueConsumer.createQueueConsumer(
                "rcvr.properties", mtHandler);
    }

    public static void main(String[] args) throws IOException, TimeoutException {
        FakeOperator fakeOperator = new FakeOperator();
        fakeOperator.init();
    }

    public static class QueueProducerMTHandler implements MTHandler {

        QueueProducer producer;

        public QueueProducerMTHandler(QueueProducer producer) {
            this.producer = producer;
        }

        @Override
        public boolean handle(String payload) {
            // FIXME implement a meaningful return value or change return type.
            try {
                LOG.info("Processing message, '{}'", payload);

                if(payload.contains("hello")) {

                    // Expects a number followed by a space followed by "hello"
                    String[] inputs = payload.split(" ", 2);
                    if (inputs.length != 2) {
                        LOG.error("Unexpected input: {}", payload);
                        return false;
                    }

                    LOG.info("Received {} --> {}", inputs[0], inputs[1]);

                    String sndText = inputs[0] + " goodbye";
                    producer.enqueue(sndText);
                } else {
                    producer.enqueue(payload);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        }
    }

}
