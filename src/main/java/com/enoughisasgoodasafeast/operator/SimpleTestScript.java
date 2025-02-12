package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.MOMessage;
import com.enoughisasgoodasafeast.MTMessage;
import com.enoughisasgoodasafeast.SharedConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SimpleTestScript {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleTestScript.class);

    static Script computeNextScript(Session session) {
        if (session.currentScript.next().isEmpty()) {
            return null;
        } else {
            return session.currentScript.next().getFirst();
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
    public class SimpleEchoResponseScript {
        public static Script evaluate(Session session, MOMessage moMessage) throws IOException {
            String mtText = String.format("%s: %s", session.currentScript.text(), moMessage.text());
            // Remember the from and to fields of the MT must be the reverse of the MO
            MTMessage mt = new MTMessage(moMessage.to(), moMessage.from(), mtText);
            session.producer.enqueue(mt);
            StringBuilder builder = new StringBuilder("Evaluated MO text '")
                    .append(moMessage.text())
                    .append("' -> MT text '")
                    .append(mtText)
                    .append("'");
            LOG.info(builder.toString());

            return computeNextScript(session);
        }
    }

    public class ReverseTextResponseScript {
        public static Script evaluate(Session session, MOMessage moMessage) throws IOException {
            String mtText = new StringBuilder(moMessage.text()).reverse().toString();
            MTMessage mt = new MTMessage(moMessage.to(), moMessage.from(), mtText);
            session.producer.enqueue(mt);

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
    public class HelloGoodbyeResponseScript {
        public static Script evaluate(Session session, MOMessage moMessage) throws IOException {
            String[] moText = moMessage.text().split(SharedConstants.TEST_SPACE_TOKEN, 2);
            MTMessage mt = null;
            if (moText[1].equals("hello")) {
                mt = new MTMessage(moMessage.to(), moMessage.from(), moText[0] + " " + "goodbye");
            } else {
                mt = new MTMessage(moMessage.to(), moMessage.from(), "Unexpected input: " + moText);
            }
            session.producer.enqueue(mt);

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
