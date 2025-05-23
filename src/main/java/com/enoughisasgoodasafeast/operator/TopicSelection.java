package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TopicSelection {
    private static final Logger LOG = LoggerFactory.getLogger(TopicSelection.class);

    public static Script evaluate(Session session, Message moMessage) throws IOException {
        return new Script("This is a topic selection",
                ScriptType.TopicSelection, null);
    }

}
