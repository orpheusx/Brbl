package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.MessageProcessor;
import com.enoughisasgoodasafeast.operator.PersistenceManager;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.ShutdownSignalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class BrblConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(BrblConsumer.class);

    MessageProcessor processor;
    PersistenceManager persistenceManager;
    /**
     * Constructs a new instance and records its association to the passed-in channel.
     *
     * @param channel the channel to which this consumer is attached
     */
    public BrblConsumer(MessageProcessor processor, Channel channel) {
        super(channel);
        this.processor = processor;
    }

    // For now, we just log loudly for all the other callback methods of Consumer.
    // All the comments are copied for

    /**
     * Called when the consumer is registered by a call to any of the
     * {@link Channel#basicConsume} methods.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     */
    public void handleConsumeOk(String consumerTag) {
        LOG.warn("handleConsumeOk called with consumerTag {}", consumerTag);
    }

    /**
     * Called when the consumer is cancelled by a call to {@link Channel#basicCancel}.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     */
    public void handleCancelOk(String consumerTag) {
        LOG.warn("handleCancelOk called with consumerTag {}", consumerTag);
    }

    /**
     * Called when the consumer is cancelled for reasons <i>other than</i> by a call to
     * {@link Channel#basicCancel}. For example, the queue has been deleted.
     * See {@link #handleCancelOk} for notification of consumer
     * cancellation due to {@link Channel#basicCancel}.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     * @throws IOException
     */
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

}
