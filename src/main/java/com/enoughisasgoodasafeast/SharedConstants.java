package com.enoughisasgoodasafeast;

public interface SharedConstants {
    String INSTANCE_ID = "INSTANCE_ID";
    String SERVER_NAME = "EIAGAAF-0_1"; // Enough Is As Good As A Feast
    String TEST_SPACE_TOKEN = " ";
    String HEALTH_ENDPOINT = "/health";
    String ENQUEUE_ENDPOINT = "/enqueue";
    long CONNECTION_TIMEOUT_SECONDS = 30L;
    String STANDARD_RABBITMQ_PORT = "5672";
    String NO_COMPANY = "NONE";
    // Make configurable if necessary:
    int STANDARD_HEARTBEAT_TIMEOUT_SECONDS = 20; // 60 might be fine...
}
