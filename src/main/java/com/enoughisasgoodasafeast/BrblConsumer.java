package com.enoughisasgoodasafeast;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.ShutdownSignalException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public abstract class BrblConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(BrblConsumer.class);
    public static final String BRBL_RETRY_COUNT_HEADER = "x-brbl-retry-count";

    /**
     * Constructs a new instance and records its association to the passed-in channel.
     *
     * @param channel the channel to which this consumer is attached
     */
    public BrblConsumer(Channel channel) {
        super(channel);
    }

    // For now, we just log loudly for all the other callback methods of Consumer.
    // All the comments are copied for

    /**
     * Called when the consumer is registered by a call to any of the
     * {@link Channel#basicConsume} methods.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     */
    public void handleConsumeOk(String consumerTag) {
        LOG.debug("handleConsumeOk called with consumerTag {}", consumerTag);
    }

    /**
     * Called when the consumer is cancelled by a call to {@link Channel#basicCancel}.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     */
    public void handleCancelOk(String consumerTag) {
        LOG.warn("handleCancelOk called with consumerTag {}", consumerTag);
    }

    /**
     * Called when the consumer is canceled for reasons <i>other than</i> by a call to
     * {@link Channel#basicCancel}. For example, the queue has been deleted.
     * See {@link #handleCancelOk} for notification of consumer
     * cancellation due to {@link Channel#basicCancel}.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     * @throws IOException stub implementation
     */
    @Override
    public void handleCancel(String consumerTag) throws IOException {
        LOG.warn("handleCancel called with consumerTag {}", consumerTag);
    }

    /**
     * Called when either the channel or the underlying connection has been shut down.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     * @param sig a {@link ShutdownSignalException} indicating the reason for the shutdown
     */
    public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        LOG.warn("handleShutdownSignal called with consumerTag {} and exception: {}", consumerTag, sig.toString());
    }

    /**
     * Called when a <code><b>basic.recover-ok</b></code> is received
     * in reply to a <code><b>basic.recover</b></code>. All messages
     * received before this is invoked that haven't been <i>ack</i>'ed will be
     * re-delivered. All messages received afterward won't be.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     */
    public void handleRecoverOk(String consumerTag) {
        LOG.warn("handleRecoverOk called with consumerTag {}", consumerTag);
    }

    /**
     *  RabbitMQ's nested x-death structure is complex; ours is simpler...
     */
    static int getBrblRetryCount(AMQP.BasicProperties properties) {
        Map<String, Object> headers = properties.getHeaders();
        if (headers != null && headers.containsKey(BRBL_RETRY_COUNT_HEADER)) {
            Object countObj = headers.get(BRBL_RETRY_COUNT_HEADER);
            return Integer.parseInt(countObj.toString());
        }
        return 0; // First attempt
    }

    /**
     * Safely acknowledges the original message from the main queue and republishes
     * it directly to the targeted exponential backoff queue.
     */
    static void routeToDelayBucket(Channel channel, String exchangeName, long deliveryTag,
                                           AMQP.BasicProperties properties, byte[] body,
                                           String routingKey) throws IOException {
        // Publish to the specific delay exchange path
        channel.basicPublish(exchangeName, routingKey, properties/*propsBuilder.build()*/, body);

        // Acknowledge the old placement so it leaves the primary queue
        channel.basicAck(deliveryTag, false);
    }


    // Determines both the number of retries supported by the consumer and the delay for each.
    // TODO Extract this into an interface so it can be replaced easily.
    @Nullable String computeDelayRoutingKey(int numFailed) {
        return switch (numFailed) {
            case 0 -> RetryDelayRoutingKey.DELAY_5S.name();
            case 1 -> RetryDelayRoutingKey.DELAY_10S.name();
            case 2 -> RetryDelayRoutingKey.DELAY_30S.name();
            case 3 -> RetryDelayRoutingKey.DELAY_1M.name();
            default -> {
                LOG.error("Max retries exceeded: {}", numFailed);
                yield null;
            }
        };
    }

}
