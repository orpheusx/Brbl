package com.enoughisasgoodasafeast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

    /**
     * Load the named .properties file from the classpath.
     * @param fileName the base name of the file
     * @return a populated Properties object
     */
    public static Properties readConfig(String fileName) throws IOException {
        LOG.info("Reading config from {}...", fileName);
        try (InputStream resourceStream = ConfigLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (resourceStream == null) {
                LOG.error("Failed to load {}", fileName);
            }
            Properties props = new Properties();
            props.load(resourceStream);

            props.forEach((key, val) -> {
                LOG.info("{}: {}={}", fileName, key, val);
            });

            return props;
        }
    }

}
