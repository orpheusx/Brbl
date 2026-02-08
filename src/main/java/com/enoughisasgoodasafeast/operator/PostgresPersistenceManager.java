package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.Message;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyVetoException;
import java.io.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import static com.enoughisasgoodasafeast.operator.SessionSerde.bytesToSession;
import static com.enoughisasgoodasafeast.operator.SessionSerde.sessionToBytes;
import static io.jenetics.util.NanoClock.utcInstant;
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
        try (Connection _ = fetchConnection()) {
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

//    public static final String USER_PROFILE_BY_PLATFORM_ID =
//            """
//                    SELECT
//                    	u.group_id, u.platform_id, u.platform_code,
//                    	u.country, u.language, u.nickname, u.created_at,
//                    	p.surname, p.given_name, p.other_languages
//                    FROM
//                        brbl_users.users u
//                    LEFT JOIN
//                        brbl_users.profiles p
//                    ON
//                        u.group_id = p.group_id
//                    WHERE
//                    	u.group_id = (
//                    		SELECT group_id
//                    		FROM brbl_users.users
//                    		WHERE platform_id = ?
//                    	)
//                    """; // FIXME figure out if a LATERAL JOIN might replace the sub-select part of this query

//    public static final String USER_PROFILE_BY_PLATFORM_ID_ROUTE =
//            """
//                    SELECT
//                        u.id,
//                    	u.group_id,
//                    	u.platform_id,
//                    	u.platform_code,
//                    	u.country,
//                    	u.language,
//                    	u.nickname,
//                    	u.created_at,
//                    	p.surname,
//                    	p.given_name,
//                    	p.other_languages as profile_other_languages,
//                    	p.created_at as profile_created_at,
//                    	p.updated_at as profile_updated_at,
//                    	r.customer_id as owner_customer_id
//                    FROM
//                        brbl_users.users u
//                    LEFT JOIN
//                        brbl_users.profiles p
//                        ON u.group_id = p.group_id
//                    LEFT JOIN
//                        brbl_logic.routes r
//                        ON r.customer_id = u.customer_id
//                    WHERE
//                        r.platform = ?::public.platform
//                        AND
//                        r.channel = ?
//                        AND
//                    	u.group_id = (
//                    		SELECT group_id
//                    		FROM brbl_users.users
//                    		WHERE platform_id = ?
//                    	)
//                    """;

    // Unclear why we're not including users.updated_at
    public static final String USER_PROFILE_BY_PLATFORM_ID_ROUTE = """
            SELECT
                u.id,
                a.group_id,
                u.platform_id,
                u.platform_code,
                u.country,
                u.language,
                u.nickname,
                u.created_at,
                p.surname,
                p.given_name,
                p.other_languages as profile_other_languages,
                p.created_at      as profile_created_at,
                p.updated_at      as profile_updated_at,
                a.customer_id     as owner_customer_id,
                u.status
            FROM
                amalgams a
            INNER JOIN users u
                ON a.user_id = u.id
            LEFT JOIN profiles p
                ON p.id = a.profile_id
            WHERE
               a.group_id =
            (
                SELECT
                    a.group_id as gid
                FROM
                    amalgams a
                INNER JOIN users u
                    ON a.user_id = u.id
                INNER JOIN routes r
                    ON r.customer_id = a.customer_id
                WHERE
                    u.platform_code = ?::public.platform
                    AND r.channel = ?
                    AND u.platform_id = ?
            )
            """;

//    public static final String USER_INSERT =
//            """
//                    INSERT INTO brbl_users.users
//                        (id, group_id, platform_id, platform_code, country, language, nickname, created_at, customer_id)
//                    VALUES
//                        (?, ?, ?, ?, ?, ?, ?, ?, ?)
//                    """;

    public static final String USER_AMALGAM_INSERT =
            """
                    WITH new_user_cte AS (
                            INSERT INTO brbl_users.users
                                (id, status, platform_id, platform_code,
                                 country, language, nickname, created_at, updated_at)
                            VALUES
                                (?::UUID, ?::user_status, ?, ?, ?, ?, ?, ?, ?)
                            RETURNING
                                id AS nuc_id, created_at AS nuc_created_at
                    )
                    INSERT INTO brbl_users.amalgams
                        (group_id, user_id, profile_id, customer_id, created_at, updated_at)
                    SELECT
                        ?::UUID, nuc_id, ?::UUID, ?::UUID, nuc_created_at, nuc_created_at
                    FROM
                        new_user_cte
                    """; // 9 params in cte. Just group_id, profile_id & customer_id in amalgams insert.

//    public static final String PROFILE_INSERT =
//            """
//                    INSERT INTO brbl_users.profiles
//                        (group_id, surname, given_name, other_languages, created_at)
//                    VALUES
//                        (?, ?, ?, ?, ?)
//                    """;

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

    public static final String SESSION_SELECT =
            """
                    SELECT
                        s.group_id, s.data, s.created_at, s.updated_at
                    FROM
                        brbl_logic.sessions s
                    WHERE
                        s.group_id = ?;
                    """;

//    public static final String SELECT_SCRIPT_GRAPH =
//            """
//                    SELECT
//                        s.id, s.label, s.text,
//                        e.match_text, e.response_text, e.dst
//                    FROM
//                        brbl_logic.nodes s
//                    INNER JOIN
//                        brbl_logic.edges e ON s.id = e.src
//                    WHERE
//                        s.id = ?;
//                    """;

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

//     public static final String SELECT_SCRIPT_GRAPH_RECURSIVE_FOR_KEYWORD =
//             """
//                     WITH RECURSIVE rgraph AS (
//                             SELECT script_id FROM brbl_logic.keywords WHERE platform= ?::platform AND pattern = ?
//                         UNION ALL
//                         SELECT e.dst
//                         FROM brbl_logic.edges AS e
//                         JOIN rgraph ON
//                             rgraph.script_id = e.src
//                     ) CYCLE script_id SET is_cycle USING path
//                     SELECT
//                         s.id, s.created_at, s.text, s.type, s.label,
//                         e.id, e.created_at, e.match_text, e.response_text, e.src, e.dst
//                     FROM
//                         brbl_logic.nodes s
//                     INNER JOIN
//                         brbl_logic.edges e ON s.id = e.src
//                     INNER JOIN
//                         rgraph ON s.id = rgraph.script_id
//                     WHERE
//                         s.id = rgraph.script_id
//                         AND
//                         rgraph.is_cycle IS FALSE
//                         ORDER BY s.id ;
//                     """;

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

    public static final String SELECT_PUSH_CAMPAIGN_USERS_BY_DELIVERY_STATUS =
            """
                    WITH campaign_group_ids AS (
                        SELECT
                            DISTINCT a.group_id,
                            pc.customer_id
                        FROM
                            brbl_users.amalgams a
                        INNER JOIN
                            brbl_logic.campaign_users cu
                                ON cu.user_id = a.user_id
                        INNER JOIN
                            brbl_logic.push_campaigns pc
                                ON pc.id = cu.campaign_id
                        WHERE
                            pc.id = ?
                            AND
                            cu.delivered = ?
                    )
                    SELECT
                        a.group_id,
                        a.user_id AS user_id, 
                        a.profile_id AS profile_id,
                        u.country, 
                        u.created_at AS user_created_at,                          
                        u.language, 
                        u.nickname, 
                        u.platform_code, 
                        u.platform_id, 
                        u.status,                        
                        p.given_name, 
                        p.other_languages, 
                        p.surname, 
                        p.created_at AS profile_created_at, 
                        p.updated_at AS profile_updated_at,
                        cgi.customer_id,
                        s.updated_at AS session_updated_at,
                        cu_all.delivered
                    FROM
                        brbl_users.amalgams a
                    INNER JOIN
                        brbl_users.users u 
                            ON u.id = a.user_id
                    INNER JOIN
                        campaign_group_ids cgi 
                            ON a.group_id = cgi.group_id
                    LEFT JOIN
                        brbl_users.profiles p 
                            ON p.id = a.profile_id
                    LEFT JOIN
                        brbl_logic.sessions s 
                            ON s.group_id = a.group_id
                    LEFT JOIN
                        brbl_logic.campaign_users cu_all 
                            ON cu_all.user_id = u.id
                    LEFT JOIN
                        brbl_logic.push_campaigns pc_all 
                            ON pc_all.id = cu_all.campaign_id
                    ORDER BY
                        a.group_id, u.id
                    """;

    public static final String SELECT_PUSH_CAMPAIGN =
            """
                    SELECT
                        pc.id,
                        pc.customer_id,
                        pc.description,
                        pc.script_id,
                        pc.created_at,
                        pc.updated_at,
                        pc.completed_at,
                        c.status,
                        s.status,
                        n.id,
                        r.channel,
                        r.platform,
                        r.status
                    FROM
                        brbl_logic.push_campaigns pc
                    INNER JOIN
                        brbl_users.customers c ON c.id = pc.customer_id
                    INNER JOIN
                        brbl_logic.scripts s ON s.id = pc.script_id
                    INNER JOIN
                        brbl_logic.nodes n ON n.id = s.node_id
                    INNER JOIN
                        brbl_logic.routes r ON r.id = pc.route_id
                    WHERE
                        pc.id = ?
                    """;

    private Connection fetchConnection() throws SQLException {
        //Instant before = utcInstant();
        return pds.getConnection();
        //Instant after = utcInstant();
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
        //Instant before = utcInstant();
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

        //Instant after = utcInstant();
        //LOG.info("insertMO: b {} a {}: d {} ", before, after, Duration.between(before, after));
        return true;
    }

    // Batch mode is definitely slower for single element lists of Messages
    //    private static boolean insertMO(Connection connection, List<Message> messages) {
    //        //Instant before = utcInstant();
    //        try (PreparedStatement ps = connection.prepareStatement(MO_MESSAGE_INSERT)) {
    //            for (Message message : messages) {
    //                Timestamp timestampFromInstant = Timestamp.from(message.receivedAt());
    //                ps.setObject(1, message.id());
    //                ps.setTimestamp(2, timestampFromInstant);
    //                ps.setString(3, message.from());
    //                ps.setString(4, message.to());
    //                ps.setString(5, message.text());
    //
    //                ps.addBatch();
    //            }
    //
    //            ps.executeBatch();
    //
    //        } catch (SQLException e) {
    //            LOG.error("insertMOs failed", e);
    //            return false;
    //        }
    //
    //        return true;
    //    }

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
        //Instant before = utcInstant();
        try (PreparedStatement ps = connection.prepareStatement(MO_MESSAGE_PRCD)) {
            ps.setObject(1, message.id());                              // id
            ps.setTimestamp(2, Timestamp.from(utcInstant()));          // prcd_at
            ps.setObject(3, session.getId());                           // session_id
            ps.setObject(4, session.getScriptForProcessedMO().id());    // script_id
            ps.execute();
        } catch (SQLException e) {
            LOG.error("insertProcessedMO failed", e);
            return false;
        }
        /*Instant after = utcInstant();
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
        //Instant before = utcInstant();
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
        //Instant after = utcInstant();
        //LOG.info("insertMT: b {} a {}: d {} ", before, after, Duration.between(before, after));
        return true;
    }

    // As with MOs, batch mode is definitely slower for single element lists of Messages
    //    private static boolean insertMTs(Connection connection, List<Message> messages, Session session) {
    //        Instant before = utcInstant();
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
    //        Instant after = utcInstant();
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
        //Instant before = utcInstant();
        try (PreparedStatement ps = connection.prepareStatement(MT_MESSAGE_DLVR)) {
            ps.setObject(1, message.id());                              // id
            ps.setTimestamp(2, Timestamp.from(utcInstant())); // dlvr_at

            ps.execute();

        } catch (SQLException e) {
            LOG.error("insertDeliveredMTs failed", e);
            return false;
        }

        //Instant after = utcInstant();
        //LOG.info("insertDeliveredMT: b {} a {}: d {} ", before, after, Duration.between(before, after));
        return true;
    }

    public void saveSession(Session session) throws PersistenceManagerException {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            saveSession(connection, session);
        } catch (SQLException e) {
            LOG.error("saveSession: fetchConnection failed", e);
            throw new PersistenceManagerException(e);
        }
    }

    private void saveSession(Connection connection, Session session) throws PersistenceManagerException {
        try (PreparedStatement ps = connection.prepareStatement(SESSION_UPSERT)) {
            ps.setObject(1, session.getId());
            ps.setBytes(2, sessionToBytes(session));
            ps.setTimestamp(3, Timestamp.from(session.getStartTimeNanos()));
            ps.setTimestamp(4, Timestamp.from(session.getLastUpdatedNanos()));
            ps.execute();
        } catch (SQLException | IOException e) {
            LOG.error(e.getMessage(), e);
            throw new PersistenceManagerException(e);
        }
    }

    public @Nullable Session loadSession(@NonNull UUID id) throws PersistenceManagerException {
        LOG.info("Fetching session data for {}", id);
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return loadSession(connection, id);
        } catch (SQLException e) {
            LOG.error("loadSession: fetchConnection failed", e);
            throw new PersistenceManagerException(e);
        }
    }

    private @Nullable Session loadSession(@NonNull Connection connection, @NonNull UUID id) throws PersistenceManagerException {
        LOG.info("Retrieving session {}", id);
        try (PreparedStatement ps = connection.prepareStatement(SESSION_SELECT)) {
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
        } catch (SQLException | IOException | ClassNotFoundException e) {
            throw new PersistenceManagerException(e);
        }
    }

    @Override
    public @Nullable Map<Pattern, Keyword> getKeywords() {
        LOG.info("(Re)Loading keyword cache...");
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return getKeywords(connection);
        } catch (SQLException e) {
            LOG.error("getKeywords: fetchConnection failed", e);
            return null;
        }
    }

    public @Nullable Map<Pattern, Keyword> getKeywords(@NonNull Connection connection) {
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

        if (allKeywordMap.isEmpty()) {
            LOG.error("No keywords found!");
            return null;
        } else {
            return allKeywordMap;
        }
    }


    @Override
    public @Nullable Route[] getActiveRoutes() {
        LOG.info("(Re)Loading route cache...");
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return getActiveRoutes(connection);
        } catch (SQLException e) {
            LOG.error("getActiveRoutes: fetchConnection failed", e);
            return null;
        }
    }

    public @Nullable Route[] getActiveRoutes(@NonNull Connection connection) {
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
            if (allRoutes.isEmpty()) {
                return null;
            } else {
                return allRoutes.toArray(new Route[0]);
            }
        } catch (SQLException e) {
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
//            ps.setObject(1, platform);
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
    public @Nullable Node getNodeGraph(@NonNull UUID scriptId) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return getNodeGraph(connection, scriptId);
        } catch (SQLException e) {
            LOG.error("getScript: fetchConnection failed", e);
            return null;
        }
    }

    public @Nullable Node getNodeGraph(@NonNull Connection connection, @NonNull UUID nodeId) {
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
        LOG.info("scriptMap size: {} for id {}", scriptMap.size(), nodeId);
        return scriptMap.get(nodeId);
    }

    @Override
    public @Nullable User getUser(@NonNull SessionKey sessionKey) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return getUser(connection, sessionKey);
        } catch (SQLException e) {
            LOG.error("getUser: fetchConnection failed", e);
            return null;
        }
    }

    public @Nullable User getUser(@NonNull Connection connection, @NonNull SessionKey sessionKey) {
        try (PreparedStatement ps = connection.prepareStatement(USER_PROFILE_BY_PLATFORM_ID_ROUTE)) {

            ps.setString(1, sessionKey.platform().code()); // platform
            ps.setString(2, sessionKey.to()); // channel
            ps.setString(3, sessionKey.from()); // platform_id

            final ResultSet rs = ps.executeQuery();

            UUID groupId = null;
            String country = "US";
            String nickName;
            Map<Platform, UUID> platformIdMap = new HashMap<>();
            Map<Platform, String> platformNumberMap = new HashMap<>();
            Map<Platform, Instant> platformCreatedMap = new HashMap<>();
            Map<Platform, String> platformNickNames = new HashMap<>();
            Set<LanguageCode> languages = new LinkedHashSet<>();
            String profileSurname = null, profileGivenName = null;
            Instant profileCreated = null, profileUpdated = null;
            UUID customerId = null;
            Map<Platform, UserStatus> platformStatus = new HashMap<>();

            Profile optionalProfile = null;
            Map<Platform, Profile> platformProfiles = new HashMap<>();
            String profileLanguages = null;

            int rowCount = 0;

            while (rs.next()) {
                rowCount++;

                UUID id = (UUID) rs.getObject("id"); // u.id

                // u.group_id
                groupId = (UUID) rs.getObject("group_id");

                String platformId = rs.getString("platform_id"); // u.platform_id
                Platform platform = Platform.byCode(rs.getString("platform_code")); // u.platform_code // throws IaE if null

                // Build up the map of "numbers" for each Platform. For SMS, this is a phone number. For others, it's an opaque string.
                platformNumberMap.put(platform, platformId);

                // Same for the UUID
                platformIdMap.put(platform, id);

                country = rs.getString("country"); // u.country

                // Build up the list of languages selected by the User.
                languages.add(LanguageCode.valueOf(rs.getString("language"))); // u.language

                nickName = rs.getString("nickname"); // u.nickname
                platformNickNames.put(platform, nickName); // NB try out an Optional here?

                Instant createdAt = rs.getTimestamp("created_at").toInstant();
                platformCreatedMap.put(platform, createdAt);

                // TODO Add these to an optional Profile
                profileSurname = rs.getString("surname"); // p.surname
                profileGivenName = rs.getString("given_name"); // p.given_name

                profileLanguages = rs.getString("profile_other_languages"); // p.other_languages

                var pca = rs.getTimestamp("profile_created_at");
                profileCreated = (pca == null) ? null : pca.toInstant();
                var pua = rs.getTimestamp("profile_updated_at");
                profileUpdated = (pua==null) ? null : pua.toInstant();

                // Use just the name fields as sentinels to detect enough data to create a Profile record
                if (profileGivenName != null || profileSurname != null) {
                    platformProfiles.put(platform, new Profile(id, profileSurname, profileGivenName, profileLanguages, profileCreated, profileUpdated));
                }

                customerId = (UUID) rs.getObject("owner_customer_id");

                platformStatus.put(platform, UserStatus.valueOf(rs.getString("status")));

            }

            LOG.info("Processed {} records for {}", rowCount, sessionKey);

            if (rowCount < 1) {
                LOG.info("No Users found for {}", sessionKey);
                return null; // FIXME should we insert a User at this point or leave it to the caller?
            }

            // FIXME Not sure it makes sense to merge these now that we're attaching a Profile object.
            // Merge the set of languages, attempting to preserve the user's preferred order
            // languages.addAll(otherLanguages);
            Set<LanguageCode> userLanguages = new HashSet<>(languages);
            LOG.info("Languages for user: {}", languages);

            return new User(platformIdMap, groupId, platformNumberMap, platformCreatedMap, country, userLanguages, customerId, platformNickNames, platformProfiles, platformStatus);

        } catch (SQLException e) {
            LOG.error("getUser failed", e);
            return null;
        }
    }

    @Override
    public boolean insertNewUser(User user) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return insertNewUserAmalgam(connection, user);
        } catch (SQLException e) {
            LOG.error("getUser: fetchConnection failed", e);
            return false;
        }
    }

    // NB: Per the method name, assumes this is a new, unconnected User. Thus, the various map properties assume a single value.
    private boolean insertNewUserAmalgam(Connection connection, User user) throws SQLException {
        LOG.info("insertUserAmalgam: {}", user);

        final var onlyId = user.platformIds().entrySet().iterator().next();
        final var onlyNumber = user.platformNumbers().entrySet().iterator().next();
        final var onlyStatus = user.platformStatus().entrySet().iterator().next();
        final var onlyProfile = (user.platformProfiles() == null) ? null : user.platformProfiles().entrySet().iterator().next().getValue();
        Instant createdAt = utcInstant();

        try (PreparedStatement ps = connection.prepareStatement(USER_AMALGAM_INSERT)) {
            ps.setObject(1, onlyId.getValue()); // id
            ps.setObject(2, onlyStatus.getValue(), OTHER);
            ps.setString(3, onlyNumber.getValue()); // platform_id
            ps.setObject(4, onlyNumber.getKey().code(), OTHER); // platform_code
            ps.setObject(5, user.countryCode(), OTHER); // country
            ps.setObject(6, user.languages().iterator().next(), OTHER); // language
            ps.setObject(7, user.platformNickNames().get(onlyNumber.getKey()));
            ps.setTimestamp(8, Timestamp.from(createdAt)); // created_at
            ps.setTimestamp(9, Timestamp.from(createdAt)); // updated_at
            // plus these two just for the amalgams table.
            ps.setObject(10, user.groupId()); // group_id
            ps.setObject(11, (onlyProfile == null) ? null : onlyProfile.id()); // profile_id
            ps.setObject(12, user.customerId()); // customer_id

            int numInserted = ps.executeUpdate();

            LOG.info("insertUserAmalgam: inserted {} rows.", numInserted);
            return (numInserted == 1);

        } catch (SQLException e) {
            LOG.error("insertUserAmalgam: insert failed", e);
            return false;
        }
    }

//    private boolean insertUser(Connection connection, User user) {
//        int numOfPlatforms = user.platformNumbers().size();
//        assert numOfPlatforms == 1;
//        final Map.Entry<Platform, String> onlyPlatform = user.platformNumbers().entrySet().iterator().next();
//        Instant createdAt = utcInstant();
//
//        LOG.info("insertUser: {}", user);
//
//        /*
//         * Note that the platform_code, country_code and language columns are defined as enum types in DDL
//         * which is why they have to be set as Objects on the PreparedStatement. Alternatively, we could
//         * follow the type declaration with 'CREATE CAST (varchar AS platform) WITH INOUT AS IMPLICIT;'
//         */
//        try (PreparedStatement ps = connection.prepareStatement(USER_INSERT)) {
//            // group_id | platform_id | platform_code | country | language | nickname | created_at
//            ps.setObject(1, user.id()); // id
//            ps.setObject(2, user.groupId());// group_id
//            ps.setString(3, onlyPlatform.getValue()); // platform_id
//            ps.setObject(4, onlyPlatform.getKey().code(), OTHER); // platform_code
//            ps.setObject(5, user.countryCode(), OTHER); // country
//            ps.setObject(6, user.languages().getFirst(), OTHER); // language
//            ps.setObject(7, user.platformNickNames().get(onlyPlatform.getKey()));
//            ps.setTimestamp(8, Timestamp.from(createdAt));
//            ps.setObject(9, user.customerId());
//
//            int numInserted = ps.executeUpdate();
//            LOG.info("insertUser: inserted {} record(s).", numInserted); // FIXME change to .debug
//            return (1 == numInserted);
//
//        } catch (SQLException e) {
//            LOG.error("insertUser failed", e);
//            return false;
//        }
//
//    }

    public @NonNull Collection<CampaignUser> getPushCampaignUsers(@NonNull UUID campaignId, DeliveryStatus byStatus) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return getPushCampaignUsers(
                    connection, campaignId, DeliveryStatus.PENDING);
        } catch (SQLException e) {
            LOG.error("getUsersForPushCampaign: fetchConnection failed", e);
            return new ArrayList<>(0);
        }
    }

    @NonNull
    private Collection<CampaignUser> getPushCampaignUsers(@NonNull Connection connection, @NonNull UUID campaignId, DeliveryStatus byStatus) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_PUSH_CAMPAIGN_USERS_BY_DELIVERY_STATUS)) {

            CampaignUserReport report = new CampaignUserReport();

            ps.setObject(1, campaignId);
            ps.setObject(2, byStatus, OTHER);
            final ResultSet rs = ps.executeQuery();

            Map<UUID, CampaignUser> campaignUsersByGroupId = new HashMap<>();

            // NB: customer_id and delivered will be null for linked users
            while (rs.next()) {

                final Map<Platform, UUID> plaformIdMap = new HashMap<>();
                final Map<Platform, String> plaformNumberMap = new HashMap<>();
                final Map<Platform, UserStatus> platformStatusMap = new HashMap<>();
                final Map<Platform, Instant> platformCreationTimesMap = new HashMap<>();
                final Map<Platform, String> platformNickNames = new HashMap<>();
                final Map<Platform, Profile> platformProfiles = new HashMap<>();
                final Set<LanguageCode> languages = new HashSet<>();

                // a.group_id,
                final UUID groupId = (UUID) rs.getObject("group_id");

                // a.user_id AS user_id,
                final UUID userId = (UUID) rs.getObject("user_id");

                // a.profile_id AS profile_id,
                final UUID profileId = (UUID) rs.getObject("profile_id");

                // u.country,
                final CountryCode country = CountryCode.valueOf(rs.getString("country"));

                // u.created_at,
                final Instant userCreatedAt = rs.getTimestamp("user_created_at").toInstant();

                // u.language,
                final LanguageCode languageCode = LanguageCode.valueOf(rs.getString("language"));

                // u.nickname,
                final String nickName = rs.getString("nickname");

                // u.platform_code,
                final Platform userPlatform = Platform.byCode(rs.getString("platform_code"));

                // u.platform_id,
                String platformId = rs.getString("platform_id");

                // u.status,
                UserStatus userStatus = UserStatus.valueOf(rs.getString("status"));

                // p.given_name (nullable)
                final String givenName = rs.getString("given_name");

                // p.other_languages (nullable)
                final String otherLanguages = rs.getString("other_languages");

                // p.surname (nullable)
                final String surname = rs.getString("surname");

                // p.created_at AS profile_created_at (nullable)
                final Timestamp pca = rs.getTimestamp("profile_created_at");
                Instant profileCreatedAt = (pca == null) ? null : pca.toInstant();

                // p.updated_at AS profile_updated_at (nullable)
                final Timestamp pua = rs.getTimestamp("profile_updated_at");
                final Instant profileUpdatedAt = (pua == null) ? null : pua.toInstant();

                // cgi.customer_id  // pc_all.customer_id (nullable)
                final UUID customerId = (UUID) rs.getObject("customer_id");

                // s.updated_at AS session_updated_at
                final Timestamp sua = rs.getTimestamp("session_updated_at");
                final Instant sessionUpdatedAt = (sua == null) ? null : sua.toInstant();

                // cu_all.delivered
                DeliveryStatus delivered = null;
                final var deliveryStatus = rs.getString("delivered");
                if(deliveryStatus != null) {
                    delivered = DeliveryStatus.valueOf(deliveryStatus);
                }

                // FIXME move to beginning of loop and skip the rest if
                //  both deliveryStatus is wrong and status isn't IN?
                if (!userStatus.equals(UserStatus.IN)) {
                    LOG.info("Campaign {}: Skipping user {} with opt-in status: {}", campaignId, userId, userStatus);
                    report.campaignUserIds.add(userId);
                }

                // delivered will be null for linked rows that are only present to construct the full User model.
                if (delivered != null && !delivered.equals(DeliveryStatus.PENDING)) {
                    LOG.info("Campaign {}: Skipping user {} with delivery status: {}", campaignId, userId, delivered);
                    continue;
                }

                languages.add(languageCode);
                plaformIdMap.put(userPlatform, userId);
                plaformNumberMap.put(userPlatform, platformId);
                platformStatusMap.put(userPlatform, userStatus);
                platformNickNames.put(userPlatform, nickName);
                platformCreationTimesMap.put(userPlatform, userCreatedAt);

                if (profileCreatedAt != null) {
                    platformProfiles.put(userPlatform, new Profile(profileId, surname, givenName, otherLanguages, profileCreatedAt, profileUpdatedAt));
                }

                final var user = new User(
                        plaformIdMap,
                        groupId,
                        plaformNumberMap,
                        platformCreationTimesMap,
                        country.name(),
                        languages,
                        customerId,
                        platformNickNames,
                        platformProfiles,
                        platformStatusMap);

                var campaignUser = new CampaignUser(
                        groupId,
                        user,
                        delivered,
                        sessionUpdatedAt/*,
                        pushSupport*/);

                // Using Map's compute method is another way of doing the following. Not sure if it would be better/clearer.
                final var existingCampaignUser = campaignUsersByGroupId.get(groupId);
                if(existingCampaignUser != null) {
                    campaignUser = existingCampaignUser.merge(campaignUser);
                } else {
                    campaignUsersByGroupId.put(groupId, campaignUser);
                }

            }

            //IO.println("Total users processed: " + campaignUsersByGroupId.size());
            //List<CampaignUser> serializeThis = new ArrayList<>(campaignUsersByGroupId.values());
            // The output for 14 records is just under 7K. This is less than 140 lines (7,245 bytes or 7,243 chars) of plain text.
            //File file = new File("FourteenCampaignUsers.ser");
            //try (FileOutputStream fos = new FileOutputStream(file);
            //     ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            //    oos.writeObject(serializeThis);
            //} catch (IOException e) {
            //    throw new RuntimeException(e);
            //}

            return campaignUsersByGroupId.values();
        }
    }

    public static final String UPDATE_PUSH_CAMPAIGN_USER_STATUS = """
           UPDATE
             brbl_logic.campaign_users
           SET
             delivered = ?
           WHERE
             campaign_id = ?
           AND
             user_id = ANY (?)
           """;

    public boolean updatePushCampaignUsersStatus(@NonNull PushReport report) throws SQLException {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return updatePushCampaignUsersStatus(connection, report);
        } catch (SQLException e) {
            LOG.error("getUsersForPushCampaign: fetchConnection failed", e);
            return false;
        }
    }

    private boolean updatePushCampaignUsersStatus(Connection connection, PushReport report) throws SQLException {
        LOG.info("Num campaignUsers to status update: " + report.processedUsers.size());
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_PUSH_CAMPAIGN_USER_STATUS)) {
            ps.setObject(1, DeliveryStatus.SENT, OTHER);
            ps.setObject(2, report.campaignId);

            UUID[] ids = report.processedUsers.toArray(new UUID[0]);
            // LOG.info("ids size: " + ids.length);
            // LOG.info("ids elements: " + Arrays.toString(ids));

            Array skippedIds = connection.createArrayOf("uuid", ids);
            ps.setArray(3, skippedIds);

            int numUpdated = ps.executeUpdate(); // ps.executeLargeUpdate()
            LOG.info("getUsersForPushCampaignUserStatus: numUpdated = {}", numUpdated);

        }
        return true;
    }

