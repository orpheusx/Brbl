package com.enoughisasgoodasafeast;

import com.rabbitmq.client.Channel;

public class TestBrblConsumer extends BrblConsumer{
    /**
     * Constructs a new instance and records its association to the passed-in channel.
     *
     * @param channel the channel to which this consumer is attached
     */
    public TestBrblConsumer(Channel channel) {
        super(channel);
    }
}
