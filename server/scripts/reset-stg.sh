#!/bin/bash
# Runs on the staging Pi through SSM. Drops staging's database volume and
# starts again from an empty database, so initdb recreates the roles with the
# current passwords from Parameter Store.
set -euo pipefail

cd /opt/locatedo-stg
docker compose down -v --remove-orphans
docker compose run --rm migrate
docker compose up -d --remove-orphans

curl -fsS --retry 15 --retry-delay 2 --retry-connrefused http://127.0.0.1:8080/healthz
echo
echo "reset staging's database"
