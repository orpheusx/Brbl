package com.enoughisasgoodasafeast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

    /**
     * Load the named .properties file from the classpath.
     * @param fileName the base name of the file
     * @return a populated Properties object
     */
    public static Properties readConfig(String fileName) throws IOException {
        LOG.info("Reading config from {}", fileName);
        String queuePropertiesPath = resourcePath(fileName);
        Properties props = new Properties();
        props.load(new FileInputStream(queuePropertiesPath));
        System.out.println("Properties loaded " + props.size());
        return props;
    }

    public static String resourcePath(String fileName) {
        return Thread.currentThread().getContextClassLoader().getResource("").getPath() + fileName; //FIXME
    }
}
