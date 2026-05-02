echo "Deleting data from brbl_logic and brbl_users schemas..."
psql -U brbl_admin -d brbl_db_dev -f delete_all_tsv.sql
