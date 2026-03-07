package com.enoughisasgoodasafeast.chatter;

import org.jspecify.annotations.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.StringJoiner;

/**
 * An Exchange is a pairing of a received bit of text and the (possibly multiple) bits of text that are valid responses.
 * It is constructed from a given Node and its Edges. It is meant to simplify the server-side model for use by a Chttr client.
 * We generally only expect Nodes of type PresentMulti to produce
 *
 * @param mtText the text sent to the client by our server.
 * @param moResponses the list of possible text responses
 */
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
