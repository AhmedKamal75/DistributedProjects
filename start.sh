#!/bin/bash
set -euo pipefail

# Clean classes directory and compile Java sources
rm -rf classes
mkdir -p classes
javac -d classes shared/*.java client/*.java server/*.java *.java

echo "Starting application..."
exec java -cp classes Start "$@"
