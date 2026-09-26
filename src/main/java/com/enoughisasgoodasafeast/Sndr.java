package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.*;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import com.enoughisasgoodasafeast.sndr.GatewayProvider;
import com.enoughisasgoodasafeast.sndr.ProcessStateRoutingKey;
import com.enoughisasgoodasafeast.sndr.RouteInfo;
import com.enoughisasgoodasafeast.sndr.TelnyxSender;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class Sndr implements SndrMessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Sndr.class);

    private QueueConsumer queueConsumer;
    private PersistenceManager persistenceManager;
    private TelnyxSender telnyxSender;

    final LoadingCache<@NonNull String, List<RouteInfo>> routeInfoCache = Caffeine.newBuilder()
            .expireAfterWrite(20, TimeUnit.MINUTES)
            .build(allRoutes -> {
                LOG.info("Loading cache of active routes");
                return persistenceManager.getRouteInfo(GatewayProvider.TELNYX, RouteStatus.ACTIVE, Platform.SMS);
            });

    public Sndr() {
    }

    public Sndr(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        this.telnyxSender = new TelnyxSender(persistenceManager);
    }

    public Sndr(QueueConsumer queueConsumer, PersistenceManager persistenceManager) {
        this.queueConsumer = queueConsumer;
        this.persistenceManager = persistenceManager;
        this.telnyxSender = new TelnyxSender(persistenceManager);
    }

    public void init(Properties properties) throws IOException, TimeoutException, PersistenceManagerException, CriticalConfigException {
        LOG.info("Initializing SNDR");

        if (persistenceManager == null) {
            persistenceManager = PostgresPersistenceManager.createPersistenceManager(properties);
            // Preload the routing data on startup, fail fast if none is available before we start processing from the queue.
            if(null == routeInfoCache.get("ALL")) {
                throw new CriticalConfigException("No routing information found.");
            }
        }

        if (telnyxSender == null) {
            telnyxSender = new TelnyxSender(persistenceManager);
        }

        if (queueConsumer == null) {
            queueConsumer = RabbitQueueConsumer.createQueueConsumer(properties, this);
        }
    }

    public final AtomicInteger okCounter = new AtomicInteger();
    public final AtomicInteger failCounter = new AtomicInteger();
    public final AtomicInteger retryCounter = new AtomicInteger();
    public final AtomicInteger expiredCounter = new AtomicInteger();
    //public final AtomicInteger noOpCounter = new AtomicInteger();

    @Override
    public ProcessStateRoutingKey process(Message message) {
        LOG.info("Processing outbound message: {}", message);
        // TODO Check creation date of the Message. We don't want to try sending messages that are outside their window of relevance.
        //  Requires a lookup of the customer's preference from the database. TBD
        var routeInfo = findRoute(message.platform(), message.from());
        if(routeInfo == null) {
            LOG.error("CRITICAL_CONFIG_ERROR: No route found for message {}", message);
            failCounter.incrementAndGet();
            return new ProcessStateRoutingKey(ProcessState.ERROR, null);
        }

        // Fail the message if it has expired.
        var now = Instant.now();
        var configuredLifetime = routeInfo.mtExpirationMs();
        if (now.isAfter(message.createdAt().plus(configuredLifetime, ChronoUnit.MILLIS))) {
            // The message has expired. Don't attempt to send it.
            LOG.warn("Send time: {}. Configured lifetime: {}. Message expired: {}", now, configuredLifetime, message.createdAt());
            expiredCounter.incrementAndGet();
            return new ProcessStateRoutingKey(ProcessState.EXPIRED, null);
        }

        final ProcessStateRoutingKey sendResult = telnyxSender.send(message, routeInfo);
        switch (sendResult.processState()) {
            case OK -> okCounter.incrementAndGet();
            case ERROR -> failCounter.incrementAndGet();
            case RETRY -> retryCounter.incrementAndGet();
            //            case EXPIRED -> expiredCounter.incrementAndGet();
            //            case NOOP -> noOpCounter.incrementAndGet();
        }

        return sendResult;
    }

    @Nullable RouteInfo findRoute(@NonNull Platform platform, @NonNull String channel) {
        final var routeInfo = routeInfoCache.get("ALL");
        if (routeInfo == null) {
            throw new IllegalStateException("CRITICAL_CONFIG_ERROR: findRoute: No routes found.");
        }
        for (RouteInfo info : routeInfo) {
            if (info.channel().equals(channel)) {
                return info;
            }
        }

        return null;
    }

    public boolean log(Message message) {
        boolean isInserted = persistenceManager.insertDeliveredMT(message);
        if (isInserted) {
            LOG.info("Delivered {}", message);
        }

        return isInserted;
    }

    public void shutdown() throws IOException, TimeoutException {
        if (queueConsumer != null) {
            queueConsumer.shutdown();
            LOG.info("Shutdown queueConsumer.");
        }
        LOG.info("Shutdown Sndr");
    }

    // Called by Brbl.main
    public static void main(String[] args) throws IOException, TimeoutException, PersistenceManagerException, CriticalConfigException {
        final Sndr sndr = new Sndr();
        final Properties properties = ConfigLoader.readConfig("sndr.properties");
        sndr.init(properties);
    }
}
