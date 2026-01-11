package com.enoughisasgoodasafeast.datagen;

import org.jeasy.random.annotation.Randomizer;
import org.jeasy.random.randomizers.FirstNameRandomizer;
import org.jeasy.random.randomizers.LastNameRandomizer;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

public class ProfileRow {

    UUID id;

    @Randomizer(FirstNameRandomizer.class)
    String surname;

    @Randomizer(LastNameRandomizer.class)

    String givenName;

    // FIXME need a comma separated string of 0-3 LanguageCodes
    String otherLanguages;

    Instant createdAt;

    Instant updatedAt;

    public ProfileRow(UUID id, String givenName, String surname, String otherLanguages, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.givenName = givenName;
        this.surname = surname;
        this.otherLanguages = otherLanguages;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ProfileRow.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("surname='" + surname + "'")
                .add("givenName='" + givenName + "'")
                .add("otherLanguages='" + otherLanguages + "'")
                .add("createdAt=" + createdAt)
                .add("updatedAt=" + updatedAt)
                .toString();
    }

}
