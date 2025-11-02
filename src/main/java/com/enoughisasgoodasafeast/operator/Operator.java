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
import java.util.regex.Pattern;

import static com.enoughisasgoodasafeast.operator.Telecom.deriveCountryCodeFromId;

public class Operator implements MessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Operator.class);
    private static final int EXPECTED_INPUT_COUNT = 1;
    private static final String ALL_KEYWORDS = "ALL";

    final LoadingCache<SessionKey, Session> sessionCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES) // TODO make duration part of the configuration
            .build(key -> createSession(key));

    final LoadingCache<SessionKey, User> userCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .build(key -> findOrCreateUser(key));

    final LoadingCache<UUID, Node> scriptCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .build(id -> getScript(id));

    // NB: This is an odd duck because it's intended to contain a single entry with all the keyword data.
    // We're using a LoadingCache here to benefit from its auto-eviction and thread safety features.
    final LoadingCache<String, Map<Pattern, Keyword>> allKeywordsByPatternCache = Caffeine.newBuilder()
            .expireAfterWrite(20, TimeUnit.MINUTES)
            .build(theOneAndOnlyKey -> loadAllKeywords(theOneAndOnlyKey));

    final LoadingCache<KeywordCacheKey, Node> scriptByKeywordCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .build(key -> findScriptForKeywordShortCode(key));

    private final Platform defaultPlatform = Platform.BRBL;

    private QueueConsumer queueConsumer;
    private QueueProducer queueProducer;
    private PersistenceManager persistenceManager;

    public Operator() {
    }

//    public Operator(QueueConsumer queueConsumer, QueueProducer queueProducer) {
//        this(queueConsumer, queueProducer, null);
//    }

    public Operator(QueueConsumer queueConsumer, QueueProducer queueProducer, PersistenceManager persistenceManager) {
        this.queueConsumer = queueConsumer;
        this.queueProducer = queueProducer;
        this.persistenceManager = persistenceManager;
    }

    // TODO get rid of this version by updating OperatorTest's use of it.
//    public void init() throws IOException, TimeoutException {
//        LOG.info("Initializing Brbl Operator");
//        if (queueConsumer == null) {
//            queueConsumer = RabbitQueueConsumer.createQueueConsumer(
//                    "rcvr.properties", this);
//        }
//        if (queueProducer == null) {
//            queueProducer = RabbitQueueProducer.createQueueProducer("sndr.properties");
//        }
//        // Other resources? Connections to database/distributed caches?
//    }

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
//                    return false;
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
//                        return false;
                    }
                }

                // FIXME Session.evaluate handles appending the node to the evaluatedScript list
                // FIXME Why split the logic for handling currentNode? Move it into Session? Or move both here?
                Node next = session.currentNode.evaluate(session, message); // FIXME session.evaluateCurrentNode()?
                session.registerEvaluated(session.currentNode); //FIXME What if this is null? Start using Optionals with a constant sentinel value instead of null?
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

                session.flush(); //FIXME ideally should be in a finally block but writing to db can throw. Hmm...
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
     * @throws ExecutionException   if any of the subtasks failed
     */
    Session createSession(SessionKey sessionKey) throws InterruptedException, ExecutionException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            Supplier<User> user = scope.fork(() -> userCache.get(sessionKey));
            Supplier<Node> script = scope.fork(() -> scriptByKeywordCache.get(KeywordCacheKey.newKey(sessionKey)));

            scope.join().throwIfFailed(); // TODO consider using joinUntil() to enforce a collective timeout.

            // Check if the Session table has a record for this User. We can skip this if the User is brand new.
//            final Instant instant = user.get().platformCreationTimes().get(sessionKey.platform());
//            final Session session = ((PostgresPersistenceManager) persistenceManager).getSession(user.get().id());
//            if (session != null) {
//                // Replace the script property with the one we just fetched.
//
//                return session;
//            } else {
                return new Session(
                        UUID.randomUUID(), script.get(), user.get(),
                        getQueueProducer(sessionKey.platform()),
                        persistenceManager);
//            }
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

    /**
     * Load the single entry into the map of Keywords
     * @return a map of all the regex pattern-to-Keyword entries in the system.
     */
    Map<Pattern, Keyword> loadAllKeywords(String theOneAndOnlyKey) {
        return persistenceManager.getKeywords();
    }

    /**
     * Evaluate the incoming shortcode and keyword against the registered regex patterns.
     * @param keywordCacheKey that contains the keyword and short/long code or platform specific program identifier.
     * @return the connected Node assigned to the keyword.
     */
    Node findScriptForKeywordShortCode(KeywordCacheKey keywordCacheKey) {
        final Map<Pattern, Keyword> all = allKeywordsByPatternCache.get(ALL_KEYWORDS);
        if (all == null || all.isEmpty()) {
            LOG.error("CRITICAL: No keywords available from system for new session!!!");
            return null;
        }

        // The key here is the pattern and the value is the Keyword object.
        // It is possible for Keywords with different patterns to point at the same Node.
        for (Map.Entry<Pattern, Keyword> entry : all.entrySet()) {
            Pattern pattern = entry.getKey();
            Keyword keyword = entry.getValue();
            LOG.info("Evaluating pattern {} for {}", pattern, keyword);
            // If the keyword's shortCode is null this means that its effectively global.
            if (keywordCacheKey.code().equals(keyword.shortCode()) || keyword.shortCode() == null) { // filter by short code
                if (pattern.matcher(keywordCacheKey.keyword()).matches()) {
                    LOG.info("Match found for {}", keywordCacheKey);
                    return scriptCache.get(keyword.scriptId());
                }
            }
            // Should we be more exhaustive in our search? How to select the right one if more than one matches?
            // The ordering of the map is non-deterministic.
            // Our authoring setup tools will need to pre-test the keyword entries to avoid unintended matches.
        }

        LOG.warn("No keyword found for {}", keywordCacheKey);
        return null; // no matches!
    }

    Node getScript(UUID nodeId) {
        return persistenceManager.getScript(nodeId);
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
