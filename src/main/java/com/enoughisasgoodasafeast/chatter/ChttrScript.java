package com.enoughisasgoodasafeast.chatter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * The collection of exchanges that comprise a client script.
 */
public class ChttrScript implements Serializable {
    private static final long serialVersionUID = 1L;

    public final List<Exchange> exchanges = new ArrayList<>();

    @Override
    public String toString() {
        return new StringJoiner(", ", ChttrScript.class.getSimpleName() + "[", "]")
                .add("exchanges=" + exchanges)
                .toString();
    }
}
