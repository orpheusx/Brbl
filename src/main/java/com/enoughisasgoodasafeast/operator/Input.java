package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.enoughisasgoodasafeast.Message.newMTfromMO;
import static com.enoughisasgoodasafeast.operator.Functions.advanceToFirstAndOnly;
import static com.enoughisasgoodasafeast.operator.Functions.renderForPlatform;

public class Input {

    private static final Logger LOG = LoggerFactory.getLogger(Input.class);

    static class Request {

        // NOTE: This is nearly identical to SendMessage.evaluate. // TODO DRY opportunity here
        public static Node evaluate(ScriptContext context, Message moMessage) {
            LOG.info("Input.Request evaluating '{}'", context.getCurrentScript());
            context.registerOutput(
                    newMTfromMO(moMessage,
                            renderForPlatform(moMessage.platform(), context.getCurrentScript().text())
                    )
            );
            return advanceToFirstAndOnly(context);
        }
    }

    static class Process {
        public static Node evaluate(ScriptContext context, Message moMessage) {
            String userText = moMessage.text().trim().toLowerCase();
            LOG.info("Input.Process evaluating '{}'", userText);

            // TODO Check for zero length responses?
            // TODO Run a sentiment analysis and compute an appropriate response?
            // TODO Persist the response + user to a Poll record?

            String responseText = context.getCurrentScript().text();
            final Message mt = newMTfromMO(moMessage, responseText);

            context.registerOutput(mt);
            //LOG.info("Enqueued {}", mt);

            return advanceToFirstAndOnly(context);
        }
    }
}
