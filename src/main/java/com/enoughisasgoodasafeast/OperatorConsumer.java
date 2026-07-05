package com.enoughisasgoodasafeast;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * An implementation of the Rabbit Consumer interface that handles Messages.
 */
public class OperatorConsumer extends BrblConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorConsumer.class);

    SessionAwareMessageProcessor processor;

    public OperatorConsumer(SessionAwareMessageProcessor processor, Channel channel) {
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
                               byte[] body)
            throws IOException {

        long deliveryTag = envelope.getDeliveryTag();
        // Should be able to deserialize directly assuming Rcvr enqueued a Message
        try {
            final Message message = Message.fromBytes(body);
            ProcessStateSession stateAndSession = processor.process(message);
            LOG.info("Processed {}", message);
            switch (stateAndSession.processState()) {
                case OK -> {
                    getChannel().basicAck(deliveryTag, false);
                    if (!processor.log(stateAndSession.session(), message)) {
                        LOG.error("Failed to log {}", message);
                    }
                }
                case ERROR -> {
                    LOG.error("Rejected {}", message);
                    getChannel().basicReject(deliveryTag, false);
                    // Write to table of error messages? Or just a special file?
                }
                case RETRY ->  {
                    LOG.warn("Queuing for retry {}", message);
                    // TODO ...
                }
                case NOOP -> {
                    LOG.info("No processing or logging required for message: {}", message);
                    getChannel().basicAck(deliveryTag, false);
                }
            }

        } catch (ClassNotFoundException e) {
            LOG.error("Failed to deserialize message in {}", envelope);
            getChannel().basicAck(deliveryTag, false);
            throw new IOException("Deserialization error: " + e.getMessage(), e);
        }
    }
}

