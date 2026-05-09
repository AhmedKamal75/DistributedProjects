#!/bin/bash
set -euo pipefail

usage() {
  cat <<EOF
Usage: $0 [uni|bi]
Runs CorrectnessHarness with stdin-driven engine input.

  uni      - for the uni-directional mode (BFS)
  bi       - for the bi-directional BFS mode
EOF
}

if [ $# -ne 1 ]; then
  usage
  exit 1
fi

mode="${1,,}"
if [[ "$mode" != "uni" && "$mode" != "bi" ]]; then
  usage
  exit 1
fi

# Clean classes directory and compile Java sources
rm -rf classes
mkdir -p classes
javac -d classes shared/*.java client/*.java server/*.java *.java

echo "Running CorrectnessHarness ($mode)..."
exec java -cp classes CorrectnessHarness "$mode"