package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static com.enoughisasgoodasafeast.Message.newMTfromMO;

public class Multi {

    private static final Logger LOG = LoggerFactory.getLogger(Multi.class);

    static class Present {

        /**
         * Create an MT that containing the currentScripts text field, queues it for sending, then advances to the
         * next element in the Script chain.
         * @param session
         * @param moMessage
         * @return
         * @throws IOException
         */
        public static Script evaluate(Session session, Message moMessage) throws IOException {
            LOG.info("Multi.Present evaluating '{}'", moMessage.text());
            // Is this the right place to handle platform-dependent message text formatting?
            String mtText = Functions.renderForPlatform(moMessage.platform(), session.currentScript().text());
            Message mt = newMTfromMO(moMessage, mtText); // FIXME Move the renderForPlatform into Message's static methods?
            session.addOutput(mt);
            return advance(session);
        }

        // TODO move this into a ScriptManager type of class that other Script impls can use.
        static Script advance(Session session) {
            if (session.currentScript.next().isEmpty()) {
                LOG.info("End of script, '{}', reached.", session.currentScript.label());
                return null;
            } else {
                final Script nextScript = session.currentScript.next().getFirst().script(); // only one ResponseLogic available
                LOG.info("Multi.Present {} dispatching to {}", session.currentScript.label(), nextScript);
                return nextScript;
            }
        }
    }

    static class Process {

        // FIXME in practice, this cannot be a constant. This could, however, serve as a default.
        final static String UNEXPECTED_INPUT_MESSAGE = """
                Oops, that's not one of the options. Try again with one of the listed numbers
                or say 'change topic' to start talking about something else.
                """;

        public static Script evaluate(Session session, Message moMessage) throws IOException {
            LOG.info("Multi.Process evaluating '{}'", moMessage.text());
            String noMatchText = session.currentScript.text(); //overloading the use of the text field feels bad. Add an errorText field to Script?

            final String userText = moMessage.text().trim().toLowerCase();
            for (ResponseLogic option : session.currentScript.next()) {
                if (option.matchText().contains(userText)) { //TODO make the matching more robust/flexible.
                    LOG.info("Input, {}, matched logic: {}", userText, option.matchText());
                    final Message mt = newMTfromMO(moMessage, option.text());
                    session.addOutput(mt);
                    LOG.info("Enqueued {}", mt);
                    return option.script();
                }
            }

            // Handle the "I want to talk about something else" case here...
            if (userText.contains("change topic")) {
                // Create a new Script graph
                // TODO this should all be handled in the called methods.
                Message changeTopicNotification = newMTfromMO(moMessage, "You want to talk about something else? OK...");
                session.addOutput(changeTopicNotification);
                return Process.constructTopicScript(session, moMessage);
            }


            // TODO "go back to revisit a previous stage"?

            // Still here? Provide a 'bad input' message.
            // Would it make sense to re-print the previous Multi.Present? Seems like it would be clear what the
            // actual options are since it's only a few lines above in the chat history, right?
            session.addOutput(newMTfromMO(moMessage, noMatchText==null ? UNEXPECTED_INPUT_MESSAGE : noMatchText));
            return session.currentScript; // we don't advance in this case.
        }

        /*
         * FIXME This should really be fetched from a database per shortcode and not handled by this
         */
        public static Script constructTopicScript(Session session, Message moMessage) throws IOException {

            session.currentScript = Functions.findTopicScript(session, moMessage);

            return Present.evaluate(session, moMessage);
        }
    }

}

