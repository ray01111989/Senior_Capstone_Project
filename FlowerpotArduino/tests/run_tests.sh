#!/bin/sh
# Builds and runs the tests for command.h on your computer (no Arduino needed).
# Needs clang++. Run it from the FlowerpotArduino folder:  sh tests/run_tests.sh
set -e
here="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# command.h does #include "network.h", and a quoted include looks in the file's own folder first, which would
# find the real network.h (it needs the WiFi/Ethernet libraries). So the tests use a copy of command.h in a
# folder that only has the mock network.h next to it.
cp "$here/../command.h" "$work/command.h"
cp "$here/mock/network.h" "$work/network.h"

# The Arduino compiler is run with -fpermissive; -Wno-c++11-narrowing is the clang equivalent for
# the { 2, STATUS_ERR } array in command.h.
${CXX:-clang++} -std=c++17 -O1 -g -fsanitize=address,undefined -fno-sanitize-recover=undefined \
  -Wno-c++11-narrowing -I"$work" -o "$work/test_command" "$here/test_command.cpp"
"$work/test_command"
