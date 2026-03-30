package com.enoughisasgoodasafeast.datagen;

import com.enoughisasgoodasafeast.operator.*;
import net.datafaker.Faker;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

import static com.enoughisasgoodasafeast.datagen.Functions.adjustPlatformId;
import static com.enoughisasgoodasafeast.operator.CompanyStatus.*;
import static com.enoughisasgoodasafeast.operator.CountryCode.*;
import static com.enoughisasgoodasafeast.operator.Platform.*;
import static com.enoughisasgoodasafeast.operator.UserStatus.*;
import static java.io.IO.println;
import static java.lang.String.join;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.StandardOpenOption.*;
import static java.util.Comparator.comparing;

/**
 * A basic test data generator that creates a set of "known" elements for use with unit and integration
 * tests along with some more random load
 * NB: We're not aiming to generate massive amounts of data here.
 */
public class BrblGenerator {

    public static Logger LOG = LoggerFactory.getLogger(BrblGenerator.class);

    // Ordered from smallest to largest.
    public static final String[] standardCompanyNames = {
            "MicroCorp", "MiniCorp", "MegaCorp", "MondoCorp", "GigaCorp", "NadaCorp"
    };

    public static final CompanyStatus[] standardCompanyStatuses = {
            ACTIVE, ACTIVE, ACTIVE, SUSPENDED, ACTIVE, LAPSED
    };

    public static final String knownCompanyName = "Yoyodyne Systems"; // always ACTIVE

    public static final String knownCompanyId = "019d2055-922c-75f7-a80e-091f01382fa3";

    public static final String[] knownUserIds = {
            "019d2055-9235-73cc-8c75-2cb97dbbc285",
            "019d2055-9235-736d-908e-b1c0229b0392",
            "019d2055-9235-713a-8fb8-ef96c0b7b6c0",
            "019d2055-9235-72b6-a72a-bdbe4dfc6a07",
            "019d2055-9235-7465-bd7a-8805df51b3ee",
            "019d2055-9235-7849-a26d-199fb6738fbe",
            "019d2055-9235-7290-8266-37ce60819893",
            "019d2055-9235-789e-999b-3e47084d5081",
            "019d2055-9235-7c22-ade9-f6b416f123a7",
            "019d2055-9235-7c95-8610-1945de2d2910"
    };

    // The five ids here are correlated by index to the elements in knownUserIds
    // which means that the second half of knownUserIds are unlinked.
    public static final String[] knownLinkedUserIds = {
            "019d2553-bdcc-7433-b100-b4fb88d92b0a",
            "019d2553-bdcc-7abb-8a6e-f607f0045a15",
            "019d2553-bdcc-7a2a-82d1-5becd6ff7f9d",
            "019d2553-bdcc-72a1-a01f-596c19c9455c",
            "019d2553-bdcc-7142-81f2-4539a656bbda"
    };

    // public static final Platform[] knownPlatformsForUser = {SMS, SMS, SMS, SMS, SMS, SMS, SMS, SMS, SMS, SMS};

    public static final String[] knownNumbersForUsers = {
            "14167209458",  // Toronto area code.
            "526641112222", // A very fake number in Mexico City.
            "17817209450", // The rest are for U.S.
            "14157209451", // California
            "17817209452", // Massachusetts
            "17817209453",
            "17817209454",
            "19297209455", // NYC
            "17817209456",
            "19787209457",
    };

    public static final CountryCode[] knownCountryCodes = {US, US, US, US, US, US, US, US, CA, MX};

    public static final String[] knownProfileIds = {
            "019d2055-9235-7daf-98ce-695cea1f25bf",
            "019d2055-9235-7fab-8150-86496418de20",
            "019d2055-9235-75d0-83b4-173de282ff9e"
    };

    public static final String[] knownCustomerIds = {
            "019d2055-9235-7c52-bfe1-0bdeb8fbe6ca",
            "019d2055-9235-7354-aaae-067e97853f4a"
    };

