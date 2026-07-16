#!/usr/bin/env sh
set -u

script_directory=${0%/*}
if [ "$script_directory" = "$0" ]; then
  script_directory=.
fi
source_script=$(CDPATH= cd -- "$script_directory" && pwd)/check-environment.sh
mktemp_command=$(command -v mktemp)
mkdir_command=$(command -v mkdir)
cp_command=$(command -v cp)
chmod_command=$(command -v chmod)
rm_command=$(command -v rm)
failures=0
executed=0

new_fixture() {
  root=$($mktemp_command -d)
  $mkdir_command -p "$root/scripts" "$root/jdk/bin" "$root/fake-bin"
  $cp_command "$source_script" "$root/scripts/check-environment.sh"

  printf '%s\n' '#!/bin/sh' \
    'version=${FAKE_JAVA_VERSION:-17}' \
    'printf '\''openjdk version "%s.0.0"\n'\'' "$version" >&2' \
    'exit 0' > "$root/jdk/bin/java"
  printf '%s\n' '#!/bin/sh' \
    'printf '\''Apache Maven 3.9.11\nJava version: 17.0.14, vendor: test, runtime: fixture\n'\''' \
    'exit 0' > "$root/mvnw"
  printf '%s\n' '#!/bin/sh' 'exit 0' > "$root/fake-bin/git"
  printf '%s\n' '#!/bin/sh' \
    'if [ "${1:-}" = version ] && [ "${FAKE_DOCKER_SERVER_FAIL:-0}" = 1 ]; then exit 1; fi' \
    'if [ "${1:-}" = compose ] && [ "${2:-}" = version ] && [ "${FAKE_COMPOSE_FAIL:-0}" = 1 ]; then exit 1; fi' \
    'exit 0' > "$root/fake-bin/docker"

  $chmod_command +x "$root/scripts/check-environment.sh" "$root/jdk/bin/java" \
    "$root/mvnw" "$root/fake-bin/git" "$root/fake-bin/docker"
  printf '%s\n' "$root"
}

run_case() {
  name=$1
  mode=$2
  expected=$3
  executed=$((executed + 1))
  root=$(new_fixture)

  java_version=17
  docker_server_failure=0
  case "$mode" in
    missing-docker) $rm_command -f "$root/fake-bin/docker" ;;
    missing-git) $rm_command -f "$root/fake-bin/git" ;;
    wrong-java) java_version=21 ;;
    missing-wrapper) $rm_command -f "$root/mvnw" ;;
    docker-server-failure) docker_server_failure=1 ;;
  esac

  PATH="$root/jdk/bin:$root/fake-bin" \
    JAVA_HOME="$root/jdk" \
    FAKE_JAVA_VERSION="$java_version" \
    FAKE_DOCKER_SERVER_FAIL="$docker_server_failure" \
    /bin/sh "$root/scripts/check-environment.sh" >"$root/output.log" 2>&1
  actual=$?

  if [ "$actual" -eq "$expected" ]; then
    printf '%s\n' "$name=PASS"
  else
    printf '%s\n' "$name=FAIL expected=$expected actual=$actual" >&2
    while IFS= read -r output_line; do
      printf '%s\n' "$output_line" >&2
    done <"$root/output.log"
    failures=$((failures + 1))
  fi
  $rm_command -rf "$root"
}

run_case normal-environment normal 0
run_case missing-docker-client missing-docker 1
run_case missing-git missing-git 1
run_case wrong-java-version wrong-java 1
run_case missing-wrapper missing-wrapper 1
run_case docker-server-unreachable docker-server-failure 1

printf '%s\n' "environment-check-cases=$executed failures=$failures"
if [ "$failures" -eq 0 ]; then
  exit 0
fi
exit 1
