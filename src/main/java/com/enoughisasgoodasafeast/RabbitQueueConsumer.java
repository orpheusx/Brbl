package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.MessageProcessor;
import com.enoughisasgoodasafeast.operator.SndrMessageProcessor;
import com.rabbitmq.client.*;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
    import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.RabbitQueueFunctions.*;
import static com.enoughisasgoodasafeast.SharedConstants.*;

public class RabbitQueueConsumer implements QueueConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitQueueConsumer.class);

    public static final boolean QUEUE_DURABILITY = true;
    public static final boolean QUEUE_EXCLUSIVE = false;
    public static final boolean QUEUE_AUTO_DELETE = false;
    public static final boolean QUEUE_CONSUME_AUTO_ACK = false;

    // These keys are defined by RabbitMQ
    public static final String X_DLX_HEADER = "x-dead-letter-exchange";
    public static final String X_DL_RK_HEADER = "x-dead-letter-routing-key";
    public static final String X_MESSAGE_TTL_HEADER = "x-message-ttl";

    // Maintain clear associations between primary and supporting queues
    public static final String EXCHANGE_SUFFIX = "-x";
    public static final String RETRY_QUEUE_SUFFIX = "-retry";
    public static final String FAILED_QUEUE_SUFFIX = "-fail";

    private final Connection connection;
    private final Channel channel;
    private final String failedQueueName;
    private final String failedExchangeName;
    private final String retryExchangeName;

    public static QueueConsumer createQueueConsumer(String configFileName, MessageProcessor processor) throws IOException, TimeoutException {
        Properties props = ConfigLoader.readConfig(configFileName);
        return createQueueConsumer(props, processor);
    }

    public static QueueConsumer createQueueConsumer(Properties props, MessageProcessor processor) throws IOException, TimeoutException {
        String queueHost = props.getProperty(CONSUMER_QUEUE_HOST);
        int queuePort = Integer.parseInt(props.getProperty(CONSUMER_QUEUE_PORT));
        String queueName = props.getProperty("consumer.queue.name");
        String queueRoutingKey = props.getProperty("consumer.queue.routingKey");
        boolean isQueueDurable = Boolean.parseBoolean(props.getProperty(CONSUMER_QUEUE_DURABLE));
        String consumerClassImpl = props.getProperty("consumerClass");

        return new RabbitQueueConsumer(queueHost, queuePort, queueName, queueRoutingKey, isQueueDurable,
                processor, consumerClassImpl, SharedConstants.STANDARD_HEARTBEAT_TIMEOUT_SECONDS);
    }

    private RabbitQueueConsumer(String queueHost,
                                int queuePort,
                                String queueName, // e.g. dev-opr8r-mo-sms
                                String routingKey,
                                boolean durable,
                                MessageProcessor processor,
                                String consumerClassImpl,
                                int requestedHeartbeatTimeout)
            throws IOException, TimeoutException {

        LOG.info("Creating RabbitQueueConsumer: queueHost: '{}', queuePort: {}, queueName: '{}', routingKey: '{}', processor: '{}', consumerClass: '{}', heartBeatTimeout: {}",
                queueHost, queuePort, queueName, routingKey, processor.getClass(), consumerClassImpl, requestedHeartbeatTimeout);

        if (queueHost == null || queuePort < 1024 || queueName == null || routingKey == null || processor == null ||
                consumerClassImpl == null || requestedHeartbeatTimeout <= 0) {
            throw new IllegalArgumentException("RabbitQueueConsumer missing required configuration (or bad numeric value).");
        }

        ConnectionFactory factory = new ConnectionFactory(); // automaticRecoveryEnabled is true by default.
        factory.setHost(queueHost);
        factory.setPort(queuePort);
        factory.setRequestedHeartbeat(requestedHeartbeatTimeout);

        // Setup socket connection, negotiate protocol version and authentication
        this.connection = factory.newConnection();
        this.channel = connection.createChannel();

        // Declare the primary and retry exchanges.
        String exchangeName = exchangeForQueueName(queueName); // dev-opr8r-mo-sms-x
        channel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT, durable);
        retryExchangeName = retryExchangeForQueueName(queueName); // dev-opr8r-mo-sms-retry-x
        channel.exchangeDeclare(retryExchangeName, BuiltinExchangeType.DIRECT, durable);

        // Declare "final resting place" exchange and queue, the bind them together.
        failedQueueName = failQueueForQueue(queueName); // dev-opr8r-mo-sms-fail
        failedExchangeName = exchangeForQueueName(failedQueueName); // dev-opr8r-mo-sms-fail-x
        channel.exchangeDeclare(failedExchangeName, BuiltinExchangeType.DIRECT, durable);
        channel.queueDeclare(failedQueueName, QUEUE_DURABILITY, QUEUE_EXCLUSIVE, QUEUE_AUTO_DELETE, null);
        channel.queueBind(failedQueueName, failedExchangeName, routingKey);
        LOG.info("Bound dead-letter queue {} to DLX {} with routing key {}", failedQueueName, failedExchangeName, routingKey);

        // Declare the primary input queue
        channel.queueDeclare(queueName, QUEUE_DURABILITY, QUEUE_EXCLUSIVE, QUEUE_AUTO_DELETE, null);
        // ...and connect the primary exchange with its queue
        channel.queueBind(queueName, exchangeName, routingKey);
        LOG.info("Bound primary queue, {}, to exchange, {} with routing key, {}.", queueName, exchangeName, routingKey);

        for (RetryDelayRoutingKey value : RetryDelayRoutingKey.values()) {
            long delayMs = value.delayMs();
            String retryQueue = delayQueueForRoutingKey(queueName, value);
            String retryRoutingKey = value.name();

            Map<String, Object> args = Map.of(
                    X_MESSAGE_TTL_HEADER, delayMs,
                    // All delay buckets expire their messages back into the primary queue
                    X_DLX_HEADER, exchangeName,
                    X_DL_RK_HEADER, routingKey
            );

            LOG.info("Declared timed retry queue: {}", retryQueue);
            channel.queueDeclare(retryQueue, true, false, false, args);
            channel.queueBind(retryQueue, retryExchangeName, retryRoutingKey);
            LOG.info("Bound timed retry queue {} to exchange {} with routing key: {}", retryQueue, retryExchangeName, retryRoutingKey);
        }

        // FIXME Convert prefetchCount to be a configuration property.
        // This is an important number where retrying/re-queueing is concerned.
        // My guess is that this influences the number of threads in the driver
        channel.basicQos(3);

        final BrblConsumer brblConsumer = getBrblConsumer(processor, consumerClassImpl);
        final String consumerTag = channel.basicConsume(queueName, QUEUE_CONSUME_AUTO_ACK, brblConsumer);

        LOG.info("Negotiated heartbeat: {} seconds", connection.getHeartbeat());
        LOG.info("ConsumerTag returned from basicConsume: {}", consumerTag);
    }

    private @NonNull BrblConsumer getBrblConsumer(MessageProcessor processor, String consumerClassImpl) {
        // Poor man's Reflection...
        return switch (consumerClassImpl) {
            case "com.enoughisasgoodasafeast.SndrConsumer" -> new SndrConsumer(
                    (SndrMessageProcessor) processor, channel);

            case "com.enoughisasgoodasafeast.OperatorConsumer" -> new OperatorConsumer(
                    (SessionAwareMessageProcessor) processor, channel, failedExchangeName, retryExchangeName);

            default -> throw new IllegalArgumentException(
                    "RabbitQueueConsumer cannot use unsupported consumerClass: " + consumerClassImpl);
        };
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
