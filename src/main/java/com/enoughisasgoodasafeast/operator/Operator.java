package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.enoughisasgoodasafeast.operator.Telecom.deriveCountryCodeFromId;

public class Operator implements MessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Operator.class);
    private static final int EXPECTED_INPUT_COUNT = 1;

    final LoadingCache<SessionKey, Session> sessionCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES) // TODO make duration part of the configuration
            .build(key -> createSession(key));

    final LoadingCache<SessionKey, User> userCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .build(key -> findOrCreateUser(key));

/*        final LoadingCache<String, Keyword> keywordCache = Caffeine.newBuilder()
 *            .expireAfterAccess(20, TimeUnit.MINUTES)
 *           .build(keyword -> findStartingScript());*/

    // TODO build a two layer cache: 1) regular exact match key map 2) a set of regex patterns that when matched put the matched value into the
    // exact match map (for efficiency). The regexes would be compiled once.
    Map<String, Keyword> keywordCache = new ConcurrentHashMap<>();


    // Consider async loading cache for this, assuming we preload all scripts
    final LoadingCache<SessionKey, Node> scriptCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .build(key -> findStartingScript(key));

    private final Platform defaultPlatform = Platform.BRBL;

    private QueueConsumer queueConsumer;
    private QueueProducer queueProducer;
    private PersistenceManager persistenceManager;

    public Operator() {
    }

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
     *
     * @param message the message being processed.
     * @return true if processing was complete, false if incomplete.
     */
    @Override
    public boolean process(Message message) {
        LOG.info("Process message: {}", message);
        final SessionKey sessionKey = SessionKey.newSessionKey(message);
        Session session = sessionCache.get(sessionKey);
        if (null == session) {
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
                session.registerInput(message);
                int size = session.currentInputsCount();
                if (size > EXPECTED_INPUT_COUNT) {
                    LOG.error("Uh oh, there are more inputs ({}) than expected in session ({})", size, session);
                    // Corner case: user sent multiple responses that arrived closely together (probably due to delays/buffering in
                    // the telco's SMSc) and, due to an unfortunate thread context switch, we've processed each in the same
                    // Likely this creates an unexpected situation. To handle it we should create a new Node of
                    // NodeType.PivotScript and chain the remaining Scripts to it.
                    // These scripts will explain the problem and ask what the user what they want to do.
                    // We'll do the same in other cases as well.
                    // TODO...fetch the PivotScript for the given shortcode
                }

                // Also check if the current Message was created prior to the previous Message in the session's history.
                // This would signal out-of-order processing which Is Bad™
                Message previousInputMessage = session.previousInput();
                if (previousInputMessage != null) {
                    if (previousInputMessage.receivedAt().isAfter(message.receivedAt())) {
                        LOG.error("Oh shit, we processed an MO received later than this one: {} > {}",
                                previousInputMessage.receivedAt(), message.receivedAt());
                    }
                }

                // FIXME Session.evaluate handles appending the node to the evaluatedScript list
                // FIXME why split the logic for handling currentNode? Move it into Session? Or move both here?
                Node next = session.currentNode.evaluate(session, message); // FIXME session.evaluateCurrentNode()?
                session.registerEvaluated(session.currentNode); //FIXME what if this is null?
                LOG.info("Next node is {}", next);
                session.currentNode = next;

                while (next != null && !next.type().isAwaitInput()) {
                    LOG.info("Continuing playback...");
                    // Assumes Present and Process are always paired. If this works, make the pattern more generic.
                    // FIXME This is hideous because we're using Session variables as globals here and in the static Multi functions
                    session.currentNode = session.currentNode.evaluate(session, message);
                    if (session.currentNode != null) {
                        session.registerEvaluated(session.currentNode);
                    }

                    next = session.currentNode;

                }

                session.flush(); //FIXME should be in a finally block but writing to db can throw. Hmm...
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
     *
     * @param sessionKey the key associated with the new Session
     * @return the newly constructed Session added to the sessionCache
     * @throws InterruptedException if any of the involved threads was interrupted
     * @throws ExecutionException if any of the subtasks failed
     */
    Session createSession(SessionKey sessionKey) throws InterruptedException, ExecutionException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            Supplier<User> user = scope.fork(() -> userCache.get(sessionKey));
            Supplier<Node> script = scope.fork(() -> findStartingScript(sessionKey));
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

    protected User findOrCreateUser(SessionKey sessionKey) {
        User user = persistenceManager.getUser(sessionKey);
        if (user == null) {
            LOG.info("User not found.");
            user = new User(UUID.randomUUID(),
                    defaultPlatformIdMap(sessionKey.from()),
                    defaultPlatformTimeCreatedMap(NanoClock.utcInstant()),
                    deriveCountryCodeFromId(sessionKey.from()),
                    defaultLanguageList(sessionKey.from(), sessionKey.to()));
            boolean isInserted = persistenceManager.insertUser(user);
            if (!isInserted) {
                LOG.error("findOrCreateUser failed to insert user. Caching it anyway: {}", user);
                // Queue or write it to a file so it can be loaded later (when the database is back up)?
            } else {
                LOG.info("New User created: {}", user);
            }

        } else {
            LOG.info("Retrieved User: {}", user);
        }
        return user;
    }


    /*
     * This is all hard coded for the moment. Obviously it needs to be replaced with something that loads
     * a Node from a database based on the content of the Message.
     * When we replace this be sure to convert the scriptCache to be a Caffeine cache with a synchronous callback
     * doing the work of returning the values.
     */
    Node findStartingScript(SessionKey sessionKey) {
        if (keywordCache.isEmpty()) {
            keywordCache.putAll(persistenceManager.getKeywords());
        }
        Keyword keyword = keywordCache.get(sessionKey.keyword()); // TODO loop over the regex patterns represented by the cache keys?
        LOG.info("Found existing keyword mapping: {}", keyword);
        return persistenceManager.getScript(keyword.scriptId());
//        Node startingScript = scriptCache.get(sessionKey.to());
//        if (startingScript == null) {
//            // TODO Expand this to look for keyword in message, other logic.
//            // TODO fetch from Redis/Postgres/file system...
//            LOG.info("No node found in cache for {}. Using default for platform, {}.", sessionKey.to(), sessionKey.platform());
//
//            startingScript = switch (sessionKey.to()) {
//                case "1234" -> new Node("PrintWithPrefix", NodeType.EchoWithPrefix, "1234");
//                case "2345" -> new Node("ReverseText", NodeType.ReverseText, "2345");
//                case "3456" -> new Node("HelloGoodbye", NodeType.HelloGoodbye, "3456");
//
//                case "45678" -> { // chain Scripts together using the PresentMulti/ProcessMulti
//                    Node one = new Node("What's you favorite color? 1) red 2) blue 3) flort", NodeType.PresentMulti, "ColorQuiz");
//                    Node two = new Node("Oops, that's not one of the options. Try again with one of the listed numbers or say 'change topic' to start talking about something else.",
//                            NodeType.ProcessMulti, "EvaluateColorAnswer");
//                    Edge linkOneToTwo = new Edge(null, null, two);
//                    one.next().add(linkOneToTwo);
//                    Node tre = new Node("End-of-Conversation", NodeType.EchoWithPrefix, "EndOfConversation");
//                    Edge twoOption1 = new Edge(List.of("1", "red"), "Red is the color of life.", tre);
//                    Edge twoOption2 = new Edge(List.of("2", "blue"), "Blue is my fave, as well.", tre);
//                    Edge twoOption3 = new Edge(List.of("1", "flort"), "Flort is for the cool kids.", tre);
//                    two.next().add(twoOption1);
//                    two.next().add(twoOption2);
//                    two.next().add(twoOption3);
//
//                    yield one;
//                }
//                default -> defaultScript(sessionKey.platform(), sessionKey.to());
//            };
//
//            scriptCache.put(message.to(), startingScript);
//        }
//        return startingScript;
    }

    Node defaultScript(Platform platform, String shortCodeOrKeywordOrChannelName) {
        // TODO use params to determine the correct initial node.
        return new Node("TEST SCRIPT RESPONSE PREFIX", NodeType.EchoWithPrefix, "DefaultScript");
    }

    Map<Platform, String> defaultPlatformIdMap(String from) {
        return Map.of(defaultPlatform, from);
    }

    Map<Platform, Instant> defaultPlatformTimeCreatedMap(Instant createdAt) {
        return Map.of(defaultPlatform, createdAt);
    }

    List<String> defaultLanguageList(String from, String to) {
        // TODO:
        // the associated shortcode/longcode should probably have expected language code(s)
        // we could also use the 'from' to guess. E.g. if the number is Mexican we could assume 'SPA'
        return List.of("ENG");
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
