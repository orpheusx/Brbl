#!/usr/bin/env python3

import sys
import subprocess
import os

def run_command(command, capture_output=False):
    """Helper function to run shell commands and handle errors."""
    print(f"Executing: {' '.join(command)}")
    try:
        if capture_output:
            result = subprocess.run(command, check=True, capture_output=True, text=True)
            print(f"STDOUT:\n{result.stdout}")
            if result.stderr:
                print(f"STDERR:\n{result.stderr}")
            return result.stdout.strip()
        else:
            subprocess.run(command, check=True)
        return True
    except subprocess.CalledProcessError as e:
        print(f"Command failed with exit code {e.returncode}")
        if e.stdout:
            print(f"STDOUT:\n{e.stdout}")
        if e.stderr:
            print(f"STDERR:\n{e.stderr}")
        sys.exit(e.returncode)
    except FileNotFoundError:
        print(f"Error: Command not found. Make sure '{command[0]}' is in your PATH.")
        sys.exit(1)

def main():
    if len(sys.argv) != 2:
        print("Usage: run_migration.py <path_to_yaml_file>")
        sys.exit(1)

    yaml_file_path = sys.argv[1]

    if not os.path.exists(yaml_file_path):
        print(f"Error: YAML file not found at '{yaml_file_path}'")
        sys.exit(1)

    # FIXME build a compiled Java program and put the binary on the user's PATH.
    brbl_path = os.getenv('BRBL_HOME', "UNSET")
    if brbl_path == "UNSET":
        print(f"Error: BRBL_HOME environment variable not set.")
        sys.exit(1)

    # Extract source_schema from the directory containing the YAML file
    source_schema = os.path.basename(os.path.dirname(os.path.abspath(yaml_file_path)))
    print(f"Extracted source_schema: {source_schema}")

    # 1. Run pgroll start
#    print("\n--- Running pgroll start ---")
#    pgroll_start_command = ["pgroll", "start", yaml_file_path, "--schema", source_schema, "--verbose"]
#    run_command(pgroll_start_command)
#
    # 2. Run pgroll latest schema and capture output
    print("\n--- Running pgroll latest schema ---")
    pgroll_latest_command = ["pgroll", "latest", "schema", "--schema", source_schema, "--verbose"]
    migration_schema = run_command(pgroll_latest_command, capture_output=True).strip()
    # remove white space from migration_schema
    print(f"Captured migration_schema: {migration_schema}")

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
        run_command(java_command)
        print("\nMigration process completed successfully.")
    except SystemExit as e:
        if e.code != 0:
            print(f"Java RoleMigrator failed with exit code {e.code}.")
            sys.exit(1)

if __name__ == "__main__":
    main()
