package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.operator.Operator;
import com.enoughisasgoodasafeast.operator.TestingMessageProcessor;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;

import static com.enoughisasgoodasafeast.Message.newMO;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Expects a running RabbitMQ in a container.
 * TODO add Testcontainers setup
 */
public class OperatorMessageFlowIT {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorMessageFlowIT.class);

    public static final String MOBILE_MX = "522005551234"; // Mexico City, MX
    public static final String SHORT_CODE = "4567";

    public static final Message keywordMO = newMO(
            MOBILE_MX, SHORT_CODE, "Color quiz"
    );
    public static final Message flortMO = newMO(
            MOBILE_MX, SHORT_CODE, "flort"
    );
    public static final Message unexpectedMO = newMO(
            MOBILE_MX, SHORT_CODE, "blargh"
    );
    public static final Message changeTopicMO = newMO(
            MOBILE_MX, SHORT_CODE, "change topic"
    );
    public static final Message selectWolverinesMO = newMO(
            MOBILE_MX, SHORT_CODE, "wolverines"
    );


    @Test
    public void testSimpleMessageFlow() {
        assertDoesNotThrow(() -> {
            QueueProducer simulatedMOSource = RabbitQueueProducer.createQueueProducer("test_producer.properties");

            InMemoryQueueProducer operatorProducer = new InMemoryQueueProducer(); // sink for Operator output
            Operator operator = new Operator(null, operatorProducer);

            operator.init(ConfigLoader.readConfig("operator_test.properties"));

            simulatedMOSource.enqueue(keywordMO);
            await().atMost(5, SECONDS).until(mtResponsesDelivered(operatorProducer));

            List<Message> queuedMessages = operatorProducer.getQueuedMessages();

            Message colorQuizMT = operatorProducer.getQueuedMessages().getFirst();
            assertNotNull(colorQuizMT);
            assertEquals(keywordMO.from(), colorQuizMT.to());
            assertEquals(keywordMO.to(), colorQuizMT.from());
            assertTrue(colorQuizMT.text().contains("What's you favorite color?"));

            queuedMessages.clear();

            simulatedMOSource.enqueue(flortMO);
            await().atMost(5, SECONDS).until(mtResponsesDelivered(operatorProducer));

            Message flortConfirmation = operatorProducer.getQueuedMessages().getFirst();
            assertNotNull(flortConfirmation);
            assertEquals(keywordMO.from(), flortConfirmation.to());
            assertEquals(keywordMO.to(), flortConfirmation.from());
            assertTrue(flortConfirmation.text().contains("for the cool kids"));

            queuedMessages.clear();

            simulatedMOSource.shutdown();
            operator.shutdown();
            // "rabbitmqctl purge_queue test.mo" can be used to clear things until we get test working
        });
    }

    @Test
    public void testMessageFlowWithUnexpectedInput() {
        assertDoesNotThrow(() -> {
            var simulatedMOSource = RabbitQueueProducer.createQueueProducer("test_producer.properties");

            var operatorProducer = new InMemoryQueueProducer();
            var operator = new Operator(null, operatorProducer);

            operator.init(ConfigLoader.readConfig("operator_test.properties"));

            simulatedMOSource.enqueue(keywordMO);
            await().atMost(5, SECONDS).until(mtResponsesDelivered(operatorProducer));

            List<Message> queuedMessages = operatorProducer.getQueuedMessages();

            Message colorQuizMT = operatorProducer.getQueuedMessages().getFirst();
            assertNotNull(colorQuizMT);
            assertEquals(keywordMO.from(), colorQuizMT.to());
            assertEquals(keywordMO.to(), colorQuizMT.from());
            assertTrue(colorQuizMT.text().contains("What's you favorite color?"));

            queuedMessages.clear();

            simulatedMOSource.enqueue(unexpectedMO);
            await().atMost(5, SECONDS).until(mtResponsesDelivered(operatorProducer));

            Message errorMessage = operatorProducer.getQueuedMessages().getFirst();
            assertNotNull(errorMessage);
            assertEquals(keywordMO.from(), errorMessage.to());
            assertEquals(keywordMO.to(), errorMessage.from());
            assertTrue(errorMessage.text().contains("Try again"));

            queuedMessages.clear();

            simulatedMOSource.shutdown();
            operator.shutdown();
            // "rabbitmqctl purge_queue test.mo" can be used to clear things until we get test working
        });
    }

    @Test
    public void testMessageFlowWithUnexpectedInputAndChangeTopicRequested() {
        assertDoesNotThrow(() -> {
            var simulatedMOSource = (RabbitQueueProducer) RabbitQueueProducer.createQueueProducer("test_producer.properties");

            var operatorProducer = new InMemoryQueueProducer();
            var operator = new Operator(null, operatorProducer);

            operator.init(ConfigLoader.readConfig("operator_test.properties"));

            simulatedMOSource.enqueue(keywordMO);
//            await().atMost(3, SECONDS).until(mtResponsesDelivered(operatorProducer));
            Thread.sleep(3000);

            List<Message> queuedMessages = operatorProducer.getQueuedMessages();

            Message colorQuizMT = operatorProducer.getQueuedMessages().getFirst();
            assertNotNull(colorQuizMT);
            assertEquals(keywordMO.from(), colorQuizMT.to());
            assertEquals(keywordMO.to(), colorQuizMT.from());
            assertTrue(colorQuizMT.text().contains("What's you favorite color?"));

            queuedMessages.clear();

            simulatedMOSource.enqueue(unexpectedMO);
            await().atMost(5, SECONDS).until(mtResponsesDelivered(operatorProducer));

            Message errorMessage = operatorProducer.getQueuedMessages().getFirst();
            assertNotNull(errorMessage);
            assertEquals(keywordMO.from(), errorMessage.to());
            assertEquals(keywordMO.to(), errorMessage.from());
            assertTrue(errorMessage.text().contains("Try again"));

            queuedMessages.clear();

            simulatedMOSource.enqueue(changeTopicMO);
            await().atMost(5, SECONDS).until(mtResponsesDelivered(operatorProducer));

            Message acknowledgeTopicChange = queuedMessages.getFirst();
            assertNotNull(acknowledgeTopicChange);
            assertEquals(keywordMO.from(), acknowledgeTopicChange.to());
            assertEquals(keywordMO.to(), acknowledgeTopicChange.from());
            assertTrue(acknowledgeTopicChange.text().contains("You want to talk about something else? OK"));

            Message availableTopicMessage = queuedMessages.get(1);
            assertNotNull(availableTopicMessage);
            assertEquals(keywordMO.from(), availableTopicMessage.to());
            assertEquals(keywordMO.to(), availableTopicMessage.from());

            queuedMessages.clear();

            String topicText = availableTopicMessage.text();
            assertTrue(topicText.contains("topics I can talk about"));
            assertTrue(topicText.contains("wolverines"));
            assertTrue(topicText.contains("international monetary policy"));

            simulatedMOSource.enqueue(selectWolverinesMO);
            await().atMost(5, SECONDS).until(mtResponsesDelivered(operatorProducer));

            Message confirmWolverineMO = queuedMessages.getFirst();
            assertNotNull(confirmWolverineMO);
            assertEquals(keywordMO.from(), confirmWolverineMO.to());
            assertEquals(keywordMO.to(), confirmWolverineMO.from());
            assertTrue(confirmWolverineMO.text().contains("pointy teeth"));

            simulatedMOSource.shutdown();
            operator.shutdown();
            // "rabbitmqctl purge_queue test.mo" can be used to clear things until we get test working
        });
    }

    private Callable<Boolean> mtResponsesDelivered(InMemoryQueueProducer operatorProducer) {
        return () -> {
            LOG.info("...");
            return !operatorProducer.getQueuedMessages().isEmpty();
        };

    }

}
