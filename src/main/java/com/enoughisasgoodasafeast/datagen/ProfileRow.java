package com.enoughisasgoodasafeast.datagen;

import org.jeasy.random.annotation.Randomizer;
import org.jeasy.random.randomizers.FirstNameRandomizer;
import org.jeasy.random.randomizers.LastNameRandomizer;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

public class ProfileRow {
    // id              | uuid
    // surname         | character varying(36)
    // given_name      | character varying(36)
    // other_languages | character varying(23)
    // created_at      | timestamp with time zone
    // updated_at      | timestamp with time zone
    // roles           | character varying(64)

    // Leaving out roles (for now) since they are used/set only brbl_admin
    public static final String[] headers = {"id", "surname", "given_name", "other_languages", "created_at", "updated_at"};

    UUID id;

    @Randomizer(FirstNameRandomizer.class)
    String givenName;

    @Randomizer(LastNameRandomizer.class)
    String surname;

    // FIXME need a comma separated string of 0-3 LanguageCodes
    String otherLanguages;

    Instant createdAt;

    Instant updatedAt;

    public ProfileRow(UUID id, String surname, String givenName, String otherLanguages, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.givenName = givenName;
        this.surname = surname;
        this.otherLanguages = otherLanguages;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String[] headers() {
        return headers;
    }

    public String[] values() {
        return new String[]{id.toString(), surname, givenName, otherLanguages, createdAt.toString(), updatedAt.toString()};
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
