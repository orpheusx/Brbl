package com.enoughisasgoodasafeast.datagen;

import com.enoughisasgoodasafeast.operator.CompanyStatus;
import com.enoughisasgoodasafeast.operator.CustomerStatus;
import com.enoughisasgoodasafeast.operator.LanguageCode;
import com.enoughisasgoodasafeast.operator.Platform;
import net.datafaker.Faker;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

import static com.enoughisasgoodasafeast.datagen.Functions.adjustPlatformId;
import static com.enoughisasgoodasafeast.operator.CompanyStatus.*;
import static java.io.IO.println;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.StandardOpenOption.*;

/**
 * A basic test data generator that creates a set of specific elements for use with unit and integration
 * tests along with some more random load.
 * NB: We're not aiming to generate massive amounts of data here.
 */
public class BrblGenerator {

    public static Logger LOG = LoggerFactory.getLogger(BrblGenerator.class);

    public static final String[] companyNames = {
            // ordered from smallest to largest.
            "MicroCorp", "MiniCorp", "MegaCorp", "MondoCorp", "GigaCorp", "NadaCorp"
    };

    public static final CompanyStatus[] companyStatuses = {
            ACTIVE, ACTIVE, ACTIVE, SUSPENDED, ACTIVE, LAPSED
    };

    public static final String DELIM = "\t";

//    public record DataSet(ArrayList<UserRow> users,
//                          ArrayList<ProfileRow> profiles,
//                          ArrayList<CustomerRow> customers,
//                          ArrayList<CompanyRow> companies,
//                          ArrayList<AmalgamRow> amalgams) {}

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

    public List<CompanyAndAmalgamTuples> generateRandom(int minUsers, int maxUsers) {

        assert minUsers < maxUsers; // 10 < 100


        int increment = (maxUsers - minUsers) / companyNames.length; // e.g. 100 - 10 = 90 / 6 = 15 increment
        // 10, [25, 40, 55, 70, 85], 100
        LOG.info("Incrementing user counts by {} per company.", increment);

        List<CompanyAndAmalgamTuples> generatedData = new ArrayList<>();

        // Create six, known companies with the above names and states.
        for (int i = 0; i < companyNames.length; i++) {

            final var company = newCompany(companyNames[i], companyStatuses[i]);
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
                Platform platform = (Platform.SMS == tuple.userRow().platform) ? Platform.WAP : Platform.SMS;
                UserRow linkedUser = tuple.userRow().with(platform);
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
        try (BufferedWriter userWriter = Files.newBufferedWriter(Path.of("dml", prefix + "users.tsv"), CREATE, WRITE, TRUNCATE_EXISTING);
             BufferedWriter profileWriter = Files.newBufferedWriter(Path.of("dml", prefix + "profiles.tsv"), CREATE, WRITE, TRUNCATE_EXISTING);
             BufferedWriter customerWriter = Files.newBufferedWriter(Path.of("dml", prefix + "customers.tsv"), CREATE, WRITE, TRUNCATE_EXISTING);
             BufferedWriter amalgamWriter = Files.newBufferedWriter(Path.of("dml", prefix + "amalgams.tsv"), CREATE, WRITE, TRUNCATE_EXISTING);
             BufferedWriter companyWriter = Files.newBufferedWriter(Path.of("dml", prefix + "companies.tsv"), CREATE, WRITE, TRUNCATE_EXISTING);) {

            // write the headers
            userWriter.write(String.join(DELIM, UserRow.headers) + "\n");
            profileWriter.write(String.join(DELIM, ProfileRow.headers) + "\n");
            customerWriter.write(String.join(DELIM, CustomerRow.headers) + "\n");
            amalgamWriter.write(String.join(DELIM, AmalgamRow.headers) + "\n");
            companyWriter.write(String.join(DELIM, CompanyRow.headers) + "\n");

            for (CompanyAndAmalgamTuples caa : data) {
                println(caa.companyRow().name + ": " + caa.amalgamTuples().size() + " user elements.");
                caa.amalgamTuples().sort(Comparator.comparing(AmalgamTuple::getGroupId)); // make it easy to see the linked users.

                companyWriter.write(String.join(DELIM, caa.companyRow().values()) + "\n");

                for (AmalgamTuple tuple : caa.amalgamTuples()) {

                    userWriter.write(String.join(DELIM, tuple.userRow().values()) + "\n");
                    amalgamWriter.write(String.join(DELIM, tuple.amalgamRow().values()) + "\n");

                    if (tuple.profileRow() != null) {
                        profileWriter.write(String.join(DELIM, tuple.profileRow().values()) + "\n");
                    }
                    if (tuple.customerRow() != null) {
                        customerWriter.write(String.join(DELIM, tuple.customerRow().values()) + "\n");
                    }
                }
            }

        } catch (IOException e) {
            LOG.error("Error while writing output.", e);
            return false;
        }

        return true;
    }

    CompanyRow newCompany(String name, CompanyStatus status) {
        var company = easyRandom.nextObject(CompanyRow.class);
        company.name = name;
        company.status = status;
        if (company.updatedAt.isBefore(company.createdAt)) {
            company.updatedAt = company.createdAt;
        }
        return company;
    }

    CustomerRow newCustomer(ProfileRow profile, UUID companyId, CompanyStatus companyStatus) {
        var customer = easyRandom.nextObject(CustomerRow.class);
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

    ProfileRow newProfile() {
        var profile = easyRandom.nextObject(ProfileRow.class);
        // doubleSingleQuotes() is only required if generating SQL where single quotes are an issue within strings.
        profile.givenName = faker.name().firstName();
        profile.surname = faker.name().lastName();
        profile.otherLanguages = "ENG";
        return profile;
    }

    // TODO Customize the weights applied for enum fields so we produce proportions
    //  that are consistent with expectation. e.g. at least 50% of the users should have
    //  U.S. phone numbers and a languageCode=ENG. Likewise status should be mostly IN.
    public UserRow newUser() {
        var user = easyRandom.nextObject(UserRow.class);
        user.nickname = faker.animal().name();
        user.platform = Platform.SMS;
        user.platformId = adjustPlatformId(user.countryCode, user.platformId);
        user.languageCode = LanguageCode.ENG;
        return user;
    }

    public static void main(String[] args) throws Exception {
        var generator = new BrblGenerator();
        int minUsers = 10;
        int maxUsers = 40; // FIXME increase to 100

        // int numCompanies = 5;
        // float percentProfiles = 0.1f;
        // float percentCustomers = 0.1f;

        var dataList = generator.generateRandom(
                minUsers, maxUsers);

        // Source - https://stackoverflow.com/a/32528632
        // Posted by Anthony, modified by community and me. See post 'Timeline' for change history
        // Retrieved 2026-03-23, License - CC BY-SA 3.0
        Comparator<AmalgamTuple> comparator = Comparator.comparing(AmalgamTuple::getGroupId);

        // dataList.forEach(caa -> {
        //     println(caa.companyRow().name + ": " + caa.amalgamTuples().size() + " users.");
        //     caa.amalgamTuples().sort(comparator); // sorting by the groupId makes it easy to see the linked users.
        //     caa.amalgamTuples().forEach(tuple -> {
        //         println("\t" + tuple.amalgamRow().groupId + ": " + tuple.userRow().platform + " " + tuple.userRow().platformId);
        //     });
        // });

        var ok = generator.outputAsTsv(dataList, "test_");
    }


}
