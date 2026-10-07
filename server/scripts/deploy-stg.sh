#!/bin/bash
# Runs on the staging Raspberry Pi through SSM. The deploy workflow prepends
# IMAGE_TAG, BUNDLE_B64 (a tar.gz of the deployed commit's compose file and
# init script), and GHCR_USER / GHCR_TOKEN (the job's token, valid until it ends).
set -euo pipefail

: "${IMAGE_TAG:?}" "${BUNDLE_B64:?}" "${GHCR_USER:?}" "${GHCR_TOKEN:?}"

bundle=$(mktemp -d)
trap 'rm -rf "${bundle}"' EXIT
base64 -d <<<"${BUNDLE_B64}" | tar -xz -C "${bundle}"

mkdir -p /opt/locatedo-stg/initdb
cd /opt/locatedo-stg
install -m 644 "${bundle}/compose.stg.yaml" compose.yaml
install -m 755 "${bundle}/initdb/create-roles.sh" initdb/create-roles.sh

umask 077
aws ssm get-parameters-by-path --region us-west-2 --path /locatedo/stg --recursive --with-decryption \
  --query 'Parameters[].[Name,Value]' --output text |
  while IFS=$'\t' read -r name value; do
    printf "%s='%s'\n" "${name##*/}" "${value}"
  done >.env.new
printf "IMAGE_TAG='%s'\n" "${IMAGE_TAG}" >>.env.new
mv .env.new .env

docker login ghcr.io --username "${GHCR_USER}" --password-stdin <<<"${GHCR_TOKEN}"
trap 'docker logout ghcr.io >/dev/null; rm -rf "${bundle}"' EXIT
docker compose pull --quiet
docker logout ghcr.io
docker compose run --rm migrate
docker compose up -d --remove-orphans
docker image prune -f

curl -fsS --retry 15 --retry-delay 2 --retry-connrefused http://127.0.0.1:8080/healthz
echo
echo "deployed ${IMAGE_TAG}"
