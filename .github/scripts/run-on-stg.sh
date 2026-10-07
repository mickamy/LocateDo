#!/bin/bash
# Runs a script as root on the staging Pi through SSM, prints its output, and
# fails when it does. Called from jobs that assumed locatedo-stg-deploy.
#   run-on-stg.sh <script file> <comment>
set -euo pipefail

script=${1:?script file}
comment=${2:?comment}

node_id=$(aws ssm describe-instance-information \
  --filters Key=tag:Name,Values=locatedo-stg-pi \
  --query "InstanceInformationList[?PingStatus=='Online'] | [0].InstanceId" --output text)
if [ -z "${node_id}" ] || [ "${node_id}" = None ]; then
  echo "::error::the locatedo-stg-pi node is not online in SSM"
  exit 1
fi

parameters=$(mktemp)
trap 'rm -f "${parameters}"' EXIT
jq -n --rawfile script "${script}" '{commands: [$script], executionTimeout: ["900"]}' >"${parameters}"

command_id=$(aws ssm send-command --instance-ids "${node_id}" \
  --document-name AWS-RunShellScript --parameters "file://${parameters}" \
  --comment "${comment:0:100}" --query Command.CommandId --output text)

status=Pending
for _ in $(seq 1 180); do
  sleep 5
  status=$(aws ssm get-command-invocation --command-id "${command_id}" --instance-id "${node_id}" \
    --query Status --output text 2>/dev/null || echo Pending)
  case "${status}" in
    Pending | InProgress | Delayed) ;;
    *) break ;;
  esac
done

aws ssm get-command-invocation --command-id "${command_id}" --instance-id "${node_id}" \
  --query '[StandardOutputContent, StandardErrorContent]' --output text
if [ "${status}" != Success ]; then
  echo "::error::${comment} finished with ${status}"
  exit 1
fi
