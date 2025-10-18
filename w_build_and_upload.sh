#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

SERVER_URL=${SERVER_URL:-http://120.26.211.209}
SERVER_USER=${SERVER_USER:-admin}
SERVER_PASS=${SERVER_PASS:-admin123}

exec python3 "$SCRIPT_DIR/scripts/build_and_upload.py" \
  --server "$SERVER_URL" \
  --username "$SERVER_USER" \
  --password "$SERVER_PASS" \
  "$@"
