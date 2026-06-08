echo "Loading data from tsv files..."
current_dir=$PWD
cd $BRBL_HOME/dml
psql -U brbl_admin -d brbl_db_dev -f load_all_tsv.sql
cd $current_dir
