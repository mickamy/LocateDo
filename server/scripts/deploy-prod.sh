#!/bin/bash
# Runs on the prod instance through SSM. The deploy workflow prepends
# IMAGE_TAG, BUNDLE_B64 (a tar.gz of the deployed commit's compose file, init
# script, backup and patch scripts, systemd units, and CloudWatch agent
# config), and GHCR_USER / GHCR_TOKEN (the job's token, valid until it ends).
set -euo pipefail

: "${IMAGE_TAG:?}" "${BUNDLE_B64:?}" "${GHCR_USER:?}" "${GHCR_TOKEN:?}"

bundle=$(mktemp -d)
trap 'rm -rf "${bundle}"' EXIT
base64 -d <<<"${BUNDLE_B64}" | tar -xz -C "${bundle}"

mkdir -p /opt/locatedo/initdb
cd /opt/locatedo
install -m 644 "${bundle}/compose.prod.yaml" compose.yaml
install -m 755 "${bundle}/initdb/create-roles.sh" initdb/create-roles.sh
install -m 755 "${bundle}/scripts/backup-prod.sh" backup.sh
install -m 755 "${bundle}/scripts/patch-prod.sh" patch.sh
install -m 644 "${bundle}"/systemd/* /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now locatedo-backup.timer locatedo-patch.timer

if ! rpm -q dnf-plugins-core >/dev/null; then
  dnf install -y dnf-plugins-core
fi

if ! rpm -q amazon-cloudwatch-agent >/dev/null; then
  dnf install -y amazon-cloudwatch-agent
fi
install -m 644 "${bundle}/cloudwatch/agent.json" /opt/aws/amazon-cloudwatch-agent/etc/locatedo.json
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -s \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/locatedo.json

umask 077
aws ssm get-parameters-by-path --region us-west-2 --path /locatedo/prod --recursive --with-decryption \
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
