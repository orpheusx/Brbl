package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.enoughisasgoodasafeast.Message;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class PostgresPersistenceManager implements PersistenceManager {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresPersistenceManager.class);

    private static ComboPooledDataSource pds;

    public static PersistenceManager createPersistenceManager(Properties properties) throws PersistenceManagerException {
        // if, at some point, we need to enforce a singleton we'll do it here.
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

    //private UUID insertTest() throws InterruptedException {
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
    //    final User user = new User(UUID.randomUUID(), Map.of(Platform.SMS, "17815551234"), "MX", List.of("es", "en"));
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
    //}

    public static void main(String[] args) throws InterruptedException, IOException, PersistenceManagerException {
        PersistenceManager pm = new PostgresPersistenceManager(ConfigLoader.readConfig("rcvr.properties"));
        //pm.insertTest();
    }

}
