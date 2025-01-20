package com.enoughisasgoodasafeast.unit;

import com.enoughisasgoodasafeast.Rcvr;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class RcvrTest {

    @Test
    public void testNoArgConstructor() {
        Rcvr rcvr = new Rcvr();
        Assertions.assertNotNull(rcvr);
    }


    public static void main(String[] args) throws UnknownHostException {
        InetAddress localhost = InetAddress.getLocalHost();
        String ipAddress = localhost.getHostAddress();
        System.out.println("ipAddress=" + ipAddress);
    }
}
