package com.enoughisasgoodasafeast.datagen;

import com.enoughisasgoodasafeast.operator.CountryCode;
import com.enoughisasgoodasafeast.operator.CustomerStatus;
import com.enoughisasgoodasafeast.operator.Platform;
import com.enoughisasgoodasafeast.operator.UserStatus;
import net.datafaker.Faker;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.io.IO.println;

/**
 * A quick data producing program that uses EasyRandom and a bit of Faker to generate roughly correct data for
 * the brbl_users schema and the few tables that relate to it.
 * We're just overwrite some of the baseline values with direct calls to core Faker functions. A better implementation
 * might use the various Bean Validation annotations available to customize the data.
 *
 * brbl_biz   . addresses
 * brbl_biz   . customer_addresses
 * brbl_biz   . payment_cc
 *
 * brbl_logic . nodes
 * brbl_logic . edges
 * brbl_logic . scripts
 * brbl_logic . push_campaigns
 * brbl_logic . campaign_users
 * brbl_logic . routes
 * brbl_logic . default_scripts >> not sure that we need this given we already have routes.
 * brbl_logic . keywords
 * brbl_logic . schedules
 * brbl_logic . sessions
 *
 * brbl_logs  . messages_mo
 * brbl_logs  . messages_mo_prcd
 * brbl_logs  . messages_mt
 * brbl_logs  . messages_mt_dlvr
 */
public class BrblUsersGenerator {

    private final Faker faker;
    private final Faker chFaker; // for Chinese data
    private final Faker esFaker; // for Spanish data
    private final EasyRandom easyRandom;
    private DataSet dataSet;

    public BrblUsersGenerator() {
        this.faker = new Faker(Locale.ENGLISH);
        this.chFaker = new Faker(Locale.SIMPLIFIED_CHINESE);
        this.esFaker = new Faker(new Locale("es", "419")); // Latin America

        // Other possibilities.
        // Argentina: new Locale("es", "AR");
        // Brazil: new Locale("pt", "BR")
        // Colombia: new Locale("es", "CO");
        // Peru: new Locale("es", "PE");
        // Chile: new Locale("es", "CL");
        // Venezuela: new Locale("es", "VE");

        this.easyRandom = new EasyRandom(new EasyRandomParameters()
                .seed(System.currentTimeMillis())
                .stringLengthRange(3, 10));
    }

    static void main() throws IOException {
        var generator = new BrblUsersGenerator();

        // println(generator.chFaker.name().firstName() + " " + generator.chFaker.name().lastName()); //returns e.g. "风华 俞" which should be displayed with "lastName" first

        // TODO implement a version that takes a configured Faker instance to vary the Locale per language we want to support,
        // TODO appending the locale code to the output file names.
        generator.dataSet = generator.generateUserGraphs(100, 30, 10, 50);
        generator.dataToSQL(generator.dataSet);
    }

    public DataSet getDataSet() {
        return this.dataSet;
    }

