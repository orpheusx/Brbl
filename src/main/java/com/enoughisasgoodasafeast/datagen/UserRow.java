package com.enoughisasgoodasafeast.datagen;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

import com.enoughisasgoodasafeast.operator.CountryCode;
import com.enoughisasgoodasafeast.operator.LanguageCode;
import com.enoughisasgoodasafeast.operator.Platform;
import com.enoughisasgoodasafeast.operator.UserStatus;
import org.jeasy.random.annotation.Randomizer;
import org.jeasy.random.randomizers.*;

/**
 * A class used with EasyRandom to generate records for the USERS table.
 */
public class UserRow {
//    id            | uuid
//    platform_code | public.platform
//    platform_id   | character varying(36)
//    customer_id   | uuid
//    country       | public.country_code
//    language      | public.language_code
//    nickname      | character varying(36)
//    status        | user_status
//    created_at    | timestamp with time zone
//    updated_at    | timestamp with time zone

    UUID id;
    Platform platform;

    // FIXME subclass this annotation to strip out the parentheses, dashes and spaces.
    @Randomizer(PhoneNumberRandomizer.class)
    String platformId;

    CountryCode countryCode;

    LanguageCode languageCode;

    // FIXME create an annotation that selects from a list of nicknames instead of just random strings.
    // @RandomizerArgument(WordRandomizer.class)
    //    ["Ace", "Sparky", "Rocky"],
    //        "female": ["Sunny", "Bubbles", "Pixie"],
    //        "general": ["Buddy", "Chief", "Ace"]
    String nickname;

    UserStatus userStatus;

    Instant createdAt;

    Instant updatedAt;

    public UserRow(UUID id, Platform platform, String platformId, CountryCode countryCode, LanguageCode languageCode, String nickname, UserStatus userStatus, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.platform = platform;
        this.platformId = platformId;
        this.countryCode = countryCode;
        this.languageCode = languageCode;
        this.nickname = nickname;
        this.userStatus = userStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", UserRow.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("platform=" + platform)
                .add("platformId='" + platformId + "'")
                //.add("customerId=" + customerId)
                .add("countryCode=" + countryCode)
                .add("languageCode=" + languageCode)
                .add("nickname='" + nickname + "'")
                .add("userStatus=" + userStatus)
                .add("createdAt=" + createdAt)
                .add("updatedAt=" + updatedAt)
                .toString();
    }
}
