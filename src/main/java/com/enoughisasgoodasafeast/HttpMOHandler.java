package com.enoughisasgoodasafeast;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * A thin wrapper around the Helidon WebClient that configures itself via Properties file.
 * "chttr.*" properties are for our customer simulator.
 */
public class HttpMOHandler extends HttpMessageHandler implements MOHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HttpMOHandler.class);
    static {
        ((ch.qos.logback.classic.Logger) LOG).setLevel(Level.ERROR);
    }

    public HttpMOHandler(String endpoint) {
        super(endpoint);
    }

    public static MOHandler newHandler(Properties properties) {
        String protocol = properties.getProperty("chttr.mo.protocol");
        String host = properties.getProperty("chttr.mo.host");
        int port = Integer.parseInt(properties.getProperty("chttr.mo.port"));
        String pathInfo = properties.getProperty("chttr.mo.pathInfo");

        // Check for leading slash in the provided pathInfo
        if (pathInfo.endsWith("/")) {
            return new HttpMOHandler(String.format("%s://%s:%d%s", protocol, host, port, pathInfo));
        } else {
            return new HttpMOHandler(String.format("%s://%s:%d/%s", protocol, host, port, pathInfo));
        }
    }
}
