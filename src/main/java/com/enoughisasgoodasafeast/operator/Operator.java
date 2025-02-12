package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static com.enoughisasgoodasafeast.operator.Telecom.deriveCountryCodeFromId;

public class Operator {

    private static final Logger LOG = LoggerFactory.getLogger(Operator.class);

    // Replace use of ConcurrentHashMap with Caffeine instead since it has a time-based expiration impl.
    private final ConcurrentHashMap<String, Session> sessionsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, User> userCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Script> scriptCache = new ConcurrentHashMap<>();

    private final Platform defaultPlatform = Platform.BRBL;

    // private QueueConsumer queueConsumer;
    private QueueProducer queueProducer;

    public Operator(QueueProducer queueProducer) {
        this.queueProducer = queueProducer;
    }

    public void init() throws IOException, TimeoutException {
        LOG.info("Initializing Brbl Operator");
//         queueProducer = RabbitQueueProducer.createQueueProducer("sndr.properties");
//         FakeOperator.QueueProducerMTHandler producerMTHandler = new FakeOperator.QueueProducerMTHandler(queueProducer);
        // queueConsumer = RabbitQueueConsumer.createQueueConsumer(
        //         "rcvr.properties", producerMTHandler);
        // Other resources? Connections to database/distributed caches?
    }

    /**
     * @param message
     * @return
     */
    boolean process(MOMessage message) {
        Session session;
        try {
            session = getUserSession(message);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to retrieve/construct Session", e);
            return false;
        }

        try {
            return process(session, message);
        } catch (IOException e) {
            LOG.error("Processing error", e);
            return false;
        }

    }

    boolean process(Session session, MOMessage moMessage) throws IOException {
        Script current = session.currentScript;
        Script next = current.evaluate(session, moMessage);
        if (next != null) {
            session.currentScript = next;
        }
        return true; // when would this be false?
    }

    /*
     * Use StructuredConcurrency to fetch user and script
     */
    Session getUserSession(MOMessage message) throws InterruptedException, ExecutionException {
        // We need a Session
        Session session = sessionsCache.get(message.from());
        if (session == null) {
            LOG.info("No session for sender, {}.", message.from());
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                Supplier<User> user = scope.fork(() -> findOrCreateUser(message.from(), message.to()));
                Supplier<Script> script = scope.fork(() -> findStartingScript(message));
                scope.join().throwIfFailed(); // TODO consider using joinUntil() to enforce a collective timeout.

                // Create and persist the new Session
                session = new Session(UUID.randomUUID(), script.get(), user.get(), getQueueProducer(message.platform()));
                addToSessionsCache(session);
            }
        }
        return session;
    }

    QueueProducer getQueueProducer(Platform platform) {
        return queueProducer;
    }

    /*
     * Do we want to share sessions across different platforms?
     * Pros: Knowing that a User is currently active on one Platform and could be reached on a different platform could
     * be useful. e.g. if SMS comes in, we could return a link that connects them to our web platform or push a message
     * to them (if there's a web socket open.)
     * Cons: Having multiple entries in the same cache could result in slightly different expiration behavior...
     */
    private Session addToSessionsCache(Session session) {
        session.user.platformIds().forEach((platform, id) -> {
            sessionsCache.put(id, session);
            LOG.info("Cached Session {}, platform {}", id, platform);
        });
        return session;
    }

    User findOrCreateUser(String from, String to) {
        User user = userCache.get(from);
        if (user == null) {
            LOG.info("Unknown user, {}", from);
            user = new User(UUID.randomUUID(),
                    defaultPlatformMap(from),
                    deriveCountryCodeFromId(from),
                    defaultLanguageList(from, to)); // We won't know everything about the user
            userCache.put(from, user);
            LOG.info("Cached {}", user);
        }
        return user;
    }

    Script findStartingScript(MOMessage message) {
        Script startingScript = scriptCache.get(message.to());
        if (startingScript == null) {
            // TODO Expand this to look for keyword in message, other logic.
            // TODO fetch from Redis/Postgres/file system...
            LOG.info("No script found for {}. Using default for platform, {}.", message.to(), message.platform());

            startingScript = switch (message.to()) {
                case "1234" -> new Script("PrintWithPrefix", ScriptType.PrintWithPrefix, null, "1234");
                case "2345" -> new Script("ReverseText", ScriptType.ReverseText, null, "2345");
                case "3456" -> new Script("HelloGoodbye", ScriptType.HelloGoodbye, null, "3456");
                case "4567" -> {
                    // Chain Scripts together
                    Script one = new Script("first_step", ScriptType.PrintWithPrefix, null);
                    Script two = new Script("second_step", ScriptType.ReverseText, one);
                    Script tre = new Script("third_step", ScriptType.HelloGoodbye, two);
                    one.next().add(two);
                    two.next().add(tre);
                    yield one;
                }
                default -> defaultScript(message.platform(), message.to());
            };

            scriptCache.put(message.to(), startingScript);
        }
        return startingScript;
    }

    Script defaultScript(Platform platform, String shortCodeOrKeywordOrChannelName) {
        // TODO use params to determine the correct initial script.
        return new Script("TEST SCRIPT RESPONSE PREFIX", ScriptType.PrintWithPrefix, null, "DefaultScript");
    }

    Map<Platform, String> defaultPlatformMap(String from) {
        return Map.of(defaultPlatform, from);
    }

    List<String> defaultLanguageList(String from, String to) {
        // TODO:
        // the associated shortcode/longcode should probably have expected language code(s)
        // we could also use the 'from' to guess. E.g. if the number is Mexican we could assume 'es'
        return List.of("en");
    }
}
