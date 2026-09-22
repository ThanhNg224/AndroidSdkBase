#!/usr/bin/env bash
# Asserts every published AAR emits Kotlin metadata no newer than the declared floor.
# A host on an older Kotlin cannot read newer metadata, so a silent bump here breaks consumers.
#
# Limitation: AAR-only. :sdk:core is a pure-Kotlin JVM module and produces a jar, so it is not
# covered here — see docs/COMPATIBILITY.md.
set -euo pipefail

cd "$(dirname "$0")/.."

FLOOR="$(grep -E '^kotlinMetadataFloor' gradle/libs.versions.toml | sed -E 's/.*"(.*)".*/\1/')"
FLOOR_MAJOR="${FLOOR%%.*}"
FLOOR_MINOR="${FLOOR#*.}"

echo "Declared Kotlin language floor: ${FLOOR_MAJOR}.${FLOOR_MINOR}"
echo "Metadata format versions at or below 2.0 are readable by every Kotlin 2.x consumer."

# The metadata BINARY format version is what a consumer's compiler reads, and it does not track
# languageVersion one-for-one: languageVersion 2.0 and 2.2 both emit mv 2.0.0, while an unpinned
# 2.4 toolchain emits 2.4.0. The gate is therefore on the format version, capped at 2.0.
MAX_MAJOR=2
MAX_MINOR=0

status=0
found=0
while IFS= read -r aar; do
  found=1
  module="$(basename "$aar" | sed -E 's/-release\.aar$//')"
  work="$(mktemp -d)"
  unzip -o -q "$aar" -d "$work"
  if [ ! -f "$work/classes.jar" ]; then
    echo "SKIP $module (no classes.jar)"
    rm -rf "$work"
    continue
  fi
  # An empty classes.jar is legitimate for a module with no sources yet, and `unzip` exits
  # non-zero on one, so tolerate that explicitly rather than letting `set -e` abort the run.
  mkdir -p "$work/cls"
  unzip -o -q "$work/classes.jar" -d "$work/cls" 2>/dev/null || true

  class_count="$(find "$work/cls" -name '*.class' | wc -l | tr -d ' ')"
  if [ "$class_count" -eq 0 ]; then
    echo "SKIP $module (no compiled classes)"
    rm -rf "$work"
    continue
  fi

  worst=""
  while IFS= read -r class; do
    mv_pair="$(javap -v -p "$class" 2>/dev/null \
      | sed -n '/Utf8               mv/,+3p' \
      | grep -E '= Integer' | head -2 | awk '{print $NF}' | paste -sd. -)"
    [ -n "$mv_pair" ] || continue
    major="${mv_pair%%.*}"
    minor="${mv_pair#*.}"
    if [ "$major" -gt "$MAX_MAJOR" ] || { [ "$major" -eq "$MAX_MAJOR" ] && [ "$minor" -gt "$MAX_MINOR" ]; }; then
      worst="$mv_pair"
      break
    fi
  done < <(find "$work/cls" -name '*.class')

  if [ -n "$worst" ]; then
    echo "FAIL $module emits metadata mv=$worst (max allowed ${MAX_MAJOR}.${MAX_MINOR})"
    status=1
  else
    echo "OK   $module"
  fi
  rm -rf "$work"
done < <(find . -path '*/build/outputs/aar/*-release.aar' | sort)

if [ "$found" -eq 0 ]; then
  echo "FAIL no release AARs found — run ./gradlew assembleRelease first"
  exit 1
fi

exit "$status"
