package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.Message;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.security.Key;
import java.sql.*;
import java.time.Instant;
import java.util.*;

import static java.sql.Types.*;

public class PostgresPersistenceManager implements PersistenceManager {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresPersistenceManager.class);

    private static ComboPooledDataSource pds;

    public static PersistenceManager createPersistenceManager(Properties properties) throws PersistenceManagerException {
        // if, at some point, we need to enforce a single instance we'll do it here.
        return new PostgresPersistenceManager(properties);
    }

    private PostgresPersistenceManager(Properties props) throws PersistenceManagerException {
        pds = new ComboPooledDataSource();
        try {
            pds.setProperties(props);

            pds.setJdbcUrl(props.getProperty("jdbcUrl"));
            pds.setDriverClass(props.getProperty("driverClass"));
            pds.setInitialPoolSize(Integer.parseInt(props.getProperty("initialPoolSize")));
            pds.setMinPoolSize(Integer.parseInt(props.getProperty("minPoolSize")));
            pds.setMaxPoolSize(Integer.parseInt(props.getProperty("maxPoolSize")));
            pds.setAcquireIncrement(Integer.parseInt(props.getProperty("acquireIncrement")));
        } catch (PropertyVetoException e) {
            throw new PersistenceManagerException("PostgresPersistenceManager configuration error", e);
        }

        // Verify that the ConnectionPool is ready for action by triggering it to fill itself.
        try (Connection poolInitingConnection = fetchConnection()) {
            LOG.info("Connection pool initialized");
        } catch (SQLException e) {
            throw new PersistenceManagerException("PostgresPersistenceManager startup error", e);
        }
    }

    public static String MO_MESSAGE_INSERT =
            """
                    INSERT INTO brbl_logs.messages_mo
                        (id, rcvd_at, _from, _to, _text)
                    VALUES
                        (?, ?, ?, ?, ?);
                    """;

    public static String MO_MESSAGE_PRCD =
            """
                    INSERT INTO brbl_logs.messages_mo_prcd 
                        (id, prcd_at, session_id, script_id)
                    VALUES
                        (?, ?, ?, ?);
                    """;

    public static String MT_MESSAGE_INSERT =
            """
                    INSERT INTO brbl_logs.messages_mt
                        (id, sent_at, _from, _to, _text, session_id, script_id)
                    VALUES
                        (?, ?, ?, ?, ?, ?, ?);
                    """;

    public static String MT_MESSAGE_DLVR =
            """
                    INSERT INTO brbl_logs.messages_mt_dlvr
                        (id, dlvr_at)
                    VALUES
                        (?, ?);
                    """;

    public static String USER_PROFILE_BY_PLATFORM_ID_AND_CODE =
            """
                    SELECT
                    	u.group_id, u.platform_id, u.platform_code,
                    	u.country, u.language, u.nickname, u.created_at,
                    	p.surname, p.given_name, p.other_languages
                    FROM
                        brbl_users.users u
                    LEFT JOIN
                        brbl_users.profiles p
                    ON
                        u.group_id = p.group_id
                    WHERE
                    	u.group_id = (
                    		SELECT group_id
                    		FROM brbl_users.users
                    		WHERE platform_id = ?
                    	)
                    """; // FIXME figure out if a LATERAL JOIN might replace the sub-select part of this query

    public static String USER_INSERT =
            """
                    INSERT INTO brbl_users.users
                        (group_id, platform_id, platform_code, country, language, nickname, created_at)
                    VALUES
                        (?, ?, ?, ?, ?, ?, ?)
                    """;

    public static String PROFILE_INSERT =
            """
                    INSERT INTO brbl_users.profiles
                        (group_id, surname, given_name, other_languages, created_at)
                    VALUES
                        (?, ?, ?, ?, ?)
                    """;

    public static String SELECT_SCRIPT_GRAPH =
            """
                    SELECT
                        s.id, s.label, s.text,
                        e.match_text, e.response_text, e.dst
                    FROM
                        brbl_logic.scripts s
                    INNER JOIN
                        brbl_logic.edges e ON s.id = e.src
                    WHERE
                        s.id = ?;
                    """;

