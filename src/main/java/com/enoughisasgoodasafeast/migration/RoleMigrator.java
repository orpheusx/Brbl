package com.enoughisasgoodasafeast.migration;

import com.enoughisasgoodasafeast.ConfigLoader;
import com.mchange.v2.c3p0.ComboPooledDataSource;

import java.beans.PropertyVetoException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.enoughisasgoodasafeast.datagen.KnownData.APPLICATION_SCHEMAS;
import static java.io.IO.println;

/**
 * Used in conjunction with the pgroll, Postgres database migration tool.
 * It reads the existing set of privileges and roles granted on the original schema and grants
 * the same set of privileges to the per-table views generated in the pgroll-managed migration schema.
 * An application can then access these views simply by setting its search_path to include them (and
 * not the underlying tables.)
 */
public class RoleMigrator {

    private static ComboPooledDataSource dataSource;

    private static final String QUERY = """
            SELECT
                privilege_type,
                table_name,
                grantee
            FROM information_schema.table_privileges
            WHERE table_schema = ?
            AND grantor <> grantee
            ORDER BY grantee, table_name, privilege_type;
            """;

    private static final String USAGE_GRANT = "\n\tGRANT USAGE ON SCHEMA %s TO %s ;";

    // GRANT <privilege_type> ON <new_schema>.<table_name> TO <grantee>;
    private static final String GRANT = "\n\tGRANT %s ON %s.%s TO %s ;";

    private static final String OUTPUT_PREFIX = "UPDATED CURRENT_SCHEMAS=";


    public RoleMigrator(Properties props) {
        dataSource = new ComboPooledDataSource();
        try {
            dataSource.setProperties(props);

            dataSource.setJdbcUrl(props.getProperty("jdbcUrl"));
            dataSource.setDriverClass(props.getProperty("driverClass"));
            dataSource.setInitialPoolSize(1);
            dataSource.setMinPoolSize(1);
            dataSource.setMaxPoolSize(1);
            dataSource.setAcquireIncrement(1);
        } catch (PropertyVetoException e) {
            throw new IllegalStateException("WTF? " + e.getMessage());
        }
    }

    List<String[]> getPrivilegeTableNameGrantee(String schemaName) throws SQLException {

        try (var connection = dataSource.getConnection();
             var ps = connection.prepareStatement(QUERY)) {

            ps.setString(1, schemaName);

            final ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                println("Error: No results for schema: " + schemaName);
                throw new IllegalStateException("No results for schema: " + schemaName);
            }

            List<String[]> roleData = new ArrayList<>();
            do {
                // Should we validate that the grantees are one of KnownData.APPLICATION_SCHEMAS?
                roleData.add(new String[]{
                        rs.getString("privilege_type"),
                        rs.getString("table_name"),
                        rs.getString("grantee")
                });
            } while (rs.next());

            return roleData;

        } /*catch (SQLException e) {
            LOG.error("Error fetching privileges for schema {}: {}", schemaName, e.getMessage());
            throw e;
        }*/
    }

    Set<String> distinctRoles(List<String[]> grantsData) {
        Set<String> roles = new HashSet<>();
        for (String[] grantData : grantsData) {
            roles.add(grantData[2]);
        }
        return roles;
    }

    String generateGrants(String migrationSchema, List<String[]> grants) {
        StringBuilder grantBuilder = new StringBuilder();
        grantBuilder.append("\nBEGIN TRANSACTION;");

        for (String role : distinctRoles(grants)) {
            grantBuilder.append(String.format(USAGE_GRANT, migrationSchema, role));
        }

        int numGrants=0;
        for (String[] grant : grants) {
            grantBuilder.append(String.format(GRANT, grant[0], migrationSchema, grant[1], grant[2]));
            ++numGrants;
        }
        grantBuilder.append("\nCOMMIT;");
        println("Generated " + numGrants + " grants");
        return grantBuilder.toString();
    }

    boolean executeGrants(String grantSql) throws SQLException {
        try (var connection = dataSource.getConnection();
             var ps = connection.prepareStatement(grantSql)) {
            ps.executeUpdate();
             /* If the connection user doesn't have ADMIN on the target role of the grants
              * this seems to fail silently.
              */
        } catch (SQLException e) {
            println("ERROR: executeGrants failed: " + e.getMessage());
            return false;
        }
        return true;
    }

    List<String> findLatestMigrationSchemaByBase(List<String> schemaNames) {
        // Assumes pgroll in available from the current PATH
        var migrationSchemas = new ArrayList<String>();

        for (String schemaName : schemaNames) {
            ProcessBuilder pb = new ProcessBuilder("pgroll", "latest", "schema", "--schema", schemaName);

            pb.redirectErrorStream(true);
            try {
                Process process = pb.start();
                String migrationSchema;

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                     migrationSchema = reader.readLine();
                }

                if (process.waitFor() == 0) {
                    if(migrationSchema==null) {
                        println("ERROR: pgroll returned w/out error but without a migration schema for " + schemaName);
                    } else {
                        migrationSchemas.add(migrationSchema.trim());
                    }

                } else {
                    // no migration schema available, so just use the base schema name
                    migrationSchemas.add(schemaName);
                }

            } catch (IOException | InterruptedException e) {
                System.err.println("Error executing pgroll status command: " + e.getMessage());
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        }

        return migrationSchemas;
    }

    static void main(String[] args) throws IOException, SQLException {
        if(args.length < 2) {
            println("Usage: <originalSchema> <migrationSchema> [--dry-run]");
            System.exit(1);
        }
        String originalSchema = args[0].trim();
        String migrationSchema = args[1].trim();
        println("OriginalSchema: " + originalSchema);
        println("MigrationSchema: " + migrationSchema);

        //  if (Arrays.stream(KnownData.APPLICATION_SCHEMAS).noneMatch(originalSchema::equals)) {
        //      println("Error: originalSchema '" + originalSchema + "' is not a supported application schema.");
        //      System.exit(1);
        //  }

        RoleMigrator rm = new RoleMigrator(ConfigLoader.readConfig("migrations.properties"));

        println("Gathering current grants on tables in schema, " + originalSchema);
        var ptngList = rm.getPrivilegeTableNameGrantee(originalSchema);

        println("Granting equivalent privileges on migration schema, " + migrationSchema);
        String grants = rm.generateGrants(migrationSchema, ptngList);
        println(grants);

        if (grants.isEmpty()) {
            println("ERROR: No grants to execute.");
            System.exit(1);
        } else {
            boolean ok = rm.executeGrants(grants);
            println("Grants executed: " + (ok ? "OK" : "FAIL"));
            if(!ok) {
                println("ERROR: Failed to execute grants. Exiting...");
            }
        }

        // Return the new list of application schemas (including the one that was newly migrated.)
        List<String> baseSchemas = new ArrayList<>(APPLICATION_SCHEMAS.length);
        Collections.addAll(baseSchemas, APPLICATION_SCHEMAS);

        List<String> currentSchemas = rm.findLatestMigrationSchemaByBase(baseSchemas);

        println("\n" + OUTPUT_PREFIX + String.join(",", currentSchemas));
    }
}
