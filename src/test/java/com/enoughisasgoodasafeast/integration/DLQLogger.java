package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.Message;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.RabbitQueueFunctions.failQueueForQueue;
import static com.enoughisasgoodasafeast.SharedConstants.*;

public class DLQLogger extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DLQLogger.class);
    private static final boolean autoAck = false;

    private final String consumerTag;
    private final List<Message> deadMessages;

    /**
     * Constructs a new instance and records its association to the passed-in channel.
     *
     * @param channel the channel to which this consumer is attached
     */
    public DLQLogger(Channel channel, String queueName) throws IOException {
        super(channel);
        deadMessages = new ArrayList<>();
        consumerTag = channel.basicConsume(queueName, autoAck, this);
        LOG.info("ConsumerTag '{}' consuming from queue, '{}'", consumerTag, queueName);
    }

    public static DLQLogger createDLQLogger(Properties p) throws IOException, TimeoutException {
        var expectedFailQueue = failQueueForQueue(p.getProperty(CONSUMER_QUEUE_NAME));
        return createDLQLogger(p, expectedFailQueue);
    }

    /**
     * Creates a {@link DLQLogger} consuming from {@code queueName} rather than the auto-derived fail queue.
     * Useful for tests that need to monitor a specific queue (e.g., a retry delay-bucket queue).
     *
     * @param p         properties containing broker host/port
     * @param queueName the exact queue name to consume from
     */
    public static DLQLogger createDLQLogger(Properties p, String queueName) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(p.getProperty(CONSUMER_QUEUE_HOST));
        factory.setPort(Integer.parseInt(p.getProperty(CONSUMER_QUEUE_PORT)));

        var channel = factory.newConnection().createChannel();

        return new DLQLogger(channel, queueName);
    }

    @Override
    public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               AMQP.BasicProperties properties,
                               byte[] body) throws IOException {

        getChannel().basicAck(envelope.getDeliveryTag(), false);

        try {
            LOG.info("handleDelivery thread before: {}", Thread.currentThread().getName());
            var message = Message.fromBytes(body);
            deadMessages.add(message);
            LOG.info("handleDelivery: one-time only {}", deadMessages.getLast());
            LOG.info("handleDelivery thread after: {}", Thread.currentThread().getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void stopConsuming() throws IOException, TimeoutException {
        var channel = this.getChannel();
            if (channel!= null && this.consumerTag != null) {
                // 1. Cancel the specific consumer using the tag
                channel.basicCancel(this.consumerTag);

                // 2. (Optional) Close resources once processing finishes
                channel.close();
            }
    }

    public List<Message> getDeadMessages() {
        return deadMessages;
    }

    public void clearDeadMessages() {
        deadMessages.clear();
    }
}
