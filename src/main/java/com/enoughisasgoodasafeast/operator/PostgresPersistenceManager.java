package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.Message;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyVetoException;
import java.io.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

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

    public static final String MO_MESSAGE_INSERT =
            """
                    INSERT INTO brbl_logs.messages_mo
                        (id, rcvd_at, _from, _to, _text)
                    VALUES
                        (?, ?, ?, ?, ?);
                    """;

    public static final String MO_MESSAGE_PRCD =
            """
                    INSERT INTO brbl_logs.messages_mo_prcd
                        (id, prcd_at, session_id, script_id)
                    VALUES
                        (?, ?, ?, ?);
                    """;

    public static final String MT_MESSAGE_INSERT =
            """
                    INSERT INTO brbl_logs.messages_mt
                        (id, sent_at, _from, _to, _text, session_id, script_id)
                    VALUES
                        (?, ?, ?, ?, ?, ?, ?);
                    """;

    public static final String MT_MESSAGE_DLVR =
            """
                    INSERT INTO brbl_logs.messages_mt_dlvr
                        (id, dlvr_at)
                    VALUES
                        (?, ?);
                    """;

    public static final String USER_PROFILE_BY_PLATFORM_ID =
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

    public static final String USER_PROFILE_BY_PLATFORM_ID_ROUTE =
            """
                    SELECT
                    	u.group_id,
                    	u.platform_id,
                    	u.platform_code,
                    	u.country,
                    	u.language,
                    	u.nickname,
                    	u.created_at,
                    	p.surname,
                    	p.given_name,
                    	p.other_languages,
                    	p.created_at,
                    	p.updated_at,
                    	r.customer_id
                    FROM
                        brbl_users.users u
                    LEFT JOIN
                        brbl_users.profiles p
                    ON
                        u.group_id = p.group_id
                    LEFT JOIN
                        brbl_logic.routes r
                    ON
                        r.customer_id = u.customer_id
                    WHERE
                        r.platform = ?::public.platform
                        AND
                        r.channel = ?
                        AND
                    	u.group_id = (
                    		SELECT group_id
                    		FROM brbl_users.users
                    		WHERE platform_id = ?
                    	)
                    """;

    public static final String USER_INSERT =
            """
                    INSERT INTO brbl_users.users
                        (group_id, platform_id, platform_code, country, language, nickname, created_at, customer_id)
                    VALUES
                        (?, ?, ?, ?, ?, ?, ?, ?)
                    """;

    public static final String PROFILE_INSERT =
            """
                    INSERT INTO brbl_users.profiles
                        (group_id, surname, given_name, other_languages, created_at)
                    VALUES
                        (?, ?, ?, ?, ?)
                    """;

    public static final String SESSION_UPSERT =
            """
                    INSERT INTO brbl_logic.sessions
                        (group_id, data, created_at, updated_at)
                    VALUES
                        (?, ?, ?, ?)
                    ON CONFLICT(group_id)
                    DO UPDATE SET
                        data = EXCLUDED.data,
                        updated_at = EXCLUDED.updated_at
                    """;

    public static final String SELECT_SESSION =
            """
                    SELECT
                        s.group_id, s.data, s.created_at, s.updated_at
                    FROM
                        brbl_logic.sessions s
                    WHERE
                        s.group_id = ?;
                    """;

    public static final String SELECT_SCRIPT_GRAPH =
            """
                    SELECT
                        s.id, s.label, s.text,
                        e.match_text, e.response_text, e.dst
                    FROM
                        brbl_logic.nodes s
                    INNER JOIN
                        brbl_logic.edges e ON s.id = e.src
                    WHERE
                        s.id = ?;
                    """;

