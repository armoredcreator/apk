#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" "$ROOT/app/src/main/java/com/frame/framematch/RegionMath.java" \
  "$ROOT/app/src/main/java/com/frame/framematch/MatchChoices.java" \
  "$ROOT/app/src/main/java/com/frame/framematch/MasterColorMatch.java" \
  "$ROOT/tests_jdk/RegionMathSelfTest.java" "$ROOT/tests_jdk/MatchChoicesSelfTest.java" \
  "$ROOT/tests_jdk/MasterColorMatchSelfTest.java"
java -cp "$OUT" RegionMathSelfTest
java -cp "$OUT" MatchChoicesSelfTest
java -cp "$OUT" MasterColorMatchSelfTest
