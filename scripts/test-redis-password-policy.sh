#!/usr/bin/env sh
set -u

script_directory=${0%/*}
if [ "$script_directory" = "$0" ]; then
  script_directory=.
fi
project_root=$(CDPATH= cd -- "$script_directory/.." && pwd)
entrypoint="$project_root/docker/redis/entrypoint.sh"
failures=0
executed=0

assert_valid() {
  name=$1
  value=$2
  executed=$((executed + 1))
  if REDIS_PASSWORD="$value" /bin/sh "$entrypoint" --validate-only >/dev/null 2>&1; then
    printf '%s\n' "$name=PASS"
  else
    printf '%s\n' "$name=FAIL expected=valid" >&2
    failures=$((failures + 1))
  fi
}

assert_invalid() {
  name=$1
  value=$2
  executed=$((executed + 1))
  output=$(REDIS_PASSWORD="$value" /bin/sh "$entrypoint" --validate-only 2>&1)
  actual=$?
  if [ "$actual" -ne 0 ] && ! printf '%s' "$output" | grep -Fq -- "$value"; then
    printf '%s\n' "$name=PASS"
  else
    printf '%s\n' "$name=FAIL expected=safe-rejection" >&2
    failures=$((failures + 1))
  fi
}

assert_valid valid-guid-style 123e4567-e89b-12d3-a456-426614174000
assert_invalid contains-space 'invalid password value'
assert_invalid contains-double-quote 'invalid"password-value'
assert_invalid contains-backslash 'invalid\password-value'
assert_invalid too-short short-value

printf '%s\n' "redis-password-policy-cases=$executed failures=$failures"
if [ "$failures" -eq 0 ]; then
  exit 0
fi
exit 1
