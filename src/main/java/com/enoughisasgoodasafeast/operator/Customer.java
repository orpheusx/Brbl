package com.enoughisasgoodasafeast.operator;

import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static com.enoughisasgoodasafeast.SharedConstants.*;

/**
 * A Customer represents a registered, paying user of Brbl.
 * It is built on top of a Profile which is built on top of a User.
 * It
 *
 * @param user
 * @param profile
 * @param companyName optional
 */
public record Customer(UUID id, User user, Profile profile, String companyName) {

    public Customer {
        if (id == null) {
            id = randomUUID();
        }
        if (user == null) {
            throw new IllegalArgumentException("user cannot be null");
        }
        if (companyName == null || companyName.isEmpty()) {
            companyName = NO_COMPANY;
        }
    }

}
