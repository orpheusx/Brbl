package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.QueueProducer;
import com.enoughisasgoodasafeast.RabbitQueueProducer;
import com.rabbitmq.client.*;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.RabbitQueueFunctions.exchangeForQueueName;
import static com.rabbitmq.client.BuiltinExchangeType.DIRECT;

public class TestingQueueProducer implements QueueProducer {

    private static final Logger LOG = LoggerFactory.getLogger(TestingQueueProducer.class);

    private final String exchangeName;
    private final String routingKey;
    private final Channel channel;
    private final AMQP.Queue.DeclareOk queueInfo;

    public TestingQueueProducer(String queueName, String routingKey) throws IOException, TimeoutException {
        this.routingKey = routingKey;

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.1.155");
        factory.setPort(5672);
        Connection connection = factory.newConnection();
        channel = connection.createChannel();

        exchangeName = exchangeForQueueName(queueName); // e.g. opr8r.mo-x
        channel.exchangeDeclarePassive(exchangeName);
        LOG.info("Found exchange {}", exchangeName);
        queueInfo = channel.queueDeclarePassive(queueName);
        LOG.info("Found queue {}. Will use routing key {}", queueName, routingKey);

    }

    @Override
    public boolean enqueue(@NonNull Message message) {
        byte[] payload = null;
        try {
            payload = message.toBytes();
            channel.basicPublish(this.exchangeName, this.routingKey, null, payload);
            LOG.info(" [x] Exchange '{}' enqueued msg '{}'", this.exchangeName, message);
            return true;
        } catch (IOException e) {
            LOG.warn(" [x] Failed to enqueue msg '{}'", this.exchangeName, e);
            return false;
        }
    }

    public int currentQueueSize() {
        return queueInfo.getMessageCount();
    }

    @Override
    public void shutdown() throws IOException, TimeoutException {
        LOG.info(" [x] Shutting down testing queue producer");
    }
}
