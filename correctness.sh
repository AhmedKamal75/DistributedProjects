#!/bin/bash
set -euo pipefail

usage() {
  cat <<EOF
Usage: $0 [ondemand|all]
Runs CorrectnessHarness with stdin-driven engine input.

  ondemand  - calculate results on demand
  all       - precompute all results beforehand
EOF
}

if [ $# -ne 1 ]; then
  usage
  exit 1
fi

mode="${1,,}"
if [[ "$mode" != "ondemand" && "$mode" != "all" ]]; then
  usage
  exit 1
fi

# Clean classes directory and compile Java sources
rm -rf classes
mkdir -p classes
javac -d classes shared/*.java client/*.java server/*.java *.java

echo "Running CorrectnessHarness ($mode)..."
exec java -cp classes CorrectnessHarness "$mode"