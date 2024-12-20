package com.enoughisasgoodasafeast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

/**
 * Rather than building separate binaries for each of the main programs (and likewise building a separate container for
 * each of them) we'll try using a dispatching program that can launch any of them with an overridable arg.
 */
public class Burble {

    private static final Logger LOG = LoggerFactory.getLogger(Burble.class);

    public static void main(String[] args) throws InterruptedException, IOException, TimeoutException {
        // FIXME remove this temp hack
        if (args == null || args.length == 0) {
            args = "Rcvr default1 default2 default3".split(" ");
            LOG.warn("No args provided. Using defaults...");
        }

        final String program = args[0];
        final String[] pargs = Arrays.stream(args, 1, args.length).toArray(String[]::new);

        LOG.info("Executing {} with arguments: {}", program, pargs);
        switch (program.toLowerCase()) {
            case "rcvr" ->
                Rcvr.main(args);

            case "sndr" ->
                Sndr.main(args);

            case "fakeoperator" ->
                FakeOperator.main(args);

            case "platformgatewaymt" ->
                PlatformGatewayMT.main(args);

            default ->
                LOG.error("Unrecognized program: {}", program);

        }
    }

}
