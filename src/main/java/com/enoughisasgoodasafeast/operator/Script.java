package com.enoughisasgoodasafeast.operator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * The instructions for processing a message.
 * (Trying this as a Record for now. The state will have to be tracked separately.)
 *
 * @param id
 * @param text
 * @param next
 * @param previous
 */
public record Script(UUID id, String text, List<Script> next, Script previous) {

    private static final Logger LOG = LoggerFactory.getLogger(User.class);

    public Script {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text cannot be null or empty.");
        }

        LOG.info("Created Script {}", id);
    }

    public boolean hasNext() {
        return next != null && next.size() > 0;
    }

    public boolean isStart() {
        return previous == null;
    }

    public String render() {
        return text;
    }

}
