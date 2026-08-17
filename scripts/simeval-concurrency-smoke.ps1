param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [Parameter(Mandatory = $true)][string]$AdminUsername,
    [Parameter(Mandatory = $true)][string]$AdminPassword,
    [int]$VirtualUsers = 100,
    [int]$TasksPerUser = 10,
    [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
if ($VirtualUsers -lt 1 -or $VirtualUsers -gt 200) { throw "VirtualUsers must be between 1 and 200" }
if ($TasksPerUser -lt 1 -or $TasksPerUser -gt 50) { throw "TasksPerUser must be between 1 and 50" }
$totalTasks = $VirtualUsers * $TasksPerUser

function Invoke-Api([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null) {
    $headers = if ($Token) { @{ Authorization = "Bearer $Token" } } else { @{} }
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $headers; TimeoutSec = 120 }
    if ($null -ne $Body) { $params.ContentType = "application/json"; $params.Body = ($Body | ConvertTo-Json -Compress) }
    Invoke-RestMethod @params
}

function Invoke-Sql([string]$Sql) {
    $result = $Sql | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=socket -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -N -s'
    if ($LASTEXITCODE -ne 0) { throw "Database query failed" }
    @($result)
}

function Get-QueueDepth {
    $lines = @(docker compose exec -T rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged 2>$null)
    if ($LASTEXITCODE -ne 0) { return 0 }
    $sum = 0
    foreach ($line in $lines) {
        $parts = ($line -split "\s+") | Where-Object { $_ }
        if ($parts.Count -ge 3 -and $parts[1] -as [int] -ne $null -and $parts[2] -as [int] -ne $null) {
            $sum += [int]$parts[1] + [int]$parts[2]
        }
    }
    $sum
}

function New-HttpTask([System.Net.Http.HttpClient]$Client, [string]$Path, [string]$Json) {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$BaseUrl$Path")
    $request.Content = [System.Net.Http.StringContent]::new($Json, [Text.Encoding]::UTF8, "application/json")
    [pscustomobject]@{ Request = $request; Task = $Client.SendAsync($request) }
}

$marker = "simeval-load-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())-$([Guid]::NewGuid().ToString('N').Substring(0, 8))"
$temp = Join-Path ([IO.Path]::GetTempPath()) $marker
New-Item -ItemType Directory -Path $temp | Out-Null
$csv = Join-Path $temp "sample.csv"
$datasetId = 0
$fileId = 0
$taskIds = @()
$client = $null
try {
    $writer = [IO.StreamWriter]::new($csv, $false, [Text.UTF8Encoding]::new($false))
    try {
        $writer.WriteLine("time,true_x,true_y,true_z,track_x,track_y,track_z")
        1..8 | ForEach-Object { $writer.WriteLine("$_,$_,$([int]($_ % 2)),0,$($_ + 0.1),$([int]($_ % 2)),0") }
    } finally { $writer.Dispose() }

    if ((Invoke-RestMethod "$BaseUrl/actuator/health").status -ne "UP") { throw "Application is not healthy" }
    $token = (Invoke-Api POST "/api/v1/auth/login" @{ username = $AdminUsername; password = $AdminPassword }).data.accessToken
    $datasetId = (Invoke-Api POST "/api/v1/datasets" @{ name = $marker } $token).data.id
    $raw = & curl.exe --fail --silent --show-error --max-time 120 -H "Authorization: Bearer $token" -F "file=@$csv;type=text/csv" -F "trackSource=RADAR" "$BaseUrl/api/v1/datasets/$datasetId/track-files"
    if ($LASTEXITCODE -ne 0) { throw "CSV upload failed" }
    $fileId = ($raw | ConvertFrom-Json).data.id
    Invoke-Api POST "/api/v1/track-files/$fileId/parse" $null $token | Out-Null

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $token)
    $json = (@{ abnormalThreshold = 0.5 } | ConvertTo-Json -Compress)
    $submitStart = [Diagnostics.Stopwatch]::StartNew()
    $requests = @()
    for ($i = 0; $i -lt $totalTasks; $i++) { $requests += New-HttpTask $client "/api/v1/track-files/$fileId/analysis-tasks" $json }
    [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]($requests | ForEach-Object { $_.Task }))
    $submitStart.Stop()
    foreach ($item in $requests) {
        $response = $item.Task.Result
        $body = $response.Content.ReadAsStringAsync().Result
        if (-not $response.IsSuccessStatusCode) { throw "Task submission failed with HTTP $([int]$response.StatusCode)" }
        $taskIds += [long](($body | ConvertFrom-Json).data.taskId)
        $item.Request.Dispose()
    }

    $pollStart = [Diagnostics.Stopwatch]::StartNew()
    $peakQueue = 0
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $completed = @()
    do {
        $depth = Get-QueueDepth
        if ($depth -gt $peakQueue) { $peakQueue = $depth }
        $idList = ($taskIds -join ",")
        $completed = Invoke-Sql "SELECT status FROM analysis_task WHERE id IN ($idList) AND status IN ('SUCCESS','FAILED');"
        if ($completed.Count -lt $totalTasks) { Start-Sleep -Milliseconds 500 }
    } while ($completed.Count -lt $totalTasks -and (Get-Date) -lt $deadline)
    $pollStart.Stop()
    if ($completed.Count -lt $totalTasks) { throw "Timed out: completed $($completed.Count)/$totalTasks tasks" }

    $rows = Invoke-Sql "SELECT status, TIMESTAMPDIFF(MICROSECOND, created_at, finished_at) / 1000 FROM analysis_task WHERE id IN ($idList) ORDER BY id;"
    $durations = @(); $success = 0; $failed = 0
    foreach ($row in $rows) {
        $parts = $row -split "\t"
        if ($parts[0] -eq "SUCCESS") { $success++ } else { $failed++ }
        if ($parts.Count -gt 1 -and $parts[1] -as [double] -ne $null) { $durations += [double]$parts[1] }
    }
    $sorted = @($durations | Sort-Object)
    $average = if ($sorted.Count) { [Math]::Round(($sorted | Measure-Object -Average).Average, 2) } else { 0 }
    $p95Index = [Math]::Max(0, [Math]::Ceiling($sorted.Count * 0.95) - 1)
    $p95 = if ($sorted.Count) { [Math]::Round($sorted[$p95Index], 2) } else { 0 }
    $totalSeconds = [Math]::Max(0.001, $pollStart.Elapsed.TotalSeconds)
    $throughput = [Math]::Round($success / $totalSeconds, 2)
    $failureRate = [Math]::Round(($failed / [double]$totalTasks) * 100, 2)
    Write-Output "SIMBENCH_CONCURRENCY=PASS VIRTUAL_USERS=$VirtualUsers TASKS_PER_USER=$TasksPerUser TOTAL_TASKS=$totalTasks"
    Write-Output "SUBMIT_HTTP_MS=$([Math]::Round($submitStart.Elapsed.TotalMilliseconds, 1)) COMPLETION_WINDOW_S=$([Math]::Round($totalSeconds, 2))"
    Write-Output "SUCCESS=$success FAILED=$failed FAILURE_RATE_PERCENT=$failureRate THROUGHPUT_TASKS_PER_SECOND=$throughput"
    Write-Output "TASK_AVERAGE_MS=$average TASK_P95_MS=$p95 QUEUE_PEAK_MESSAGES=$peakQueue"
    Write-Output "WORKER_CONCURRENCY=read from RABBITMQ_CONCURRENCY/RABBITMQ_MAX_CONCURRENCY; this run measures the current container configuration."
    Write-Output "This is a local LAN engineering benchmark, not a production capacity claim."
} finally {
    if ($client) { $client.Dispose() }
    if ($datasetId -gt 0) {
        $sql = "DELETE FROM analysis_task WHERE track_file_id=$fileId; DELETE FROM abnormal_interval WHERE analysis_result_id IN (SELECT id FROM analysis_result WHERE track_file_id=$fileId); DELETE FROM analysis_result WHERE track_file_id=$fileId; DELETE FROM track_point WHERE track_file_id=$fileId; DELETE FROM track_file WHERE id=$fileId; DELETE FROM dataset WHERE id=$datasetId;"
        Invoke-Sql $sql | Out-Null
    }
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
}
