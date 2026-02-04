package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ScriptEngine {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptEngine.class);

    private static final int EXPECTED_INPUT_COUNT = 1;

    private PersistenceManager persistenceManager;

    public ScriptEngine(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    /**
     * Process the given Message in the context of given Session.
     * @param session the state-bearing Session providing the context for the executing graph Node graph.
     * @param message the Message that initiates or continues the processing of the Session's Node graph.
     * @return false if the processing of the Message fails or there were exceptions thrown.
     */
    public boolean process(Session session, Message message) {
        synchronized (session) { // FIXME move the synchronization to caller where the session is created?
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

            } catch (IOException | PersistenceManager.PersistenceManagerException e) {
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
}
