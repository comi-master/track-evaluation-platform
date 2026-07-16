#!/usr/bin/env sh
set -eu

password=${REDIS_PASSWORD:-}
password_length=${#password}

if [ "$password_length" -lt 16 ] || [ "$password_length" -gt 128 ]; then
  printf '%s\n' 'REDIS_PASSWORD must be 16-128 characters using only letters, digits, dot, underscore, or hyphen.' >&2
  exit 1
fi

case "$password" in
  *[!A-Za-z0-9._-]*)
    printf '%s\n' 'REDIS_PASSWORD must be 16-128 characters using only letters, digits, dot, underscore, or hyphen.' >&2
    exit 1
    ;;
esac

if [ "${1:-}" = "--validate-only" ]; then
  exit 0
fi

umask 077
printf 'requirepass %s\n' "$password" > /tmp/redis-auth.conf
chown redis:redis /tmp/redis-auth.conf
exec /usr/local/bin/docker-entrypoint.sh redis-server /tmp/redis-auth.conf --appendonly yes
