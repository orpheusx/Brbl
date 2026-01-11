package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.jenetics.util.NanoClock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;

import static com.enoughisasgoodasafeast.operator.Telecom.deriveCountryCodeFromId;

public class Operator implements MessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Operator.class);

    private static final int EXPECTED_INPUT_COUNT = 1;

    public static final String ALL = "ALL";

    final LoadingCache<@NonNull SessionKey, Session> sessionCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES) // TODO make duration part of the configuration
            .build(key -> createSession(key));

    final LoadingCache<@NonNull SessionKey, User> userCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .build(key -> findOrCreateUser(key));

    final LoadingCache<@NonNull UUID, Node> scriptCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .build(id -> getScript(id));

    // NB: This is an odd duck because it's intended to contain a single entry with all the keyword data.
    // We're using a LoadingCache here to benefit from its auto-eviction and thread safety features.
    final LoadingCache<@NonNull String, Map<Pattern, Keyword>> allKeywordsByPatternCache = Caffeine.newBuilder()
            .expireAfterWrite(20, TimeUnit.MINUTES)
            .build(theOneAndOnlyKey -> loadAllKeywords(theOneAndOnlyKey));

    final LoadingCache<@NonNull KeywordCacheKey, Node> scriptByKeywordCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .build(key -> findScriptForKeywordChannel(key));

    // NB: Another single entry cache used for the same benefits as above.
    final LoadingCache<@NonNull String, @NonNull Route[]> activeRoutesCache = Caffeine.newBuilder()
            .expireAfterWrite(20, TimeUnit.MINUTES)
            .build(allRoutes -> loadAllActiveRoutes(allRoutes));


    private final Platform defaultPlatform = Platform.BRBL;

    private QueueConsumer queueConsumer;
    private QueueProducer queueProducer;
    private PersistenceManager persistenceManager;

    public Operator() {
    }

    public Operator(QueueConsumer queueConsumer, QueueProducer queueProducer, PersistenceManager persistenceManager) {
        this.queueConsumer = queueConsumer;
        this.queueProducer = queueProducer;
        this.persistenceManager = persistenceManager;
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
                    // NB: We've synchronized on the Session which seems like it should prevent the following:
                        // Corner case: user sent multiple responses that arrived closely together (possibly due to delays/buffering in
                        // the telco's SMSc) and, due to an unfortunate thread context switch, we've processed each in the same process call.
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
                        // TODO fetch a special script to apologize to the user then replay the Node returned by Session.getScriptForProcessedMO()?
                    }
                }

                // FIXME Session.evaluate handles appending the evaluated node to the evaluatedScript list
                // NB Script processing functions are limited to getting the currentNode, never setting it.
                // Setting it is only done here based on the function's return value but can be
                Node next = evaluate(session.currentNode, session, message); // FIXME What if currentNode is null? Start using Optionals with a constant sentinel value instead of null?
                LOG.info("Next node is {}", next);
                session.currentNode = next;

                // Continue to walk the graph until we reach the end (null) or a node that blocks for input
                while (next != null && !next.type().isAwaitInput()) {
                    LOG.info("Continuing playback...");
                    next = evaluate(session.currentNode, session, message);

                    session.currentNode = next;
                }

                session.flush(); // FIXME ideally should be in a finally block but writing to db can throw. Hmm...

                persistenceManager.saveSession(session);

                return true; // when would this be false?

            } catch (IOException | PersistenceManagerException e) {
                LOG.error("Processing error", e); // TODO need to consider options for better handling of error scenarios.
                return false;
            }
        }
    }

    /**
     * Execute the node in the context of the given session and message.
     * Most simply this can result in the creation of one more MTMessages.
     * There are a variety of possible side effects including:
     *  - inserts/updates to the database
     *  - schedule new messages
     *  - invoke an ML operation
     * @param node the node being evaluated
     * @param session the user context
     * @param moMessage the MO message being processed
     * @return the next Node in the conversation (or null if the conversation is complete?)
     * FIXME Maybe instead of null we return a symbolic Node that indicates the end of Node?
     */
    private Node evaluate(Node node, ScriptContext session, Message moMessage) throws IOException {
        Node nextNode = switch (node.type()) {
            case EchoWithPrefix ->
                    SimpleTestScript.SimpleEchoResponseScript.evaluate(session, moMessage);

            case ReverseText ->
                    SimpleTestScript.ReverseTextResponseScript.evaluate(session, moMessage);

            case HelloGoodbye ->
                    SimpleTestScript.HelloGoodbyeResponseScript.evaluate(session, moMessage);

            // NOTE: practically speaking there's no reason to have any of the above. Most Scripts should
            // be of the following types or more specific versions thereof. Simple chaining conversations can
            // simply have a single logic list.
            case PresentMulti ->
                    Multi.Present.evaluate(session, moMessage); // Could re-use SendMessage logic while keeping the type difference

            case ProcessMulti ->
                    Multi.Process.evaluate(session, moMessage);

            // TODO Behaves like a SendMessage albeit with the expectation that there's no "next" node so we could replace impl
            case EndOfChat -> SendMessage.evaluate(session, moMessage); //EndOfSession? 'request' that the session be cleared?

            // TODO Even easier to replace with SendMessage.evaluate(). The Editor will always pair it with an Input.Process
            case RequestInput ->
                    Input.Request.evaluate(session, moMessage);

            case ProcessInput ->
                    Input.Process.evaluate(session, moMessage);

            case SendMessage ->
                    SendMessage.evaluate(session, moMessage);

        };

        session.registerEvaluated(node);
        return nextNode;

    }


    @Override
    public boolean log(Message message) {
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
    private Session createSession(SessionKey sessionKey) throws InterruptedException, ExecutionException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            Supplier<User> user = scope.fork(() -> userCache.get(sessionKey));
            Supplier<Node> keywordScript = scope.fork(() -> scriptByKeywordCache.get(KeywordCacheKey.newKey(sessionKey)));
            Supplier<Node> defaultScript = scope.fork(() ->
                    findDefaultScriptByRoute(sessionKey));

            scope.join().throwIfFailed(); // TODO consider using joinUntil() to enforce a collective timeout.

            // Check if the Session table has a record for this User. We can skip this check if the User is brand new.
            //            final Instant instant = user.get().platformCreationTimes().get(sessionKey.platform());
            //            final Session session = ((PostgresPersistenceManager) persistenceManager).getSession(user.get().id());
            //            if (session != null) {
            //                // Replace the script property with the one we just fetched.
            //
            //                return session;
            //            } else {
//            persistenceManager.getActiveRoutes()loadSession

            Node script = (keywordScript.get() != null) ? keywordScript.get() : defaultScript.get();
            if (script == null) {
                LOG.error("No script available by keyword match or route default. Configuration error in route table for {}:{} ",
                        sessionKey.platform(), sessionKey.to());
                return null;
            } else {
                return new Session(
                        UUID.randomUUID(),
                        script,
                        user.get(),
                        getQueueProducer(sessionKey.platform()),
                        persistenceManager);
            }
            //            }
        }
    }

    private QueueProducer getQueueProducer(Platform platform) {
        // We may need to add different handling for each Platform.
        return queueProducer;
    }

    User findOrCreateUser(SessionKey sessionKey) {
        User user = persistenceManager.getUser(sessionKey);
        if (user == null) {
            LOG.info("User not found.");
            user = new User(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    defaultPlatformIdMap(sessionKey.from()),
                    defaultPlatformTimeCreatedMap(NanoClock.utcInstant()),
                    deriveCountryCodeFromId(sessionKey.from()),
                    defaultLanguageList(sessionKey.from(), sessionKey.to()),
                    findCustomerIdByRoute(sessionKey),
                    defaultNickNameMap(),
                    null,
                    defaultPlatformStatusMap(UserStatus.IN) // FIXME is this right value?
            );

            boolean isInserted = persistenceManager.insertNewUser(user);
            if (!isInserted) {
                LOG.error("findOrCreateUser failed to insert new user: {}", user);
                return null;
                // LOG.error("findOrCreateUser failed to insert user. Caching it anyway: {}", user);
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
    private Map<Pattern, Keyword> loadAllKeywords(String theOneAndOnlyKey) {
        return persistenceManager.getKeywords();
    }

    /**
     * Evaluate the incoming shortcode and keyword against the registered regex patterns.
     * @param keywordCacheKey that contains the keyword and short/long code or platform specific program identifier.
     * @return the connected Node assigned to the keyword.
     */
    private Node findScriptForKeywordChannel(KeywordCacheKey keywordCacheKey) {
        final Map<Pattern, Keyword> all = allKeywordsByPatternCache.get(ALL);
        if (all == null || all.isEmpty()) {
            LOG.error("CRITICAL: No keywords available from system for new session!!!");
            return null;
        }
        UUID scriptId = findMatch(all, keywordCacheKey);
        if (scriptId != null) {
            return scriptCache.get(scriptId);
        } else {
            return null;
        }
    }

    protected UUID findMatch(Map<Pattern, Keyword> all, KeywordCacheKey keywordCacheKey) {
        // The key here is the pattern and the value is the Keyword object.
        // It is possible for Keywords with different patterns to point at the same Node.
        for (Map.Entry<Pattern, Keyword> entry : all.entrySet()) {
            Pattern pattern = entry.getKey();
            Keyword keyword = entry.getValue();
            LOG.info("Evaluating pattern {} for {}", pattern, keyword);
            if (keywordCacheKey.platform() == keyword.platform() && keyword.channel().equals(keywordCacheKey.channel())) {
                if (pattern.matcher(keywordCacheKey.keyword()).matches()) {
                    LOG.info("Match found for {}", keywordCacheKey);
                    return keyword.scriptId();
                } else {
                    LOG.info("Not a match: {} !~ {}", keywordCacheKey.keyword(), pattern);
                }
            }
            // Should we be more exhaustive in our search? How to select the right one if more than one matches?
            // The ordering of the map is non-deterministic.
            // Our authoring setup tools will need to pre-test the keyword entries to avoid unintended matches.
        }
        LOG.info("No keyword matches for {}", keywordCacheKey.keyword());
        return null;
    }

    @Nullable Node getScript(UUID nodeId) {
        return persistenceManager.getScript(nodeId);
    }


    private Route[] loadAllActiveRoutes(@NonNull String allRoutes) {
        // NB: Conventionally we'd use a map but the number here is probably pretty well bounded to < 1000 for the foreseeable future.
        // Faster to just iterate over the array.
        return persistenceManager.getActiveRoutes();
    }

    /*
     * Loops through the routes looking for one that matches the provided platform and channel.
     * NB: For (even not so) small numbers of elements, looping is faster that using a map.
     */
    @Nullable Node findDefaultScriptByRoute(SessionKey sessionKey) {
        //final Route[] routes = activeRoutesCache.get(ALL);
        //if (routes != null) {
        //    for (Route route : routes) {
        //        if (route.platform() == sessionKey.platform() && route.channel().equals(sessionKey.to())) {
        //            LOG.info("Found default script for route: {}", route);
        //            return scriptCache.get(route.default_node_id());
        //        }
        //    }
        //}
        Route route = findRoute(sessionKey);
        if (route != null) {
            return scriptCache.get(route.default_node_id());
        } else {
            return null;
        }
    }

    @Nullable UUID findCustomerIdByRoute(SessionKey sessionKey) {
        Route route = findRoute(sessionKey);
        if (route != null) {
            return route.customer_id();
        } else {
            return null;
        }
    }

    @Nullable Route findRoute(SessionKey sessionKey) {
        final Route[] routes = activeRoutesCache.get(ALL);
        if (routes != null) {
            for (Route route : routes) {
                if (route.platform() == sessionKey.platform() && route.channel().equals(sessionKey.to())) {
                    return route;
                }
            }
        }
        return null;
    }

    private Map<Platform, String> defaultPlatformIdMap(String from) {
        return Map.of(defaultPlatform, from);
    }

    private Map<Platform, Instant> defaultPlatformTimeCreatedMap(Instant createdAt) {
        return Map.of(defaultPlatform, createdAt);
    }

    private Map<Platform, UserStatus> defaultPlatformStatusMap(UserStatus userStatus) {
        return Map.of(defaultPlatform, userStatus);
    }

    private Map<Platform, String> defaultNickNameMap() {
        return Collections.emptyMap();
    }

    private List<String> defaultLanguageList(String from, String to) {
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
