package com.enoughisasgoodasafeast.chatter;

import com.enoughisasgoodasafeast.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UserActor {

    private static final Logger LOG = LoggerFactory.getLogger(UserActor.class);

    private final String phoneNumber;
    private final ChttrScript script;
    private final Set<Exchange> visitedExchanges;

    final List<Message> rcvdMessages;
    final List<Message> sentMessages;

    public UserActor(String phoneNumber, ChttrScript script) {
        this.phoneNumber = phoneNumber;
        this.script = script;
        final int size = script.exchanges.size();
        this.visitedExchanges = new HashSet<>(size);
        this.rcvdMessages = new ArrayList<>(size);
        this.sentMessages = new ArrayList<>(size);
        LOG.info("UserActor created: phone num: {} script: {}", phoneNumber, script);
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public ChttrScript getScript() {
        return script;
    }

    public void visit(Exchange exchange) {
        visitedExchanges.add(exchange);
    }

    public boolean hasVisited(Exchange exchange) {
        return visitedExchanges.contains(exchange);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", UserActor.class.getSimpleName() + "[", "]")
                .add("phoneNumber='" + phoneNumber + "'")
                .add("script=" + script)
                .add("visitedExchange=" + visitedExchanges)
                .add("rcvdMessages=" + rcvdMessages)
                .add("sentMessages=" + sentMessages)
                .toString();
    }
}
