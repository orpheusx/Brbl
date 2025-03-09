package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.enoughisasgoodasafeast.operator.Telecom.deriveCountryCodeFromId;

public class Operator {

    private static final Logger LOG = LoggerFactory.getLogger(Operator.class);

    // Replaced ConcurrentHashMap with Caffeine cache
    Cache <String, Session> sessionCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .build();

    private final ConcurrentHashMap<String, User> userCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Script> scriptCache = new ConcurrentHashMap<>();

    private final Platform defaultPlatform = Platform.BRBL;

    private QueueConsumer queueConsumer;
    private QueueProducer queueProducer;

    public Operator(QueueProducer queueProducer) {
        this.queueProducer = queueProducer;
    }

    public void init() throws IOException, TimeoutException {
        LOG.info("Initializing Brbl Operator");
//         queueProducer = RabbitQueueProducer.createQueueProducer("sndr.properties");
//         FakeOperator.QueueProducerMTHandler producerMTHandler = new FakeOperator.QueueProducerMTHandler(queueProducer);
//         queueConsumer = RabbitQueueConsumer.createQueueConsumer(
//                 "rcvr.properties", producerMTHandler);
        // Other resources? Connections to database/distributed caches?
    }

    /**
     * Find/setup session and process message, tracking state changes in the session.
     * @param message the message being processed.
     * @return true if processing was complete, false if incomplete.
     */
    public boolean process(MOMessage message) {
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
        synchronized (session) {
            Script current = session.currentScript;
            if (session.inputs.size() > current.expectedInputCount()) {
                // user responded with more MOs than expected (usually only once is expected)
                // create a new, special Script of ScriptType.PivotScript and chain the remaining Scripts to it...
                // Problem with this is the previous member can't be patched. It requires recreating the entire remainder of the
                // Script chain.
                // Alternatively, we could simply emit a

            }
            Script next = current.evaluate(session, moMessage);
            if (next != null) {
                session.currentScript = next;
            }
            return true; // when would this be false?
        }
    }



    /*
     * Fetch/create Session for the given identifier.
     * Use StructuredConcurrency to fetch user and script.
     * Because we use a pool of Channels we may be processing 2+ messages from a user concurrently. So we
     * synchronize the method to avoid out-of-order processing. This is not ideal for performance but, hopefully, safe.
     * Think about ways to limit the scope of the lock to just the session.
     * We can't use the nice LoadingCache impl because the function is limited to the same param type as the cache key.
     */
    synchronized Session getUserSession(MOMessage message) throws InterruptedException, ExecutionException {
        // We need a Session
        Session session = sessionCache.getIfPresent(message.from());
        if (session == null) {
            session = createSession(message);
            sessionCache.put(message.from(), session);
            // addToSessionsCache(session); // See https://github.com/ben-manes/caffeine/tree/master/examples/indexable
        }

        // Add the MOMessage to the session here in a synchronized method to insure ordering.
        session.addInput(message);

        return session;
    }

    Session createSession(MOMessage message) throws InterruptedException, ExecutionException {
        LOG.info("No session for sender, {}.", message.from());
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Supplier<User> user = scope.fork(() -> findOrCreateUser(message.from(), message.to()));
            Supplier<Script> script = scope.fork(() -> findStartingScript(message));
            scope.join().throwIfFailed(); // TODO consider using joinUntil() to enforce a collective timeout.

            // Create and persist the new Session
            return new Session(UUID.randomUUID(), script.get(), user.get(), getQueueProducer(message.platform()));
        }
    }

    QueueProducer getQueueProducer(Platform platform) {
        // We may need to add different handling for each Platform.
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
            sessionCache.put(id, session);
            LOG.info("Cached Session id: {}, platform: {}", id, platform);
        });
        return session;
    }

    protected User findOrCreateUser(String from, String to) {
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

    /*
     * This is all hard coded for the moment. Obviously it needs to be replaced with something that loads
     * a Script from a database based on the content of the MOMessage.
     */
    Script findStartingScript(MOMessage message) {
        Script startingScript = scriptCache.get(message.to());
        if (startingScript == null) {
            // TODO Expand this to look for keyword in message, other logic.
            // TODO fetch from Redis/Postgres/file system...
            LOG.info("No script found in cache for {}. Using default for platform, {}.", message.to(), message.platform());

            startingScript = switch (message.to()) {
                case "1234" -> new Script("PrintWithPrefix", ScriptType.PrintWithPrefix, null, "1234");
                case "2345" -> new Script("ReverseText", ScriptType.ReverseText, null, "2345");
                case "3456" -> new Script("HelloGoodbye", ScriptType.HelloGoodbye, null, "3456");

                case "4567" -> { // chain Scripts together using the PresentMulti/ProcessMulti
                    Script one = new Script("What's you favorite color? 1) red 2) blue 3) flort", ScriptType.PresentMulti, null, "ColorQuiz");
                    Script two = new Script("No text required", ScriptType.ProcessMulti, one, "EvaluateAnswer");
                    ResponseLogic linkOneToTwo = new ResponseLogic(null, null, two);
                    one.next().add(linkOneToTwo);
                    Script tre = new Script("End-of-Conversation", ScriptType.PrintWithPrefix, two, "EndOfConversation");
                    ResponseLogic twoOption1 = new ResponseLogic(List.of("1", "red"), "Red is the color of life.", tre);
                    ResponseLogic twoOption2 = new ResponseLogic(List.of("2", "blue"), "Blue is my fave, as well.", tre);
                    ResponseLogic twoOption3 = new ResponseLogic(List.of("1", "flort"), "Flort is for the cool kids.", tre);
                    two.next().add(twoOption1);
                    two.next().add(twoOption2);
                    two.next().add(twoOption3);

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
