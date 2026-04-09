package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.*;
import com.enoughisasgoodasafeast.operator.PersistenceManager.PersistenceManagerException;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.jenetics.util.NanoClock;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static com.enoughisasgoodasafeast.operator.Telecom.deriveCountryCodeFromId;
import static io.jenetics.util.NanoClock.utcInstant;

public class Operator implements MessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Operator.class);

    //private static final int EXPECTED_INPUT_COUNT = 1;

    public static final String ALL = "ALL";

    // TODO replace with a mechanism that check the country code and returns a set appropriate for that country.
    public static final Set<LanguageCode> DEFAULT_LANGUAGE_CODE_SET = Set.of(LanguageCode.ENG);

    final LoadingCache<@NonNull SessionKey, Session> sessionCache = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES) // TODO make duration part of the configuration here and for all the other caches.
            .build(key -> findOrCreateSession(key));

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
        return ScriptEngine.process(session, message);
    }

//    /**
//     * Execute the node in the context of the given session and message.
//     * Most simply this can result in the creation of one more MTMessages.
//     * There are a variety of possible side effects including:
//     * - inserts/updates to the database
//     * - schedule new messages
//     * - invoke an ML operation
//     *
//     * @param node      the node being evaluated
//     * @param session   the user context
//     * @param moMessage the MO message being processed
//     * @return the next Node in the conversation (or null if the conversation is complete?)
//     * FIXME Maybe instead of null we return a symbolic Node that indicates the end of Node?
//     */
//    private Node evaluate(Node node, ScriptContext session, Message moMessage) throws IOException {
//        Node nextNode = switch (node.type()) {
//            case EchoWithPrefix -> SimpleTestScript.SimpleEchoResponseScript.evaluate(session, moMessage);
//
//            case ReverseText -> SimpleTestScript.ReverseTextResponseScript.evaluate(session, moMessage);
//
//            case HelloGoodbye -> SimpleTestScript.HelloGoodbyeResponseScript.evaluate(session, moMessage);
//
//            // NOTE: practically speaking there's no reason to have any of the above. Most Scripts should
//            // be of the following types or more specific versions thereof. Simple chaining conversations can
//            // simply have a single logic list.
//            case PresentMulti ->
//                    Multi.Present.evaluate(session, moMessage); // Could re-use SendMessage logic while keeping the type difference
//
//            case ProcessMulti -> Multi.Process.evaluate(session, moMessage);
//
//            // TODO Behaves like a SendMessage albeit with the expectation that there's no "next" node so we could replace impl
//            case EndOfChat ->
//                    SendMessage.evaluate(session, moMessage); //EndOfSession? 'request' that the session be cleared?
//
//            // TODO Even easier to replace with SendMessage.evaluate(). The Editor will always pair it with an Input.Process
//            case RequestInput -> Input.Request.evaluate(session, moMessage);
//
//            case ProcessInput -> Input.Process.evaluate(session, moMessage);
//
//            case SendMessage -> SendMessage.evaluate(session, moMessage);
//
//        };
//
//        session.registerEvaluated(node);
//        return nextNode;
//
//    }


    @Override
    public boolean log(Message message) {
        return persistenceManager.insertProcessedMO(
                message,
                this.sessionCache.get(SessionKey.newSessionKey(message)));
        // More logging here if insert fails?
    }

    /*
     * Check for an active Session without triggering the builder method.
     */
