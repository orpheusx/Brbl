package com.enoughisasgoodasafeast;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

public class RabbitQueueConsumer implements QueueConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitQueueConsumer.class);

    public static QueueConsumer createQueueConsumer(String configFileName, MTHandler consumingHandler) throws IOException, TimeoutException {
        Properties props = ConfigLoader.readConfig(configFileName);
        return createQueueConsumer(props, consumingHandler);
    }

    public static QueueConsumer createQueueConsumer(Properties props, MTHandler consumingHandler) throws IOException, TimeoutException {
        String queueHost = props.getProperty("queue.host");
        int queuePort = Integer.parseInt(props.getProperty("queue.port"));
        String queueName = props.getProperty("queue.name");
        String queueRoutingKey = props.getProperty("queue.routingKey");
        boolean isQueueDurable = Boolean.parseBoolean(props.getProperty("queue.durable"));
        int heartbeatTimeoutSeconds = SharedConstants.STANDARD_HEARTBEAT_TIMEOUT_SECONDS;

        return new RabbitQueueConsumer(queueHost, queuePort, queueName, queueRoutingKey, isQueueDurable,
                consumingHandler, heartbeatTimeoutSeconds);
    }

    private RabbitQueueConsumer(String queueHost,
                                int queuePort,
                                String queueName,
                                String routingKey,
                                boolean durable,
                                MTHandler consumingHandler,
                                int requestedHeartbeatTimeout)
            throws IOException, TimeoutException {

        LOG.info("Creating RabbitQueueConsumer: queueHost: '{}', queueName: '{}', routingKey: '{}'",
                queueHost, queueName, routingKey);

        if (queueHost == null || queueName == null || routingKey == null || consumingHandler == null) {
            throw new IllegalArgumentException("RabbitQueueConsumer missing required configuration.");
        }

        ConnectionFactory factory = new ConnectionFactory(); // automaticRecoveryEnabled is true by default.
        factory.setHost(queueHost);
        factory.setPort(queuePort);
        factory.setRequestedHeartbeat(requestedHeartbeatTimeout);

        // Setup socket connection, negotiate protocol version and authentication
        Connection connection = factory.newConnection();

        Channel channel = connection.createChannel();

        // The RabbitMQ docs use a bare string for the exchange type, despite the nice enum that's available.
        // We use the enum because we're not animals.
        // This creates topic only if it doesn't already exist.
        channel.exchangeDeclare(queueName, BuiltinExchangeType.TOPIC, durable); // FIXME leave the durability to the topic producer?

        AMQP.Queue.DeclareOk declareOk = channel.queueDeclare();
        LOG.info("AMQP.Queue.DeclareOk: queue={} consumerCount={} messageCount={}",
                declareOk.getQueue(), declareOk.getConsumerCount(), declareOk.getMessageCount());

        AMQP.Queue.BindOk bindOk = channel.queueBind(declareOk.getQueue(), queueName, routingKey);
        LOG.info("AMQP.Queue.BindOk: protocolClassId={} protocolMethodId={} protocolMethodName={}",
                bindOk.protocolClassId(), bindOk.protocolMethodId(), bindOk.protocolMethodName());

        channel.basicQos(3); // An important number where retrying/re-queueing is concerned.

        // TODO move this into a separate class.
        //        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
        //            String message = new String(delivery.getBody(), "UTF-8");
        //            LOG.info("ConsumerTag for deliverCallback: {}", consumerTag);
        //            LOG.info(" [x] Receiving on {}: '{}'", delivery.getEnvelope().getRoutingKey(), message);
        //            boolean ok = this.consumingHandler.handle(message);
        //            if (ok) {
        //                LOG.info("Processed message: {}", message);
        //            } else {
        //                LOG.error("Delivery failed for message: {}", message);
        //                // FIXME need to implement ack/no-ack with broker.
        //            }
        //            // Either way we want to be done with this message.
        //            long deliveryTag = delivery.getEnvelope().getDeliveryTag();
        //            channel.basicAck(deliveryTag, false);
        //            LOG.info("Acked message with deliveryTag, {}", deliveryTag);
        //        };
        //
        //        LOG.info("DeliverCallback: {}", deliverCallback);

        CancelCallback cancelCallback = (consumerTag) -> {
            LOG.info("Consumer has been CANCELLED. ConsumerTag {}. THIS IS UNEXPECTED!!!", consumerTag);
        };

        LOG.info("CancelCallback: {}", cancelCallback);

        // TODO add support for the other Callback interfaces...
        Consumer consumer = new MTConsumer(channel, consumingHandler);
//        OperatorConsumer operatorConsumer = new OperatorConsumer();

        // TODO/FIXME handle the ack in our message processing
        final String consumerTag = channel.basicConsume(queueName, false, consumer);

        LOG.info("Negotiated heartbeat: {} seconds", connection.getHeartbeat());
        LOG.info("ConsumerTag returned from basicConsume: {}", consumerTag);
    }

    @Override
    public Object dequeue() throws IOException { // FIXME remove from interface since we're not using it?
        return null;
    }

    @Override
    public long getPollIntervalMs() {
        return 0;
    } // FIXME this only makes sense for SQS

    @Override
    public QueueConsumer setPollIntervalMs(long pollIntervalMs) {
        return null;
    }


//    public static void main(String[] argv) throws Exception {
//        QueueConsumer rqc = RabbitQueueConsumer.createQueueConsumer();
//    }
}
