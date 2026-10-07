#!/bin/bash
# Runs on the staging Pi through SSM. The workflow prepends USER_ID and PLAN.
set -euo pipefail

: "${USER_ID:?}" "${PLAN:?}"

cd /opt/locatedo-stg
docker compose run --rm admin set-plan -user "${USER_ID}" -plan "${PLAN}"
