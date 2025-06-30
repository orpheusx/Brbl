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
 * Won't work as a Record since we need to update the currentNode field
 * and maintain state
 */
public class Session implements ScriptContext {
    private static final Logger LOG = LoggerFactory.getLogger(Session.class);
    public static final int MAX_INPUT_HISTORY = 10;

    final long startTimeNanos;
    final UUID id;
    final User user;
    final QueueProducer producer;
    final PersistenceManager persistenceManager;

    Node currentNode;

    private final Queue<Message> outputBuffer = new LinkedList<>();
    private final SequencedSet<Message> inputs = new LinkedHashSet<>();
    private final SequencedSet<Message> inputHistory = new LinkedHashSet<Message>(MAX_INPUT_HISTORY) {
        @Override
        public void addLast(Message message) {
            if (1 + this.size() > MAX_INPUT_HISTORY) {
                removeFirst();
            }
            super.addLast(message);
        }
    };
    private final List<Node> evaluatedNodes = new ArrayList<>(); // TODO make this a stack instead?

    /**
     * Creates a new, fully configured Session object.
     * @param id the unique identifier
     * @param currentNode the starting Node in the graph
     * @param user the unique User
     * @param producer the sink for messages created on behalf of this Session
     * @param persistenceManager the object that writes artifacts created for this Session
     */
    public Session(UUID id, Node currentNode, User user, QueueProducer producer, PersistenceManager persistenceManager) {
        startTimeNanos = NanoClock.systemUTC().nanos();
        this.id = Objects.requireNonNull(id);
        this.currentNode = Objects.requireNonNull(currentNode);
        this.user = Objects.requireNonNull(user);
        this.producer = Objects.requireNonNull(producer);
        this.persistenceManager = persistenceManager;
        LOG.info("Created Session {} for User {}", id, user.id());
    }

    public int currentInputsCount() {
        return inputs.size();
    }

    public Message previousInput() {
        return inputHistory.isEmpty() ? null : inputHistory.getLast();
    }

    public SequencedSet<Message> getInputHistory() {
        return inputHistory;
    }

    public Queue<Message> getOutputBuffer() {
        return outputBuffer;
    }

    public Node previousScript() {
        return evaluatedNodes.getLast();
    }

    @Override
    public List<Node> getEvaluatedNodes() {
        return evaluatedNodes;
    }

    public void registerInput(Message moMessage) {
        inputs.addLast(moMessage);
        LOG.info("Registered input message {}", moMessage);
    }

    @Override
    public void registerOutput(Message mtMessage) {
        outputBuffer.add(mtMessage);
        LOG.info("Registered output message {}", mtMessage);
    }
    
    public void registerEvaluated(Node node) {
        evaluatedNodes.addLast(node);
        LOG.info("Registered evaluated node {}", node.id());
    }

    /**
     * Since multiple Scripts may be evaluated in response to a single MO we need a way of
     * finding the one that prompted it. Used when logging the processed MO.
     * @return the Node that prompted the User's latest MO.
     */

    public Node getScriptForProcessedMO() {

        if (currentNode != null) { // FIXME should
            return currentNode;
        }

        for (int i = evaluatedNodes.size() - 1; i >= 0; i--) {
            Node evaluated = evaluatedNodes.get(i);
            if (evaluated.type().isAwaitInput()) {
                return evaluated;
            }
        }

        return evaluatedNodes.getFirst();
    }

    public void flush() throws IOException {
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

    @Override
    public Node getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(Node node) {
        currentNode = node;
    }
}
