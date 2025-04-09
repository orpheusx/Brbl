package com.enoughisasgoodasafeast.integration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Just enough of a wrapper to be able to avoid using GenericContainer directly.
 */
public class BrblContainer extends GenericContainer<BrblContainer> {

    private static final String BURBLE_CONTAINER = "burble-jvm:0.1.0";

    public BrblContainer() {
        super(DockerImageName.parse(BURBLE_CONTAINER));
    }
}
