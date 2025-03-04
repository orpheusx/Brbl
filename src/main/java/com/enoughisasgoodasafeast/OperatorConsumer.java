package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.Operator;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import java.io.IOException;

public class OperatorConsumer extends DefaultConsumer {

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
            final MOMessage moMessage = MOMessage.fromBytes(body);
            operator.process(moMessage);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("", e);
        }

    }
}

