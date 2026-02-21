package com.enoughisasgoodasafeast;

import java.util.Properties;

public interface MOHandler {

    boolean handle(Message payload);

    static MOHandler newHandler(Properties properties) {
        return null;
    }

}
