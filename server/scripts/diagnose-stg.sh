#!/bin/bash
# Runs on the staging Pi through SSM. Prints what helps when staging misbehaves.
set -uo pipefail

cd /opt/locatedo-stg || exit 1

echo "== API from the host"
code=$(curl -s -o /dev/null -m 5 -w "%{http_code}" http://127.0.0.1:8080/healthz || true)
echo "http://127.0.0.1:8080/healthz: ${code:-unreachable}"

echo "== Containers"
docker compose ps --format 'table {{.Service}}\t{{.Image}}\t{{.Status}}'

echo "== API from inside the tunnel's network"
tunnel=$(docker compose ps -q tunnel)
if [ -n "${tunnel}" ]; then
  code=$(docker run --rm --network "container:${tunnel}" curlimages/curl:8.16.0 \
    -s -o /dev/null -m 5 -w "%{http_code}" http://api:8080/healthz || true)
  echo "tunnel -> http://api:8080/healthz: ${code:-unreachable}"

  echo "== Tunnel log (errors and ingress only)"
  docker logs --tail 200 "${tunnel}" 2>&1 |
    grep -i -E "ERR|error|ingress|originService|dial|refused" | tail -30 || true
else
  echo "no tunnel container"
fi

echo "== API internal errors (last 6 hours)"
docker compose logs --no-log-prefix --since 6h api 2>&1 | python3 -c '
import json, sys
for line in sys.stdin:
    try:
        entry = json.loads(line)
    except ValueError:
        continue
    if entry.get("msg") == "internal error":
        print(entry.get("time"), entry.get("procedure"), entry.get("error"))
' | tail -20

echo "== Worker warnings and errors (last 6 hours)"
docker compose logs --no-log-prefix --since 6h worker 2>&1 | python3 -c '
import json, sys
for line in sys.stdin:
    try:
        entry = json.loads(line)
    except ValueError:
        continue
    if entry.get("level") in ("WARN", "ERROR") or entry.get("msg", "").startswith("forgetting"):
        print(entry.get("time"), entry.get("level"), entry.get("msg"),
              entry.get("kind") or entry.get("task") or "", entry.get("apns_environment") or "",
              entry.get("error"))
' | tail -30

echo "== Outbox and devices"
docker compose exec -T db psql -U admin -d locatedo -c "
  SELECT kind, status, count(*), max(attempts) AS max_attempts, min(run_at) AS oldest_run_at
  FROM outbox_messages GROUP BY kind, status ORDER BY kind, status"
docker compose exec -T db psql -U admin -d locatedo -c "
  SELECT kind, attempts, left(last_error, 200) AS last_error
  FROM outbox_messages WHERE last_error IS NOT NULL ORDER BY created_at DESC LIMIT 10"
docker compose exec -T db psql -U admin -d locatedo -c "
  SELECT platform, apns_environment, count(*), max(last_seen_at) AS last_seen_at
  FROM devices GROUP BY platform, apns_environment ORDER BY platform, apns_environment"
