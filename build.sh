#!/bin/bash

# Compile Java files into classes directory
echo "Compiling Java files..."
mkdir -p classes
javac -d classes shared/*.java client/*.java server/*.java *.java

if [ $? -eq 0 ]; then
    echo "✓ Compilation successful"
else
    echo "✗ Compilation failed"
    exit 1
fi
