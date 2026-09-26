#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p core/build
kotlinc core/src/main/kotlin scripts/Demo.kt -jvm-target 17 -include-runtime -d core/build/rehab-demo.jar
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar core/build/rehab-demo.jar --smoke
