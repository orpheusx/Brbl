package com.enoughisasgoodasafeast;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;
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
    String failedQueueName;

    public OperatorConsumer(SessionAwareMessageProcessor processor, Channel channel, String failedQueueName) {
        super(channel);
        this.processor = processor;
        this.failedQueueName = failedQueueName;
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
                               byte[] body)
            throws IOException {
        LOG.info("handleDelivery: called");
        long deliveryTag = envelope.getDeliveryTag();
        // Should be able to deserialize directly assuming Rcvr enqueued a Message
        try {
            final Message message = Message.fromBytes(body);
            ProcessStateSession stateAndSession = processor.process(message);
            LOG.info("Processed {}", message);
            switch (stateAndSession.processState()) {
                case OK -> {
                    getChannel().basicAck(deliveryTag, false);
                    processor.complete(message, stateAndSession.session());
                }
                case ERROR -> {
                    LOG.info("Failed {}", message);
                    // Put it on the failed message queue
                    getChannel().basicPublish("", failedQueueName, MessageProperties.PERSISTENT_TEXT_PLAIN, body);
                    // Ack the original once its safely in the failed queue.
                    getChannel().basicAck(deliveryTag, false);
                    // Also write to table or file of error messages?
                    // ...
                }
                case RETRY -> {
                    long numFailed = getRetriedCount(properties);
                    assert numFailed < 50; // Even this seems pathological...
                    LOG.warn("Queueing for retry {}: {}", numFailed, message);
                    String delayByKey = computeDelayRoutingKey(numFailed);
                    if (delayByKey != null) {
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
                    // Any additional bookkeeping needed?
                }
            }

        } catch (ClassNotFoundException e) {
            // We can't recover from this dynamically so ack it
            LOG.error("Failed to deserialize message in {}", envelope, e);
            getChannel().basicAck(deliveryTag, false);
        }
    }

    //    void flushSession(Session session, Message message) {
    //        if (!session.flush(session.getCurrentNode() == null)) {
    //            LOG.error("Errors flushing session: {}", session);
    //        } // TODO Gotta clear the operator's session cache, too but Session doesn't have access to it.
    //        processor.clearSession(SessionKey.newSessionKey(message)); // we're going to need this for testing and, possibly, even production.
    //    }

    // Effectively determines the number of retries supported by the consumer.
    private @Nullable String computeDelayRoutingKey(@NonNull long numFailed) {
        return switch (numFailed) {
            case 0L -> RetryDelayRoutingKey.DELAY_5S.name();
            case 1L -> RetryDelayRoutingKey.DELAY_5S.name(); //FIX -> RetryDelayRoutingKey.DELAY_10S.name();
            case 3L -> RetryDelayRoutingKey.DELAY_5S.name(); //FIX -> RetryDelayRoutingKey.DELAY_30S.name();
            default -> {
                LOG.error("Unsupported number of retries");
                yield null;
            }
        };
    }
}