    // The group_id values for each amalgam.
    // NB: Expected to be equal to size of knownNumbersForUsers.
    public static final String[] knownAmalgamIds = {
            "019d2055-9235-7e29-b0de-8903d9f662db",
            "019d2055-9235-7ce6-bfc6-4ccbb9c16a89",
            "019d2055-9235-7073-a9ae-9dbaf02e817c",
            "019d2055-9235-72f4-8f7e-3b23435a5717",
            "019d2055-9235-74fc-bbf1-da2f32199ffc",
            "019d2055-9235-7a1c-9d1d-268d9ec1b6ba",
            "019d2055-9235-7bce-acf2-33353f7287df",
            "019d2055-9236-7b00-a680-f7d417788432",
            "019d2055-9236-7806-b193-24270d2698d0",
            "019d2055-9236-7701-b644-114d0e32a565"
    };

    public static final String DLM = "\t";

    private final Faker faker;
    private final EasyRandom easyRandom;

    private final LocalDate newestDate = LocalDate.now();
    private final LocalDate oldestDate = newestDate.minusMonths(9);

    public BrblGenerator() {
        this.faker = new Faker(Locale.ENGLISH);
        this.easyRandom = new EasyRandom(new EasyRandomParameters()
                .seed(System.currentTimeMillis())
                .stringLengthRange(3, 20)
                .dateRange(oldestDate, newestDate)
        );
    }

    public List<CompanyAndAmalgamTuples> generateKnown() {
        List<CompanyAndAmalgamTuples> generatedData = new ArrayList<>();

        // Create known company
        final var company = newCompany(knownCompanyName, ACTIVE, knownCompanyId);
        final var companyAmalgams = new CompanyAndAmalgamTuples(company);

        final var numUsers = knownUserIds.length;
        final var numCustomers = knownCustomerIds.length;
        final var numProfiles = knownProfileIds.length;

        LOG.info("generateKnown: Creating company, {}, with {} users, {} profiles and {} customers.", company.name, numUsers, numProfiles, numCustomers);

        // assert numUsers > numCustomers && numCustomers > numProfiles;
        assert knownUserIds.length == knownAmalgamIds.length;

        for (int j = 0, k = 0, m = 0; j < numUsers; j++, k++, m++) { // Only supporting SMS users, for now.
            var user = newUser(knownUserIds[j], SMS, knownNumbersForUsers[j], IN, knownCountryCodes[j]);

            ProfileRow profile = null;
            CustomerRow customer = null;

            if (k < numProfiles) {
                profile = newProfile();
                if (m < numCustomers) {
                    customer = newCustomer(profile, company.id, company.status);
                }
            }

            var tuple = new AmalgamTuple(user, profile, customer, company, knownAmalgamIds[j]);
            companyAmalgams.addTuple(tuple);
        }

        // Add some linked users
        int numLinkedUsers = knownLinkedUserIds.length;
        for (int p = 0; p < numLinkedUsers; p++) {
            var tuple = companyAmalgams.amalgamTuples().get(p);
            Platform platform = (SMS == tuple.userRow().platform) ? WAP : SMS;
            UserRow linkedUser = tuple.userRow().withIdPlatform(knownLinkedUserIds[p], platform); // recall that WhatsApp uses the same phone number used for SMS.
            companyAmalgams.addTuple(new AmalgamTuple(tuple.amalgamRow().groupId, linkedUser, company));
        }

        generatedData.add(companyAmalgams);

        return generatedData;
    }

