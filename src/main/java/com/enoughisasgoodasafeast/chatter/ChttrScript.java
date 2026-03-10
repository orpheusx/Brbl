package com.enoughisasgoodasafeast.chatter;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/**
 * The collection of exchanges that comprise a client script.
 */
public class ChttrScript implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<Event> events;

    public ChttrScript(List<Event> events) {
        this.events = events;
    }

    public List<Event> getEvents() {
        return events;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ChttrScript.class.getSimpleName() + "[", "]")
                .add("events=" + events)
                .toString();
    }
}
