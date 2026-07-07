package com.enoughisasgoodasafeast;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
    public void handleDelivery(@NonNull String consumerTag,
                               @NonNull Envelope envelope,
                               AMQP.BasicProperties properties,
                               @NonNull byte[] body)
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
                    } else {
                        LOG.info("Message processed, acked and logged: {}",
                                message.id()); // FIXME change to debug
                    }
                }
                case ERROR -> {
                    getChannel().basicReject(deliveryTag, false);
                    LOG.error("Rejected {}", message);
                    // Write to table or file of error messages?
                }
                case RETRY ->  {
                    long numFailed = getRetriedCount(properties);
                    LOG.warn("Queueing for retry {}: {}", numFailed, message);
                    assert numFailed < 50; // Can't imagine doing anything this many times...
                    String delayByKey = computeDelayRoutingKey(numFailed);
                    if(delayByKey != null) {
                        LOG.info("Routing to delay queue with key {}", delayByKey);
                        routeToDelayBucket(getChannel(), envelope.getExchange(), deliveryTag,
                                properties, body, delayByKey);
                    } else {
                        LOG.info("Retries exceeded. Failing message: {}", message);

                    }
                }
                case NOOP -> {
                    // e.g. OPT_OUT from unknown users or known users that already opted out.
                    LOG.info("Acked NOOP message: {}", message);
                    getChannel().basicAck(deliveryTag, false);
                }
            }

        } catch (ClassNotFoundException e) {
            LOG.error("Failed to deserialize message in {}", envelope);
            getChannel().basicAck(deliveryTag, false);
            throw new IOException("Deserialization error: " + e.getMessage(), e);
        }
    }

    // Effectively determines the number of retries supported by the consumer.
    private @Nullable String computeDelayRoutingKey(@NonNull long numFailed) {
        return switch (numFailed) {
            case 0L -> RetryDelayRoutingKey.DELAY_5S.name();
            case 1L -> RetryDelayRoutingKey.DELAY_10S.name();
            case 3L -> RetryDelayRoutingKey.DELAY_30S.name();
            default -> {
                LOG.error("Unsupported number of retries");
                yield null;
            }
        };
    }
}

