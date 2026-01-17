package com.enoughisasgoodasafeast;

import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;
import java.util.Random;
import java.util.UUID;

public class Functions {

    private static final TimeBasedEpochRandomGenerator UUID_GENERATOR =
            new TimeBasedEpochRandomGenerator(new Random(System.currentTimeMillis()));
    public static UUID randomUUID() {
        return UUID_GENERATOR.generate();
    }
}
