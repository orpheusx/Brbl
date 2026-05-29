package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.MessageProcessor;
import com.enoughisasgoodasafeast.operator.SndrMessageProcessor;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SndrConsumer extends BrblConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(SndrConsumer.class);

    SndrMessageProcessor processor;

    public SndrConsumer(SndrMessageProcessor processor, Channel channel) {
        super(channel);
        this.processor = processor;
    }

    /**
     * Called when a basic deliver is received for this consumer.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     * @param envelope packaging data for the message
     * @param properties content header data for the message
     * @param body the message body (opaque, client-specific byte array)
     * @throws IOException if unable to deserialize a message.
     */
    @Override
    public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               AMQP.BasicProperties properties,
                               byte[] body) throws IOException {

        // Should be able to deserialize directly assuming Rcvr enqueued a Message
        try {
            long deliveryTag = envelope.getDeliveryTag();
            final Message message = Message.fromBytes(body);
            boolean ack = processor.process(message);
            LOG.info("Processed message: {}", message);
            if(ack) {
                getChannel().basicAck(deliveryTag, false);
                if (!processor.log(message)) {
                    LOG.error("Failed to log {}", message);
                }

            } else {
                LOG.warn("Rejecting {}", message);
                getChannel().basicReject(deliveryTag, true);
            }

        } catch (ClassNotFoundException e) {
            throw new IOException("Deserialization error: " + e.getMessage(), e);
        }
    }

}
