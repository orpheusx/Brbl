package com.enoughisasgoodasafeast.chatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserActor {

    private static final Logger LOG = LoggerFactory.getLogger(UserActor.class);

    private final String phoneNumber;
    private final ChttrScript script;
    private int currentExchangeIndex = 0;

    public UserActor(String phoneNumber, ChttrScript script) {
        this.phoneNumber = phoneNumber;
        this.script = script;
        LOG.info("UserActor has been created: phone num {} script {}", phoneNumber, script);
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public ChttrScript getScript() {
        return script;
    }

    public Exchange getNextExchange() {
        if (currentExchangeIndex < script.exchanges.size()) {
            return script.exchanges.get(currentExchangeIndex++);
        }
        return null;
    }

    public boolean isFinished() {
        return currentExchangeIndex >= script.exchanges.size();
    }

    @Override
    public String toString() {
        return "UserActor{" +
                "phoneNumber='" + phoneNumber + '\'' +
                ", currentExchangeIndex=" + currentExchangeIndex +
                '}';
    }

}
