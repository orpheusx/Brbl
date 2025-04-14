package com.enoughisasgoodasafeast;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.AbstractLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.RabbitQueueFunctions.exchangeForQueueName;
import static com.enoughisasgoodasafeast.SharedConstants.STANDARD_RABBITMQ_PORT;
import static com.rabbitmq.client.BuiltinExchangeType.TOPIC;

public class RabbitQueueProducer implements QueueProducer {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitQueueProducer.class);

    private final String queueHost;
    private final int queuePort;
    private final String queueName;
    private final String routingKey;

    private final Connection moConnection;
    private final Channel channel;

    private final ArrayBlockingQueue<Object> internalMessageBuffer // TODO <Object> --> <Message>
            = new ArrayBlockingQueue<>(100); // Parameterize the size here


    public static QueueProducer createQueueProducer(String configFileName) throws IOException, TimeoutException {
        Properties properties = ConfigLoader.readConfig(configFileName);
        return createQueueProducer(properties);
    }

    public static QueueProducer createQueueProducer(Properties props) throws IOException, TimeoutException {
        String queueHost = props.getProperty("producer.queue.host");
        int queuePort = Integer.parseInt(props.getProperty("producer.queue.port", STANDARD_RABBITMQ_PORT));
        String queueName = props.getProperty("producer.queue.name");
        String queueRoutingKey = props.getProperty("producer.queue.routingKey");

        boolean queueIsDurable = Boolean.parseBoolean(props.getProperty("producer.queue.durable"));
        int heartbeatTimeoutSeconds = SharedConstants.STANDARD_HEARTBEAT_TIMEOUT_SECONDS;


        return new RabbitQueueProducer(queueHost, queuePort, queueName, queueRoutingKey, queueIsDurable, heartbeatTimeoutSeconds);
    }

    private RabbitQueueProducer(String queueHost, int queuePort, String queueName, String routingKey,
                                boolean isDurable, int requestedHeartbeatTimeout)
            throws IOException, TimeoutException {

        LOG.info("Creating RabbitQueueProducer: queueHost: '{}', queuePort: '{}', exchangeName: '{}', routingKey: '{}', isDurable: {}",
                queueHost, queuePort, queueName, routingKey, isDurable);

        this.queueHost = queueHost;
        this.queuePort = queuePort;
        this.queueName = queueName;
        this.routingKey = routingKey;

        if (queueHost == null || queueName == null || routingKey == null) {
            throw new IllegalArgumentException("RabbitQueueProducer missing required configuration.");
        }

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(this.queueHost);
        factory.setPort(this.queuePort);
        factory.setRequestedHeartbeat(requestedHeartbeatTimeout);

        moConnection = factory.newConnection();
        channel = moConnection.createChannel();
        /*AMQP.Exchange.DeclareOk declareOk = */
        final String matchingExchangeName = exchangeForQueueName(queueName);
        final AMQP.Exchange.DeclareOk exchangeDeclare = channel.exchangeDeclare(matchingExchangeName, TOPIC, isDurable);
        LOG.info("Declared exchange, {}: {}", matchingExchangeName, exchangeDeclare);

        channel.queueDeclare(this.queueName, true, false, false, null);
        channel.queueBind(queueName, matchingExchangeName, routingKey);
        LOG.info("Bound exchange, {}, to queue, {}.", matchingExchangeName, queueName);

        // Heartbeat frames will be sent approx moConnection.getHeartbeat() / 2 seconds
        // After two missed heartbeats, the peer is considered to be unreachable.
        LOG.info("Negotiated heartbeat: {} seconds", moConnection.getHeartbeat());

        // NB: RabbitMQ provides an extension that provides confirmation from the broker
        // that each message is received.
        // See https://www.rabbitmq.com/tutorials/tutorial-seven-java
        //      channel.confirmSelect();
        // We won't use this initially...

        // RabbitMQ's channel impl really isn't thread safe so write to it only via this thread.
        Thread brokerPublisherThread = new Thread(new BrokerPublisher(channel, internalMessageBuffer));
        brokerPublisherThread.start();
        LOG.info("Broker publisher thread running.");
        LOG.info("Start up complete.");

    }

    @Override
    public void enqueue(Object event) throws IOException { //TODO let's please make this only take a Message
        boolean ok = internalMessageBuffer.offer(event);
        if (!ok) {
            LOG.error("Unable to add message to internalMessageBuffer: {}", event);
            // TODO write to disk? Do any telcos support retries? Probably not...
        }
    }

    /*
     * Removes entries from the internalMessageBuffer and writes them to the broker.
     */
    private class BrokerPublisher implements Runnable {
        Channel channel;
        BlockingQueue<Object> queue;

        public BrokerPublisher(Channel channel, BlockingQueue<Object> queue) {
            this.channel = channel;
            this.queue = queue;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    enqueueToBroker(channel, queue.take());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    LOG.error("Error converting Message to byte array");
                }
            }
        }
    }

    private void enqueueToBroker(Channel channel, Object event) throws IOException {
        byte[] payload = null;
        switch(event) {
            case String s -> payload = s.getBytes(StandardCharsets.UTF_8); // remove this
            case Message m -> payload = m.toBytes();
            default -> throw new IllegalArgumentException("Unsupported message type: " + event.getClass());
        }
        channel.basicPublish(this.queueName, this.routingKey, /*deliveryModeProps*/null, payload);
        LOG.info(" [x] Enqueued msg '{}'", event);
    }



    public void shutdown() throws IOException, TimeoutException {
        this.channel.close();
        moConnection.close();
    }

    // Test only
    public static void main() throws IOException, TimeoutException {
        QueueProducer rqp = RabbitQueueProducer.createQueueProducer("queue.properties");
        long timestamp = System.currentTimeMillis();

        rqp.enqueue("one " + timestamp);
        rqp.enqueue("two " + timestamp);
        rqp.enqueue("three " + timestamp);

        LOG.info("Messages sent. Program complete.");
    }

}