    public List<CompanyAndAmalgamTuples> generateRandom(int minUsers, int maxUsers) {

        assert minUsers < maxUsers; // 10 < 100

        int increment = (maxUsers - minUsers) / standardCompanyNames.length; // e.g. 100 - 10 = 90 / 6 = 15 increment
        // 10, [25, 40, 55, 70, 85], 100
        LOG.info("Incrementing user counts by {} per company.", increment);

        List<CompanyAndAmalgamTuples> generatedData = new ArrayList<>();

        // Create six, standard companies with the above names and states.
        for (int i = 0; i < standardCompanyNames.length; i++) {

            final var company = newCompany(standardCompanyNames[i], standardCompanyStatuses[i], null);
            final var companyAmalgams = new CompanyAndAmalgamTuples(company);

            final var numUsers = minUsers + (i * increment);
            final var numCustomers = 2 + i;
            final var numProfiles = numCustomers + i;

            LOG.info("Creating company, {}, with {} users, {} profiles and {} customers.", company.name, numUsers, numProfiles, numCustomers);

            // assert numUsers > numCustomers && numCustomers > numProfiles;

            for (int j = 0, k = 0, m = 0; j < numUsers; j++, k++, m++) {
                var user = newUser();

                ProfileRow profile = null;
                CustomerRow customer = null;

                if (k < numProfiles) {
                    profile = newProfile();
                    if (m < numCustomers) {
                        customer = newCustomer(profile, company.id, company.status);
                    }
                }

                AmalgamTuple tuple = new AmalgamTuple(user, profile, customer, company);
                companyAmalgams.addTuple(tuple);
            }

            // Add some linked users
            final var numLinkedUsers = numUsers / 4;
            LOG.info("Adding {} linked users.", numLinkedUsers);
            for (int p = 0; p < numLinkedUsers; p++) {
                var tuple = companyAmalgams.amalgamTuples().get(p);
                Platform platform = (SMS == tuple.userRow().platform) ? WAP : SMS;
                UserRow linkedUser = tuple.userRow().withIdPlatform(platform);
                companyAmalgams.addTuple(new AmalgamTuple(tuple.amalgamRow().groupId, linkedUser, company));
            }
            generatedData.add(companyAmalgams);
        }

        return generatedData;
    }

    /**
     * Writes the provided data to four separate tab delimited files, one for each table/type.
     * <p>
     * The files produced by this method can be loaded into Postgres via the COPY command.
     * <p>For example,<pre>
     *      COPY table_name (column1, column2, ...)
     *      FROM '/absolute/path/to/your/file.csv'
     *      DELIMITER E'\t'
     *      CSV HEADER;
     * </pre>
     *
     * @param data   the source data for the output files.
     * @param prefix the string that will be prepended to the output file names.
     * @return true if there were no errors, false otherwise.
     */
    private boolean outputAsTsv(List<CompanyAndAmalgamTuples> data, String prefix) {

        if (data == null || data.isEmpty()) {
            LOG.error("Empty data provided. No output will be produced.");
            return false;
        }

        // prepare the output files/streams.
        try (var userWriter = dmlWriter( prefix, "users.tsv");
             var profileWriter = dmlWriter( prefix, "profiles.tsv" );
             var customerWriter = dmlWriter( prefix, "customers.tsv");
             var amalgamWriter = dmlWriter( prefix, "amalgams.tsv" );
             var companyWriter = dmlWriter( prefix, "companies.tsv"); ) {

            // write the headers
            userWriter.write(join(DLM, UserRow.headers) + "\n");
            profileWriter.write(join(DLM, ProfileRow.headers) + "\n");
            customerWriter.write(join(DLM, CustomerRow.headers) + "\n");
            amalgamWriter.write(join(DLM, AmalgamRow.headers) + "\n");
            companyWriter.write(join(DLM, CompanyRow.headers) + "\n");

            for (CompanyAndAmalgamTuples caa : data) {
                println(caa.companyRow().name + ": " + caa.amalgamTuples().size() + " user elements.");
                caa.amalgamTuples().sort(comparing(AmalgamTuple::getGroupId)); // make it easy to see the linked users.

                companyWriter.write(join(DLM, caa.companyRow().values()) + "\n");

                for (AmalgamTuple tuple : caa.amalgamTuples()) {

                    userWriter.write(join(DLM, tuple.userRow().values()) + "\n");
                    amalgamWriter.write(join(DLM, tuple.amalgamRow().values()) + "\n");

                    if (tuple.profileRow() != null) {
                        profileWriter.write(join(DLM, tuple.profileRow().values()) + "\n");
                    }
                    if (tuple.customerRow() != null) {
                        customerWriter.write(join(DLM, tuple.customerRow().values()) + "\n");
                    }
                }
            }

        } catch (IOException e) {
            LOG.error("Error while writing output.", e);
            return false;
        }

        return true;
    }

    private BufferedWriter dmlWriter(String prefix, String fileName) throws IOException {
        return newBufferedWriter(Path.of("dml", prefix + fileName), CREATE, WRITE, TRUNCATE_EXISTING);
    }

