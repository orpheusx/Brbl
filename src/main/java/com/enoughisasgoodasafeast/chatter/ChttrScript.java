package com.enoughisasgoodasafeast.chatter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.StringJoiner;

/**
 * The collection of Events that comprise a client script.
 */
public record ChttrScript(List<Event> events) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