    public List<String> dataToSQL(DataSet dataSet) throws IOException {
        List<String> output = new ArrayList<>(12);
        output.add("BEGIN TRANSACTION;");

        // ---------------------------------------------------------
        var userSql = new StringBuilder();
        userSql.append(INSERT_USERS);
        for(var user : dataSet.users) {
            userSql.append(String.format("\n('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s'),",
                    user.id, user.platform.code(), user.platformId,
                    user.countryCode.name(), user.languageCode, user.nickname,
                    user.userStatus, user.createdAt, user.updatedAt)
            );
        }
        output.add(userSql.substring(0, userSql.length() - 1));
        output.add(";");

        // ---------------------------------------------------------
        var profileSql = new StringBuilder();
        profileSql.append(INSERT_PROFILES);
        for(var profile : dataSet.profiles) {
            profileSql.append(String.format("\n('%s', '%s', '%s', '%s', '%s', '%s'),",
                    profile.id, profile.surname, profile.givenName,
                    profile.otherLanguages, profile.createdAt, profile.updatedAt));

        }
        output.add(profileSql.substring(0, profileSql.length() - 1));
        output.add(";");

        // ---------------------------------------------------------
        var companySql = new StringBuilder();
        companySql.append(INSERT_COMPANIES);
        for(var company : dataSet.companies) {
            companySql.append(String.format("\n('%s', '%s', '%s', '%s'),",
                    company.id, company.name, company.createdAt, company.updatedAt));
        }
        output.add(companySql.substring(0, companySql.length() - 1));
        output.add(";");

        // ---------------------------------------------------------
        var customerSql = new StringBuilder();
        customerSql.append(INSERT_CUSTOMERS);
        for(var customer : dataSet.customers) {
            customerSql.append(String.format("\n('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s'),",
                    customer.id, customer.createdAt, customer.updatedAt, customer.email,
                    customer.companyId, customer.status, customer.confirmationCode, customer.password));
        }
        output.add(customerSql.substring(0, customerSql.length() - 1));
        output.add(";");

        // ---------------------------------------------------------
        var amalgamsSql = new StringBuilder(218);
        amalgamsSql.append(INSERT_AMALGAMS);
        for(var amalgam : dataSet.amalgams) {
            amalgamsSql.append(String.format("\n('%s', '%s', %s, %s, '%s', '%s'),",
                    amalgam.groupId, amalgam.userId,
                    (amalgam.profileId == null) ? "null" : String.format("'%s'", amalgam.profileId),
                    (amalgam.customerId== null) ? "null" : String.format("'%s'", amalgam.customerId),
                    amalgam.createdAt, amalgam.updatedAt));
        }
        output.add(amalgamsSql.substring(0, amalgamsSql.length() - 1));
        output.add(";");

        output.add("COMMIT;");

        return output;
    }

    //public void sqlToFile(String fileName, String sql) throws IOException {
    //    var filePath = Paths.get("dml", fileName);
    //    Files.writeString(filePath, sql.substring(0, sql.length() - 1) + "\n", StandardCharsets.UTF_8);
    //    println("Wrote: " + filePath);
    //}

    public UserRow randomUser() {
        var user = easyRandom.nextObject(UserRow.class);
        user.nickname = doubleSingleQuotes(faker.name().firstName());
        user.nickname = doubleSingleQuotes(faker.animal().name());
        return user;
    }

    public ProfileRow randomProfile() {
        var profile = easyRandom.nextObject(ProfileRow.class);
        profile.givenName = doubleSingleQuotes(faker.name().firstName());
        profile.surname = doubleSingleQuotes(faker.name().lastName());
        profile.otherLanguages = "ENG"; // FIXME should be more dynamic
        return profile;
    }

    public CompanyRow randomCompany() {
        var company = easyRandom.nextObject(CompanyRow.class);
        company.name = faker.clashOfClans().defensiveBuilding() + " Inc"; // haha
        return company;
    }

    public CustomerRow randomCustomer() {
        var customer = easyRandom.nextObject(CustomerRow.class);
        customer.email = faker.internet().emailAddress();
        return customer;
    }

    public AmalgamRow randomAmalgam() {
        return easyRandom.nextObject(AmalgamRow.class);
    }

    /**
     * @param numUsers          total number of users to create
     * @param prcntProfiles     percentage of users that should have profiles
     * @param prcntCustomers    percentage of profiles that should be customers
     * @param prcntCompanies    percentage of customers that are associated with a company (i.e. not just individuals)
     */
    public DataSet generateUserGraphs(int numUsers, float prcntProfiles, float prcntCustomers, float prcntCompanies) {
        assert numUsers > 0;

        int numProfiles = (int) (numUsers * (prcntProfiles / 100f));
        int numCustomers = (int) (numProfiles * (prcntCustomers / 100f));
        int numCompanies = (int) (numCustomers * (prcntCompanies / 100f));

        println("Generating user graphs for " + numUsers + " users.");
        println("Generating profile records for " + numProfiles + " users.");
        println("Generating customer records for " + numCustomers + " customers and " + numCompanies + " companies.");

        return generate(numUsers, numProfiles, numCustomers, numCompanies);
    }