//    public static final String SELECT_SCRIPT_GRAPH_FULL =
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
//                        brbl_logic.nodes s
//                    INNER JOIN
//                        brbl_logic.edges e ON s.id = e.src
//                        WHERE
//                        s.id IN (SELECT DISTINCT(cte.script_id) FROM cte);
//                    """;

    public static final String SELECT_SCRIPT_GRAPH_RECURSIVE =
            """
                    WITH RECURSIVE rgraph AS (
                            SELECT ? AS node_id
                            UNION
                            SELECT
                                e.dst
                            FROM
                                brbl_logic.edges AS e
                            JOIN
                                rgraph ON rgraph.node_id = e.src
                    
                    ) CYCLE node_id SET is_cycle USING path
                    SELECT
                        s.id, s.created_at, s.text, s.type, s.label,
                        e.id, e.created_at, e.match_text, e.response_text, e.src, e.dst
                    FROM
                        brbl_logic.nodes s
                    INNER JOIN
                        brbl_logic.edges e ON s.id = e.src
                    INNER JOIN
                        rgraph ON s.id = rgraph.node_id
                    WHERE
                        s.id = rgraph.node_id
                        AND
                        rgraph.is_cycle IS FALSE
                        ORDER BY s.id ;
                    """; // FIXME redundant to include 's.id = rgraph.node_id' in the where clause when its already an inner join.

    public static final String SELECT_SCRIPT_GRAPH_RECURSIVE_FOR_KEYWORD =
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
                        brbl_logic.nodes s
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

    public static final String SELECT_ALL_KEYWORDS =
            """
                    SELECT
                        k.id, k.pattern, k.platform, k.script_id, k.channel
                    FROM
                        brbl_logic.keywords k ;
                    """;

    public static final String SELECT_ALL_ROUTES_WITH_STATUS =
            """
                    SELECT
                        r.id,
                        r.platform,
                        r.channel,
                        r.default_node_id,
                        r.customer_id,
                        r.status,
                        r.created_at,
                        r.updated_at
                    FROM
                        brbl_logic.routes r
                    WHERE
                        r.status = ?::route_status
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
            ps.setObject(1, message.id());                              // id
            ps.setTimestamp(2, Timestamp.from(Instant.now()));          // prcd_at
            ps.setObject(3, session.getId());                           // session_id
            ps.setObject(4, session.getScriptForProcessedMO().id());    // script_id
            ps.execute();
        } catch (SQLException e) {
            LOG.error("insertProcessedMO failed", e);
            return false;
        }
        /*Instant after = NanoClock.utcInstant();
         *LOG.info("insertProcessedMO: b {} a {}: d {} ", before, after, Duration.between(before, after)); */
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
            ps.setObject(6, session.getId());                           // session_id
            ps.setObject(7, session.previousScript().id());    // script_id
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

    public boolean saveSession(Session session) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return saveSession(connection, session);
        } catch (SQLException e) {
            LOG.error("upsertSession: fetchConnection failed", e);
            return false;
        }
    }

    private boolean saveSession(Connection connection, Session session) {
        try (PreparedStatement ps = connection.prepareStatement(SESSION_UPSERT)) {
            ps.setObject(1, session.getId());
            ps.setBytes(2, sessionToBytes(session));
            ps.setTimestamp(3, Timestamp.from(session.getStartTimeNanos()));
            ps.setTimestamp(4, Timestamp.from(session.getLastUpdatedNanos()));
            ps.execute();
            return true;
        } catch (SQLException | IOException e) {
            LOG.error(e.getMessage(), e);
            return false;
        }
    }

    public Session loadSession(UUID id) {
        LOG.info("Fetching session data for {}", id);
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return loadSession(connection, id);
        } catch (SQLException e) {
            LOG.error("getSession: fetchConnection failed", e);
            return null;
        }
    }

    private Session loadSession(Connection connection, UUID id) {
        LOG.info("Retrieving session {}", id);
        try (PreparedStatement ps = connection.prepareStatement(SELECT_SESSION)) {
            ps.setObject(1, id);
            final ResultSet rs = ps.executeQuery();
            final boolean next = rs.next();
            if (next) {
                final byte[] data = rs.getBytes(2);
                return bytesToSession(data);
            } else {
                LOG.error("Session {} not found.", id);
                return null;
            }
        } catch (Exception e) {
            LOG.error("Error retrieving Session object for {}", id, e);
            return null;
        }
    }

    private byte[] sessionToBytes(Session session) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(session);
            return baos.toByteArray();
        }
    }

    private Session bytesToSession(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (Session) ois.readObject();
        } /*catch (IOException | ClassNotFoundException e) {
            LOG.error("Exception in bytesToSession", e);
            return null;
        }*/
    }


    @Override
    public Map<Pattern, Keyword> getKeywords() {
        LOG.info("(Re)Loading keyword cache...");
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return getKeywords(connection);
        } catch (SQLException e) {
            LOG.error("getKeywords: fetchConnection failed", e);
            return null;
        }
    }

    public Map<Pattern, Keyword> getKeywords(Connection connection) {
        Map<Pattern, Keyword> allKeywordMap = new HashMap<>();
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
                // k.channel
                String channel = rs.getString(5);

                Keyword keyword = new Keyword(id, pattern.trim(), platform, scriptId, channel);

                Pattern compiledPattern = Pattern.compile(pattern);

                allKeywordMap.put(compiledPattern, keyword); // word may be a regex
            }

        } catch (SQLException e) {
            LOG.error("getKeywords failed.");
            throw new RuntimeException(e);
        }

        return allKeywordMap;
    }


    @Override
    public Route[] getActiveRoutes() {
        LOG.info("(Re)Loading route cache...");
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return getActiveRoutes(connection);
        } catch (SQLException e) {
            LOG.error("getActiveRoutes: fetchConnection failed", e);
            return null;
        }
    }

    public Route[] getActiveRoutes(Connection connection) {
        List<Route> allRoutes = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_ALL_ROUTES_WITH_STATUS)) {
            ps.setObject(1, "ACTIVE");
            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                // r.id
                UUID id = (UUID) rs.getObject(1);
                // r.platform
                Platform platform = Platform.byCode(rs.getString(2));
                // r.channel
                String channel = rs.getString(3);
                // r.default_node_id
                UUID nodeId = (UUID) rs.getObject(4);
                // r.customer_id
                UUID customerId = (UUID) rs.getObject(5);
                // r.status
                RouteStatus status = RouteStatus.valueOf(rs.getString(6)); //(RouteStatus) rs.getObject(6);
                // r.created_at
                Instant createdAt = rs.getTimestamp(7).toInstant();
                // r.updated_at
                Instant updatedAt = rs.getTimestamp(8).toInstant();

                Route route = new Route(id, platform, channel, nodeId, customerId, status, createdAt, updatedAt);
                allRoutes.add(route);
            }
            return allRoutes.toArray(new Route[0]);
        }

        catch (SQLException e) {
            LOG.error("getActiveRoutes failed.");
            throw new RuntimeException(e);
        }
    }

//        @Override
//    public Node getScriptForKeyword(Platform platform, String keyword) {
//        try (Connection connection = fetchConnection()) {
//            assert connection != null;
//            return getScriptForKeyword(connection, platform, keyword);
//        } catch (SQLException e) {
//            LOG.error("getScript: fetchConnection failed", e);
//            return null;
//        }
//    }

//    public Node getScriptForKeyword(Connection connection, Platform platform, String keyword) {
//        Map<UUID, Node> scriptMap = new HashMap<>(); // FIXME does the ordering matter?
//        try (PreparedStatement ps = connection.prepareStatement(SELECT_SCRIPT_GRAPH_RECURSIVE_FOR_KEYWORD)) {
////            ps.setObject(1, platform);
//            ps.setString(1, platform.code());
//            ps.setString(2, keyword.trim());
//
//            // s.id, s.created_at, s.text, s.type, s.label,
//            // e.id, e.created_at, e.match_text, e.response_text, e.src, e.dst
//            Map<UUID, SequencedSet<Edge>> tempEdges = new HashMap<>();
//            Map<UUID, UUID> edgeIdToDstId = new HashMap<>();
//
//            final ResultSet rs = ps.executeQuery();
//            while (rs.next()) {
//
//                UUID id = (UUID) rs.getObject(1); // id
//
//                Node node = scriptMap.get(id);
//                if (node == null) {
//                    // columnIndex 2, // createdAt
//                    String text = rs.getString(3);  // text
//                    NodeType type = NodeType.forValue(rs.getInt(4));  // type
//                    String label = rs.getString(5); // label
//
//                    node = new Node(id, text, type, null, label); // no next entries yet...
//                    scriptMap.put(id, node);
//                }
//
//                UUID edgeId = (UUID) rs.getObject(6); // Edge.id
//                // index 7, skip the createdAt
//                List<String> matchText = Functions.parseMatchTextPatterns(rs.getString(8)); // matchText
//                String responseText = rs.getString(9);
//                UUID srcId = (UUID) rs.getObject(10);
//                UUID dstId = (UUID) rs.getObject(11);
//
//                Edge tempEdge = new Edge(
//                        edgeId, responseText, matchText, scriptMap.get(dstId)
//                ); // NB: the destination node may not exist so we will need to update/replace this edge at the end of the while loop
//                edgeIdToDstId.put(edgeId, dstId);
//
//                SequencedSet<Edge> edgesForParentScript = tempEdges.computeIfAbsent(srcId, k -> new LinkedHashSet<>());
//                edgesForParentScript.add(tempEdge);
//            }
//
//            // Now patch all the references for both the edges and the scripts
//            for (Map.Entry<UUID, Node> idAndScript : scriptMap.entrySet()) {
//                SequencedSet<Edge> edgesForScript = tempEdges.get(idAndScript.getKey());
//                Node node = idAndScript.getValue();
//                for (Edge edge : edgesForScript) {
//                    if (edge.targetNode() == null) {
//                        UUID destinationScriptID = edgeIdToDstId.get(edge.id());
//                        Node missingNode = scriptMap.get(destinationScriptID);
//                        //LOG.info("Patching edge {} with dst: {}", node.id(), missingNode);
//                        edge = edge.copyReplacing(missingNode);
//                    }
//                    // else the node was already available when we created the Edge from the ResultSet
//
//                    node.edges().add(edge);
//                }
//            }
//
//        } catch (SQLException e) {
//            LOG.error("getScript failed", e);
//            return null;
//        }
//
//        // FIXME remove
//        scriptMap.forEach((k, v) -> {
//            LOG.info(v.toString());
//        });
//
//        Node initialNode = null;
//        for (Node s : scriptMap.values()) {
//            if (s.text().equals(keyword)) {
//                initialNode = s;
//                break;
//            }
//        }
//        return initialNode;
//    }

    @Override
    public Node getScript(UUID scriptId) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return getScript(connection, scriptId);
        } catch (SQLException e) {
            LOG.error("getScript: fetchConnection failed", e);
            return null;
        }
    }

    public Node getScript(Connection connection, UUID nodeId) {
        Map<UUID, Node> scriptMap = new HashMap<>(); // FIXME does the ordering matter?
        try (PreparedStatement ps = connection.prepareStatement(SELECT_SCRIPT_GRAPH_RECURSIVE)) {
            ps.setObject(1, nodeId);

            // s.id, s.created_at, s.text, s.type, s.label,
            // e.id, e.created_at, e.match_text, e.response_text, e.src, e.dst
            Map<UUID, SequencedSet<Edge>> tempEdges = new HashMap<>();
            Map<UUID, UUID> edgeIdToDstId = new HashMap<>();

            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {

                UUID id = (UUID) rs.getObject(1); // id

                Node node = scriptMap.get(id);

                if (node == null) {
                    // columnIndex 2, // createdAt
                    String text = rs.getString(3);  // text
                    NodeType type = NodeType.forValue(rs.getInt(4));  // type
                    String label = rs.getString(5); // label

                    node = new Node(id, text, type, null, label); // no next entries yet...
                    scriptMap.put(id, node);
                }

                UUID edgeId = (UUID) rs.getObject(6); // Edge.id
                // index 7, skip the createdAt
                List<String> matchText = Functions.parseMatchTextPatterns(rs.getString(8)); // matchText
                String responseText = rs.getString(9);
                UUID srcId = (UUID) rs.getObject(10);
                UUID dstId = (UUID) rs.getObject(11);

                Edge tempEdge = new Edge(
                        edgeId, responseText, matchText, scriptMap.get(dstId)
                ); // NB: the destination node may not exist so we will need to update/replace this edge at the end of the while loop
                edgeIdToDstId.put(edgeId, dstId);

                SequencedSet<Edge> edgesForParentScript = tempEdges.computeIfAbsent(srcId, _ -> new LinkedHashSet<>());
                edgesForParentScript.add(tempEdge);
            }

            // Now patch all the references for both the edges and the scripts
            for (Map.Entry<UUID, Node> idAndScript : scriptMap.entrySet()) {
                SequencedSet<Edge> edgesForScript = tempEdges.get(idAndScript.getKey());
                Node node = idAndScript.getValue();
                for (Edge edge : edgesForScript) {
                    if (edge.targetNode() == null) {
                        UUID destinationScriptID = edgeIdToDstId.get(edge.id());
                        Node missingNode = scriptMap.get(destinationScriptID);
                        //LOG.info("Patching edge {} with dst: {}", node.id(), missingNode);
                        edge = edge.copyReplacing(missingNode);
                    } //else {
                        //LOG.info("Edge {} already points to {}", edge.id(), edge.node());
                    //}
                    // else the node was already available when we created the Edge from the ResultSet

                    node.edges().add(edge);
                }
            }

        } catch (SQLException e) {
            LOG.error("getScript failed", e);
            return null;
        }

        return scriptMap.get(nodeId);
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
        try (PreparedStatement ps = connection.prepareStatement(USER_PROFILE_BY_PLATFORM_ID_ROUTE)) {

            ps.setString(1, sessionKey.platform().code()); // platform
            ps.setString(2, sessionKey.to()); // channel
            ps.setString(3, sessionKey.from()); // platform_id

            final ResultSet rs = ps.executeQuery();

            UUID id = null; //UUID.randomUUID();
            String country = "US";
            String nickName;
            Map<Platform, String> platformMap = new HashMap<>();
            Map<Platform, Instant> platformCreatedMap = new HashMap<>();
            Map<Platform, String> platformNickNames = new HashMap<>();
            Set<String> languages = new LinkedHashSet<>();
            String profileSurname = null, profileGivenName = null;
            Instant profileCreated = null, profileUpdated = null;
            UUID customerId = null;

            Profile optionalProfile = null;

            int rowCount = 0;

            String profileLanguages = null;
            while (rs.next()) {

                id = (UUID) rs.getObject(1); // u.group_id

                String platformId = rs.getString(2); // u.platform_id
                Platform platform = Platform.byCode(rs.getString(3)); // u.platform_code // throws IaE if null

                // Build up the map of IDs for each Platform.
                platformMap.put(platform, platformId);

                country = rs.getString(4); // u.country

                // Build up the list of languages selected by the User.
                languages.add(rs.getString(5)); // u.language

                nickName = rs.getString(6); // u.nickname
                platformNickNames.put(platform, nickName); // NB trying out an Optional here

                Instant createdAt = rs.getTimestamp(7).toInstant();
                platformCreatedMap.put(platform, createdAt);

                // TODO Add these to an optional Profile
                profileSurname = rs.getString(8); // p.surname
                profileGivenName = rs.getString(9); // p.given_name

                profileLanguages = rs.getString(10); // p.other_languages

                profileCreated = rs.getTimestamp(11).toInstant();
                profileUpdated = rs.getTimestamp(12).toInstant();

                customerId = (UUID) rs.getObject(13);

                ++rowCount;
            }

            LOG.info("Processed {} records for {}", rowCount, sessionKey);

            if (rowCount < 1) {
                LOG.info("No Users found for {}", sessionKey);
                return null; // FIXME should we insert a User at this point or leave it to the caller?
            }

            // FIXME Not sure it makes sense to merge these now that we're attaching a Profile object.
            // Merge the set of languages, attempting to preserve the user's preferred order
            // languages.addAll(otherLanguages);
            List<String> userLanguages = new ArrayList<>(languages);
            LOG.info("Languages for user: {}", languages);

            // Use just the name fields as sentinels to detect enough data to create a Profile record
            if (profileGivenName != null || profileSurname != null) {
                optionalProfile = new Profile(id, profileSurname, profileGivenName, profileLanguages, profileCreated, profileUpdated);
            }

            return new User(id, platformMap, platformCreatedMap, country, userLanguages, customerId, platformNickNames, optionalProfile);

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

    private boolean insertUser(Connection connection, User user) {
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
            ps.setObject(6, user.platformNickNames().get(onlyPlatform.getKey()));
            ps.setTimestamp(7, Timestamp.from(createdAt));
            ps.setObject(8, user.customerId());

            int numInserted = ps.executeUpdate();
            LOG.info("insertUser: inserted {} record(s).", numInserted); // FIXME change to .debug
            return (1 == numInserted);

        } catch (SQLException e) {
            LOG.error("insertUser failed", e);
            return false;
        }

    }

    static void main(String[] args) throws /*InterruptedException,*/ IOException, PersistenceManagerException {
        PostgresPersistenceManager pm = new PostgresPersistenceManager(ConfigLoader.readConfig("persistence_manager_test.properties"));
        //var routes = pm.getActiveRoutes();
        //for (var route : routes) {
        //    LOG.info(route.toString());
        //}

        final Map<Pattern, Keyword> keywords = pm.getKeywords();
        keywords.forEach((key, value) -> LOG.info("{} -> {}", key, value.wordPattern()));
    }

}
