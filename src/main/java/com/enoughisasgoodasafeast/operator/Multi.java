package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.MOMessage;
import com.enoughisasgoodasafeast.MTMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class Multi {

    private static final Logger LOG = LoggerFactory.getLogger(Multi.class);

    static class Present {

        public static Script evaluate(Session session, MOMessage moMessage) throws IOException {
            LOG.info("Multi.Present evaluating '{}'", moMessage.text());
            String mtText = formatForPlatform(moMessage.platform(), session.currentScript.text());
            MTMessage mt = MTMessage.create(moMessage, mtText);
            session.producer.enqueue(mt);
            return advance(session);
        }

        static String formatForPlatform(Platform platform, String mtText) {
            return switch (platform) {
                default -> mtText;
            };
        }
    }

    static class Process {

        final static String UNEXPECTED_INPUT_MESSAGE = """
                Oops, that's not one of the options. Try again with one of the listed numbers
                or enter 'change' to start talking about something else.
                """;

        public static Script evaluate(Session session, MOMessage moMessage) throws IOException {
            LOG.info("Multi.Process evaluating '{}'", moMessage.text());
            String noMatchText = session.currentScript.text();
            for (ResponseLogic option :  session.currentScript.next()) {
                if (option.matchText().contains(moMessage.text().trim().toLowerCase())) { //TODO make the matching more robust/flexible.
                    LOG.info("Input, {}, matched logic: {}", moMessage.text().trim(), option.matchText());
                    final MTMessage mt = MTMessage.create(moMessage, option.text());
                    session.producer.enqueue(mt);
                    LOG.info("Enqueued {}", mt);
                    //FIXME Maybe session should collect MTs and send them all in order at the end of the process() call?
                    // This would make it more transactional. Yes! Let's do this.
                    return option.script();
                }
                // TODO handle the "I want to talk about something else" case here...
            }
            // TODO "go back to revisit a previous stage"?

            // Still here? Provide a general oops message (TODO use the appropriate language

            session.producer.enqueue(MTMessage.create(moMessage, UNEXPECTED_INPUT_MESSAGE));
            return session.currentScript; // we don't advance in this case.
        }
    }

    static Script advance(Session session) {
        if (session.currentScript.next().isEmpty()) {
            LOG.info("End of script reached." + session.currentScript.label());
            return null;
        } else {
            LOG.info("Multi.Present dispatching to {}", session.currentScript.next().getFirst().script());
            return session.currentScript.next().getFirst().script();
        }
//        if (!n.await()) {
//            return n.evaluate(session, moMessage);
//        } else {
//            return n;
//        }
    }
//
}

