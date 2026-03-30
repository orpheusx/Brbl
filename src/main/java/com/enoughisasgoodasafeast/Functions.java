package com.enoughisasgoodasafeast;

import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;

import java.io.IO;
import java.util.Random;
import java.util.UUID;

public class Functions {

    private static final TimeBasedEpochRandomGenerator UUID_GENERATOR =
            new TimeBasedEpochRandomGenerator(new Random(System.currentTimeMillis()));

    public static UUID randomUUID() {
        return UUID_GENERATOR.generate();
    }

    static void main() {
        for (int i = 0; i < 10; i++) {
            IO.println(randomUUID());
        }
    }
}
