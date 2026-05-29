package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.*;
import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static com.enoughisasgoodasafeast.operator.Platform.SMS;

/**
 * FIXME we would like the Operator logic to be neutral where the queuing implementation is concerned.
 * This class is "Fake" because it combines direct knowledge of RabbitMQ and the work needed to process
 * messages.
 */
public class FakeOperator implements SessionAwareMessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FakeOperator.class);

    private QueueConsumer queueConsumer;
    private QueueProducer queueProducer;
    private QueueProducerMTHandler producerMTHandler;

    public void init() throws IOException, TimeoutException {
        queueProducer = RabbitQueueProducer.createQueueProducer("fo-mt-sink.properties");
        producerMTHandler = new QueueProducerMTHandler(queueProducer);
        queueConsumer = RabbitQueueConsumer.createQueueConsumer(
                "fo-mo-source.properties", this);
    }

    public void init(Properties properties) throws IOException, TimeoutException {
        LOG.info("Initializing FakeOperator");
        queueProducer = RabbitQueueProducer.createQueueProducer(properties);
        producerMTHandler = new QueueProducerMTHandler(queueProducer);
        queueConsumer = RabbitQueueConsumer.createQueueConsumer(
                properties, this);
        LOG.info("Ready");
    }


    public static void main(String[] args) throws IOException, TimeoutException {
        FakeOperator fakeOperator = new FakeOperator();
        fakeOperator.init();
    }

    @Override
    public BooleanSession process(Message message) {
        LOG.info("Processed {}", message);
        boolean ok = producerMTHandler.handle(message);
        var session = new Session(
                randomUUID(),
                new Node("Node text for fake Session", NodeType.END_OF_CHAT),
                new User(
                        Map.of(SMS, randomUUID()), // platformIds
                        randomUUID(), // groupId
                        Map.of(SMS, "12125551234"), // platformNumbers
                        Map.of(SMS, NanoClock.utcInstant()), // platformCreationTimes
                        "CA", // countryCode
                        Set.of(LanguageCode.ENG), // languages
                        randomUUID(), // claimantId
                        randomUUID(), // companyId
                        Map.of(SMS, "whazzisface"), // platformNickNames
                        Map.of(SMS, new Profile(
                                "surname", "givenName", "SPA")
                        ), //platformProfiles
                        Map.of(SMS, UserStatus.KNOWN) // platformStatus
                ),
                new InMemoryQueueProducer(),
                null);
        return new BooleanSession(ok, session);
    }

    @Override
    public boolean log(Session session, Message message) {
        LOG.info("Logged {}", message);
        return false;
    }

    public static class QueueProducerMTHandler implements MTHandler {

        QueueProducer producer;

        public QueueProducerMTHandler(QueueProducer producer) {
            this.producer = producer;
        }

        @Override
        public boolean handle(Message payload) {
            // FIXME implement a meaningful return value or change return type.
            LOG.info("Processing message, '{}'", payload);
            // if(payload.contains("hello")) {
            //
            //     // Expects a number followed by a space followed by "hello"
            //     String[] inputs = payload.split(TEST_SPACE_TOKEN,2);
            //     if (inputs.length != 2) {
            //         LOG.error("Unexpected input: {}", payload);
            //         return false;
            //     }
            //
            //     LOG.info("Process {} --> {}", inputs[0], inputs[1]);
            //
            //     Message sndText = inputs[0] + " goodbye";
            //     producer.enqueue(sndText);
            // } else {
            return producer.enqueue(payload);
        }

        public MTHandler newHandler(Properties properties) {
            throw new UnsupportedOperationException("TODO placeholder");
        }
    }

}
