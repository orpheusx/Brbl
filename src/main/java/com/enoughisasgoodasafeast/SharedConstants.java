package com.enoughisasgoodasafeast;

public interface SharedConstants {
    String INSTANCE_ID = "INSTANCE_ID";
    String SERVER_NAME = "EIAGAAF-0_1"; // Enough Is As Good As A Feast
    String TEST_SPACE_TOKEN = " ";
    String HEALTH_ENDPOINT = "/health";
    String ENQUEUE_ENDPOINT = "/enqueue";
    String BRBL_ENQUEUE_ENDPOINT = "/brblEnqueue";
    long CONNECTION_TIMEOUT_SECONDS = 30L;
    String STANDARD_RABBITMQ_PORT = "5672";
    String NO_COMPANY = "NONE";
    // Make configurable if necessary:
    int STANDARD_HEARTBEAT_TIMEOUT_SECONDS = 20; // 60 might be fine...
    String PRODUCER_QUEUE_HOST = "producer.queue.host";
    String PRODUCER_QUEUE_PORT = "producer.queue.port";
    String PRODUCER_QUEUE_ROUTING_KEY = "producer.queue.routingKey";
    String PRODUCER_QUEUE_DURABLE = "producer.queue.durable";
    String CONSUMER_QUEUE_HOST = "consumer.queue.host";
    String CONSUMER_QUEUE_PORT = "consumer.queue.port";
    String CONSUMER_QUEUE_ROUTING_KEY = "consumer.queue.routingKey";
    String CONSUMER_QUEUE_DURABLE = "consumer.queue.durable";
}