//    public @Nullable Session getActiveSession(SessionKey sessionKey) {
//        return sessionCache.getIfPresent(sessionKey);
//    }

    /**
     * Builder method used with the LoadingCache.
     *
     * @param sessionKey the key associated with the new Session
     * @return the newly constructed Session
     * @throws InterruptedException if any of the involved threads was interrupted
     * @throws ExecutionException   if any of the subtasks threw an exception
     */
    private @Nullable Session findOrCreateSession(SessionKey sessionKey) throws InterruptedException, ExecutionException {
        var start = NanoClock.utcInstant();
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Supplier<User> suppliedUser = scope.fork(()  -> userCache.get(sessionKey));
            Supplier<Node> keywordScript = scope.fork(() -> scriptByKeywordCache.get(KeywordCacheKey.newKey(sessionKey)));
            Supplier<Node> defaultScript = scope.fork(() ->
                    findDefaultScriptByRoute(sessionKey));

            scope.join().throwIfFailed(); // TODO consider using joinUntil() to enforce a collective timeout.

            var script = (keywordScript.get() != null) ? keywordScript.get() : defaultScript.get();
            if (script == null) { // For now let's fail hard on configuration errors even if the user has an existing session that could be used instead.
                LOG.error("No script available by keyword match or route default. Configuration error in route table for {}:{} ",
                        sessionKey.platform(), sessionKey.to());
                return null;
            }

            Session session;

            var user = suppliedUser.get();
            if (user == null) {
                // We should have logged a critical error in the userCache's provider method, findOrCreateUser.
                LOG.error("findOrCreateSession: no user provided for {}", sessionKey);
                return null;
            }

            // Check if the Session table has a record for this User. We can skip this check if we just created the User
            final Instant userCreatedAt = user.platformCreationTimes().get(sessionKey.platform());
            try {
                if (start.isBefore(userCreatedAt) || start.equals(userCreatedAt) || null == (session = persistenceManager.loadSession(user.groupId()))) {
                    // brand new
                    LOG.info("Creating new Session for user {}", suppliedUser.get().groupId());
                    return new Session(
                            randomUUID(),
                            script,
                            user,
                            getQueueProducer(sessionKey.platform()),
                            persistenceManager);
                }
            } catch (PersistenceManagerException e) {
                LOG.error("Error loading session for user {}", user.groupId(), e);
                return null;
            }

            // If we had an existing session, check if we were awaiting input. If not replace the script with one we looked up above.
            if (!session.currentNode.type().equals(NodeType.ProcessMulti)) { // add other input expecting node types here.
                session.currentNode = script;
            }

            return session.postDeserialize(getQueueProducer(sessionKey.platform()), persistenceManager);
        }
    }

    private QueueProducer getQueueProducer(Platform platform) {
        // We may need to add different handling for each Platform. Only one is used for now.
        return queueProducer;
    }

    @Nullable User findOrCreateUser(@NonNull SessionKey sessionKey) {
        User user = persistenceManager.getUser(sessionKey);
        if (user == null) {
            LOG.info("Existing user not found.");

            var owningId = findOwningCompanyIdByRouteChannel(sessionKey);
            if(owningId == null) {
                // very bad
                LOG.error("CRITICAL_CONFIG_ERROR: findOrCreateUser: No owning company found for route {}:{}. (User: {})",
                        sessionKey.platform(), sessionKey.to(), sessionKey.from());
                return null;
            }

            user = new User(
                    Map.of(sessionKey.platform(), randomUUID()),            // platformIds
                    randomUUID(),                                           // groupId
                    Map.of(sessionKey.platform(), sessionKey.from()),       // platformNumbers
                    Map.of(sessionKey.platform(), utcInstant()),            // platformCreationTimes
                    deriveCountryCodeFromId(sessionKey.from()),             // countryCode
                    defaultLanguageSet(sessionKey.from(), sessionKey.to()), // languages
                    owningId,                                               // claimant_id
                    null,                                                   // customer_id
                    defaultNickNameMap(),                                   // platformNicknames
                    null, // FIXME need to do a consistency check on how we handle these Platform-keyed maps. How should we represent an absence of values?
                    defaultPlatformStatusMap(UserStatus.IN) // FIXME is this right initial value?
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
     *
     * @return a map of all the regex pattern-to-Keyword entries in the system.
     */
    private Map<Pattern, Keyword> loadAllKeywords(String theOneAndOnlyKey) {
        return persistenceManager.getKeywords();
    }

    /**
     * Evaluate the incoming shortcode and keyword against the registered regex patterns.
     *
     * @param keywordCacheKey that contains the keyword and short/long code or platform specific program identxifier.
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
        return persistenceManager.getNodeGraph(nodeId);
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
        //            LOG.info("Found
        //            default script for route: {}", route);
        //            return scriptCache.get(route.default_node_id());
        //        }
        //    }
        //}
        Route route = findRoute(sessionKey);
        if (route != null) {
            return scriptCache.get(route.defaultNodeId());
        } else {
            return null;
        }
    }


    @Nullable UUID findOwningCompanyIdByRouteChannel(SessionKey sessionKey) {
        Route route = findRoute(sessionKey);
        if (route != null) {
            return route.companyId();
        } else { // If this is null we have a MAJOR configuration error. Routes without an owner cannot exist!
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

//    private Map<Platform, String> defaultPlatformIdMap(String from) {
//        return Map.of(defaultPlatform, from);
//    }

//    private Map<Platform, Instant> defaultPlatformTimeCreatedMap(Instant createdAt) {
//        return Map.of(defaultPlatform, createdAt);
//    }

    private Map<Platform, UserStatus> defaultPlatformStatusMap(UserStatus userStatus) {
        return Map.of(defaultPlatform, userStatus);
    }

    private Map<Platform, String> defaultNickNameMap() {
        return Collections.emptyMap();
    }

    private Set<LanguageCode> defaultLanguageSet(String from, String to) {
        // TODO:
        // the associated shortcode/longcode should probably have expected language code(s)
        // we could also use the 'from' to guess. E.g. if the number is Mexican we could assume 'SPA'
        return DEFAULT_LANGUAGE_CODE_SET;
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
