package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.Operator;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class OperatorConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorConsumer.class);

    private final Operator operator;

    /**
     * Constructs a new instance and records its association to the passed-in channel.
     *
     * @param channel the channel to which this consumer is attached
     */
    public OperatorConsumer(Operator operator, Channel channel) {
        super(channel);
        this.operator = operator;
    }

    @Override
    public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               AMQP.BasicProperties properties,
                               byte[] body)
            throws IOException {

        // Should be able to deserialize directly assuming Rcvr enqueued an MOMessage
        try {
            long deliveryTag = envelope.getDeliveryTag();
            final MOMessage moMessage = MOMessage.fromBytes(body);
            boolean ack = operator.process(moMessage);
            LOG.info("Processed message: {}", moMessage);
            if(ack) {
                getChannel().basicAck(deliveryTag, false);
            } else {
                getChannel().basicReject(deliveryTag, true);
            }

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("", e);
        }

    }
}

