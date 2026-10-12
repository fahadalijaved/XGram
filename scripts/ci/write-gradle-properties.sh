#!/usr/bin/env bash
# Writes Gradle memory/worker settings for the CI runner into $GRADLE_USER_HOME/gradle.properties
# (default ~/.gradle), which overrides the project's gradle.properties. The project file keeps the 3 GB heap
# and 3 workers chosen for an 8 GB laptop; this picks values from the runner's RAM and CPU count:
#   >= 15 GB RAM (4-core public-repo runner): -Xmx5g, Kotlin daemon 2g, up to 4 workers
#   smaller (2-core / 7 GB private-repo runner): -Xmx3g, Kotlin daemon 1536m, up to 2 workers
# Overrides: XGRAM_GRADLE_JVMARGS, XGRAM_GRADLE_WORKERS.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

home=${GRADLE_USER_HOME:-$HOME/.gradle}
mkdir -p "$home"

cpus=$(nproc 2> /dev/null || echo 2)
mem_kb=$(awk '/^MemTotal:/ { print $2 }' /proc/meminfo 2> /dev/null || echo 0)
mem_gb=$((mem_kb / 1024 / 1024))

if ((mem_gb >= 15)); then
  heap=5g
  kotlin=2g
  max_workers=4
else
  heap=3g
  kotlin=1536m
  max_workers=2
fi
workers=$((cpus < max_workers ? cpus : max_workers))
((workers >= 1)) || workers=1

jvmargs=${XGRAM_GRADLE_JVMARGS:-"-Xmx$heap -Dkotlin.daemon.jvm.options=\"-Xmx$kotlin\" -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED"}
workers=${XGRAM_GRADLE_WORKERS:-$workers}

{
  printf '\n# Added by scripts/ci/write-gradle-properties.sh (%s CPUs, %s GB RAM)\n' "$cpus" "$mem_gb"
  printf 'org.gradle.jvmargs=%s\n' "$jvmargs"
  printf 'org.gradle.workers.max=%s\n' "$workers"
} >> "$home/gradle.properties"

log "Gradle: org.gradle.jvmargs=$jvmargs, org.gradle.workers.max=$workers ($cpus CPUs, $mem_gb GB RAM)"
