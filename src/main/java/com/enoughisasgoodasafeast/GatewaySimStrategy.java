package com.enoughisasgoodasafeast;

public class GatewaySimStrategy {

    int counter;

    // Create inner classes for different cases?

    /**
     * Reject the first message we see.
     *
     * @param message
     * @return
     */
    boolean canAccept(String message) {
        counter++;
        if (counter == 1) {
            return false;
        } else {
            return true;
        }
    }
}
