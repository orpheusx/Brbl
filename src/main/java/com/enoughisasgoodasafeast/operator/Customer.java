package com.enoughisasgoodasafeast.operator;

import java.util.UUID;

import static com.enoughisasgoodasafeast.SharedConstants.*;

/**
 * This is just a placeholder for the moment that illustrates how we might use
 * composition of records usefully. The backing table would, presumably, be joined
 * with the User table, enabling a Customer to seamlessly work like a User.
 *
 * @param user
 * @param profile
 * @param companyName optional
 */
public record Customer(UUID id, User user, Profile profile, /*String firstName, String surname,*/ String companyName) {

    public Customer {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (user == null) {
            fail("user cannot be null");
        }
        if (companyName == null || companyName.isEmpty()) {
            companyName = NO_COMPANY;
        }
    }

    void fail(String message) {
        throw new IllegalArgumentException(message);
    }
}
