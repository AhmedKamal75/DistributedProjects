#!/bin/bash
set -euo pipefail

rm -rf classes
mkdir -p classes
javac -d classes shared/*.java client/*.java server/*.java *.java
echo "Compilation complete."