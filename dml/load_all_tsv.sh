echo "Loading data from tsv files..."
psql -U brbl_admin -d brbl_db_dev -f load_all_tsv.sql
