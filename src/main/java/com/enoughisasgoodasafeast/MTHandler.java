package com.enoughisasgoodasafeast;

import java.util.Properties;

public interface MTHandler {

    boolean handle(Message payload);

    static MTHandler newHandler(Properties properties) {
        return null;
    }
}