//    public static String SELECT_SCRIPT_GRAPH_FULL =
//            """
//                    WITH RECURSIVE cte AS (
//                            SELECT ? AS script_id
//                            UNION ALL
//                            SELECT e.dst
//                            FROM brbl_logic.edges AS e
//                                JOIN cte ON (cte.script_id = e.src)
//                    ) CYCLE script_id SET is_cycle USING path
//                    SELECT
//                        s.id, s.created_at, s.text, s.type, s.label,
//                        e.id, e.created_at, e.match_text, e.response_text, e.src, e.dst
//                    FROM
//                        brbl_logic.scripts s
//                    INNER JOIN
//                        brbl_logic.edges e ON s.id = e.src
//                        WHERE
//                        s.id IN (SELECT DISTINCT(cte.script_id) FROM cte);
//                    """;

    public static String SELECT_SCRIPT_GRAPH_RECURSIVE =
            """
                    WITH RECURSIVE rgraph AS (
                            SELECT ? AS script_id
                        UNION
                        SELECT e.dst
                        FROM brbl_logic.edges AS e
                        JOIN rgraph ON
                            rgraph.script_id = e.src
                    ) CYCLE script_id SET is_cycle USING path
                    SELECT
                        s.id, s.created_at, s.text, s.type, s.label,
                        e.id, e.created_at, e.match_text, e.response_text, e.src, e.dst
                    FROM
                        brbl_logic.scripts s
                    INNER JOIN
                        brbl_logic.edges e ON s.id = e.src
                    INNER JOIN
                        rgraph ON s.id = rgraph.script_id
                    WHERE
                        s.id = rgraph.script_id
                        AND
                        rgraph.is_cycle IS FALSE
                        ORDER BY s.id ;
                    """;

    public static String SELECT_SCRIPT_GRAPH_RECURSIVE_FOR_KEYWORD =
            """
                    WITH RECURSIVE rgraph AS (
                            SELECT script_id FROM brbl_logic.keywords WHERE platform= ?::platform AND pattern = ?
                        UNION ALL
                        SELECT e.dst
                        FROM brbl_logic.edges AS e
                        JOIN rgraph ON
                            rgraph.script_id = e.src
                    ) CYCLE script_id SET is_cycle USING path
                    SELECT
                        s.id, s.created_at, s.text, s.type, s.label,
                        e.id, e.created_at, e.match_text, e.response_text, e.src, e.dst
                    FROM
                        brbl_logic.scripts s
                    INNER JOIN
                        brbl_logic.edges e ON s.id = e.src
                    INNER JOIN
                        rgraph ON s.id = rgraph.script_id
                    WHERE
                        s.id = rgraph.script_id
                        AND
                        rgraph.is_cycle IS FALSE
                        ORDER BY s.id ;
                    """;

    public static String SELECT_ALL_KEYWORDS =
            """
                    SELECT
                        k.id, k.pattern, k.platform, k.script_id, k.is_default
                    FROM
                        brbl_logic.keywords k ;
                    """;


    private Connection fetchConnection() throws SQLException {
        //Instant before = NanoClock.utcInstant();
        return pds.getConnection();
        //Instant after = NanoClock.utcInstant();
        //LOG.info("fetchConnection: b {} a {}: d {} ", before, after, Duration.between(before, after));
    }


    //---------------------------------- MO --------------------------------------

    // Called by Rcvr
    @Override
    public boolean insertMO(Message message) {
        try (Connection connection = fetchConnection()) {
            return insertMO(connection, message);
        } catch (SQLException e) {
            LOG.error("fetchConnection failed", e);
            return false;
        }
    }


    //    public boolean insertMO(List<Message> messages) {
    //        try (Connection connection = fetchConnection()) {
    //            return insertMO(connection, messages);
    //        } catch (SQLException e) {
    //            LOG.error("fetchConnection failed", e);
    //            return false;
    //        }
    //    }

    private static boolean insertMO(Connection connection, Message message) {
        //Instant before = NanoClock.utcInstant();
        try (PreparedStatement ps = connection.prepareStatement(MO_MESSAGE_INSERT)) {
            Timestamp timestampFromInstant = Timestamp.from(message.receivedAt());
            ps.setObject(1, message.id());
            ps.setTimestamp(2, timestampFromInstant);
            ps.setString(3, message.from());
            ps.setString(4, message.to());
            ps.setString(5, message.text());
            ps.execute();
        } catch (SQLException e) {
            LOG.error("insertMO failed for {} {}", message, e.getMessage());
            return false;
        }

        //Instant after = NanoClock.utcInstant();
        //LOG.info("insertMO: b {} a {}: d {} ", before, after, Duration.between(before, after));
        return true;
    }

    // Batch mode is definitely slower for single element lists of Messages
    private static boolean insertMO(Connection connection, List<Message> messages) {
        //Instant before = NanoClock.utcInstant();
        try (PreparedStatement ps = connection.prepareStatement(MO_MESSAGE_INSERT)) {
            for (Message message : messages) {
                Timestamp timestampFromInstant = Timestamp.from(message.receivedAt());
                ps.setObject(1, message.id());
                ps.setTimestamp(2, timestampFromInstant);
                ps.setString(3, message.from());
                ps.setString(4, message.to());
                ps.setString(5, message.text());

                ps.addBatch();
            }

            ps.executeBatch();

        } catch (SQLException e) {
            LOG.error("insertMOs failed", e);
            return false;
        }

        //Instant after = NanoClock.utcInstant();
        //LOG.info("insertMO<List>: b {} a {}: d {} ", before, after, Duration.between(before, after));
        return true;
    }

    //---------------------------------- MO metadata -----------------------------

    @Override
    public boolean insertProcessedMO(Message message, Session session) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return insertProcessedMO(connection, message, session);
        } catch (SQLException e) {
            LOG.error("insertProcessedMO: fetchConnection failed", e);
            return false;
        }
    }

    private static boolean insertProcessedMO(Connection connection, Message message, Session session) {
        //Instant before = NanoClock.utcInstant();
        try (PreparedStatement ps = connection.prepareStatement(MO_MESSAGE_PRCD)) {
            ps.setObject(1, message.id());                      // id
            ps.setTimestamp(2, Timestamp.from(Instant.now()));  // prcd_at
            ps.setObject(3, session.id);                        // session_id
            ps.setObject(4, session.currentScript.id());        // script_id
            // FIXME Actually, haven't we already advanced to the next script at this point?
            // FIXME if so then we'd need to grab the most recent script from session.evaluatedScripts
            ps.execute();
        } catch (SQLException e) {
            LOG.error("insertProcessedMO failed", e);
            return false;
        }
        //Instant after = NanoClock.utcInstant();
        //LOG.info("insertProcessedMO: b {} a {}: d {} ", before, after, Duration.between(before, after));
        return true;
    }

    //---------------------------------- MT --------------------------------------

    // Called by Operator
    @Override
    public boolean insertMT(Message message, Session session) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return insertMT(connection, message, session);
        } catch (SQLException e) {
            LOG.error("insertMTs: fetchConnection failed", e);
            return false;
        }
    }

    // Called by Operator
    //    public boolean insertMTs(List<Message> messages, Session session) {
    //        try (Connection connection = fetchConnection()) {
    //            assert connection != null;
    //            return insertMTs(connection, messages, session);
    //        } catch (SQLException e) {
    //            LOG.error("insertMTs: fetchConnection failed", e);
    //            return false;
    //        }
    //    }

    private static boolean insertMT(Connection connection, Message message, Session session) {
        //Instant before = NanoClock.utcInstant();
        try (PreparedStatement ps = connection.prepareStatement(MT_MESSAGE_INSERT)) {
            ps.setObject(1, message.id());                              // id
            ps.setTimestamp(2, Timestamp.from(message.receivedAt()));   // sent_at
            ps.setString(3, message.from());                            // _from
            ps.setString(4, message.to());                              // _to
            ps.setString(5, message.text());                            // _text
            ps.setObject(6, session.id);                                // session_id
            ps.setObject(7, session.evaluatedScripts.getLast().id());   // script_id
            ps.execute();
        } catch (SQLException e) {
            LOG.error("insertMT failed", e);
            return false;
        }
        //Instant after = NanoClock.utcInstant();
        //LOG.info("insertMT: b {} a {}: d {} ", before, after, Duration.between(before, after));
        return true;
    }

    // As with MOs, batch mode is definitely slower for single element lists of Messages
    //    private static boolean insertMTs(Connection connection, List<Message> messages, Session session) {
    //        Instant before = NanoClock.utcInstant();
    //        try (PreparedStatement ps = connection.prepareStatement(MT_MESSAGE_INSERT)) {
    //            for (Message message : messages) {
    //                ps.setObject(1, message.id());                              // id
    //                ps.setTimestamp(2, Timestamp.from(message.receivedAt()));   // sent_at
    //                ps.setString(3, message.from());                            // _from
    //                ps.setString(4, message.to());                              // _to
    //                ps.setString(5, message.text());                            // _text
    //                ps.setObject(6, session.id);                                // session_id
    //                ps.setObject(7, session.evaluatedScripts.getLast().id());   // script_id
    //
    //                ps.addBatch();
    //            }
    //            ps.executeBatch();
    //
    //        } catch (SQLException e) {
    //            LOG.error("insertMTs failed", e);
    //            return false;
    //        }
    //        Instant after = NanoClock.utcInstant();
    //        LOG.info("insertMTs<List>: b {} a {}: d {} ", before, after, Duration.between(before, after));
    //        return true;
    //    }

    //---------------------------------- MT metadata --------------------------------------

    @Override
    public boolean insertDeliveredMT(Message message) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return insertDeliveredMT(connection, message);
        } catch (SQLException e) {
            LOG.error("insertDeliveredMTs: fetchConnection failed", e);
            return false;
        }
    }

    private static boolean insertDeliveredMT(Connection connection, Message message) {
        //Instant before = NanoClock.utcInstant();
        try (PreparedStatement ps = connection.prepareStatement(MT_MESSAGE_DLVR)) {
            ps.setObject(1, message.id());                              // id
            ps.setTimestamp(2, Timestamp.from(NanoClock.utcInstant())); // dlvr_at

            ps.execute();

        } catch (SQLException e) {
            LOG.error("insertDeliveredMTs failed", e);
            return false;
        }

        //Instant after = NanoClock.utcInstant();
        //LOG.info("insertDeliveredMT: b {} a {}: d {} ", before, after, Duration.between(before, after));
        return true;
    }

    @Override
    public Map<String, Keyword> getKeywords() {
        LOG.info("(Re)Loading keyword cache...");
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return getKeywords(connection);
        } catch (SQLException e) {
            LOG.error("getKeywords: fetchConnection failed", e);
            return null;
        }
    }

    public Map<String, Keyword> getKeywords(Connection connection) {
        Map<String, Keyword> allKeywordMap = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_ALL_KEYWORDS)) {
            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                // k.id
                UUID id = (UUID) rs.getObject(1);
                // k.pattern
                String pattern = rs.getString(2);
                // k.platform
                Platform platform = Platform.byCode(rs.getString(3));
                // k.script_id
                UUID scriptId = (UUID) rs.getObject(4);
                // k.is_default
                boolean isDefault = rs.getBoolean(5);

                Keyword keyword = new Keyword(id, pattern.trim(), platform, scriptId
                        /*,new Customer(tempUser
                                , "givenName", "surname", "companyName")*/);
                allKeywordMap.put(pattern, keyword); // word may be a regex
            }

        } catch (SQLException e) {
            LOG.error("getKeywords failed.");
            throw new RuntimeException(e);
        }

        return allKeywordMap;
    }


        @Override
    public Script getScriptForKeyword(Platform platform, String keyword) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return getScriptForKeyword(connection, platform, keyword);
        } catch (SQLException e) {
            LOG.error("getScript: fetchConnection failed", e);
            return null;
        }
    }

    public Script getScriptForKeyword(Connection connection, Platform platform, String keyword) {
        Map<UUID, Script> scriptMap = new HashMap<>(); // FIXME does the ordering matter?
        try (PreparedStatement ps = connection.prepareStatement(SELECT_SCRIPT_GRAPH_RECURSIVE_FOR_KEYWORD)) {
//            ps.setObject(1, platform);
            ps.setString(1, platform.code());
            ps.setString(2, keyword.trim());

            // s.id, s.created_at, s.text, s.type, s.label,
            // e.id, e.created_at, e.match_text, e.response_text, e.src, e.dst
            Map<UUID, SequencedSet<ResponseLogic>> tempEdges = new HashMap<>();
            Map<UUID, UUID> edgeIdToDstId = new HashMap<>();

            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {

                UUID id = (UUID) rs.getObject(1); // id

                Script script = scriptMap.get(id);
                if (script == null) {
                    // columnIndex 2, // createdAt
                    String text = rs.getString(3);  // text
                    ScriptType type = ScriptType.forValue(rs.getInt(4));  // type
                    String label = rs.getString(5); // label

                    script = new Script(id, text, type, null, label); // no next entries yet...
                    scriptMap.put(id, script);
                }

                UUID edgeId = (UUID) rs.getObject(6); // ResponseLogic.id
                // index 7, skip the createdAt
                List<String> matchText = Functions.parseMatchTextPatterns(rs.getString(8)); // matchText
                String responseText = rs.getString(9);
                UUID srcId = (UUID) rs.getObject(10);
                UUID dstId = (UUID) rs.getObject(11);

                ResponseLogic tempEdge = new ResponseLogic(
                        edgeId, responseText, matchText, scriptMap.get(dstId)
                ); // NB: the destination script may not exist so we will need to update/replace this edge at the end of the while loop
                edgeIdToDstId.put(edgeId, dstId);

                SequencedSet<ResponseLogic> edgesForParentScript = tempEdges.computeIfAbsent(srcId, k -> new LinkedHashSet<>());
                edgesForParentScript.add(tempEdge);
            }

            // Now patch all the references for both the edges and the scripts
            for (Map.Entry<UUID, Script> idAndScript : scriptMap.entrySet()) {
                SequencedSet<ResponseLogic> edgesForScript = tempEdges.get(idAndScript.getKey());
                Script script = idAndScript.getValue();
                for (ResponseLogic edge : edgesForScript) {
                    if (edge.script() == null) {
                        UUID destinationScriptID = edgeIdToDstId.get(edge.id());
                        Script missingScript = scriptMap.get(destinationScriptID);
                        //LOG.info("Patching edge {} with dst: {}", script.id(), missingScript);
                        edge = edge.copyReplacing(missingScript);
                    }
                    // else the script was already available when we created the ResponseLogic from the ResultSet

                    script.next().add(edge);
                }
            }

        } catch (SQLException e) {
            LOG.error("getScript failed", e);
            return null;
        }

        // FIXME remove
        scriptMap.forEach((k, v) -> {
            LOG.info(v.toString());
        });

        Script initialScript = null;
        for (Script s : scriptMap.values()) {
            if (s.text().equals(keyword)) {
                initialScript = s;
                break;
            }
        }
        return initialScript;
    }

    @Override
    public Script getScript(UUID scriptId) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return getScript(connection, scriptId);
        } catch (SQLException e) {
            LOG.error("getScript: fetchConnection failed", e);
            return null;
        }
    }

    public Script getScript(Connection connection, UUID scriptId) {
        Map<UUID, Script> scriptMap = new HashMap<>(); // FIXME does the ordering matter?
        try (PreparedStatement ps = connection.prepareStatement(SELECT_SCRIPT_GRAPH_RECURSIVE)) {
            ps.setObject(1, scriptId);

            // s.id, s.created_at, s.text, s.type, s.label,
            // e.id, e.created_at, e.match_text, e.response_text, e.src, e.dst
            Map<UUID, SequencedSet<ResponseLogic>> tempEdges = new HashMap<>();
            Map<UUID, UUID> edgeIdToDstId = new HashMap<>();

            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {

                UUID id = (UUID) rs.getObject(1); // id

                Script script = scriptMap.get(id);
                if (script == null) {
                    // columnIndex 2, // createdAt
                    String text = rs.getString(3);  // text
                    ScriptType type = ScriptType.forValue(rs.getInt(4));  // type
                    String label = rs.getString(5); // label

                    script = new Script(id, text, type, null, label); // no next entries yet...
                    scriptMap.put(id, script);
                }

                UUID edgeId = (UUID) rs.getObject(6); // ResponseLogic.id
                // index 7, skip the createdAt
                List<String> matchText = Functions.parseMatchTextPatterns(rs.getString(8)); // matchText
                String responseText = rs.getString(9);
                UUID srcId = (UUID) rs.getObject(10);
                UUID dstId = (UUID) rs.getObject(11);

                ResponseLogic tempEdge = new ResponseLogic(
                        edgeId, responseText, matchText, scriptMap.get(dstId)
                ); // NB: the destination script may not exist so we will need to update/replace this edge at the end of the while loop
                edgeIdToDstId.put(edgeId, dstId);

                SequencedSet<ResponseLogic> edgesForParentScript = tempEdges.computeIfAbsent(srcId, k -> new LinkedHashSet<>());
                edgesForParentScript.add(tempEdge);
            }

            // Now patch all the references for both the edges and the scripts
            for (Map.Entry<UUID, Script> idAndScript : scriptMap.entrySet()) {
                SequencedSet<ResponseLogic> edgesForScript = tempEdges.get(idAndScript.getKey());
                Script script = idAndScript.getValue();
                for (ResponseLogic edge : edgesForScript) {
                    if (edge.script() == null) {
                        UUID destinationScriptID = edgeIdToDstId.get(edge.id());
                        Script missingScript = scriptMap.get(destinationScriptID);
                        //LOG.info("Patching edge {} with dst: {}", script.id(), missingScript);
                        edge = edge.copyReplacing(missingScript);
                    } else {
                        //LOG.info("Edge {} already points to {}", edge.id(), edge.script());
                    }
                    // else the script was already available when we created the ResponseLogic from the ResultSet

                    script.next().add(edge);
                }
            }

        } catch (SQLException e) {
            LOG.error("getScript failed", e);
            return null;
        }

        // FIXME remove
        scriptMap.forEach((k, v) -> {
            LOG.info(v.toString());
        });

        return scriptMap.get(scriptId);
    }

    @Override
    public User getUser(SessionKey sessionKey) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return getUser(connection, sessionKey);
        } catch (SQLException e) {
            LOG.error("getUser: fetchConnection failed", e);
            return null;
        }
    }

    public User getUser(Connection connection, SessionKey sessionKey) {
        try (PreparedStatement ps = connection.prepareStatement(USER_PROFILE_BY_PLATFORM_ID_AND_CODE)) {
            ps.setString(1, sessionKey.from()); // platform_id

            final ResultSet rs = ps.executeQuery();

            UUID id = UUID.randomUUID();
            String country = "US", nickName, surname, givenName;
            Map<Platform, String> platformMap = new HashMap<>();
            Map<Platform, Instant> platformCreatedMap = new HashMap<>();
            Set<String> languages = new LinkedHashSet<>();
            Set<String> otherLanguages = new LinkedHashSet<>();
            int rowCount = 0;

            while (rs.next()) {

                id = (UUID) rs.getObject(1); // u.group_id

                String platformId = rs.getString(2); // u.platform_id
                Platform platform = Platform.byCode(rs.getString(3)); // u.platform_code // throws IaE if null
                if (platformId == null) {
                    LOG.error("Platform id {} was null for SessionKey {}", platformId, sessionKey);
                    return null;
                }

                // Build up the map of IDs for each Platform.
                platformMap.put(platform, platformId);

                country = rs.getString(4);// u.country

                // Build up the list of languages selected by the User.
                languages.add(rs.getString(5)); // u.language

                nickName = rs.getString(6); // u.nickname // FIXME per platform, how to handle creation

                Instant createdAt = rs.getTimestamp(7).toInstant();
                platformCreatedMap.put(platform, createdAt);

                surname = rs.getString(7); // p.surname
                givenName = rs.getString(8); // p.given_name

                String otherLangStr = rs.getString(10); // p.other_languages
                if (otherLangStr != null) {
                    Collections.addAll(otherLanguages, otherLangStr.split(","));
                }

                ++rowCount;
            }

            LOG.info("Processed {} records for {}", rowCount, sessionKey);

            if (rowCount < 1) {
                LOG.info("No Users found for {}", sessionKey);
                return null; // FIXME should we insert a User at this point or leave it to the caller?
            }

            // Merge the set of languages, attempting to preserve the user's preferred order
            languages.addAll(otherLanguages);
            List<String> userLanguages = new ArrayList<>(languages);
            LOG.info("Languages for user: {}", languages);

            return new User(id, platformMap, platformCreatedMap, country, userLanguages);

        } catch (SQLException e) {
            LOG.error("getUser failed", e);
            return null;
        }
    }


    @Override
    public boolean insertUser(User user) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return insertUser(connection, user);
        } catch (SQLException e) {
            LOG.error("getUser: fetchConnection failed", e);
            return false;
        }
    }

    public boolean insertUser(Connection connection, User user) {
        int numOfPlatforms = user.platformIds().size();
        assert numOfPlatforms == 1;
        final Map.Entry<Platform, String> onlyPlatform = user.platformIds().entrySet().iterator().next();
        Instant createdAt = NanoClock.utcInstant();

        LOG.info("insertUser: insert {}", user);

        /*
         * Note that the platform_code, country_code and language columns are defined as enum types in DDL
         * which is why they have to be set as Objects on the PreparedStatement. Alternatively, we could
         * follow the type declaration with 'CREATE CAST (varchar AS platform) WITH INOUT AS IMPLICIT;'
         */
        try (PreparedStatement ps = connection.prepareStatement(USER_INSERT)) {
            // group_id | platform_id | platform_code | country | language | nickname | created_at
            ps.setObject(1, user.id()); // group_id
            ps.setString(2, onlyPlatform.getValue()); // platform_id
            ps.setObject(3, onlyPlatform.getKey().code(), OTHER); // platform_code
            ps.setObject(4, user.countryCode(), OTHER); // country
            ps.setObject(5, user.languages().getFirst(), OTHER); // language
            ps.setString(6, null); // nickname // FIXME move nickname to Profile?
            ps.setTimestamp(7, Timestamp.from(createdAt));

            int numInserted = ps.executeUpdate();
            LOG.info("insertUser: inserted {} record(s).", numInserted); // FIXME change to .debug
            return (1 == numInserted);

        } catch (SQLException e) {
            LOG.error("insertUser failed", e);
            return false;
        }

    }

    private void insertTest() throws InterruptedException {
//        final SessionKey sessionKey = new SessionKey(Platform.SMS, "17817299468", "12345");
//        LOG.info("Testing getUser with {}", sessionKey);
//        User u = getUser(sessionKey);
//        LOG.info("Returned {}", u);
//        if (u == null) {

//        Map<Platform, String> platformIds = new LinkedHashMap<>();
//        platformIds.put(Platform.SMS, "14162221234");
//        Map<Platform, Instant> platformCreatedAt = new LinkedHashMap<>();
//        platformCreatedAt.put(Platform.SMS, NanoClock.utcInstant());
//        User newUser = new User(UUID.randomUUID(), platformIds, platformCreatedAt, "CA", List.of("FRA"));
//        final boolean insertOk = insertUser(newUser);
//        LOG.info("Inserted {}", insertOk);

        final SessionKey sessionKey = new SessionKey(Platform.SMS, "14162221234", "12345", "ignore");
        LOG.info("Testing getUser with {}", sessionKey);
        User u = getUser(sessionKey);
        LOG.info("Returned {}", u);

//        }
        //    try {
        //        Connection c = fetchConnection(); // warm the pool
        //        c.close();
        //    } catch (SQLException e) {
        //        throw new RuntimeException(e);
        //    }
        //    final Message moMessage = Message.newMO("17817209468", "12345", "first message");
        //    final UUID uuid = moMessage.id();
        //    final boolean insertsOk = insertMO(moMessage);
        //    LOG.info("Messages inserted: {}", insertsOk);
        //    LOG.info("New Message id: {}", uuid);
//
        //    final Script script = new Script("blah", ScriptType.PresentMulti, null, "blahLabel");
        //    final Script previousScript = new Script("response to blah", ScriptType.PresentMulti, null, "blahResponseLabel");
        //    final User user = new User(UUID.randomUUID(), Map.of(Platform.SMS, "17815551234"), "MX", List.of("spa", "eng"));
        //    final InMemoryQueueProducer queueProducer = new InMemoryQueueProducer();
        //    Session session = new Session(UUID.randomUUID(), script, user, queueProducer, null);
        //    session.addEvaluated(previousScript);
        //    boolean procdMessageOk = insertProcessedMO(moMessage, session);
        //    LOG.info("processedMessage inserted: {}", procdMessageOk);
        //
        //    final Message mtMessage = Message.newMO("12345", "17817209468", "first response");
        //    final boolean mtInsertOk = insertMT(mtMessage, session);
        //    LOG.info("MT inserted: {}", mtInsertOk);
        //
        //    boolean dlvrMtOk = insertDeliveredMT(mtMessage);
        //    LOG.info("deliveredMessage inserted: {}", dlvrMtOk);
        //
        //    return uuid;
    }

    public static void main(String[] args) throws InterruptedException, IOException, PersistenceManagerException {
        PostgresPersistenceManager pm = new PostgresPersistenceManager(ConfigLoader.readConfig("persistence_manager_test.properties"));

        Script script = pm.getScript(UUID.fromString("89eddcb8-7fe5-4cd1-b18b-78858f0789fb"));
        System.out.println("\n\n\n\n\n\n");
        Script.printGraph(script, script, 0);

//      Script script2 = pm.getScript(UUID.fromString("525028ae-0a33-4c80-a22f-868f77bb9531"));

//        LOG.info("Calling getKeywords...");
//        final Map<String, Keyword> keywords = pm.getKeywords();
//        keywords.forEach( (s, keyword) -> {
//            System.out.println(keyword);
//        });
    }

}
