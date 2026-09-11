package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.ProcessState;
import com.enoughisasgoodasafeast.operator.SndrMessageProcessor;
import com.enoughisasgoodasafeast.sndr.ProcessStateMessage;
import com.enoughisasgoodasafeast.sndr.ProcessStateRoutingKey;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SndrConsumer extends BrblConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(SndrConsumer.class);

    SndrMessageProcessor processor;
    String failedExchangeName;
    String retryExchangeName;

    public SndrConsumer(SndrMessageProcessor processor, Channel channel, String failedExchangeName, String retryExchangeName) {
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
    public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               AMQP.BasicProperties properties,
                               byte[] body) throws IOException {
        try {
            final long deliveryTag = envelope.getDeliveryTag();
            final var message = Message.fromBytes(body);
            final var psk = processor.process(message);

            switch (psk.processState()) {
                case OK -> {
                    LOG.info("Sent {}", message);
                    getChannel().basicAck(deliveryTag, false);
                    // TODO add .complete(Message,correlating_gateway_id) to write a log to a (new) table
                }
                case ERROR -> {
                    LOG.error("Failed to send {}", message);
                    getChannel().basicPublish(failedExchangeName, envelope.getRoutingKey(), properties,
                            /*new ProcessStateMessage(ProcessState.ERROR, message).toBytes()*/ body);
                    getChannel().basicAck(deliveryTag, false);
                }
                case RETRY -> {
                    LOG.warn("Retrying with {}: {}", psk.retryDelayRoutingKey(), message);
                    int numRetries = getBrblRetryCount(properties);

                    if (numRetries > processor.getRouteRetryLimit(message)) { // found using platform and "from" number
                        // fail the message
                        LOG.warn("Retry count {} exceeded limit for {}", numRetries, message);
                        getChannel().basicPublish(failedExchangeName, envelope.getRoutingKey(), properties,
                                /*new ProcessStateMessage(ProcessState.ERROR, message).toBytes()*/ body);
                        getChannel().basicAck(deliveryTag, false);
                        LOG.warn("Acked message ('{}') and published to {}", message.text(), failedExchangeName);
                    } else {
                        properties = incrementBrblRetryCount(properties, numRetries);
                        routeToDelayBucket(getChannel(), retryExchangeName, deliveryTag,
                                properties, body,
                                psk.retryDelayRoutingKey().name());
                        LOG.warn("Delaying retry for message ('{}') by {}ms", message.text(),
                                psk.retryDelayRoutingKey().delayMs());
                    }
                }
                case NOOP -> { // Change/add enum: EXPIRE ?
                    LOG.info("FIXME This case makes no sense for Sndr.");
                    getChannel().basicAck(deliveryTag, false);
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("Deserialization error: " + e.getMessage(), e);
        }
    }


//    @Override
//    public void handleDelivery(String consumerTag,
//                               Envelope envelope,
//                               AMQP.BasicProperties properties,
//                               byte[] body) throws IOException {
//
//        try {
//             long deliveryTag = envelope.getDeliveryTag();
//            final Message message = Message.fromBytes(body);
//            var statusException = processor.process(message); // TODO return something with a ProcessState
//
//            LOG.info("Processed message: {}", message);
//            // TODO Retry? Throttle? Other situations
//            if(statusException.isSuccess()) {
//                getChannel().basicAck(deliveryTag, false);
//                if (!processor.log(message)) {
//                    LOG.error("Failed to log {}", message);
//                }
//
//            } else {
//                LOG.warn("Rejecting {}", message);
//                getChannel().basicReject(deliveryTag, true);
//            }
//
//        } catch (ClassNotFoundException e) {
//            throw new IOException("Deserialization error: " + e.getMessage(), e);
//        }
//    }

}
