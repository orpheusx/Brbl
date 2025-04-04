package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.MessageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.SharedConstants.*;

/**
 * FIXME we would like the Operator logic to be neutral where the queuing implementation is concerned.
 * This class is "Fake" because it combines direct knowledge of RabbitMQ and the work needed to process
 * messages.
 */
public class FakeOperator implements MessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FakeOperator.class);

    private QueueConsumer queueConsumer;
    private QueueProducer queueProducer;
    private QueueProducerMTHandler producerMTHandler;

    public void init() throws IOException, TimeoutException {
        LOG.info("Initializing FakeOperator");
        queueProducer = RabbitQueueProducer.createQueueProducer("fo-mt-sink.properties");
        producerMTHandler = new QueueProducerMTHandler(queueProducer);
        queueConsumer = RabbitQueueConsumer.createQueueConsumer(
                "fo-mo-source.properties", this/*producerMTHandler*/);
        // FIXME add a startup messsage that signals readiness...
    }

    public static void main(String[] args) throws IOException, TimeoutException {
        FakeOperator fakeOperator = new FakeOperator();
        fakeOperator.init();
    }

    @Override
    public boolean process(Message message) {
        return producerMTHandler.handle(message.text());
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
                    String[] inputs = payload.split(TEST_SPACE_TOKEN,2);
                    if (inputs.length != 2) {
                        LOG.error("Unexpected input: {}", payload);
                        return false;
                    }

                    LOG.info("Process {} --> {}", inputs[0], inputs[1]);

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

        public MTHandler newHandler(Properties properties) {
            throw new UnsupportedOperationException("TODO placeholder");
        }
    }

}
