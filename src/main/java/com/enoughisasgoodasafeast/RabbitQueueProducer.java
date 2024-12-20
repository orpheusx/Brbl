package com.enoughisasgoodasafeast;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static com.rabbitmq.client.BuiltinExchangeType.TOPIC;

public class RabbitQueueProducer implements QueueProducer {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitQueueProducer.class);

    private final String queueHost;
    private final String exchangeName;
    private final String routingKey;

    private final Channel channel;

    public static QueueProducer createQueueProducer(String configFileName) throws IOException, TimeoutException {
        Properties props = ConfigLoader.readConfig(configFileName);

        String queueHost = props.getProperty("queue.host");
        String queueName = props.getProperty("queue.name");
        String queueRoutingKey = props.getProperty("queue.routingKey");

        boolean queueIsDurable = Boolean.parseBoolean(props.getProperty("queue.durable"));

        return new RabbitQueueProducer(queueHost, queueName, queueRoutingKey, queueIsDurable);
    }

    private RabbitQueueProducer(String queueHost, String exchangeName, String routingKey, boolean isDurable)
            throws IOException, TimeoutException {

        LOG.info("Creating RabbitQueueProducer: queueHost: '{}', exchangeName: '{}', routingKey: '{}', isDurable: {}",
                queueHost, exchangeName, routingKey, isDurable);

        this.queueHost = queueHost;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;

        if (queueHost == null || exchangeName == null || routingKey == null) {
            throw new IllegalArgumentException("RabbitQueueProducer missing required configuration.");
        }

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(this.queueHost);

        Connection moConnection = factory.newConnection();
        channel = moConnection.createChannel();
        /*AMQP.Exchange.DeclareOk declareOk = */channel.exchangeDeclare(this.exchangeName, TOPIC, isDurable);

        channel.queueDeclare(this.exchangeName, true, false, false, null);
        channel.queueBind(exchangeName, this.exchangeName, routingKey);

        // NB: RabbitMQ provides an extension that provides confirmation from the broker
        // that each message is received.
        // See https://www.rabbitmq.com/tutorials/tutorial-seven-java
        //      channel.confirmSelect();
        // We won't use this initially
    }

    @Override
    public void enqueue(Object event) throws IOException {
        String message = (String) event;
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        channel.basicPublish(this.exchangeName, this.routingKey, MessageProperties.PERSISTENT_TEXT_PLAIN/*deliveryModeProps*/, payload);
        LOG.info(" [x] Enqueued msg '{}'", message);
    }

    public static void main(String[] args) throws IOException, TimeoutException {
        QueueProducer rqp = RabbitQueueProducer.createQueueProducer("queue.properties");
        long timestamp = System.currentTimeMillis();

        rqp.enqueue("one " + timestamp);
        rqp.enqueue("two " + timestamp);
        rqp.enqueue("three " + timestamp);

        LOG.info("Messages sent. Program complete.");
    }

}
