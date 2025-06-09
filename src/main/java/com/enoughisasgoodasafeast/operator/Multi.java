package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.enoughisasgoodasafeast.Message.newMTfromMO;

public class Multi {

    private static final Logger LOG = LoggerFactory.getLogger(Multi.class);

    static class Present {

        /**
         * Creates an MT from the currentNode's text field, queues it for sending, then advances to the
         * next element in the Node chain.
         * @param context the context being processed.
         * @param moMessage the incoming MO message that triggered the response message produced here.
         * @return the next Node in the context's node graph.
         */
        public static Node evaluate(ScriptContext context, Message moMessage) {
            LOG.info("Multi.Present evaluating '{}'", moMessage.text());
            // Is this the right place to handle platform-dependent message text formatting?
            String mtText = Functions.renderForPlatform(moMessage.platform(), context.getCurrentScript().text());
            Message mt = newMTfromMO(moMessage, mtText); // FIXME Move the renderForPlatform into Message's static methods?
            context.registerOutput(mt);
            return advance(context);
        }

        // TODO move this into a ScriptManager type of class that other Node impls can use?
        static Node advance(ScriptContext session) {
            if (session.getCurrentScript().edges().isEmpty()) {
                LOG.info("End of node, '{}', reached.", session.getCurrentScript().label());
                return null;
            } else {
                final Node nextNode = session.getCurrentScript().edges().getFirst().node(); // only one ResponseLogic available
                LOG.info("Multi.Present {} dispatching to {}", session.getCurrentScript().label(), nextNode);
                return nextNode;
            }
        }
    }

    static class Process {

        // FIXME in practice, this cannot be a constant. This could, however, serve as a default.
        final static String UNEXPECTED_INPUT_MESSAGE = """
                Oops, that's not one of the options. Try again with one of the listed options
                or say 'change topic' to start talking about something else.
                """;

        public static Node evaluate(ScriptContext context, Message moMessage) throws IOException {
            LOG.info("Multi.Process evaluating '{}'", moMessage.text());

            Node current = context.getCurrentScript();

            final String noMatchText = current.text(); //overloading the use of the text field feels bad. Add an errorText field to Node?
            final String userText = moMessage.text().trim().toLowerCase();

            LOG.info("Present currentNode: {}", context.getCurrentScript());

            for (ResponseLogic option : current.edges()) {
                LOG.info("Checking option for match {} -> {}", userText, option.matchText());
                if (option.matchText().contains(userText)) { //TODO make the matching more robust/flexible. Efficient regexes?
                    LOG.info("Input, {}, matched logic: {}", userText, option.matchText());
                    final Message mt = newMTfromMO(moMessage, option.text());
                    context.registerOutput(mt);
                    //LOG.info("Enqueued {}", mt);
                    return option.node();
                } else {
                    LOG.info("No match {} != {}", userText, option.matchText());
                }
            }

            // Handle the "I want to talk about something else" case here...Should it be above the ResponseLogic loop?
            if (userText.contains("change topic")) {
                // Create a new Node graph
                // TODO this should all be handled in the called methods.
                Message changeTopicNotification = newMTfromMO(moMessage, "You want to talk about something else? OK...");
                context.registerOutput(changeTopicNotification);
//                return Process.constructTopicScript(context, moMessage);
                LOG.error("Unsupported feature: change topic");
                return null;
            }


            // TODO add "go back to revisit a previous stage"?
            // ...

            // Still here? Provide a 'bad input' message.
            // Would it make sense to re-print the previous Multi.Present? Seems like it would be clear what the
            // actual options are since it's only a few lines above in the chat history, right?
            context.registerOutput(newMTfromMO(moMessage, noMatchText==null ? UNEXPECTED_INPUT_MESSAGE : noMatchText));
            return current; // we won't advance in this case.
        }

        /*
         * FIXME This should really be fetched from a database per shortcode and not handled by this
         */
        public static Node constructTopicScript(ScriptContext session, Message moMessage) throws IOException {

//            session.currentNode = Functions.findTopicScript(session, moMessage);

            return Present.evaluate(session, moMessage);
        }
    }

}

