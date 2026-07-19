package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.RabbitMQContainer;

import java.io.IOException;
import java.util.Properties;

import static com.enoughisasgoodasafeast.SharedConstants.*;

public class IntegrationTestFunctions {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTestFunctions.class);

    public static Properties loadPropertiesWithContainerOverrides(RabbitMQContainer rabbitContainer, String path) throws IOException {
        final String brokerHost = rabbitContainer.getHost();
        final Integer amqpPort = rabbitContainer.getAmqpPort();

        final Properties properties = ConfigLoader.readConfig(path);
        properties.setProperty(PRODUCER_QUEUE_HOST, brokerHost);
        properties.setProperty(PRODUCER_QUEUE_PORT, amqpPort.toString());
        properties.setProperty(CONSUMER_QUEUE_HOST, brokerHost);
        properties.setProperty(CONSUMER_QUEUE_PORT, amqpPort.toString());

        LOG.info("Overriding host and port for producer and consumer: {}:{}", brokerHost, amqpPort);
        return properties;
    }
}
