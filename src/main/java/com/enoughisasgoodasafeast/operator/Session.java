package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.QueueProducer;
import io.jenetics.util.NanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;

/**
 * The Session tracks and persists state for a single User interaction with Brbl's runtime.
 * Won't work as a Record since we need to update the currentNode field
 * and maintain state
 */
public class Session implements ScriptContext, Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(Session.class);

    public static final int MAX_INPUT_HISTORY = 10;

    @Serial
    private static final long serialVersionUID = 1L;

    private final Instant startTimeNanos;
    private final UUID id;
    private final User user;

    // TODO move these out of the class
    private final transient QueueProducer producer;
    private final transient PersistenceManager persistenceManager;

    Node currentNode;

    private final Queue<Message> outputBuffer = new LinkedList<>();
    private final SequencedSet<Message> inputs = new LinkedHashSet<>();
    private final SequencedSet<Message> inputHistory = new LinkedHashSet<>(MAX_INPUT_HISTORY) {
        @Override
        public void addLast(Message message) {
            if (1 + this.size() > MAX_INPUT_HISTORY) {
                removeFirst();
            }
            super.addLast(message);
        }
    };
    private final List<Node> evaluatedNodes = new ArrayList<>(); // TODO make this a stack instead?

    private Instant lastUpdatedNanos;

    /**
     * Creates a new, fully configured Session object.
     *
     * @param id                 the unique identifier
     * @param currentNode        the starting Node in the graph
     * @param user               the unique User
     * @param producer           the sink for messages created on behalf of this Session
     * @param persistenceManager the object that writes artifacts created for this Session
     */
    public Session(UUID id, Node currentNode, User user, QueueProducer producer, PersistenceManager persistenceManager) {
        this.startTimeNanos = NanoClock.utcInstant();
        this.lastUpdatedNanos = startTimeNanos;
        this.id = Objects.requireNonNull(id);
        this.currentNode = Objects.requireNonNull(currentNode);
        this.user = Objects.requireNonNull(user);
        this.producer = Objects.requireNonNull(producer);
        this.persistenceManager = persistenceManager;
        LOG.debug("Created Session {} for User {}", id, user.id());
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
        LOG.debug("Registered input message {}", moMessage);
    }

    @Override
    public void registerOutput(Message mtMessage) {
        outputBuffer.add(mtMessage);
        LOG.debug("Registered output message {}", mtMessage);
    }

    /**
     * Add the evaluated Node to the Session history.
     * We avoid appending the same Node if we're just looping due to user input mistakes
     * However, if we revisit a Node multiple times due to branching in the script we want to record that
     * as part of the history.
     */
    public void registerEvaluated(Node node) {
        if (node == null) {
            return;
        }
        if (!evaluatedNodes.isEmpty()) {
            if (!evaluatedNodes.getLast().id().equals(node.id())) {
                LOG.info("registerEvaluated (unique): {}:{}", node.id(), node.text());
                evaluatedNodes.add(node);
            }
        } else {
            LOG.info("registerEvaluated (empty): {}:{}", node.id(), node.text());
            evaluatedNodes.add(node);
        }
    }


    /**
     * Since multiple Scripts may be evaluated in response to a single MO we need a way of
     * finding the one that prompted it. Used when logging the processed MO.
     *
     * @return the Node that prompted the User's latest MO.
     */
    public Node getScriptForProcessedMO() {

        if (currentNode != null) {
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

    public UUID getId() {
        return id;
    }

    public Instant getStartTimeNanos() {
        return startTimeNanos;
    }

    public Instant getLastUpdatedNanos() {
        return lastUpdatedNanos;
    }

    public void sessionUpdated() {
        lastUpdatedNanos = NanoClock.utcInstant();
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

    @Override
    public String toString() {
        return new StringJoiner(", ", Session.class.getSimpleName() + "[", "]")
                .add("startTimeNanos=" + startTimeNanos)
                .add("id=" + id)
                .add("user=" + user)

                .add("producer=" + producer)
                .add("persistenceManager=" + persistenceManager)
                .add("currentNode=" + currentNode)
                .add("outputBuffer=" + outputBuffer)
                .add("inputs=" + inputs)
                .add("inputHistory=" + inputHistory)
                .add("evaluatedNodes=" + evaluatedNodes)
                .toString();
    }
}
