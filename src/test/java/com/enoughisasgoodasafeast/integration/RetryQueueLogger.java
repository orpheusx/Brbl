package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.RetryDelayRoutingKey;
import com.enoughisasgoodasafeast.sndr.ProcessStateMessage;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.RabbitQueueFunctions.delayQueueForRoutingKey;
import static com.enoughisasgoodasafeast.SharedConstants.*;

public class RetryQueueLogger extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DLQLogger.class);
    private static final boolean autoAck = false;

    private final String consumerTag;
    private final List<Message> retryingMessages;

    /**
     * Constructs a new instance and records its association to the passed-in channel.
     *
     * @param channel the channel to which this consumer is attached
     */
    public RetryQueueLogger(Channel channel, String queueName) throws IOException {
        super(channel);
        retryingMessages = new ArrayList<>();
        consumerTag = channel.basicConsume(queueName, autoAck, this);
        LOG.info("ConsumerTag '{}' consuming from queue, '{}'", consumerTag, queueName);
    }

    public static RetryQueueLogger createDLQLogger(Properties p) throws IOException, TimeoutException {
        var retryQueue = delayQueueForRoutingKey(p.getProperty(CONSUMER_QUEUE_NAME), RetryDelayRoutingKey.DELAY_5S);
        return createRetryQueueLogger(p, retryQueue);
    }

    /**
     * Creates a {@link DLQLogger} consuming from {@code queueName} rather than the auto-derived fail queue.
     * Useful for tests that need to monitor a specific queue (e.g., a retry delay-bucket queue).
     *
     * @param p         properties containing broker host/port
     * @param queueName the exact queue name to consume from
     */
    public static RetryQueueLogger createRetryQueueLogger(Properties p, String queueName) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(p.getProperty(CONSUMER_QUEUE_HOST));
        factory.setPort(Integer.parseInt(p.getProperty(CONSUMER_QUEUE_PORT)));

        var channel = factory.newConnection().createChannel();

        return new RetryQueueLogger(channel, queueName);
    }

    @Override
    public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               AMQP.BasicProperties properties,
                               byte[] body) throws IOException {

        getChannel().basicAck(envelope.getDeliveryTag(), false);

        try {
            var message = Message.fromBytes(body);
            retryingMessages.add(message);
            LOG.info("DLQLogger received: {}", message);
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

    public List<Message> getRetryingMessages() {
        return retryingMessages;
    }

    public void clearRetryingMessages() {
        retryingMessages.clear();
    }
}
