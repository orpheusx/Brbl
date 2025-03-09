package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.MOMessage;
import com.enoughisasgoodasafeast.MTMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Multi {

    private static final Logger LOG = LoggerFactory.getLogger(Multi.class);

    static class Present {

        public static Script evaluate(Session session, MOMessage moMessage) throws IOException {
            LOG.info("Multi.Present evaluating '{}'", moMessage.text());
            // Is this the right place to handle platform-dependent message text formatting?
            String mtText = Functions.renderForPlatform(moMessage.platform(), session.currentScript().text());
            MTMessage mt = MTMessage.create(moMessage, mtText);
            session.addOutput(mt);
            return advance(session);
        }

        static Script advance(Session session) {
            if (session.currentScript.next().isEmpty()) {
                LOG.info("End of script, '{}', reached.", session.currentScript.label());
                return null;
            } else {
                final Script nextScript = session.currentScript.next().getFirst().script();
                LOG.info("Multi.Present dispatching to {}", nextScript);
                return nextScript;
            }
        }
    }

    static class Process {

        final static String UNEXPECTED_INPUT_MESSAGE = """
                Oops, that's not one of the options. Try again with one of the listed numbers
                or 'change topic' to start talking about something else.
                """;

        public static Script evaluate(Session session, MOMessage moMessage) {
            LOG.info("Multi.Process evaluating '{}'", moMessage.text());
            String noMatchText = session.currentScript.text();
            for (ResponseLogic option : session.currentScript.next()) {
                if (option.matchText().contains(moMessage.text().trim().toLowerCase())) { //TODO make the matching more robust/flexible.
                    LOG.info("Input, {}, matched logic: {}", moMessage.text().trim(), option.matchText());
                    final MTMessage mt = MTMessage.create(moMessage, option.text());
                    session.addOutput(mt);
                    LOG.info("Enqueued {}", mt);
                    //FIXME Maybe session should collect MTs and send them all in order at the end of the process() call?
                    // This would make it more transactional. Yes! Let's do this.
                    return option.script();
                }
            }

            // Handle the "I want to talk about something else" case here...
            if (moMessage.text().trim().toLowerCase().contains("change topic")) {
                MTMessage changeTopic = MTMessage.create(moMessage, "");
                session.addOutput(changeTopic);
//                Script topLevel = ... // find the current customer's "top of funnel" Script.
                return null; // TODO return a non-null Script
            }


            // TODO "go back to revisit a previous stage"?

            // Still here? Provide a bad input message
            session.addOutput(MTMessage.create(moMessage, noMatchText==null ? UNEXPECTED_INPUT_MESSAGE : noMatchText));
            return session.currentScript; // we don't advance in this case.
        }
    }

//
}

