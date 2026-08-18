package com.enoughisasgoodasafeast;

import java.util.Properties;

public interface MOHandler {

    StatusException send(Message payload);

    static MOHandler newHandler(Properties properties) {
        return null;
    }

}
