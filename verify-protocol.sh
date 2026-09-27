#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"
out_dir="${TMPDIR:-/tmp}/buds4-readonly-protocol-test"
mkdir -p "$out_dir"
compiler=(javac)
if ! command -v javac >/dev/null 2>&1; then
  compiler=(java -m jdk.compiler/com.sun.tools.javac.Main)
fi
"${compiler[@]}" -d "$out_dir" \
  "$project_dir/app/src/main/java/dev/ashraf/buds4readonly/protocol/InnerPacket.java" \
  "$project_dir/app/src/main/java/dev/ashraf/buds4readonly/protocol/GestureRecord.java" \
  "$project_dir/app/src/main/java/dev/ashraf/buds4readonly/protocol/GestureResponseParser.java" \
  "$project_dir/app/src/main/java/dev/ashraf/buds4readonly/protocol/OpoV1.java" \
  "$project_dir/app/src/main/java/dev/ashraf/buds4readonly/core/GestureSelectors.java" \
  "$project_dir/app/src/main/java/dev/ashraf/buds4readonly/core/ConnectionStatus.java" \
  "$project_dir/app/src/main/java/dev/ashraf/buds4readonly/core/SleepLockStatus.java" \
  "$project_dir/app/src/main/java/dev/ashraf/buds4readonly/core/UiStateResolver.java" \
  "$project_dir/app/src/main/java/dev/ashraf/buds4readonly/SleepLockTransaction.java" \
  "$project_dir/tools/ProtocolSelfTest.java" \
  "$project_dir/tools/UiStateResolverTest.java" \
  "$project_dir/tools/SleepLockTransactionTest.java"
java -cp "$out_dir" ProtocolSelfTest
java -cp "$out_dir" UiStateResolverTest
java -cp "$out_dir" SleepLockTransactionTest
