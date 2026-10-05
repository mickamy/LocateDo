#!/bin/sh
# Runs once, when Postgres initializes an empty data directory. The roles
# migration then finds the roles and only grants.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v writer_password="$DB_WRITER_PASSWORD" \
  -v reader_password="$DB_READER_PASSWORD" <<'SQL'
CREATE ROLE locatedo_writer LOGIN PASSWORD :'writer_password';
CREATE ROLE locatedo_reader LOGIN PASSWORD :'reader_password';
SQL
