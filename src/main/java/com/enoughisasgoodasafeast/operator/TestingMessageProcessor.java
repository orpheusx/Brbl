package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.QueueProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestingMessageProcessor implements MessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(TestingMessageProcessor.class);

    QueueProducer producer;

    public TestingMessageProcessor(QueueProducer producer) {
        this.producer = producer;
    }

    @Override
    public boolean process(Message message) {
        try {
            producer.enqueue(message);
            LOG.info("Processed {}", message);
        } catch (IOException e) {
            LOG.error("Exception thrown processing {}", message);
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public boolean log(Message message) {
        LOG.info("Logged {}", message);
        return true;
    }
}
