package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.MOMessage;
import com.enoughisasgoodasafeast.MTMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Multichoice {

    private static final Logger LOG = LoggerFactory.getLogger(Multichoice.class);

    class Present {

        public static Script evaluate(Session session, MOMessage moMessage) throws IOException {
            String mtText = String.format("%s", session.currentScript.text());
            // Remember the from and to fields of the MT must be the reverse of the MO
            MTMessage mt = new MTMessage(moMessage.to(), moMessage.from(), mtText);
            session.producer.enqueue(mt);
            return advance(session);
        }
    }

    class Process {

        public static Script evaluate(Session session, MOMessage moMessage) throws IOException {
            return null;
        }
    }

    static Script advance(Session session) {
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
//
}

