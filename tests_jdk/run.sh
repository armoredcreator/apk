#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" "$ROOT/app/src/main/java/com/frame/framematch/RegionMath.java" \
  "$ROOT/app/src/main/java/com/frame/framematch/MatchChoices.java" \
  "$ROOT/app/src/main/java/com/frame/framematch/MasterColorMatch.java" \
  "$ROOT/app/src/main/java/com/frame/framematch/QualityProfile.java" \
  "$ROOT/app/src/main/java/com/frame/framematch/NaturalSelector.java" \
  "$ROOT/app/src/main/java/com/frame/framematch/AlignmentMath.java" \
  "$ROOT/app/src/main/java/com/frame/framematch/EntranceMatch.java" \
  "$ROOT/app/src/main/java/com/frame/framematch/TemporalColor.java" \
  "$ROOT/app/src/main/java/com/frame/framematch/PreviewWindow.java" \
  "$ROOT/tests_jdk/RegionMathSelfTest.java" "$ROOT/tests_jdk/MatchChoicesSelfTest.java" \
  "$ROOT/tests_jdk/MasterColorMatchSelfTest.java" \
  "$ROOT/tests_jdk/QualityProfileSelfTest.java" \
  "$ROOT/tests_jdk/NaturalSelectorSelfTest.java" \
  "$ROOT/tests_jdk/AlignmentMathSelfTest.java" \
  "$ROOT/tests_jdk/EntranceMatchSelfTest.java" \
  "$ROOT/tests_jdk/TemporalColorSelfTest.java" \
  "$ROOT/tests_jdk/PreviewWindowSelfTest.java"
java -cp "$OUT" RegionMathSelfTest
java -cp "$OUT" MatchChoicesSelfTest
java -cp "$OUT" MasterColorMatchSelfTest
java -cp "$OUT" QualityProfileSelfTest
java -cp "$OUT" NaturalSelectorSelfTest
java -cp "$OUT" AlignmentMathSelfTest
java -cp "$OUT" EntranceMatchSelfTest
java -cp "$OUT" TemporalColorSelfTest
java -cp "$OUT" PreviewWindowSelfTest