    CompanyRow newCompany(String name, CompanyStatus status, String... id) {
        var company = easyRandom.nextObject(CompanyRow.class);
        company.name = name;
        company.status = status;
        if (company.updatedAt.isBefore(company.createdAt)) {
            company.updatedAt = company.createdAt;
        }
        if (id != null && id.length > 0) {
            company.id = UUID.fromString(id[0]);
        }
        return company;
    }

    CustomerRow newCustomer(ProfileRow profile, UUID companyId, CompanyStatus companyStatus, String... id) {
        var customer = easyRandom.nextObject(CustomerRow.class);
        if (id != null && id.length > 0) {
            customer.id = UUID.fromString(id[0]);
        }
        customer.companyId = companyId;
        customer.email = faker.internet().emailAddress();
        var domain = customer.email.substring(customer.email.indexOf('@'));
        var prefix = profile.givenName.charAt(0) + profile.surname;
        // FIXME remove any non-ascii characters to avoid addresses like gd'amore@hotmail.com
        customer.email = prefix.concat(domain).toLowerCase();
        customer.status = switch (companyStatus) {
            case ACTIVE -> CustomerStatus.ACTIVE;
            case SUSPENDED -> CustomerStatus.SUSPENDED;
            case LAPSED ->  CustomerStatus.LAPSED;
            case REQUESTED -> CustomerStatus.REQUESTED;
        };
        return customer;
    }

    ProfileRow newProfile(String... id) {
        var profile = easyRandom.nextObject(ProfileRow.class);
        if(id != null && id.length > 0) {
            profile.id = UUID.fromString(id[0]);
        }
        profile.givenName = faker.name().firstName();
        profile.surname = faker.name().lastName();
        profile.otherLanguages = "ENG";
        return profile;
    }

    public UserRow newUser() {
        return newUser(null, null, null, null, null);
    }

    // TODO Customize the weights applied for enum fields so we produce proportions
    //  that are consistent with expectation. e.g. at least 50% of the users should have
    //  U.S. phone numbers and a languageCode=ENG. Likewise status should be mostly IN.
    public UserRow newUser(String id, Platform platform, String number, UserStatus status, CountryCode countryCode) {
        var user = easyRandom.nextObject(UserRow.class);
        user.nickname = faker.animal().name();
        user.languageCode = LanguageCode.ENG;
        user.platformId = adjustPlatformId(user.countryCode, user.platformId);

        // overrides
        if (id != null) user.id = UUID.fromString(id);
        user.platform = (platform == null) ? SMS : platform;
        if(number != null) user.platformId = number;
        user.userStatus = (status == null) ? IN : status;
        if(countryCode != null) user.countryCode = countryCode;


        return user;
    }

    public static void main(String[] args) throws Exception {
        var generator = new BrblGenerator();
        int minUsers = 10;
        int maxUsers = 40; // FIXME increase to 100

        if (standardCompanyNames.length != standardCompanyStatuses.length) {
            throw new IllegalStateException("The standardCompanyNames and standardCompanyStatuses arrays must be the same length");
        }


        var knownDataList = generator.generateKnown();
        var randomDataList = generator.generateRandom(minUsers, maxUsers);

        // Debug output:
        // Source - https://stackoverflow.com/a/32528632
        // Posted by Anthony, modified by community and me. See post 'Timeline' for change history
        // Retrieved 2026-03-23, License - CC BY-SA 3.0
        // Comparator<AmalgamTuple> comparator = comparing(AmalgamTuple::getGroupId);
        //
        // dataList.forEach(caa -> {
        //     println(caa.companyRow().name + ": " + caa.amalgamTuples().size() + " users.");
        //     caa.amalgamTuples().sort(comparator); // sorting by the groupId makes it easy to see the linked users.
        //     caa.amalgamTuples().forEach(tuple -> {
        //         println("\t" + tuple.amalgamRow().groupId + ": " + tuple.userRow().platform + " " + tuple.userRow().platformId);
        //     });
        // });

        if (!generator.outputAsTsv(knownDataList, "known_")) {
            LOG.error("Failed writing known output files.");
        }

        if (!generator.outputAsTsv(randomDataList, "random_")) {
            LOG.error("Failed writing random output files.");
        }

    }


}
