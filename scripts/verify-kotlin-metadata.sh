#!/usr/bin/env bash
# Asserts every published AAR emits Kotlin metadata no newer than the declared floor.
#
# A consumer compiling against an AAR whose kotlin.Metadata is newer than their own compiler
# understands gets "class was compiled with a newer version of Kotlin" and cannot use the SDK at
# all. That is why this is a gate and not a warning.
#
# HOW THE VERSION IS READ. `javap -v` prints the real annotation, e.g.
#     RuntimeVisibleAnnotations:
#       0: #34(#35=I#4,#36=[I#5,I#6,I#7],...)
#         kotlin.Metadata(
#           mv=[2,2,0]
# so we read `mv=[...]` directly. An earlier version of this script scraped two Integer entries out
# of the constant pool near the `mv` Utf8 string; that was wrong — the constant pool has no such
# ordering guarantee, and it silently reported unrelated numbers. Never go back to that.
#
# LIMITATION: AAR-only. :sdk:core is a pure-Kotlin JVM module and produces a jar, so it is not
# covered here. See docs/COMPATIBILITY.md.
set -euo pipefail

cd "$(dirname "$0")/.."

FLOOR="$(grep -E '^kotlinMetadataFloor' gradle/libs.versions.toml | sed -E 's/.*"(.*)".*/\1/')"
MAX_MAJOR="${FLOOR%%.*}"
MAX_MINOR="${FLOOR#*.}"

echo "Declared floor: languageVersion ${FLOOR} => metadata must not exceed mv=${MAX_MAJOR}.${MAX_MINOR}.x"
echo

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
  mkdir -p "$work/cls"
  # An empty classes.jar is legitimate for a module with no sources; unzip exits non-zero on one.
  unzip -o -q "$work/classes.jar" -d "$work/cls" 2>/dev/null || true

  if [ "$(find "$work/cls" -name '*.class' | wc -l | tr -d ' ')" -eq 0 ]; then
    echo "SKIP $module (no compiled classes)"
    rm -rf "$work"
    continue
  fi

  worst=""
  checked=0
  while IFS= read -r class; do
    mv_raw="$(javap -v -p "$class" 2>/dev/null | grep -oE 'mv=\[[0-9,]+\]' | head -1)"
    [ -n "$mv_raw" ] || continue          # not a Kotlin class: nothing to check
    checked=$((checked + 1))
    triple="${mv_raw#mv=[}"; triple="${triple%]}"
    major="$(echo "$triple" | cut -d, -f1)"
    minor="$(echo "$triple" | cut -d, -f2)"
    if [ "$major" -gt "$MAX_MAJOR" ] || { [ "$major" -eq "$MAX_MAJOR" ] && [ "$minor" -gt "$MAX_MINOR" ]; }; then
      worst="${major}.${minor}"
      echo "     offending class: ${class#"$work/cls/"}"
      break
    fi
  done < <(find "$work/cls" -name '*.class')

  if [ "$checked" -eq 0 ]; then
    echo "SKIP $module (no Kotlin classes carrying metadata)"
  elif [ -n "$worst" ]; then
    echo "FAIL $module emits mv=$worst (max allowed ${MAX_MAJOR}.${MAX_MINOR}) — $checked Kotlin classes checked"
    status=1
  else
    echo "OK   $module ($checked Kotlin classes checked)"
  fi
  rm -rf "$work"
done < <(find . -path '*/build/outputs/aar/*-release.aar' | sort)

if [ "$found" -eq 0 ]; then
  echo "FAIL no release AARs found — run ./gradlew assembleRelease first"
  exit 1
fi

exit "$status"
