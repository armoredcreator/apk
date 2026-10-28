#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" "$ROOT/app/src/main/java/com/frame/framematch/RegionMath.java" "$ROOT/tests_jdk/RegionMathSelfTest.java"
java -cp "$OUT" RegionMathSelfTest
