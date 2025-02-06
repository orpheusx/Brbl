package com.enoughisasgoodasafeast;

import io.jenetics.util.NanoClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

public class RcvrTest {

    @Test
    public void testNoArgConstructor() {
        Rcvr rcvr = new Rcvr();
        Assertions.assertNotNull(rcvr);
    }


    public static void main(/*String[] args*/) /*throws UnknownHostException*/ {
//        InetAddress localhost = InetAddress.getLocalHost();
//        String ipAddress = localhost.getHostAddress();
//        System.out.println("ipAddress=" + ipAddress);
//        long nanos = 1675080820000000000L; // Example nanoseconds since epoch
//
        final Instant instantUtc = NanoClock.systemUTC().instant();
        final Instant instantEDT = NanoClock.system(ZoneId.of("America/New_York")).instant();
//
        System.out.println(instantUtc);
        System.out.println(instantEDT); // still returns UTC time. Hmm...
    }
}
