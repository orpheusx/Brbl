package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.MOMessage;
import com.enoughisasgoodasafeast.MTMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SimpleEchoResponseScript {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleEchoResponseScript.class);

    public static void evaluate(Session session, MOMessage moMessage) throws IOException {
        String mtText = String.format("%s: %s", session.currentScript.text(), moMessage.text());
        // Remember the from and to fields of the MT must be the reverse of the MO
        MTMessage mt = new MTMessage(moMessage.to(), moMessage.from(), mtText);
        session.addOutput(mt);
        StringBuilder builder = new StringBuilder("Evaluated MO text '");
        builder
                .append(moMessage.text())
                .append("' -> MT text '")
                .append(mtText)
                .append("'");
        LOG.info(builder.toString());

    }
}
