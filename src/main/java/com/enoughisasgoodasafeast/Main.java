package com.enoughisasgoodasafeast;

import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static java.io.IO.println;

public class Main {
    static void main() {
        println("Hello world!");
        final UUID uuid7 = randomUUID();
        println(uuid7);
        UUID uuid4 = randomUUID();
        println(uuid4);
    }
}