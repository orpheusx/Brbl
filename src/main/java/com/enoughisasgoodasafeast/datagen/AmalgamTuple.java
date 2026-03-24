package com.enoughisasgoodasafeast.datagen;

import org.jspecify.annotations.NonNull;

import java.util.StringJoiner;
import java.util.UUID;

import static com.enoughisasgoodasafeast.Functions.randomUUID;

/**
 * Association of related rows.
 */
public record AmalgamTuple(AmalgamRow amalgamRow, UserRow userRow, ProfileRow profileRow, CustomerRow customerRow, CompanyRow companyRow) {

    public AmalgamTuple(UserRow userRow, ProfileRow profileRow, CustomerRow customerRow, CompanyRow companyRow) {
        final var ar = new AmalgamRow(
                randomUUID(),
                userRow.id,
                (null==profileRow) ? null : profileRow.id,
                (null==customerRow) ? null : customerRow.id,
                userRow.createdAt,
                userRow.updatedAt,
                companyRow.id
                );
        this(ar, userRow, profileRow, customerRow, companyRow);
    }

    public AmalgamTuple(UUID groupId, UserRow userRow, CompanyRow companyRow) {
        final var ar = new AmalgamRow(groupId, userRow.id, null, null, userRow.createdAt, userRow.updatedAt, companyRow.id);
        this(ar, userRow, null, null,companyRow);
    }

    public String getGroupId() {
        return amalgamRow.groupId.toString();
    }

    @NonNull
    public String toString() {
        return new StringJoiner(", ", AmalgamTuple.class.getSimpleName() + "[", "]")
                .add(userRow.toString())
                .add((profileRow==null) ? "[No Profile]" : profileRow.toString())
                .add((customerRow==null) ? "[No Customer]" : customerRow.toString())
                .toString();
    }
}


