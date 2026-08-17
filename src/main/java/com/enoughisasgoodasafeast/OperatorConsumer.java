package com.enoughisasgoodasafeast;

import com.rabbitmq.client.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of the Rabbit Consumer interface that handles Messages.
 */
public class OperatorConsumer extends BrblConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorConsumer.class);

    SessionAwareMessageProcessor processor;
    String failedExchangeName;
    String retryExchangeName;

    public OperatorConsumer(SessionAwareMessageProcessor processor, Channel channel, String failedExchangeName, String retryExchangeName) {
        super(channel);
        this.processor = processor;
        this.failedExchangeName = failedExchangeName;
        this.retryExchangeName = retryExchangeName;
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
                    try {
                        processor.complete(message, stateAndSession.session(), stateAndSession.isNewUser(), stateAndSession.updatedUserStatus());
                        getChannel().basicAck(deliveryTag, false);
                    } catch (Exception e) {
                        LOG.error("Failed to commit processing for message: {}. Routing to failed queue.", message.id(), e);
                        getChannel().basicPublish(failedExchangeName, envelope.getRoutingKey(), properties, body);
                        getChannel().basicAck(deliveryTag, false);
                    }
                }

                case ERROR -> {
                    LOG.error("Failed {}", message);
                    // Put it on the failed message queue
                    LOG.error("Routing message to failed queue {}.", failedExchangeName);
                    getChannel().basicPublish(failedExchangeName, envelope.getRoutingKey(), properties, body);
                    // Ack the original once its safely in the failed queue.
                    getChannel().basicAck(deliveryTag, false);
                    // Also write to table or file of error messages?
                    // ...
                }
                case RETRY -> {
                    int numRetries = getBrblRetryCount(properties);
                    assert numRetries < 50; // TODO Backstop infinite retries...
                    LOG.warn("Queueing for retry {}: {}", numRetries, message);
                    String delayByKey = computeDelayRoutingKey(numRetries);

                    if (delayByKey != null) {
                        LOG.info("Routing to delay queue with key {}", delayByKey);
                        // Publish to the *retry* exchange which will route to the appropriate delay queue
                        properties = incrementBrblRetryCount(properties, numRetries);
                        routeToDelayBucket(getChannel(), retryExchangeName, deliveryTag,
                                properties, body, delayByKey);

                    } else {
                        // DRY this up; we're doing the same thing in the ERROR case.
                        // Put it on the failed message queue TODO/FIXME somehow compute the routingKey
                        getChannel().basicPublish(failedExchangeName, envelope.getRoutingKey(), properties, body);
                        // Ack the original once its safely in the failed queue.
                        getChannel().basicAck(deliveryTag, false);

                        LOG.info("Retries exceeded. Failed: {}", message);
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
            // TODO write to a file for later forensic analysis?
        }
    }

    private AMQP.BasicProperties incrementBrblRetryCount(AMQP.BasicProperties props, int numRetries) {
        int counter = numRetries + 1;
        LOG.info("Incrementing brbl retry count to {}", counter);
        // Create new properties with the updated header
        Map<String, Object> headers = props.getHeaders();
        if (headers == null) {
            headers = new HashMap<>();
        }

        headers.put(BRBL_RETRY_COUNT_HEADER, counter);

        return new AMQP.BasicProperties.Builder()
                .headers(headers)
                .contentType(props.getContentType())
                .deliveryMode(2) // Persistent
                .build();
    }

}

