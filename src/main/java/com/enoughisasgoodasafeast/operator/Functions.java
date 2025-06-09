package com.enoughisasgoodasafeast.operator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public class Functions {
    private static final Logger LOG = LoggerFactory.getLogger(Functions.class);

    public static String renderForPlatform(Platform platform, String mtText) {
        return switch (platform) { // TODO an actual implementation
            default -> mtText;
        };
    }

    /*
     * Usable by Scripts that only have/expect a single edge
     */
    public static Node advanceToFirstAndOnly(ScriptContext context) {
        if (context.getCurrentScript().edges().isEmpty()) {
            LOG.info("End of node, '{}', reached.", context.getCurrentScript().label());
            return null;
        } else {
            assert context.getCurrentScript().edges().size() == 1;
            final Node nextNode = context.getCurrentScript().edges().getFirst().node(); // only one Edge available
            LOG.info("Advancing from {} dispatching to {}", context.getCurrentScript().label(), nextNode);
            return nextNode;
        }
    }

    public static void waitSeconds(int num) {
        try {
            Thread.sleep(Duration.ofSeconds(num));
        } catch (InterruptedException e) {
            LOG.error("waitSeconds was interrupted", e);
        }
    }

    // FIXME Investigate fast regexes here.
    public static List<String> parseMatchTextPatterns(String t) {
        // For now, split on the pipe character
        String[] patterns = t.split("\\|");
        return Arrays.asList(patterns);
    }

    // NB: for now we're putting this logic here. As we start adding database persistence we might make a dedicated
    // class that bundles all such logic.
    // Also, for now, this is just hard-coded for a limited number of cases to make tests work.
//    public static Node findTopicScript(Session session, Message message) {
//        return switch (message) {
//            case Message m when m.to().equals("45678") -> {
//                String text = """
//                    Here are the topics I can talk about:
//                    1) wolverines
//                    2) international monetary policy
//                """;
//                Node topicPresentation = new Node(text, NodeType.PresentMulti, "topic-selection");
//                Node endScript = new Node("End-of-Conversation", NodeType.ProcessMulti, "e-o-c");
//                Edge topicOne = new Edge(List.of("1", "wolverine", "wolverines"), "They have pointy teeth and a nasty disposition", endScript);
//                Edge topicTwo = new Edge(List.of("1", "international", "monetary", "policy"), "It's kinda boring actually.", endScript);
//                endScript.next().add(topicOne);
//                endScript.next().add(topicTwo);
//                topicPresentation.next().add(new Edge(null, null, endScript));//define a constant that means "unset" instead of overloading null.
//                yield topicPresentation;
//            } // more to come before we fully implement using a database?
//            default -> null;
//        };
//    }

}
