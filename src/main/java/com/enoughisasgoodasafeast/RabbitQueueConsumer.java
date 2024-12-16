package com.enoughisasgoodasafeast;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

public class RabbitQueueConsumer implements QueueConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitQueueConsumer.class);

    private static final String EXCHANGE_TYPE = "topic";

    private final MTHandler consumingHandler;

    public static QueueConsumer createQueueConsumer(String configFileName, MTHandler consumingHandler) throws IOException, TimeoutException {
        Properties props = ConfigLoader.readConfig(configFileName);

        String queueHost = props.getProperty("queue.host");
        String queueName = props.getProperty("queue.name");
        String queueRoutingKey = props.getProperty("queue.routingKey");
        boolean isQueueDurable = Boolean.parseBoolean(props.getProperty("queue.durable"));

        return new RabbitQueueConsumer(queueHost, queueName, queueRoutingKey, isQueueDurable, consumingHandler);
    }

    private RabbitQueueConsumer(String queueHost,
                                String queueName,
                                String routingKey,
                                boolean durable,
                                MTHandler consumingHandler)
            throws IOException, TimeoutException {

        LOG.info("Creating RabbitQueueConsumer: queueHost: '{}', queueName: '{}', routingKey: '{}'",
                queueHost, queueName, routingKey);

        if (queueHost == null || queueName == null || routingKey == null || consumingHandler == null) {
            throw new IllegalArgumentException("RabbitQueueConsumer missing required configuration.");
        }

        this.consumingHandler = consumingHandler;

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(queueHost);
        // Setup socket connection, negotiate protocol version and authentication
        Connection connection = factory.newConnection();

        Channel channel = connection.createChannel();

        // The RabbitMQ docs stupidly use a bare string for the exchange type, despite the nice enum that's available.
        // We use the enum because we're not animals.
        channel.exchangeDeclare(queueName, BuiltinExchangeType.TOPIC, durable); // creates topic only if it doesn't already exist.

        AMQP.Queue.DeclareOk declareOk = channel.queueDeclare();
        LOG.info("AMQP.Queue.DeclareOk: queue={} consumerCount={} messageCount={}",
                declareOk.getQueue(), declareOk.getConsumerCount(), declareOk.getMessageCount());

        AMQP.Queue.BindOk bindOk = channel.queueBind(declareOk.getQueue(), queueName, routingKey);
        LOG.info("AMQP.Queue.BindOk: protocolClassId={} protocolMethodId={} protocolMethodName={}",
                bindOk.protocolClassId(), bindOk.protocolMethodId(), bindOk.protocolMethodName());

        // TODO move this into a separate class.
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            LOG.info("ConsumerTag for deliverCallback: {}", consumerTag);
            LOG.info(" [x] Receiving {}: '{}'", delivery.getEnvelope().getRoutingKey(), message);
            boolean ok = this.consumingHandler.handle(message);
            if (ok) {
                LOG.info("Received message: {}", message);
            } else {
                LOG.error("Delivery failed for message: {}", message);
                // FIXME need to implement ack/no-ack with broker.
            }
        };

        LOG.info("DeliverCallback: {}", deliverCallback);

        CancelCallback cancelCallback = (consumerTag) -> {
            LOG.info("ConsumerTag for cancelCallback: {}",consumerTag);
        };

        LOG.info("CancelCallback: {}", cancelCallback);

        // TODO/FIXME handle the ack in our message processing
        String consumerTag = channel.basicConsume(declareOk.getQueue(), true, deliverCallback, cancelCallback);
        LOG.info("consumerTag returned from basicConsume: {}", consumerTag);
    }

    @Override
    public Object dequeue() throws IOException {
        return null;
    }

    @Override
    public long getPollIntervalMs() {
        return 0;
    }

    @Override
    public QueueConsumer setPollIntervalMs(long pollIntervalMs) {
        return null;
    }


//    public static void main(String[] argv) throws Exception {
//        QueueConsumer rqc = RabbitQueueConsumer.createQueueConsumer();
//    }
}
