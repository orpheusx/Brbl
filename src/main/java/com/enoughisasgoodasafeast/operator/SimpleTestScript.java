package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.SharedConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.enoughisasgoodasafeast.Message.newMT;

public class SimpleTestScript {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleTestScript.class);

    static Script computeNextScript(Session session) {
        if (session.currentScript.next().isEmpty()) {
            return null;
        } else {
            return session.currentScript.next().getFirst().script();
        }
//        if (!n.await()) {
//            return n.evaluate(session, moMessage);
//        } else {
//            return n;
//        }
    }

    /*
     * Sends
     */
    public static class SimpleEchoResponseScript {
        public static Script evaluate(Session session, Message moMessage) throws IOException {
            String mtText = String.format("%s: %s", session.currentScript.text(), moMessage.text());
            // Remember the from and to fields of the MT must be the reverse of the MO
            Message mt = newMT(moMessage.to(), moMessage.from(), mtText);
            session.addOutput(mt);
            StringBuilder builder = new StringBuilder("Evaluated MO text '")
                    .append(moMessage.text())
                    .append("' -> MT text '")
                    .append(mtText)
                    .append("'");
            LOG.info(builder.toString());

            return computeNextScript(session);
        }
    }

    public static class ReverseTextResponseScript {
        public static Script evaluate(Session session, Message moMessage) throws IOException {
            String mtText = new StringBuilder(moMessage.text()).reverse().toString();
            Message mt = newMT(moMessage.to(), moMessage.from(), mtText);
            session.addOutput(mt);

            StringBuilder builder = new StringBuilder("Evaluated MO text '")
                    .append(moMessage.text())
                    .append("' -> MT text '")
                    .append(new StringBuilder(moMessage.text()).reverse())
                    .append("'");
            LOG.info(builder.toString());
            return computeNextScript(session);
        }
    }

    /*
     * Expects the MO to follow the format '<num> hello'
     * and will send a response: '<num> goodbye'
     */
    public static class HelloGoodbyeResponseScript {
        public static Script evaluate(Session session, Message moMessage) throws IOException {
            String[] moText = moMessage.text().split(SharedConstants.TEST_SPACE_TOKEN, 2);
            Message mt = null;
            if (moText[1].equals("hello")) {
                mt = newMT(moMessage.to(), moMessage.from(), moText[0] + " " + "goodbye");
            } else {
                mt = newMT(moMessage.to(), moMessage.from(), "Unexpected input: " + moText);
            }
            session.addOutput(mt);

            StringBuilder builder = new StringBuilder("Evaluated MO text '")
                    .append(moMessage.text())
                    .append("' -> MT text '")
                    .append(new StringBuilder(moMessage.text()).reverse())
                    .append("'");
            LOG.info(builder.toString());
            return computeNextScript(session);
        }
    }
}
