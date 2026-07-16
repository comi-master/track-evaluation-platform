#!/usr/bin/env sh
set +e

script_directory=${0%/*}
if [ "$script_directory" = "$0" ]; then
  script_directory=.
fi
project_root=$(CDPATH= cd -- "$script_directory/.." && pwd)
status=0

fail() {
  printf '%s\n' "$1" >&2
  status=1
}

printf '%s\n' '== Java on PATH =='
java_path=$(command -v java 2>/dev/null)
if [ -z "$java_path" ]; then
  fail 'Java is not installed or is not on PATH.'
else
  java_output=$(java -version 2>&1)
  java_exit=$?
  printf '%s\n' "$java_output"
  if [ "$java_exit" -ne 0 ]; then
    fail 'Java could not be executed.'
  else
    case "$java_output" in
      *'version "17.'*|*'version "17"'*) ;;
      *) fail 'Java 17 is required. Set JAVA_HOME and PATH to a JDK 17 installation.' ;;
    esac
  fi

  printf '%s\n' '== Java command =='
  printf '%s\n' "$java_path"

  if [ -z "${JAVA_HOME:-}" ]; then
    fail 'JAVA_HOME must point to the active JDK 17 installation.'
  elif [ ! -x "$JAVA_HOME/bin/java" ]; then
    fail 'JAVA_HOME does not contain an executable bin/java.'
  else
    resolved_java=$java_path
    if command -v readlink >/dev/null 2>&1; then
      readlink_result=$(readlink -f "$java_path" 2>/dev/null)
      if [ -n "$readlink_result" ]; then
        resolved_java=$readlink_result
      fi
    fi
    actual_java_dir=$(CDPATH= cd -P -- "${resolved_java%/*}" 2>/dev/null && pwd)
    expected_java_dir=$(CDPATH= cd -P -- "$JAVA_HOME/bin" 2>/dev/null && pwd)
    if [ -z "$actual_java_dir" ] || [ "$actual_java_dir" != "$expected_java_dir" ]; then
      fail 'JAVA_HOME and the active java command do not reference the same JDK.'
    fi
  fi
fi

printf '%s\n' '== Maven Wrapper =='
if [ ! -f "$project_root/mvnw" ] || [ ! -x "$project_root/mvnw" ]; then
  fail 'Maven Wrapper is missing or is not executable: mvnw'
else
  maven_output=$("$project_root/mvnw" -version 2>&1)
  maven_exit=$?
  printf '%s\n' "$maven_output"
  if [ "$maven_exit" -ne 0 ]; then
    fail 'Maven Wrapper could not be executed.'
  else
    case "$maven_output" in
      *'Java version: 17.'*|*'Java version: 17,'*) ;;
      *) fail 'The Maven Wrapper must run on Java 17.' ;;
    esac
  fi
fi

printf '%s\n' '== Global Maven (optional) =='
if command -v mvn >/dev/null 2>&1; then
  mvn -version
else
  printf '%s\n' 'Global mvn is not installed; Maven Wrapper will be used.'
fi

printf '%s\n' '== Git =='
if ! command -v git >/dev/null 2>&1; then
  fail 'Git is not installed or is not on PATH.'
else
  git -C "$project_root" status --short --branch
  if [ "$?" -ne 0 ]; then
    fail 'Git repository check failed.'
  fi
fi

printf '%s\n' '== Docker =='
if ! command -v docker >/dev/null 2>&1; then
  fail 'Docker Client is not installed or is not on PATH.'
else
  docker version
  if [ "$?" -ne 0 ]; then
    fail 'Docker Client cannot connect to Docker Server.'
  fi

  printf '%s\n' '== Docker Compose =='
  docker compose version
  if [ "$?" -ne 0 ]; then
    fail 'Docker Compose is unavailable or failed to execute.'
  fi
fi

exit "$status"
