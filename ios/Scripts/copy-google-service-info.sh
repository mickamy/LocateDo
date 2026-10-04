#!/bin/sh
set -eu

source="${SRCROOT}/Firebase/GoogleService-Info-${CONFIGURATION}.plist"
destination="${BUILT_PRODUCTS_DIR}/${UNLOCALIZED_RESOURCES_FOLDER_PATH}/GoogleService-Info.plist"

if [ -f "${source}" ]; then
  cp "${source}" "${destination}"
else
  echo "warning: ${source} not found; Firebase stays disabled in this build"
  rm -f "${destination}"
fi
