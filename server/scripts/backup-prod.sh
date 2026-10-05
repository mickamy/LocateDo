#!/bin/bash
# Installed as /opt/locatedo/backup.sh and run daily by locatedo-backup.timer.
set -euo pipefail

account_id=$(aws sts get-caller-identity --query Account --output text)
key="prod/$(date -u +%Y-%m-%dT%H%M%SZ).dump"

cd /opt/locatedo
docker compose exec -T db pg_dump --username admin --dbname locatedo --format custom |
  aws s3 cp - "s3://locatedo-prod-backups-${account_id}/${key}" --region us-west-2

aws cloudwatch put-metric-data --region us-west-2 \
  --namespace LocateDo/Backup --metric-name Succeeded --value 1
echo "backed up to ${key}"
