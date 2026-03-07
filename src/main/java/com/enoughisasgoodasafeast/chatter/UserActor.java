package com.enoughisasgoodasafeast.chatter;

import com.enoughisasgoodasafeast.Message;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UserActor {

    private static final Logger LOG = LoggerFactory.getLogger(UserActor.class);

    private final String phoneNumber;
    private final List<ChttrScript> scripts;

    private int numScripts, scriptIndex, numEvents, eventIndex;

    final List<Message> rcvdMessages;
    final List<Message> sentMessages;

    public UserActor(String phoneNumber, List<ChttrScript> scripts) {
        this.phoneNumber = phoneNumber;
        this.scripts = scripts;
        this.numScripts = scripts.size();
        this.numEvents = scripts.getFirst().getEvents().size();
        this.rcvdMessages = new ArrayList<>();
        this.sentMessages = new ArrayList<>();
        LOG.info("UserActor created: phone num: {} scripts: {}", phoneNumber, scripts);
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    // TODO Make synchronized?
    public ChttrScript getCurrentScript() {
        return scripts.get(scriptIndex);
    }

    public synchronized Event currentEvent() {
        // These will throw at runtime.
        final ChttrScript script = scripts.get(scriptIndex);
        return script.getEvents().get(eventIndex);
    }

    public synchronized @Nullable Event nextEvent() {
        if (1 + eventIndex >= numEvents) {
            eventIndex = 0;
            if (1 + scriptIndex >= numScripts) {
                LOG.warn("Reached end of scripts.");
                return null;
            }
            ++scriptIndex;
            numEvents = scripts.get(scriptIndex).getEvents().size();

        } else {
            ++eventIndex;
        }
        return currentEvent();
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", UserActor.class.getSimpleName() + "[", "]")
                .add("phoneNumber='" + phoneNumber + "'")
                .add("scripts=" + scripts)
                .add("rcvdMessages=" + rcvdMessages)
                .add("sentMessages=" + sentMessages)
                .toString();
    }
}
