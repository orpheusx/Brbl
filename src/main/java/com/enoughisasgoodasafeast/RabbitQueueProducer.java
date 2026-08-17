package com.enoughisasgoodasafeast;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

import static com.enoughisasgoodasafeast.RabbitQueueFunctions.exchangeForQueueName;
import static com.enoughisasgoodasafeast.SharedConstants.*;
import static com.rabbitmq.client.BuiltinExchangeType.DIRECT;

public class RabbitQueueProducer implements QueueProducer {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitQueueProducer.class);

    private final String queueHost;
    private final int queuePort;
    private final String queueName;
    private final String exchangeName;
    private final String routingKey;

    private final Connection moConnection;
    private final Channel moChannel;

    private final ArrayBlockingQueue<Message> internalMessageBuffer
            = new ArrayBlockingQueue<>(100); // Parameterize the size here


    public static QueueProducer createQueueProducer(String configFileName) throws IOException, TimeoutException {
        Properties properties = ConfigLoader.readConfig(configFileName);
        return createQueueProducer(properties);
    }

    public static QueueProducer createQueueProducer(Properties props) throws IOException, TimeoutException {
        String queueHost = props.getProperty(PRODUCER_QUEUE_HOST);
        int queuePort = Integer.parseInt(props.getProperty(PRODUCER_QUEUE_PORT, STANDARD_RABBITMQ_PORT));
        String queueName = props.getProperty("producer.queue.name");
        String queueRoutingKey = props.getProperty(PRODUCER_QUEUE_ROUTING_KEY);

        boolean queueIsDurable = Boolean.parseBoolean(props.getProperty(PRODUCER_QUEUE_DURABLE));

        return new RabbitQueueProducer(queueHost, queuePort, queueName, queueRoutingKey, queueIsDurable, SharedConstants.STANDARD_HEARTBEAT_TIMEOUT_SECONDS);
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
        moChannel = moConnection.createChannel();

        exchangeName = exchangeForQueueName(queueName);
        moChannel.exchangeDeclare(exchangeName, DIRECT, isDurable);

        moChannel.queueDeclare(this.queueName, true, false, false, null);
        moChannel.queueBind(queueName, exchangeName, routingKey);
        LOG.info("Bound exchange, {}, to queue, {} with routing key, {}.", exchangeName, queueName, routingKey);

        // Heartbeat frames will be sent approx moConnection.getHeartbeat() / 2 seconds
        // After two missed heartbeats, the peer is considered to be unreachable.
        LOG.info("Negotiated heartbeat: {} seconds", moConnection.getHeartbeat());

        // NB: RabbitMQ provides an extension that provides confirmation from the broker
        // that each message is received.
        // See https://www.rabbitmq.com/tutorials/tutorial-seven-java
        //      channel.confirmSelect();
        // We won't use this initially...

        // RabbitMQ's channel impl really isn't thread safe so write to it only via this thread.
        // TODO/FIXME def need something more robust here. Small thread pool?
        Thread brokerPublisherThread = new Thread(new BrokerPublisher(moChannel, internalMessageBuffer));
        brokerPublisherThread.start();
        LOG.info("Broker publisher thread running.");
        LOG.info("Start up complete.");

    }

    @Override
    public boolean enqueue(@NonNull Message event) {
        boolean ok = internalMessageBuffer.offer(event);
        if (!ok) {
            LOG.error("Failed to add message to internalMessageBuffer: {}", event);
            // TODO write to disk? Do any telcos support retries? Probably not...
            // FIXME Wait and retry some number of times before failing?
        }
        return ok;
    }

    /*
     * Removes entries from the internalMessageBuffer and writes them to the broker.
     */
    private class BrokerPublisher implements Runnable {
        Channel channel;
        BlockingQueue<Message> queue;

        public BrokerPublisher(Channel channel, BlockingQueue<Message> queue) {
            this.channel = channel;
            this.queue = queue;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    final Message taken = queue.take();
                    enqueueToBroker(channel, taken);

                } catch (InterruptedException e) {
                    LOG.error("BrokerPublisher thread interrupted!", e);
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    LOG.error("Error enqueuing Message", e);
                }
            }
        }
    }

    private void enqueueToBroker(Channel channel, Message message) throws IOException {
        byte[] payload = message.toBytes();
        channel.basicPublish(this.exchangeName, this.routingKey, /*deliveryModeProps*/null, payload);
        LOG.info(" [x] Exchange '{}' enqueued msg '{}'", this.exchangeName, message);
    }

    public void shutdown() throws IOException, TimeoutException {
        this.moChannel.close();
        moConnection.close();
    }

}
