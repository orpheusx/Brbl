package com.enoughisasgoodasafeast.integration;

import com.enoughisasgoodasafeast.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.RabbitMQContainer;

import java.io.IOException;
import java.util.Properties;

public class IntegrationTestFunctions {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTestFunctions.class);

    public static Properties loadPropertiesWithContainerOverrides(RabbitMQContainer rabbitContainer, String path) throws IOException {
        final String brokerHost = rabbitContainer.getHost();
        final Integer amqpPort = rabbitContainer.getAmqpPort();

        final Properties properties = ConfigLoader.readConfig(path);
        properties.setProperty("producer.queue.host", brokerHost);
        properties.setProperty("producer.queue.port", amqpPort.toString());
        properties.setProperty("consumer.queue.host", brokerHost);
        properties.setProperty("consumer.queue.port", amqpPort.toString());

        LOG.info("Overriding host and port for producer and consumer: {}:{}", brokerHost, amqpPort);
        return properties;
    }
}
