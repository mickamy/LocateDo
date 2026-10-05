#!/bin/bash
# Runs on the prod instance through SSM. The deploy workflow prepends
# IMAGE_TAG, COMPOSE_B64 and CREATE_ROLES_B64 (the files from the deployed
# commit), and GHCR_USER / GHCR_TOKEN (the job's token, valid until it ends).
set -euo pipefail

: "${IMAGE_TAG:?}" "${COMPOSE_B64:?}" "${CREATE_ROLES_B64:?}" "${GHCR_USER:?}" "${GHCR_TOKEN:?}"

mkdir -p /opt/locatedo/initdb
cd /opt/locatedo

base64 -d <<<"${COMPOSE_B64}" >compose.yaml
base64 -d <<<"${CREATE_ROLES_B64}" >initdb/create-roles.sh
chmod +x initdb/create-roles.sh

umask 077
aws ssm get-parameters-by-path --region us-west-2 --path /locatedo/prod --recursive --with-decryption \
  --query 'Parameters[].[Name,Value]' --output text |
  while IFS=$'\t' read -r name value; do
    printf "%s='%s'\n" "${name##*/}" "${value}"
  done >.env.new
printf "IMAGE_TAG='%s'\n" "${IMAGE_TAG}" >>.env.new
mv .env.new .env

docker login ghcr.io --username "${GHCR_USER}" --password-stdin <<<"${GHCR_TOKEN}"
trap 'docker logout ghcr.io >/dev/null' EXIT
docker compose pull --quiet
docker logout ghcr.io
docker compose run --rm migrate
docker compose up -d --remove-orphans
docker image prune -f

curl -fsS --retry 15 --retry-delay 2 --retry-connrefused http://127.0.0.1:8080/healthz
echo
echo "deployed ${IMAGE_TAG}"
