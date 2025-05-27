package com.enoughisasgoodasafeast.operator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlatformTest {

    @Test
    void code() {
    }

    @Test
    void values() {
    }

    @Test
    void valueOf() {
        final Platform s = Platform.valueOf("SMS");
        final Platform b = Platform.valueOf("BRBL");
    }

    @Test
    void name() {
        System.out.println(Platform.BRBL);
        System.out.println(Platform.BRBL.code());
        System.out.println(Platform.SMS);
        System.out.println(Platform.SMS.code());
    }
}