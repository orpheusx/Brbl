package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.enoughisasgoodasafeast.operator.Telecom.deriveCountryCodeFromId;

public class Operator implements MessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Operator.class);
    private static final int EXPECTED_INPUT_COUNT = 1;

    // Replaced ConcurrentHashMap with Caffeine cache
    LoadingCache<SessionKey, Session> sessionCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES) // TODO make duration part of the configuration
            .build(key -> createSession(key));

    // Replace with another LoadingCache
    private final ConcurrentHashMap<String, User> userCache = new ConcurrentHashMap<>();

    // Consider async loading cache for this, assuming we preload all scripts
    private final ConcurrentHashMap<String, Script> scriptCache = new ConcurrentHashMap<>();

    private final Platform defaultPlatform = Platform.BRBL;

    private QueueConsumer queueConsumer;
    private QueueProducer queueProducer;
    private PersistenceManager persistenceManager;

    public Operator() {}

    public Operator(QueueConsumer queueConsumer, QueueProducer queueProducer) {
        this(queueConsumer, queueProducer, null);
    }
    public Operator(QueueConsumer queueConsumer, QueueProducer queueProducer, PersistenceManager persistenceManager) {
        this.queueConsumer = queueConsumer;
        this.queueProducer = queueProducer;
        this.persistenceManager = persistenceManager;
    }

    // TODO get rid of this version by updating OperatorTest's use of it.
    public void init() throws IOException, TimeoutException {
        LOG.info("Initializing Brbl Operator");
        if (queueConsumer == null) {
            queueConsumer = RabbitQueueConsumer.createQueueConsumer(
                    "rcvr.properties", this);
        }
        if (queueProducer == null) {
            queueProducer = RabbitQueueProducer.createQueueProducer("sndr.properties");
        }
        // Other resources? Connections to database/distributed caches?
    }

    public void init(Properties props) throws IOException, TimeoutException, PersistenceManagerException {
        LOG.info("Initializing Brbl Operator with provided Properties object");
        if (queueConsumer == null) {
            queueConsumer = RabbitQueueConsumer.createQueueConsumer(
                    props, this);
        }
        if (queueProducer == null) {
            queueProducer = RabbitQueueProducer.createQueueProducer(props);
        }

        if (persistenceManager == null) {
            persistenceManager = PostgresPersistenceManager.createPersistenceManager(props);
        }

        // Other resources? Connections to database/distributed caches?
    }

    /**
     * Find/setup session and process message, tracking state changes in the session.
     * @param message the message being processed.
     * @return true if processing was complete, false if incomplete.
     */
    @Override
    public boolean process(Message message) {
        LOG.info("Process message: {}", message);
        final SessionKey sessionKey = SessionKey.newSessionKey(message);
        Session session = sessionCache.get(sessionKey);
        if (null==session) {
            LOG.error("Cache fetch failed for {}", sessionKey);
            return false;
        }
        // FIXME the gap between the creation/retrieval of the Session and it being locked for
        //  processing is worrisome but possibly unavoidable...
        return process(session, message);
    }

    private boolean process(Session session, Message message) {
        synchronized (session) {
            try {
                session.addInput(message);
                int size = session.inputs.size();
                if ( size > EXPECTED_INPUT_COUNT) {
                    LOG.error("Uh oh, there are more inputs ({}) than expected in session ({})", size, session);
                    // Corner case: user sent multiple responses that arrived closely together (probably due to delays/buffering in
                    // the telco's SMSc) and, due to an unfortunate thread context switch, we've processed each in the same
                    // Likely this creates an unexpected situation. To handle it we should create a new Script of
                    // ScriptType.PivotScript and chain the remaining Scripts to it.
                    // These scripts will explain the problem and ask what the user what they want to do.
                    // We'll do the same in other cases as well.
                    // TODO...fetch the PivotScript for the given shortcode
                }

                // Also check if the current Message was created prior to the previous Message in the session's history.
                // This would signal out-of-order processing which Is Bad™
                Message previousInputMessage = session.inputHistory.isEmpty() ? null : session.inputHistory.getLast();
                if(previousInputMessage != null) {
                    if (previousInputMessage.receivedAt().isAfter(message.receivedAt())) {
                        LOG.error("Oh shit, we processed an MO received later than this one: {} > {}",
                                previousInputMessage.receivedAt(), message.receivedAt());
                    }
                }

                Script next = session.currentScript.evaluate(session, message);
                if (next != null) {
                    session.currentScript = next;
                }
                session.flushOutput();
                return true; // when would this be false?
            } catch (IOException e) {
                LOG.error("Processing error", e);
                return false;
            }
        }
    }

    @Override
    public boolean log(/*Session session, */Message message) {
        return persistenceManager.insertProcessedMO(
                message,
                this.sessionCache.get(SessionKey.newSessionKey(message)));
        // More logging here if insert fails?
    }

    /**
     * Builder method used with the LoadingCache.
     * @param sessionKey the key associated with the new Session
     * @return the newly constructed Session added to the sessionCache
     * @throws InterruptedException
     * @throws ExecutionException
     */
    Session createSession(SessionKey sessionKey) throws InterruptedException, ExecutionException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            Supplier<User> user = scope.fork(() -> findOrCreateUser(sessionKey.from(), sessionKey.to()));
            Supplier<Script> script = scope.fork(() -> findStartingScript(sessionKey));
            scope.join().throwIfFailed(); // TODO consider using joinUntil() to enforce a collective timeout.

            return new Session(
                    UUID.randomUUID(), script.get(), user.get(),
                    getQueueProducer(sessionKey.platform()),
                    persistenceManager);
        }
    }

    QueueProducer getQueueProducer(Platform platform) {
        // We may need to add different handling for each Platform.
        return queueProducer;
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
     * a Script from a database based on the content of the Message.
     * When we replace this be sure to convert the scriptCache to be a Caffeine cache with a synchronous callback
     * doing the work of returning the values.
     */
    Script findStartingScript(SessionKey sessionKey) {
        Script startingScript = scriptCache.get(sessionKey.to());
        if (startingScript == null) {
            // TODO Expand this to look for keyword in message, other logic.
            // TODO fetch from Redis/Postgres/file system...
            LOG.info("No script found in cache for {}. Using default for platform, {}.", sessionKey.to(), sessionKey.platform());

            startingScript = switch (sessionKey.to()) {
                case "1234" -> new Script("PrintWithPrefix", ScriptType.EchoWithPrefix, null, "1234");
                case "2345" -> new Script("ReverseText", ScriptType.ReverseText, null, "2345");
                case "3456" -> new Script("HelloGoodbye", ScriptType.HelloGoodbye, null, "3456");

                case "45678" -> { // chain Scripts together using the PresentMulti/ProcessMulti
                    Script one = new Script("What's you favorite color? 1) red 2) blue 3) flort", ScriptType.PresentMulti, null, "ColorQuiz");
                    Script two = new Script("Oops, that's not one of the options. Try again with one of the listed numbers or say 'change topic' to start talking about something else.",
                            ScriptType.ProcessMulti, one, "EvaluateColorAnswer");
                    ResponseLogic linkOneToTwo = new ResponseLogic(null, null, two);
                    one.next().add(linkOneToTwo);
                    Script tre = new Script("End-of-Conversation", ScriptType.EchoWithPrefix, two, "EndOfConversation");
                    ResponseLogic twoOption1 = new ResponseLogic(List.of("1", "red"), "Red is the color of life.", tre);
                    ResponseLogic twoOption2 = new ResponseLogic(List.of("2", "blue"), "Blue is my fave, as well.", tre);
                    ResponseLogic twoOption3 = new ResponseLogic(List.of("1", "flort"), "Flort is for the cool kids.", tre);
                    two.next().add(twoOption1);
                    two.next().add(twoOption2);
                    two.next().add(twoOption3);

                    yield one;
                }
                default -> defaultScript(sessionKey.platform(), sessionKey.to());
            };

//            scriptCache.put(message.to(), startingScript);
        }
        return startingScript;
    }

    Script defaultScript(Platform platform, String shortCodeOrKeywordOrChannelName) {
        // TODO use params to determine the correct initial script.
        return new Script("TEST SCRIPT RESPONSE PREFIX", ScriptType.EchoWithPrefix, null, "DefaultScript");
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

    public static void main(String[] args) throws IOException, TimeoutException, PersistenceManagerException {
        Operator operator = new Operator();
        operator.init(ConfigLoader.readConfig("operator.properties"));
    }

    public void shutdown() throws IOException, TimeoutException {
        LOG.info("Shutting down Operator");
        queueConsumer.shutdown();
        LOG.info("Shutdown queueConsumer.");
        queueProducer.shutdown(); // FIXME call getQueueProducer() and iterate through all the queueProducers
        LOG.info("Shutdown queueProducer.");
    }
}
