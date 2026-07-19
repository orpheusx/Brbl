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
import static com.enoughisasgoodasafeast.RetryDelayRoutingKey.*;
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
    public static final String RETRY_QUEUE_SUFFIX = "_retry";
    public static final String FAILED_QUEUE_SUFFIX = "_fail";

    private final Connection connection;
    private final Channel channel;
    private final String failedQueueName;

    private final AMQP.Queue.DeclareOk primaryQueueInfo;
    private final AMQP.Queue.DeclareOk failedQueueInfo;

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
                                String queueName,
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

        // Create the exchanges, the primary and the retry.
        String exchangeName = exchangeForQueueName(queueName); // e.g. x.opr8r.mo
        channel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT, durable);
        String retryExchangeName = retryForExchangeName(exchangeName); // e.g. rtx.opr8r.mo
        channel.exchangeDeclare(retryExchangeName, BuiltinExchangeType.DIRECT, durable);

        // Declare the primary input queue
        primaryQueueInfo = channel.queueDeclare(queueName, QUEUE_DURABILITY, QUEUE_EXCLUSIVE, QUEUE_AUTO_DELETE, null);
        // ...and connect the primary exchange with its queue
        channel.queueBind(queueName, exchangeName, routingKey);
        LOG.info("Bound exchange, {}, to queue, {} with routing key, {}.", exchangeName, queueName, routingKey);

        // Declare "final resting place" queue
        failedQueueName = failQueueForQueue(queueName);
        failedQueueInfo = channel.queueDeclare(failedQueueName, QUEUE_DURABILITY, QUEUE_EXCLUSIVE, QUEUE_AUTO_DELETE, null);

        String baseRetryQueueName = queueName + RETRY_QUEUE_SUFFIX;

        // 4. Declare 5-Second Delay Bucket Queue
        String retryAfter5sQueueName = delayQueueForRoutingKey(baseRetryQueueName, DELAY_5S);
        channel.queueDeclare(retryAfter5sQueueName, QUEUE_DURABILITY, QUEUE_EXCLUSIVE, QUEUE_AUTO_DELETE,
                Map.of(X_DLX_HEADER, exchangeName,
                        X_DL_RK_HEADER, routingKey,
                        X_MESSAGE_TTL_HEADER, DELAY_5S.delayMs())
                );
        // ...and connect the retry exchange with its delay specific queue
        channel.queueBind(retryAfter5sQueueName, retryExchangeName, DELAY_5S.name());

        // 5. Declare additional delay bucket queues with longer TTLs
        // ...

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
        return switch (consumerClassImpl) {
            case "com.enoughisasgoodasafeast.SndrConsumer" -> new SndrConsumer((SndrMessageProcessor) processor, channel);
            case "com.enoughisasgoodasafeast.OperatorConsumer" -> new OperatorConsumer((SessionAwareMessageProcessor) processor, channel, failedQueueName);
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
