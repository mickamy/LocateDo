#!/bin/bash
# Runs on the staging Pi through SSM. The workflow prepends CAMPAIGN_LANGUAGE,
# CAMPAIGN_TITLE, CAMPAIGN_BODY, CAMPAIGN_URL, DRY_RUN, and FORCE.
set -euo pipefail

: "${CAMPAIGN_LANGUAGE:?}" "${CAMPAIGN_TITLE:?}" "${CAMPAIGN_BODY:?}" "${DRY_RUN:?}" "${FORCE:?}"

args=(send-campaign "-language=${CAMPAIGN_LANGUAGE}" "-title=${CAMPAIGN_TITLE}" "-body=${CAMPAIGN_BODY}")
if [ -n "${CAMPAIGN_URL:-}" ]; then
  args+=("-url=${CAMPAIGN_URL}")
fi
if [ "${DRY_RUN}" = true ]; then
  args+=(-dry-run)
fi
if [ "${FORCE}" = true ]; then
  args+=(-force)
fi

cd /opt/locatedo-stg
docker compose run --rm admin "${args[@]}"
