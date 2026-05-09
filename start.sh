#!/bin/bash
set -euo pipefail

echo "Starting application..."
exec java -cp classes Start "$@"
