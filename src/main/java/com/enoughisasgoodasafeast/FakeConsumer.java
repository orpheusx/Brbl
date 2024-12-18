package com.enoughisasgoodasafeast;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class FakeConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(FakeConsumer.class);

    private final MTHandler handler;

    /**
     * Constructs a new instance and records its association to the passed-in channel.
     *
     * @param channel the channel to which this consumer is attached
     */
    public FakeConsumer(Channel channel, MTHandler handler) {
        super(channel);
        this.handler = handler;
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        super.handleDelivery(consumerTag, envelope, properties, body);

        String message = new String(body, "UTF-8");
        LOG.info("ConsumerTag for deliverCallback: {}", consumerTag);
        LOG.info(" [x] Receiving on {}: '{}'", envelope.getRoutingKey(), message);
        boolean ok = this.handler.handle(message);
        if (ok) {
            LOG.info("Processed message: {}", message);
        } else {
            LOG.error("Delivery failed for message: {}", message);
            // FIXME need to implement ack/no-ack with broker.
        }
        // Either way we want to be done with this message.
        long deliveryTag = envelope.getDeliveryTag();
//        channel.basicAck(deliveryTag, false);
//        LOG.info("Acked message with deliveryTag, {}", deliveryTag);
    }
}
