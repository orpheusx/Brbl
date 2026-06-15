package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.enoughisasgoodasafeast.Message.newMTfromMO;
import static com.enoughisasgoodasafeast.operator.Functions.advanceToFirstAndOnly;
import static com.enoughisasgoodasafeast.operator.Functions.renderForPlatform;

public class Opt {
    private static final Logger LOG = LoggerFactory.getLogger(com.enoughisasgoodasafeast.operator.Opt.class);

    static class In {
        static ProcessStateNode evaluate(ScriptContext context, Message moMessage) {
            LOG.info("Input.Request evaluating '{}'", context.getCurrentNode());
            return null;
        }
    }
    static class Out {
        static ProcessStateNode evaluate(ScriptContext context, Message moMessage) {
            LOG.info("Opt.Out evaluating '{}'", context.getCurrentNode());
            context.registerOutput(
                    newMTfromMO(moMessage,
                            renderForPlatform(moMessage.platform(), context.getCurrentNode().text())
                    )
            );
            return new ProcessStateNode(ProcessState.OK, advanceToFirstAndOnly(context));
        }
    }
}
