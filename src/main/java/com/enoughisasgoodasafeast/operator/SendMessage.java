package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.enoughisasgoodasafeast.Message.newMTfromMO;
import static com.enoughisasgoodasafeast.operator.Functions.*;
import static com.enoughisasgoodasafeast.operator.Functions.advanceToFirstAndOnly;

public class SendMessage {

    private static final Logger LOG = LoggerFactory.getLogger(SendMessage.class);

    static Node evaluate(ScriptContext context, Message moMessage) {
        LOG.info("SendMessage evaluating '{}'", moMessage.text()); // we're not really evaluating the message but...
        context.registerOutput(
                newMTfromMO(moMessage,
                        renderForPlatform(moMessage.platform(), context.getCurrentNode().text())
                )
        );
        return advanceToFirstAndOnly(context);
    }
}
