package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.QueueProducer;
import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * The Session tracks and persists state for a single User
 * Won't work as a Record since we need to update the currentScript field
 * and maintain state
 */
public class Session {
    private static final Logger LOG = LoggerFactory.getLogger(Session.class);
    public static final int MAX_INPUT_HISTORY = 10;

    final long startTimeNanos;
    final UUID id;
    final User user;
    final QueueProducer producer;
    final PersistenceManager persistenceManager;

    Script currentScript;

    final Queue<Message> outputBuffer = new LinkedList<>();
    final SequencedSet<Message> inputs = new LinkedHashSet<>();
    final SequencedSet<Message> inputHistory = new LinkedHashSet<Message>(MAX_INPUT_HISTORY) {
        @Override
        public void addLast(Message message) {
            if (1 + this.size() > MAX_INPUT_HISTORY) {
                removeFirst();
            }
            super.addLast(message);
        }
    };

    List<Script> evaluatedScripts = new ArrayList<>(); // TODO make this a stack instead?

    // Db manager goes here

//    public static void main(String[] args) {
//        String url = "jdbc:postgresql://localhost" + "/brbl_dev";
//        Properties props = new Properties();
//        props.setProperty("user", "mark");
//        props.setProperty("password", "mark");
//        props.setProperty("ssl", "false");
//        props.setProperty("preparedStatementCacheQueries", "10");
//        try {
//            Connection conn = DriverManager.getConnection(url, props);
//            final boolean valid = conn.isValid(2000);
//            final PGConnection extendedConn = conn.unwrap(PGConnection.class);//preparedStatementCacheQueries(10);
//            extendedConn.escapeIdentifier("");
//            LOG.info("connection is valid: {}", valid);
//        } catch (SQLException e) {
//            throw new RuntimeException(e);
//        }
//    }

    public Session(UUID id, Script currentScript, User user, QueueProducer producer, PersistenceManager persistenceManager) {
        startTimeNanos = NanoClock.systemUTC().nanos();
        this.id = Objects.requireNonNull(id);
        this.currentScript = Objects.requireNonNull(currentScript);
        this.user = Objects.requireNonNull(user);
        this.producer = Objects.requireNonNull(producer);
        this.persistenceManager = persistenceManager;
        LOG.info("Created Session {} for User {}", id, user.id());
    }

    public void registerInput(Message moMessage) {
        inputs.add(moMessage);
        LOG.info("Registered input message {}", moMessage);
    }

    public void registerOutput(Message mtMessage) {
        outputBuffer.add(mtMessage);
        LOG.info("Registered output message {}", mtMessage);
    }
    
    public void registerEvaluated(Script script) {
        evaluatedScripts.addLast(script);
        LOG.info("Registered evaluated script {}", script.id());
    }

    public void flushOutput() throws IOException {
        int numInBuffer = outputBuffer.size();
        LOG.info("flushOutput: outputBuffer size = {}", numInBuffer);
        for (int i = 0; i < numInBuffer; i++) {
            Message mtMessage = outputBuffer.poll();
            producer.enqueue(mtMessage);
            persistenceManager.insertMT(mtMessage, this);
        }

        outputBuffer.clear();

        inputs.forEach(inputHistory::addLast);

        inputs.clear();
    }

    public User getUser() {
        return user;
    }

    public Script currentScript() {
        return currentScript;
    }
}
