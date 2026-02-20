package com.enoughisasgoodasafeast.chatter;

import java.io.Serializable;
import java.util.List;
import java.util.StringJoiner;

public class Exchange implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String mtText;
    public final List<String> moResponses;

    public Exchange(String mtText, List<String> moResponses) {
        this.mtText = mtText;
        this.moResponses = moResponses;
    }

    @Override
    public String toString() {
        return new StringJoiner("|", Exchange.class.getSimpleName() + "[", "]")
                .add("mtText='" + mtText + "'")
                .add("moResponses=" + moResponses)
                .toString();
    }
}
