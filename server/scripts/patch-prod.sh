#!/bin/bash
# Installed as /opt/locatedo/patch.sh and run weekly by locatedo-patch.timer.
# Amazon Linux 2023 pins its repositories to the AMI's release, so security
# fixes from later releases only arrive with --releasever=latest.
set -euo pipefail

dnf upgrade --releasever=latest --security -y

if ! dnf needs-restarting -r >/dev/null; then
  echo "rebooting to finish the update"
  systemctl reboot
fi
