package com.enoughisasgoodasafeast;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FakeConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(FakeConsumer.class);

    private final MTHandler handler;

    /**
     * Constructs a new instance and records its association to the passed-in channel.
     *
     * @param channel the channel to which this consumer is attached
     * @param handler the handler that will process incoming messages in some fashion.
     */
    public FakeConsumer(Channel channel, MTHandler handler) {
        super(channel);
        this.handler = handler;
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        // super.handleDelivery(consumerTag, envelope, properties, body);
        long deliveryTag = envelope.getDeliveryTag();

        try {
            String message = new String(body, StandardCharsets.UTF_8);
            LOG.info("ConsumerTag for handleDelivery: {}", consumerTag);
            LOG.info(" [x] Receiving on {}: '{}'", envelope.getRoutingKey(), message);
            boolean ok = this.handler.handle(message);
            if (ok) {
                LOG.info("Processed message: {}", message);
            } else {
                LOG.error("Delivery failed for message: {}", message);
            }
        } finally {
            // Either way we want to be done with this message.
            LOG.info("Acked message with deliveryTag, {}", deliveryTag);
            getChannel().basicAck(deliveryTag, false);
        }

    }
}
