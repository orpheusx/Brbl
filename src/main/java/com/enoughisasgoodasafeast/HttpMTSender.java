package com.enoughisasgoodasafeast;

import ch.qos.logback.classic.Level;
import io.helidon.http.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * A thin wrapper around WebClient that, currently, doesn't handle any of the issues (throttling, transient outages, etc.)
 * with sending to a 3rd party messaging API (e.g. Slack, WhatsApp, etc.) or, for that matter, our own Rcvr.
 * We should not consider it ready for anything but light, integration/unit testing.
 * A real implementation might want to use the application.yaml configuration support (for TLS setup, metrics, tracking)
 * provided by Helidon.
 */
public class HttpMTSender extends HttpMessageSender implements MTHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HttpMTSender.class);
    static {
        ((ch.qos.logback.classic.Logger) LOG).setLevel(Level.ERROR);
    }

    public HttpMTSender(String endpoint) {
        super(endpoint);
    }

    public static MTHandler newHandler(Properties properties) {
        String protocol = properties.getProperty(PLATFORM_MT_PROTOCOL); // TODO HTTP/2? constants.
        String host = properties.getProperty(PLATFORM_MT_HOST);
        int port = Integer.parseInt(properties.getProperty(PLATFORM_MT_PORT));
        String pathInfo = properties.getProperty(PATH_INFO);
        // Check for leading slash in the provided pathInfo
        if (pathInfo.endsWith("/")) {
            return new HttpMTSender(String.format("%s://%s:%d%s", protocol, host, port, pathInfo));
        } else {
            return new HttpMTSender(String.format("%s://%s:%d/%s", protocol, host, port, pathInfo));
        }
    }

    static void main() {
        final HttpMTSender handler = new HttpMTSender("http://localhost:2424/chttr");
        final StatusException result = handler.send(Message.newMO("1234567890", "01234", "fromhost"));
        final var statusFamily = result.status().family();
        final var resultException = result.exception();
        // TODO embed all the checking into a method on the StatusException record class.
        LOG.info("Send ok?: {}", statusFamily!=null && statusFamily == Status.Family.SUCCESSFUL);
        if(resultException != null) {
            LOG.warn("Send failed with {}", resultException.getMessage());
        }
        if (statusFamily != null) {
            LOG.warn("Send failed with status {}", statusFamily);
        }
    }
}