    private DataSet generate(int numUsers, int numProfiles, int numCustomers, int numCompanies) {

        int numAmalgams = numUsers;

        //assert numCompanies <= numCustomers;
        //assert numProfiles <= numUsers;
        //assert numProfiles >= numCustomers;

        final var userRowsList = new ArrayList<UserRow>(numUsers);
        final var profileRowsList = new ArrayList<ProfileRow>(numProfiles);
        final var customerRowsList = new ArrayList<CustomerRow>(numCustomers);
        final var companiesRowsList = new ArrayList<CompanyRow>(numCompanies);
        final var amalgamRowsList = new ArrayList<AmalgamRow>(numAmalgams);

        // Users
        // Users
        for (int i = 0; i < numUsers; i++) {
            var userRow = randomUser();
            userRow.userStatus = UserStatus.IN;
            if(i > (numUsers - 3)) {
                userRow.userStatus = UserStatus.OUT;
            }
            userRow.platformId = adjustPlatformId(userRow.countryCode, userRow.platformId);
            // if(userRow.countryCode == CountryCode.MX) {
            //     userRow.platformId = "52" + userRow.platformId;
            // } else {
            //     userRow.platformId = "1" + userRow.platformId;
            // }

            //System.out.println(userRow);
            userRowsList.add(userRow);
        }

        // Profiles
        for (int i = 0; i < numProfiles; i++) {
            var profileRow = randomProfile();
            //println(profileRow);
            profileRowsList.add(profileRow);
        }
        // Companies
        for (int i = 0; i < numCompanies; i++) {
            var company = randomCompany();
            //println(company);
            companiesRowsList.add(company);
        }

        // Customers
        for (int i = 0; i < numCustomers; i++) {
            var customer = randomCustomer();
            customer.status = CustomerStatus.ACTIVE;
            customerRowsList.add(customer);
        }

        // Update Customer with their Company
        for (int i = 0; i < numCustomers; i++) {
            var company = companiesRowsList.get(0);
            var customer = customerRowsList.get(i);
            customer.companyId = company.id;
            //println(customer);
        }

        // Amalgams, one User for each, created at the same instant.
        for (int i = 0; i < numAmalgams; i++) {
            var ar = randomAmalgam();
            amalgamRowsList.add(ar);

            var ur = userRowsList.get(i);
            ar.createdAt = ur.createdAt;
            ar.updatedAt = ur.updatedAt;
            ar.userId = ur.id;
            // clear the rest
            ar.profileId = null;
            ar.customerId = null;
        }

        // only a subset of the Amalgams have the rest of the references
        for (int i = 0; i < numProfiles; i++) {
            var pr = profileRowsList.get(i);
            var ar = amalgamRowsList.get(i);
            ar.profileId = pr.id;
        }

        for (int i = 0; i < numCustomers; i++) {
            var customerRow = customerRowsList.get(i);
            var amalgamRow = amalgamRowsList.get(i);
            amalgamRow.customerId = customerRow.id;
        }

        return new DataSet(userRowsList, profileRowsList, customerRowsList, companiesRowsList, amalgamRowsList);
    }

    public static String convertPlatformId(String platformId) {
        return platformId.replaceAll("[\\s\\-\\(\\)]", "");
    }

    public static String adjustPlatformId(CountryCode countryCode, String platformId) {
        var mungedPlatformId = convertPlatformId(platformId);
        if(countryCode == CountryCode.MX) {
            mungedPlatformId = "52" + platformId;
        } else {
            mungedPlatformId = "1" + platformId;
        }
        return mungedPlatformId;
    }

    private static String doubleSingleQuotes(String word) {
        return word.replaceAll("'", "''");
    }

    public record DataSet(ArrayList<UserRow> users, ArrayList<ProfileRow> profiles, ArrayList<CustomerRow> customers,
                          ArrayList<CompanyRow> companies, ArrayList<AmalgamRow> amalgams) {}

    public static final String INSERT_USERS = """
            INSERT INTO brbl_users.users
             (id, platform_code, platform_id, country, language, nickname, status, created_at, updated_at)
            VALUES""";

    public static final String INSERT_PROFILES = """
            INSERT INTO brbl_users.profiles
             (id, surname, given_name, other_languages, created_at, updated_at)
            VALUES""";

    public final String INSERT_COMPANIES = """
            INSERT INTO brbl_users.companies
            (id, name, created_at, updated_at)
            VALUES""";

    public final String INSERT_CUSTOMERS = """
            INSERT INTO brbl_users.customers
            (id, created_at, updated_at, email,
             company_id, status, confirmation_code, password)
            VALUES""";

    public final String INSERT_AMALGAMS = """
            INSERT INTO brbl_users.amalgams
            (group_id, user_id, profile_id, customer_id,
             created_at, updated_at)
            VALUES""";
}
