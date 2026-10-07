#!/bin/bash
# Runs a script as root on the prod instance through SSM, prints its output,
# and fails when it does. Called from jobs that assumed locatedo-prod-deploy.
#   run-on-prod.sh <script file> <comment>
set -euo pipefail

script=${1:?script file}
comment=${2:?comment}

instance_id=$(aws ec2 describe-instances \
  --filters Name=tag:Name,Values=locatedo-prod-app Name=instance-state-name,Values=running \
  --query 'Reservations[].Instances[].InstanceId' --output text)
if [ -z "${instance_id}" ]; then
  echo "::error::no running locatedo-prod-app instance"
  exit 1
fi

parameters=$(mktemp)
trap 'rm -f "${parameters}"' EXIT
encoded=$(base64 -w0 "${script}")
command="set -e; f=\$(mktemp); trap 'rm -f \"\$f\"' EXIT; echo ${encoded} | base64 -d >\"\$f\"; bash \"\$f\""
jq -n --arg command "${command}" '{commands: [$command], executionTimeout: ["900"]}' >"${parameters}"

command_id=$(aws ssm send-command --instance-ids "${instance_id}" \
  --document-name AWS-RunShellScript --parameters "file://${parameters}" \
  --comment "${comment:0:100}" --query Command.CommandId --output text)

status=Pending
for _ in $(seq 1 180); do
  sleep 5
  status=$(aws ssm get-command-invocation --command-id "${command_id}" --instance-id "${instance_id}" \
    --query Status --output text 2>/dev/null || echo Pending)
  case "${status}" in
    Pending | InProgress | Delayed) ;;
    *) break ;;
  esac
done

aws ssm get-command-invocation --command-id "${command_id}" --instance-id "${instance_id}" \
  --query '[StandardOutputContent, StandardErrorContent]' --output text
if [ "${status}" != Success ]; then
  echo "::error::${comment} finished with ${status}"
  exit 1
fi
