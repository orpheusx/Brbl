package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.QueueProducer;
import com.enoughisasgoodasafeast.StatusException;
import com.enoughisasgoodasafeast.sndr.ProcessStateRoutingKey;
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
    public ProcessStateRoutingKey process(Message message) {
        var enqueuedOk = producer.enqueue(message);
        LOG.info("Processed {}", message);
        return enqueuedOk ? new ProcessStateRoutingKey(ProcessState.OK, null)
                : new ProcessStateRoutingKey(ProcessState.ERROR, null);
    }

    @Override
    public boolean log(Message message) {
        LOG.info("Logged {}", message);
        return true;
    }
}
