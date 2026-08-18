$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath '.env')) {
  throw 'Missing .env. Copy .env.example to .env and replace every placeholder first.'
}

$envText = Get-Content -Raw -LiteralPath '.env'
if ($envText -match 'replace-with-|<generate-|CHANGE_ME|TODO') {
  throw 'The .env file still contains a placeholder. No containers were started.'
}

docker compose --env-file .env -f docker-compose.yml config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose configuration validation failed.' }

docker compose --env-file .env -f docker-compose.yml up -d mysql redis rabbitmq minio
if ($LASTEXITCODE -ne 0) { throw 'Dependency startup failed.' }

$deadline = (Get-Date).AddMinutes(3)
do {
  $rows = @(docker compose --env-file .env -f docker-compose.yml ps --format json | ConvertFrom-Json)
  $healthy = @($rows | Where-Object { $_.Health -eq 'healthy' }).Count
  if ($rows.Count -eq 4 -and $healthy -eq 4) {
    Write-Output 'Database and middleware dependencies are healthy. Flyway will initialize the schema on application startup.'
    exit 0
  }
  Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

docker compose --env-file .env -f docker-compose.yml ps
throw 'Dependencies did not become healthy within three minutes.'
