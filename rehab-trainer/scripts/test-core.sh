#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v kotlinc >/dev/null || { echo "Install Kotlin CLI 1.9+ and JDK17+; or use gradle :core:regressionTest" >&2; exit 1; }
mkdir -p core/build
kotlinc core/src/main/kotlin core/src/test/kotlin -jvm-target 17 -include-runtime -d core/build/regression-tests.jar
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar core/build/regression-tests.jar
