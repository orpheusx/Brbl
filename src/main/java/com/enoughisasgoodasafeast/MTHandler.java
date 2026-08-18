package com.enoughisasgoodasafeast;

import java.util.Properties;

public interface MTHandler {

    StatusException send(Message payload);

    static MTHandler newHandler(Properties properties) {
        return null;
    }

}