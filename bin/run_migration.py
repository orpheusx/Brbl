#!/usr/bin/env python3

import sys
import subprocess
import os
import re


def run_command(command, capture_output=False):
    """Helper function to run shell commands and handle errors."""

    # print(f"Executing: {' '.join(command)}")
    try:
        if capture_output:
            result = subprocess.run(command, check=True, capture_output=True, text=True)
            # print(f"STDOUT:\n{result.stdout}")
            # if result.stderr:
            #     print(f"STDERR:\n{result.stderr}")
            return result.stdout.strip()
        else:
            subprocess.run(command, check=True)
        return True
    except subprocess.CalledProcessError as e:
        print(f"Command failed with exit code {e.returncode}")
        if e.stdout:
            print(f"ERROR FROM STDOUT:\n{e.stdout}")
        if e.stderr:
            print(f"ERROR FROM STDERR:\n{e.stderr}")
        sys.exit(e.returncode)
    except FileNotFoundError:
        print(f"Error: Command not found. Make sure '{command[0]}' is in your PATH.")
        sys.exit(1)


def update_jdbc_urls(folder, search_path):
    """Scan the folder for .properties files and update any .properties files found with the new search_path."""
    # TODO Basic sanity check to make sure the schemas look like what we expect?

    print(f"\tScanning {folder} for .properties files in need of updating...")

    # Find all the .properties file in the folder and replace the "currentSchema" parameter to any jdbcUrl property it contains
    # with the value of search_path.
    # If the jdbcUrl property doesn't include a currentSchema then leave it untouched.

    for root, _, files in os.walk(folder):
        for file in files:
            if file.endswith(".properties"):
                file_path = os.path.join(root, file)
                # print(f"Processing {file_path}...")
                try:
                    with open(file_path, 'r') as f:
                        lines = f.readlines()

                    updated = False
                    new_lines = []
                    # Regex to find jdbcUrl and replace the currentSchema parameter value
                    # It looks for currentSchema= followed by any characters until a & or end of line
                    schema_regex = r"(jdbcUrl=.*[?&]currentSchema=)([^&\s]+)(.*)"

                    for line in lines:
                        if "jdbcUrl" in line and "currentSchema=" in line:
                            new_line = re.sub(schema_regex, rf"\1{search_path}\3", line)
                            if new_line != line:
                                # print(f"\n\t{new_line}")
                                new_lines.append(new_line)
                                updated = True
                                continue
                        new_lines.append(line)

                    if updated:
                        with open(file_path, 'w') as f:
                            f.writelines(new_lines)
                        print(f"\tUpdated {file_path}")
                    # else:
                    #     print(f"Nothing to update in {file_path}")

                except Exception as e:
                    print(f"Failed to process {file_path}: {e}")


def main():
    if len(sys.argv) != 2:
        print("Usage: run_migration.py <path_to_yaml_file>")
        sys.exit(1)

    yaml_file_path = sys.argv[1]

    if not os.path.exists(yaml_file_path):
        print(f"Error: YAML file not found at '{yaml_file_path}'")
        sys.exit(1)

    print(f"\n--- Starting migration process ---")

    # FIXME build a compiled Java program and put the binary on the user's PATH.
    brbl_path = os.getenv('BRBL_HOME', "UNSET")
    if brbl_path == "UNSET":
        print(f"Error: BRBL_HOME environment variable not set.")
        sys.exit(1)

    # Extract source_schema from the directory containing the YAML file
    source_schema = os.path.basename(os.path.dirname(os.path.abspath(yaml_file_path)))
    print(f"\tInferred source_schema: {source_schema}")

    # 1. Run pgroll start.
    print("\n--- Running pgroll start ---")
    pgroll_start_command = ["pgroll", "start", yaml_file_path, "--schema", source_schema, "--verbose"]
    print(f"DEBUG: Command: {pgroll_start_command}")
    run_command(pgroll_start_command)

    # 2. Run pgroll latest schema and capture output
    print("\n--- Running pgroll latest schema ---")
    pgroll_latest_command = ["pgroll", "latest", "schema", "--schema", source_schema, "--verbose"]
    migration_schema = run_command(pgroll_latest_command, capture_output=True).strip()
    print(f"\tNew post-migration schema: {migration_schema}")

    # 3. Run Java RoleMigrator
    print("\n--- Running RoleMigrator ---")
    java_command = [
        "java",
        "--enable-preview",
        "-cp",
        brbl_path + "/target/sndrRcvr-1.0-SNAPSHOT-jar-with-dependencies.jar",
        "com.enoughisasgoodasafeast.migration.RoleMigrator",
        source_schema,
        migration_schema
    ]
    try:
        output = run_command(java_command, capture_output=True)
        # Find the line in output that starts with "UPDATED CURRENT_SCHEMAS=" and set new_search_path with the remainder of that line.
        new_search_path = None
        for line in output.splitlines():
            if line.startswith("UPDATED CURRENT_SCHEMAS="):
                new_search_path = line.split("=", 1)[1].strip()
                break

        if not new_search_path:
            print("ERROR: run_command output:")
            print(f"ERROR: {output}")
            print("ERROR: Could not find UPDATED CURRENT_SCHEMAS in RoleMigrator output.")
            sys.exit(1)

        print(f"\tUpdated search_path: {new_search_path}")

        # 4. Update JDBC URLs in properties files to use the new search_path
        print(f"\n--- Updating configuration properties files with new search_path ---")
        update_jdbc_urls(brbl_path + "/src", new_search_path)

        print("\n--- Migration process completed successfully. --- ")

    except SystemExit as e:
        if e.code != 0:
            print(f"RoleMigrator failed with exit code {e.code}.")
            print(f"Trying to rollback migration...")
            run_command(["pgroll", "rollback", "--schema", source_schema], False)
            sys.exit(1)


if __name__ == "__main__":
    main()
