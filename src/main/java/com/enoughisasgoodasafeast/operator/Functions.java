package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

public class Functions {
    private static final Logger LOG = LoggerFactory.getLogger(Functions.class);

    public static String renderForPlatform(Platform platform, String mtText) {
        return switch (platform) { // TODO an actual implementation
            default -> mtText;
        };
    }

    public static void waitSeconds(int num) {
        try {
            Thread.sleep(Duration.ofSeconds(num));
        } catch (InterruptedException e) {
            LOG.error("waitSeconds was interrupted", e);
        }
    }

    // NB: for now we're putting this logic here. As we start adding database persistence we might make a dedicated
    // class that bundles all such logic.
    // Also, for now, this is just hard-coded for a limited number of cases to make tests work.
    public static Script findTopicScript(Session session, Message message) {
        return switch (message) {
            case Message m when m.to().equals("4567") -> {
                String text = """
                    Here are the topics I can talk about:
                    1) wolverines
                    2) international monetary policy
                """;
                Script topicPresentation = new Script(text, ScriptType.PresentMulti, null, "topic-selection");
                Script endScript = new Script("End-of-Conversation", ScriptType.ProcessMulti, null, "e-o-c");
                ResponseLogic topicOne = new ResponseLogic(List.of("1", "wolverine", "wolverines"), "They have pointy teeth and a nasty disposition", endScript);
                ResponseLogic topicTwo = new ResponseLogic(List.of("1", "international", "monetary", "policy"), "It's kinda boring actually.", endScript);
                endScript.next().add(topicOne);
                endScript.next().add(topicTwo);
                topicPresentation.next().add(new ResponseLogic(null, null, endScript));//define a constant that means "unset" instead of overloading null.
                yield topicPresentation;
            } // more to come before we fully implement using a database?
            default -> null;
        };
    }

}