//    public @Nullable List<CampaignUser> getUsersForPushCampaign(@NonNull UUID pushCampaignId) {
//        try (Connection connection = fetchConnection()) {
//            assert connection != null;
//            return getUsersForPushCampaign(connection, pushCampaignId);
//        } catch (SQLException e) {
//            LOG.error("getUsersForPushCampaign: fetchConnection failed", e);
//            return null;
//        }
//    }
//

//    private @Nullable List<CampaignUser> getUsersForPushCampaign(Connection connection, @NonNull UUID pushCampaignId) {
//        try (PreparedStatement ps = connection.prepareStatement(SELECT_PUSH_CAMPAIGN_USERS)) {
//            ps.setObject(1, pushCampaignId);
//            final ResultSet rs = ps.executeQuery();
//
//            List<CampaignUser> userList = new ArrayList<>();
//
//            while (rs.next()) {
//                // u.id
//                UUID userId = (UUID) rs.getObject(1);
//                // u.platform_id
//                String platformId = rs.getString(2);
//                // u.status
//                UserStatus userStatus = UserStatus.valueOf(rs.getString(3));
//                // u.country
//                CountryCode countryCode = CountryCode.valueOf(rs.getString(4));
//                // u.nickname
//                String nickName = rs.getString(5);
//                // u.language
//                LanguageCode languageCode = LanguageCode.valueOf(rs.getString(6));
//                // s.delivered
//                DeliveryStatus deliveryStatus = DeliveryStatus.valueOf(rs.getString(7));
//                // p.given_name
//                String givenName = rs.getString(8);
//                // p.surname
//                String surname = rs.getString(9);
//                // s.updated_at
//                final Timestamp sessionUpdatedAtTs = rs.getTimestamp(10);
//                Instant sessionUpdatedAt = (sessionUpdatedAtTs == null) ? null : sessionUpdatedAtTs.toInstant();
//
//                // CampaignUser = CampaignUser + User + Script + Profile + Session
//                userList.add(
//                        new CampaignUser(
//                                userId, platformId, userStatus, countryCode, nickName, languageCode,
//                                deliveryStatus,
//                                givenName, surname,
//                                sessionUpdatedAt)
//                );
//
//            }
//            return userList;
//
//        } catch (SQLException e) {
//            LOG.error("getUsersForPushCampaign failed", e);
//            return null;
//        }
//    }

    public PushCampaign getPushCampaign(@NonNull UUID pushCampaignId) {
        try (Connection connection = fetchConnection()) {
            assert connection != null;
            return getPushCampaign(connection, pushCampaignId);
        } catch (SQLException e) {
            LOG.error("getPushCampaign: fetchConnection failed", e);
            return null;
        }
    }

    private PushCampaign getPushCampaign(Connection connection, UUID campaignId) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_PUSH_CAMPAIGN)) {
            ps.setObject(1, campaignId);

            final ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                LOG.info("getPushCampaign: No campaigns found for {}", campaignId);
                return null;
            }

            // id
            UUID id = (UUID) rs.getObject(1);

            // customer_id
            UUID customerId = (UUID) rs.getObject(2);

            // description
            String description = rs.getString(3);

            // script_id
            UUID scriptId = (UUID) rs.getObject(4);

            // created_at
            Instant createdAt = rs.getTimestamp(5).toInstant();

            // updated_at
            Instant updatedAt = rs.getTimestamp(6).toInstant();

            // completed_at (Watch out! This column is nullable)
            Timestamp ts = rs.getTimestamp(7);
            Instant completedAt = (ts != null) ? ts.toInstant() : null;

            // status
            CustomerStatus customerStatus = CustomerStatus.valueOf(rs.getString(8));

            ScriptStatus scriptStatus = ScriptStatus.valueOf(rs.getString(9));

            // node id
            UUID nodeId = (UUID) rs.getObject(10);

            // r.channel
            String channel = rs.getString(11);

            // r.platform
            Platform platform = Platform.byCode(rs.getString(12));

            // r.status
            RouteStatus routeStatus = RouteStatus.valueOf(rs.getString(13));


            return new PushCampaign(
                    id, customerId, description, scriptId, createdAt, updatedAt, completedAt,
                    customerStatus, scriptStatus,
                    nodeId,
                    channel, platform, routeStatus);

        } catch (SQLException e) {
            LOG.error("getPushCampaign failed", e);
            return null;
        }
    }

    static void main() throws IOException, PersistenceManagerException {
        PostgresPersistenceManager pm = new PostgresPersistenceManager(ConfigLoader.readConfig("persistence_manager_test.properties"));
        //var routes = pm.getActiveRoutes();
        //for (var route : routes) {
        //    LOG.info(route.toString());
        //}

//        final PushCampaign pushCampaign = pm.getPushCampaign(UUID.fromString("eb7aa81a-b314-420c-8f3d-df4755faa9bb"));
//        LOG.info("PC: {}", pushCampaign);

//        final Map<Pattern, Keyword> keywords = pm.getKeywords();
//        keywords.forEach((key, value) -> LOG.info("{} -> {}", key, value.wordPattern()));

//        final List<CampaignUser> usersForPushCampaign = pm.getUsersForPushCampaign(UUID.fromString("eb7aa81a-b314-420c-8f3d-df4755faa9bb"));
//        if (usersForPushCampaign != null && !usersForPushCampaign.isEmpty()) {
//            usersForPushCampaign.forEach(cu -> LOG.info("> {}", cu));
//        } else {
//            LOG.info("Ack, no results");
//        }
        final var sessionKey = new SessionKey(Platform.SMS, "18484242144", "119839196677", "keyword");
        final User user = pm.getUser(sessionKey);
        LOG.info("user: {}", user);
    }

}
