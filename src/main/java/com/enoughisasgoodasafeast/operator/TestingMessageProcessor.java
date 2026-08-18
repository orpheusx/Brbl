package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.QueueProducer;
import com.enoughisasgoodasafeast.StatusException;
import io.helidon.http.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestingMessageProcessor implements SndrMessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(TestingMessageProcessor.class);

    QueueProducer producer;

    public TestingMessageProcessor(QueueProducer producer) {
        this.producer = producer;
    }

    @Override
    public StatusException process(Message message) {
        var enqueuedOk = producer.enqueue(message);
        LOG.info("Processed {}", message);
        return enqueuedOk ? new StatusException(Status.OK_200, null) : new StatusException(Status.BAD_REQUEST_400, null);
    }

    @Override
    public boolean log(Message message) {
        LOG.info("Logged {}", message);
        return true;
    }
}
