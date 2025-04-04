package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.MessageProcessor;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.RabbitQueueFunctions.exchangeForQueueName;

public class RabbitQueueConsumer implements QueueConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitQueueConsumer.class);

    public static final boolean QUEUE_EXCLUSIVE = false;
    public static final boolean QUEUE_AUTO_DELETE = false;
    public static final boolean QUEUE_CONSUME_AUTO_ACK = false;

    public Connection connection;
    public Channel channel;

    public static QueueConsumer createQueueConsumer(String configFileName, MessageProcessor processor/*Consumer consumer*//*MTHandler consumingHandler*/) throws IOException, TimeoutException {
        Properties props = ConfigLoader.readConfig(configFileName);
        return createQueueConsumer(props, processor);
    }

    public static QueueConsumer createQueueConsumer(Properties props, MessageProcessor processor/*MTHandler consumingHandler*/) throws IOException, TimeoutException {
        String queueHost = props.getProperty("consumer.queue.host");
        int queuePort = Integer.parseInt(props.getProperty("consumer.queue.port"));
        String queueName = props.getProperty("consumer.queue.name");
        String queueRoutingKey = props.getProperty("consumer.queue.routingKey");
        boolean isQueueDurable = Boolean.parseBoolean(props.getProperty("consumer.queue.durable"));
        int heartbeatTimeoutSeconds = SharedConstants.STANDARD_HEARTBEAT_TIMEOUT_SECONDS;

        return new RabbitQueueConsumer(queueHost, queuePort, queueName, queueRoutingKey, isQueueDurable,
                processor, heartbeatTimeoutSeconds);
    }

    private RabbitQueueConsumer(String queueHost,
                                int queuePort,
                                String queueName,
                                String routingKey,
                                boolean durable,
                                MessageProcessor processor,
                                int requestedHeartbeatTimeout)
            throws IOException, TimeoutException {

        LOG.info("Creating RabbitQueueConsumer: queueHost: '{}', queueName: '{}', routingKey: '{}'",
                queueHost, queueName, routingKey);

        if (queueHost == null || queueName == null || routingKey == null || processor == null) {
            throw new IllegalArgumentException("RabbitQueueConsumer missing required configuration.");
        }

        ConnectionFactory factory = new ConnectionFactory(); // automaticRecoveryEnabled is true by default.
        factory.setHost(queueHost);
        factory.setPort(queuePort);
        factory.setRequestedHeartbeat(requestedHeartbeatTimeout);

        // Setup socket connection, negotiate protocol version and authentication
        /*Connection*/this.connection = factory.newConnection();

        /*Channel */this.channel = connection.createChannel();

        // The RabbitMQ docs use a bare string for the exchange type, despite the nice enum that's available.
        // We use the enum because we're not animals.
        // This creates topic only if it doesn't already exist.
        // FIXME leave the durability to the topic producer?
        final String matchingExchangeName = exchangeForQueueName(queueName);
        final AMQP.Exchange.DeclareOk exchangeDeclare = channel.exchangeDeclare(matchingExchangeName, BuiltinExchangeType.TOPIC, durable);
        LOG.info("AMQP.Exchange.DeclareOk: protocolClassId={} protocolMethodId={} protocolMethodName={}",
                exchangeDeclare.protocolClassId(), exchangeDeclare.protocolMethodId(), exchangeDeclare.protocolMethodName());

        AMQP.Queue.DeclareOk declareOk = channel.queueDeclare(queueName, durable, QUEUE_EXCLUSIVE, QUEUE_AUTO_DELETE, null); // no args for now at least
        LOG.info("AMQP.Queue.DeclareOk: queue={} consumerCount={} messageCount={}",
                declareOk.getQueue(), declareOk.getConsumerCount(), declareOk.getMessageCount());

        // Now connect the exchange and queue
        AMQP.Queue.BindOk bindOk = channel.queueBind(queueName, matchingExchangeName, routingKey);
        LOG.info("AMQP.Queue.BindOk: protocolClassId={} protocolMethodId={} protocolMethodName={}",
                bindOk.protocolClassId(), bindOk.protocolMethodId(), bindOk.protocolMethodName());
        LOG.info("Bound exchange, {}, to queue, {}.", matchingExchangeName, queueName);

        channel.basicQos(3); // An important number where retrying/re-queueing is concerned.
        // My guess is that this influences the number of threads in the driver

        final OperatorConsumer operatorConsumer = new OperatorConsumer(processor, channel); // pooling?
        final String consumerTag = channel.basicConsume(queueName, QUEUE_CONSUME_AUTO_ACK, operatorConsumer);

        LOG.info("Negotiated heartbeat: {} seconds", connection.getHeartbeat());
        LOG.info("ConsumerTag returned from basicConsume: {}", consumerTag);
    }

    @Override
    public void shutdown() throws IOException, TimeoutException {
        LOG.info("Shutdown called.");
        channel.close(); // FIXME should probably use the version that takes a return code and message string
        connection.close();// FIXME should probably use the version that takes a return code and message string
    }

//    @Override
//    public Object dequeue() throws IOException { // FIXME remove from interface since we're not using it?
//        return null;
//    }

//    @Override
//    public long getPollIntervalMs() {
//        return 0;
//    } // FIXME this only makes sense for SQS

//    @Override
//    public QueueConsumer setPollIntervalMs(long pollIntervalMs) {
//        return null;
//    }


//    public static void main(String[] argv) throws Exception {
//        QueueConsumer rqc = RabbitQueueConsumer.createQueueConsumer();
//    }
}
