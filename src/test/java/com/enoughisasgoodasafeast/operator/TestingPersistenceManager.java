package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;

import java.util.Properties;

public class TestingPersistenceManager implements PersistenceManager {

    @Override
    public boolean insertMO(Message message) {
        return true;
    }

    @Override
    public boolean insertProcessedMO(Message message, Session session) {
        return true;
    }

    @Override
    public boolean insertMT(Message message, Session session) {
        return true;
    }

    @Override
    public boolean insertDeliveredMT(Message message) {
        return true;
    }
}
