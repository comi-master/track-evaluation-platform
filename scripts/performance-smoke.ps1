param([int]$Rows = 10000, [string]$BaseUrl = "http://127.0.0.1:8080")
$ErrorActionPreference = "Stop"
if ($Rows -lt 1 -or $Rows -gt 200000) { throw "Rows must be between 1 and 200000" }
$temp = Join-Path ([IO.Path]::GetTempPath()) ("track-performance-" + [Guid]::NewGuid())
New-Item -ItemType Directory $temp | Out-Null
$csv = Join-Path $temp "synthetic-$Rows.csv"
$name = "perf" + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$created = $false
function Api($method, $path, $body = $null, $token = $null) {
    $headers = if ($token) { @{ Authorization = "Bearer $token" } } else { @{} }
    $p = @{ Method = $method; Uri = "$BaseUrl$path"; Headers = $headers; TimeoutSec = 120 }
    if ($null -ne $body) { $p.ContentType = "application/json"; $p.Body = ($body | ConvertTo-Json -Compress) }
    Invoke-RestMethod @p
}
function Milliseconds($watch) { [Math]::Round($watch.Elapsed.TotalMilliseconds, 1) }
try {
    $writer = [IO.StreamWriter]::new($csv, $false, [Text.UTF8Encoding]::new($false))
    try {
        $writer.WriteLine("time,true_x,true_y,true_z,track_x,track_y,track_z")
        for ($i = 1; $i -le $Rows; $i++) { $writer.WriteLine("$i,$i,0,0,$($i + ($i % 7) / 10.0),0,0") }
    } finally { $writer.Dispose() }
    if ((Api GET "/actuator/health").status -ne "UP") { throw "Application is not healthy" }
    Api POST "/api/v1/auth/register" @{ username = $name; password = "temporary-performance-password" } | Out-Null
    $created = $true
    $token = (Api POST "/api/v1/auth/login" @{ username = $name; password = "temporary-performance-password" }).data.accessToken
    $dataset = (Api POST "/api/v1/datasets" @{ name = "Synthetic performance smoke" } $token).data.id
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $raw = & curl.exe --fail --silent --show-error --max-time 120 -H "Authorization: Bearer $token" -F "file=@$csv;type=text/csv" -F "trackSource=RADAR" "$BaseUrl/api/v1/datasets/$dataset/track-files"
    if ($LASTEXITCODE -ne 0) { throw "Upload failed" }
    $watch.Stop(); $uploadMs = Milliseconds $watch
    $file = ($raw | ConvertFrom-Json).data.id
    $watch.Restart(); Api POST "/api/v1/track-files/$file/parse" $null $token | Out-Null; $watch.Stop(); $parseMs = Milliseconds $watch
    $watch.Restart(); $sync = (Api POST "/api/v1/track-files/$file/analyses" @{ abnormalThreshold = 0.5 } $token).data; $watch.Stop(); $syncMs = Milliseconds $watch
    $watch.Restart(); $task = (Api POST "/api/v1/track-files/$file/analysis-tasks" @{ abnormalThreshold = 0.5 } $token).data
    $deadline = (Get-Date).AddSeconds(120)
    do { Start-Sleep -Milliseconds 200; $state = (Api GET "/api/v1/analysis-tasks/$($task.taskId)" $null $token).data } while ($state.status -notin @("SUCCESS", "FAILED") -and (Get-Date) -lt $deadline)
    $watch.Stop(); if ($state.status -ne "SUCCESS") { throw "Async analysis failed or timed out" }
    $machine = Get-CimInstance Win32_ComputerSystem
    $cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
    Write-Output "PERFORMANCE_SMOKE=PASS ROWS=$Rows BYTES=$((Get-Item $csv).Length) UPLOAD_MS=$uploadMs PARSE_MS=$parseMs SYNC_ANALYSIS_MS=$syncMs ASYNC_END_TO_END_MS=$(Milliseconds $watch)"
    Write-Output "RESULT_POINT_COUNT=$($sync.pointCount) RMSE=$($sync.rmse) ABNORMAL_COUNT=$($sync.abnormalCount)"
    Write-Output "ENVIRONMENT=$env:OS CPU=$($cpu.Name) MEMORY_BYTES=$($machine.TotalPhysicalMemory)"
    Write-Output "This is one local engineering smoke run, not a production benchmark or throughput claim."
} finally {
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
    if ($created -and (Test-Path (Join-Path (Split-Path -Parent $PSScriptRoot) ".env"))) {
        $bucketLine = Get-Content (Join-Path (Split-Path -Parent $PSScriptRoot) ".env") | Where-Object { $_ -match '^MINIO_BUCKET=' } | Select-Object -First 1
        $bucket = if ($bucketLine) { ($bucketLine -split '=', 2)[1] } else { "track-files" }
        if ($bucket -notmatch '^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$') { throw "Configured MinIO bucket name is unsafe" }
        $objects = @('SELECT object_name FROM track_file tf JOIN dataset d ON d.id=tf.dataset_id JOIN sys_user u ON u.id=d.user_id WHERE u.username=''' + $name + ''';' | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=socket -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -N -s')
        foreach ($object in $objects) {
            if ($object) { docker compose exec -T minio sh -c "mc alias set local http://localhost:9000 `"`$MINIO_ROOT_USER`" `"`$MINIO_ROOT_PASSWORD`" >/dev/null && mc rm --force local/$bucket/$object >/dev/null" }
        }
        $cleanup = "DELETE t FROM analysis_task t JOIN track_file f ON f.id=t.track_file_id JOIN dataset d ON d.id=f.dataset_id JOIN sys_user u ON u.id=d.user_id WHERE u.username='$name'; DELETE i FROM abnormal_interval i JOIN analysis_result r ON r.id=i.analysis_result_id JOIN track_file f ON f.id=r.track_file_id JOIN dataset d ON d.id=f.dataset_id JOIN sys_user u ON u.id=d.user_id WHERE u.username='$name'; DELETE r FROM analysis_result r JOIN track_file f ON f.id=r.track_file_id JOIN dataset d ON d.id=f.dataset_id JOIN sys_user u ON u.id=d.user_id WHERE u.username='$name'; DELETE p FROM track_point p JOIN track_file f ON f.id=p.track_file_id JOIN dataset d ON d.id=f.dataset_id JOIN sys_user u ON u.id=d.user_id WHERE u.username='$name'; DELETE f FROM track_file f JOIN dataset d ON d.id=f.dataset_id JOIN sys_user u ON u.id=d.user_id WHERE u.username='$name'; DELETE d FROM dataset d JOIN sys_user u ON u.id=d.user_id WHERE u.username='$name'; DELETE FROM sys_user WHERE username='$name';"
        $cleanup | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=socket -u"$MYSQL_USER" -D"$MYSQL_DATABASE"'
    }
}
