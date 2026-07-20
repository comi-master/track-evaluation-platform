[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$OutputDirectory = "demo-output",
    [int]$StartupTimeoutSeconds = 180,
    [int]$TaskTimeoutSeconds = 90,
    [double]$AbnormalThreshold = 60.0
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $utf8WithoutBom
$OutputEncoding = $utf8WithoutBom
$startedAt = Get-Date
$root = Split-Path -Parent $PSScriptRoot
$out = if ([IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory } else { Join-Path $root $OutputDirectory }
$dataDirectory = Join-Path $out "generated-data"
$reportDirectory = Join-Path $out "reports"
$appLog = Join-Path $out "application.log"
$appErrorLog = Join-Path $out "application-error.log"
$appProcess = $null
$username = "demo_$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())_$((Get-Random -Maximum 9999).ToString('0000'))"
$datasetName = "Synthetic multi-source demo $([DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))"
$password = "Demo-$([Guid]::NewGuid().ToString('N'))!Aa9"
$token = $null
New-Item -ItemType Directory -Force -Path $out, $reportDirectory | Out-Null

function Write-Step([string]$Message) { Write-Output "[$([DateTime]::UtcNow.ToString('HH:mm:ss')) UTC] $Message" }
function Invoke-Checked([scriptblock]$Command, [string]$Failure) { & $Command; if ($LASTEXITCODE -ne 0) { throw $Failure } }
function Get-ComposeServiceRuntimeState([string]$Service) {
    $containerIds = @(& docker compose --env-file .env ps -q $Service)
    if ($LASTEXITCODE -ne 0) { throw "Failed to resolve the container for Compose service '$Service'." }
    $containerIds = @($containerIds | ForEach-Object { $_.Trim() } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($containerIds.Count -eq 0) {
        return [pscustomobject]@{ Service = $Service; ContainerId = $null; Status = 'missing'; Health = $null }
    }
    if ($containerIds.Count -ne 1) { throw "Compose service '$Service' resolved to more than one container." }

    $containerId = $containerIds[0]
    $stateJson = @(& docker inspect --format '{{json .State}}' $containerId)
    if ($LASTEXITCODE -ne 0) { throw "Failed to inspect the container for Compose service '$Service'." }
    $stateText = ($stateJson -join '').Trim()
    if ([string]::IsNullOrWhiteSpace($stateText)) { throw "Docker returned an empty state for Compose service '$Service'." }
    try { $state = $stateText | ConvertFrom-Json } catch { throw "Docker returned an invalid state for Compose service '$Service'." }
    $health = if ($null -ne $state.Health) { [string]$state.Health.Status } else { $null }
    [pscustomobject]@{ Service = $Service; ContainerId = $containerId; Status = [string]$state.Status; Health = $health }
}
function Wait-ComposeInfrastructure {
    $services = @('mysql','redis','rabbitmq','minio')
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    do {
        $states = @($services | ForEach-Object { Get-ComposeServiceRuntimeState $_ })
        $ready = @($states | Where-Object { $_.Status -eq 'running' -and ([string]::IsNullOrWhiteSpace($_.Health) -or $_.Health -eq 'healthy') }).Count
        if ($ready -eq $services.Count) { return $states }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    $summary = ($states | ForEach-Object { "$($_.Service)=$($_.Status)/$(if ($_.Health) { $_.Health } else { 'no-healthcheck' })" }) -join ', '
    throw "The four infrastructure services did not become healthy within $StartupTimeoutSeconds seconds ($summary)."
}
function Import-DotEnv {
    $path = Join-Path $root ".env"
    if (-not (Test-Path $path)) { throw ".env is required. Copy .env.example and replace every placeholder." }
    foreach ($line in [IO.File]::ReadAllLines($path)) {
        if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
    }
}
function Api([string]$Method, [string]$Path, $Body = $null, [string]$TokenValue = $null, [int]$ExpectedStatus = 200) {
    $headers = if ($TokenValue) { @{ Authorization = "Bearer $TokenValue" } } else { @{} }
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $headers; TimeoutSec = 30; UseBasicParsing = $true }
    if ($null -ne $Body) { $params.ContentType = "application/json"; $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress) }
    try { $response = Invoke-WebRequest @params } catch { throw "HTTP transport failed: $Method $Path ($($_.Exception.Message))" }
    if ([int]$response.StatusCode -ne $ExpectedStatus) { throw "HTTP $Method $Path returned $($response.StatusCode), expected $ExpectedStatus." }
    if ([string]::IsNullOrWhiteSpace($response.Content)) { return $null }
    $response.Content | ConvertFrom-Json
}
function Upload([long]$DatasetId, [string]$Source, [string]$File, [string]$TokenValue) {
    $responseFile = Join-Path $out "upload-$($Source.ToLowerInvariant()).json"
    try {
        & curl.exe --fail --silent --show-error --max-time 30 -o $responseFile -w "%{http_code}" -H "Authorization: Bearer $TokenValue" -F "file=@$File;type=text/csv" -F "trackSource=$Source" "$BaseUrl/api/v1/datasets/$DatasetId/track-files" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Upload transport failed." }
        $payload = Get-Content -Raw -Encoding UTF8 $responseFile | ConvertFrom-Json
        if (-not $payload.data.id) { throw "Upload response did not contain a file ID." }
        $payload.data.id
    } finally { Remove-Item -Force -ErrorAction SilentlyContinue $responseFile }
}
function Wait-Health {
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    $lastFailure = "no successful response"
    do {
        try {
            $health = Invoke-RestMethod -Method Get -UseBasicParsing -TimeoutSec 10 -Uri "$BaseUrl/actuator/health"
            if ($health.status -eq 'UP') { return $health }
            $lastFailure = "reported status $($health.status)"
        } catch { $lastFailure = $_.Exception.Message }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Application health did not become UP within $StartupTimeoutSeconds seconds (last result: $lastFailure). See the ignored application logs in demo-output."
}
function Redis-KeyExists([string]$Key) {
    $value = & docker compose --env-file .env exec -T redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --no-auth-warning EXISTS "$1"' sh $Key
    if ($LASTEXITCODE -ne 0) { throw "Redis cache verification failed." }
    (($value | Select-Object -Last 1).Trim() -eq '1')
}
function Queue-Depths {
    $lines = & docker compose --env-file .env exec -T rabbitmq rabbitmqctl list_queues name messages --quiet
    if ($LASTEXITCODE -ne 0) { throw "RabbitMQ queue verification failed." }
    $depths = @{}
    foreach ($line in $lines) { if ($line -match '^([^\s]+)\s+(\d+)$') { $depths[$matches[1]] = [int]$matches[2] } }
    $depths
}

try {
    Set-Location $root
    Write-Step "Checking Java 17, Docker, Compose configuration, and local environment."
    Import-DotEnv
    $javaText = (& java --version) -join ' '
    if ($LASTEXITCODE -ne 0 -or $javaText -notmatch '(?i)(openjdk|java) 17[\.]') { throw "Java 17 is required (detected: $javaText)." }
    Invoke-Checked { docker version --format '{{.Server.Version}}' | Out-Null } "Docker is not available."
    Invoke-Checked { docker compose --env-file .env config --quiet } "Compose configuration is invalid or contains unresolved required values."
    & "$PSScriptRoot\check-environment.ps1"
    if ($LASTEXITCODE -ne 0) { throw "Environment validation failed." }

    Write-Step "Starting or reusing MySQL, Redis, RabbitMQ, and MinIO."
    Invoke-Checked { docker compose --env-file .env up -d } "Compose infrastructure failed to start."
    $infrastructureStates = Wait-ComposeInfrastructure

    try { $null = Api GET "/actuator/health" } catch {
        Write-Step "Starting the Spring Boot application."
        $appProcess = Start-Process -FilePath (Join-Path $root 'mvnw.cmd') -ArgumentList 'spring-boot:run' -WorkingDirectory $root -PassThru -WindowStyle Hidden -RedirectStandardOutput $appLog -RedirectStandardError $appErrorLog
    }
    $health = Wait-Health
    $swagger = Invoke-WebRequest -UseBasicParsing -TimeoutSec 30 -Uri "$BaseUrl/swagger-ui.html"
    if ($swagger.StatusCode -ne 200) { throw "Swagger UI is not reachable." }

    Write-Step "Generating and validating deterministic 2000-point synthetic tracks."
    & "$PSScriptRoot\generate-demo-tracks.ps1" -OutputDirectory $dataDirectory -AbnormalThreshold $AbnormalThreshold
    if ($LASTEXITCODE -ne 0) { throw "Synthetic track generation failed." }

    Write-Step "Registering an isolated temporary user and creating the dataset."
    Api POST "/api/v1/auth/register" @{ username = $username; password = $password } $null 201 | Out-Null
    $login = Api POST "/api/v1/auth/login" @{ username = $username; password = $password }
    $token = $login.data.accessToken
    $userId = [long]$login.data.user.id
    $datasetId = [long](Api POST "/api/v1/datasets" @{ name = $datasetName } $token 201).data.id

    $fileSpecs = @(
        @{ Source = 'RADAR'; Path = Join-Path $dataDirectory 'radar-track.csv' },
        @{ Source = 'INFRARED'; Path = Join-Path $dataDirectory 'infrared-track.csv' },
        @{ Source = 'FUSION'; Path = Join-Path $dataDirectory 'fusion-track.csv' }
    )
    $files = @{}
    $analyses = @{}
    foreach ($spec in $fileSpecs) {
        Write-Step "Uploading, parsing, and synchronously analyzing $($spec.Source)."
        $fileId = [long](Upload $datasetId $spec.Source $spec.Path $token)
        Api POST "/api/v1/track-files/$fileId/parse" $null $token | Out-Null
        $file = (Api GET "/api/v1/track-files/$fileId" $null $token).data
        if ($file.parseStatus -ne 'PARSED' -or $file.pointCount -ne 2000 -or [string]::IsNullOrWhiteSpace($file.sha256)) { throw "$($spec.Source) file verification failed." }
        $analysis = (Api POST "/api/v1/track-files/$fileId/analyses" @{ abnormalThreshold = $AbnormalThreshold } $token 201).data
        if ($analysis.pointCount -ne 2000) { throw "$($spec.Source) analysis point count is invalid." }
        foreach ($metric in @('meanError','rmse','minError','maxError','standardDeviation','abnormalRatio','maxErrorTime')) { if ([double]::IsNaN([double]$analysis.$metric) -or [double]::IsInfinity([double]$analysis.$metric)) { throw "$($spec.Source) contains a non-finite analysis metric." } }
        $intervalPoints = 0L; foreach ($interval in $analysis.intervals) { $intervalPoints += [long]$interval.pointCount }
        if ($intervalPoints -ne [long]$analysis.abnormalCount) { throw "$($spec.Source) abnormal intervals do not match abnormalCount." }
        $files[$spec.Source] = $file
        $analyses[$spec.Source] = $analysis
    }
    if (-not ($analyses.FUSION.rmse -lt $analyses.RADAR.rmse -and $analyses.RADAR.rmse -lt $analyses.INFRARED.rmse)) { throw "Platform RMSE order is invalid." }
    if (-not ($analyses.FUSION.meanError -lt $analyses.RADAR.meanError -and $analyses.RADAR.meanError -lt $analyses.INFRARED.meanError)) { throw "Platform mean-error order is invalid." }
    if (-not ($analyses.FUSION.abnormalRatio -lt $analyses.RADAR.abnormalRatio -and $analyses.RADAR.abnormalRatio -lt $analyses.INFRARED.abnormalRatio)) { throw "Platform abnormal-ratio order is invalid." }

    Write-Step "Executing and polling RabbitMQ asynchronous analysis for FUSION."
    $task = (Api POST "/api/v1/track-files/$($files.FUSION.id)/analysis-tasks" @{ abnormalThreshold = $AbnormalThreshold } $token 202).data
    $observedStates = [Collections.Generic.List[string]]::new(); $observedStates.Add([string]$task.status)
    $deadline = (Get-Date).AddSeconds($TaskTimeoutSeconds)
    do {
        Start-Sleep -Milliseconds 300
        $task = (Api GET "/api/v1/analysis-tasks/$($task.taskId)" $null $token).data
        if (-not $observedStates.Contains([string]$task.status)) { $observedStates.Add([string]$task.status) }
    } while ($task.status -notin @('SUCCESS','FAILED') -and (Get-Date) -lt $deadline)
    if ($task.status -ne 'SUCCESS' -or -not $task.analysisResultId) { throw "Asynchronous analysis did not succeed before timeout." }

    Write-Step "Verifying Redis cache creation, invalidation, and recreation."
    $latestKey = "analysis:latest:${userId}:$($files.FUSION.id)"; $comparisonKey = "analysis:comparison:${userId}:${datasetId}"
    Api GET "/api/v1/track-files/$($files.FUSION.id)/analyses/latest" $null $token | Out-Null
    Api GET "/api/v1/datasets/$datasetId/analysis-comparison" $null $token | Out-Null
    $initialCache = (Redis-KeyExists $latestKey) -and (Redis-KeyExists $comparisonKey)
    if (-not $initialCache) { throw "Redis latest/comparison caches were not established." }
    Api POST "/api/v1/track-files/$($files.FUSION.id)/analyses" @{ abnormalThreshold = $AbnormalThreshold } $token 201 | Out-Null
    $invalidated = -not (Redis-KeyExists $latestKey) -and -not (Redis-KeyExists $comparisonKey)
    if (-not $invalidated) { throw "New analysis did not invalidate both Redis caches." }
    $latest = (Api GET "/api/v1/track-files/$($files.FUSION.id)/analyses/latest" $null $token).data
    $comparison = @(Api GET "/api/v1/datasets/$datasetId/analysis-comparison" $null $token).data
    $recreated = (Redis-KeyExists $latestKey) -and (Redis-KeyExists $comparisonKey)
    if (-not $recreated) { throw "Redis caches were not recreated after reads." }

    $queues = Queue-Depths
    if (($queues['track.analysis.queue'] -as [int]) -ne 0 -or ($queues['track.analysis.dead.queue'] -as [int]) -ne 0) { throw "RabbitMQ main/dead queue is not empty after the asynchronous task." }

    Write-Step "Generating, listing, downloading, and validating the HTML report."
    $report = (Api POST "/api/v1/datasets/$datasetId/reports" @{ title = "Synthetic track comparison report" } $token 201).data
    $history = (Api GET "/api/v1/datasets/$datasetId/reports?page=1&size=20" $null $token).data
    if ($report.sourceFileCount -ne 3 -or $history.total -lt 1) { throw "Report metadata validation failed." }
    $reportPath = Join-Path $reportDirectory "analysis-report-$($report.reportId).html"
    $download = Invoke-WebRequest -UseBasicParsing -TimeoutSec 30 -Headers @{ Authorization = "Bearer $token" } -Uri "$BaseUrl/api/v1/reports/$($report.reportId)/download" -OutFile $reportPath -PassThru
    if ($download.StatusCode -ne 200) { throw "HTML report download failed." }
    $html = Get-Content -Raw -Encoding UTF8 $reportPath
    foreach ($required in @('RADAR','INFRARED','FUSION')) { if ($html -notmatch $required) { throw "HTML report does not contain $required." } }
    if ($html -match '(?i)<script[^>]+src=|<link[^>]+href=https?://|Authorization:\s*Bearer|JWT_SECRET|Demo-[0-9a-f]{20}') { throw "HTML report contains an external dependency or sensitive material." }

    $metrics = @()
    foreach ($source in @('FUSION','RADAR','INFRARED')) {
        $a = $analyses[$source]
        $metrics += [ordered]@{ source = $source; fileId = $files[$source].id; rowCount = $files[$source].pointCount; pointCount = $a.pointCount; meanError = $a.meanError; rmse = $a.rmse; minError = $a.minError; maxError = $a.maxError; standardDeviation = $a.standardDeviation; abnormalCount = $a.abnormalCount; abnormalRatio = $a.abnormalRatio; maxErrorTime = $a.maxErrorTime; abnormalIntervals = $a.intervals }
    }
    $summary = [ordered]@{
        demoTimeUtc = [DateTime]::UtcNow.ToString('o'); gitCommit = (git rev-parse HEAD).Trim(); javaVersion = $javaText
        applicationHealth = $health.status; swaggerStatus = $swagger.StatusCode; demoUsername = $username; datasetId = $datasetId
        thresholdMeters = $AbnormalThreshold; files = $metrics; asynchronousTask = [ordered]@{ taskId = $task.taskId; finalStatus = $task.status; analysisResultId = $task.analysisResultId; observedStates = @($observedStates) }
        redis = [ordered]@{ ownerKeyed = $true; initiallyCreated = $initialCache; invalidatedAfterAnalysis = $invalidated; recreatedAfterRead = $recreated }
        rabbitMq = [ordered]@{ mainQueueDepth = [int]$queues['track.analysis.queue']; deadQueueDepth = [int]$queues['track.analysis.dead.queue'] }
        htmlReportPath = $reportPath; ordering = 'FUSION < RADAR < INFRARED for RMSE, meanError, and abnormalRatio'; elapsedSeconds = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds, 3)
    }
    $summary | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 (Join-Path $out 'demo-summary.json')
    $table = $metrics | ForEach-Object { "| $($_.source) | $($_.pointCount) | $([Math]::Round($_.meanError,3)) | $([Math]::Round($_.rmse,3)) | $([Math]::Round($_.maxError,3)) | $($_.abnormalCount) | $([Math]::Round(100 * $_.abnormalRatio,2))% |" }
    @("# End-to-end demo summary", "", "- Time (UTC): $($summary.demoTimeUtc)", "- Git commit: $($summary.gitCommit)", "- Demo username: $username", "- Dataset ID: $datasetId", "- Application health: UP; Swagger: HTTP $($swagger.StatusCode)", "- Async task: $($task.status), result $($task.analysisResultId), observed $($observedStates -join ' -> ')", "- Redis: created=$initialCache, invalidated=$invalidated, recreated=$recreated", "- RabbitMQ: main=$($summary.rabbitMq.mainQueueDepth), dead=$($summary.rabbitMq.deadQueueDepth)", "- HTML report: $reportPath", "- Ordering: $($summary.ordering)", "- Elapsed: $($summary.elapsedSeconds) seconds", "", "| source | pointCount | meanError | RMSE | maxError | abnormalCount | abnormalRatio |", "|---|---:|---:|---:|---:|---:|---:|", $table) | Set-Content -Encoding UTF8 (Join-Path $out 'demo-summary.md')
    Write-Step "DEMO PASS. Dataset=$datasetId; user=$username; report=$reportPath. Credentials and tokens were not printed or saved."
} catch {
    Write-Error "DEMO FAILED: $($_.Exception.Message)"
    throw
} finally {
    $token = $null; $password = $null
    if ($appProcess -and -not $appProcess.HasExited) {
        Write-Step "Stopping the application process started by this demo."
        & taskkill.exe /PID $appProcess.Id /T /F 2>$null | Out-Null
        try { Wait-Process -Id $appProcess.Id -Timeout 15 -ErrorAction SilentlyContinue } catch { }
    }
    Set-Location $root
}
