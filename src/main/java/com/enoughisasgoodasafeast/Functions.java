package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.chatter.ScriptInterpreter;
import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.UUID;

public class Functions {

    private static final Logger LOG = LoggerFactory.getLogger(Functions.class);

    private static final TimeBasedEpochRandomGenerator UUID_GENERATOR =
            new TimeBasedEpochRandomGenerator(new Random(System.currentTimeMillis()));

    public static UUID randomUUID() {
        return UUID_GENERATOR.generate();
    }

    static void main() {
        for (int i = 0; i < 10; i++) {
            LOG.info("{}", randomUUID());
        }
    }
}
