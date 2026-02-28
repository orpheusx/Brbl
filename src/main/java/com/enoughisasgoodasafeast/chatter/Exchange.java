package com.enoughisasgoodasafeast.chatter;

import org.jspecify.annotations.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.StringJoiner;

public record Exchange(String mtText, List<String> moResponses) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public Exchange(@NonNull String mtText, @NonNull List<String> moResponses) {
        this.mtText = mtText;
        this.moResponses = moResponses;
    }

    @NonNull
    public String toString() {
        return new StringJoiner("|", Exchange.class.getSimpleName() + "[", "]")
                .add("mtText='" + mtText + "'")
                .add("moResponses=" + moResponses)
                .toString();
    }
}
