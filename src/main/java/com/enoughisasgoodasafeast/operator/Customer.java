package com.enoughisasgoodasafeast.operator;

import static com.enoughisasgoodasafeast.SharedConstants.*;

/**
 * This is just a placeholder for the moment that illustrates how we might use
 * composition of records usefully. The backing table would, presumably, be joined
 * with the User table, enabling a Customer to seamlessly work like a User.
 *
 * @param user
 * @param firstName
 * @param surname
 * @param companyName optional
 */
public record Customer(User user, String firstName, String surname, String companyName) {

    public Customer {
        if (user == null) {
            fail("user cannot be null");
        }
        if (firstName == null || firstName.isEmpty()) {
            fail("firstName cannot be null");
        }
        if (surname == null || surname.isEmpty()) {
            fail("surname cannot be null");
        }
        if (companyName == null || companyName.isEmpty()) {
            companyName = NO_COMPANY;
        }
    }

    void fail(String message) {
        throw new IllegalArgumentException(message);
    }
}
