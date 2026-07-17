$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Get-Content .env | ForEach-Object {
    if ($_ -match '^[A-Za-z_][A-Za-z0-9_]*=') {
        $name, $value = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}
if ([string]::IsNullOrWhiteSpace($env:MINIO_BUCKET)) { $env:MINIO_BUCKET = "track-files" }

$marker = "m4smoke" + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$base = "http://127.0.0.1:8080"
$temp = Join-Path ([IO.Path]::GetTempPath()) $marker
$log = Join-Path $temp "application.log"
$errorLog = Join-Path $temp "application-error.log"
$process = $null
$token = $null

function Request([string]$method, [string]$path, [object]$body = $null, [string]$authorization = $null, [string]$requestId = $null) {
    $headers = @{}
    if ($authorization) { $headers.Authorization = "Bearer $authorization" }
    if ($requestId) { $headers.'X-Request-Id' = $requestId }
    $parameters = @{ Method = $method; Uri = "$base$path"; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = ($body | ConvertTo-Json -Compress)
    }
    try {
        $response = Invoke-WebRequest @parameters
        return @{ Status = [int]$response.StatusCode; HeaderRequestId = $response.Headers.'X-Request-Id'; Json = ($response.Content | ConvertFrom-Json) }
    } catch {
        $response = $_.Exception.Response
        $content = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } elseif ($response.Content) { [string]$response.Content } else { "" }
        return @{ Status = if ($response) { [int]$response.StatusCode } else { 0 }; Json = if ($content) { $content | ConvertFrom-Json } else { $null } }
    }
}

function Require([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}

function Upload([long]$datasetId, [string]$source, [string]$path) {
    $json = & curl.exe -sS -X POST -H "Authorization: Bearer $token" -F "file=@$path;type=text/csv" -F "trackSource=$source" "$base/api/v1/datasets/$datasetId/track-files" | ConvertFrom-Json
    Require ($json.code -eq "SUCCESS") "Upload failed for $source"
    return [long]$json.data.id
}

New-Item -ItemType Directory -Path $temp | Out-Null
$header = "time,true_x,true_y,true_z,track_x,track_y,track_z`n"
[IO.File]::WriteAllText((Join-Path $temp "radar.csv"), $header + "1,0,0,0,0,0,0`n2,0,0,0,3,0,0`n3,0,0,0,4,0,0`n")
[IO.File]::WriteAllText((Join-Path $temp "infrared.csv"), $header + "1,0,0,0,1,0,0`n2,0,0,0,2,0,0`n")
[IO.File]::WriteAllText((Join-Path $temp "fusion.csv"), $header + "1,0,0,0,5,0,0`n")
[IO.File]::WriteAllText((Join-Path $temp "pending.csv"), $header + "1,0,0,0,1,0,0`n")

try {
    $process = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList "-jar", "target\track-analysis-platform-0.1.0-SNAPSHOT.jar" -WorkingDirectory $root -RedirectStandardOutput $log -RedirectStandardError $errorLog -WindowStyle Hidden -PassThru
    $deadline = (Get-Date).AddSeconds(90)
    do {
        Start-Sleep -Milliseconds 500
        if ($process.HasExited) {
            throw "Application process exited before health became UP: $((Get-Content $log,$errorLog -Tail 30) -join ' ')"
        }
        try { $healthStatus = (Invoke-RestMethod -Uri "$base/actuator/health" -Method Get).status } catch { $healthStatus = $null }
    } while ($healthStatus -ne "UP" -and (Get-Date) -lt $deadline)
    Require ($healthStatus -eq "UP") "Application health did not become UP"

    Require ((Request POST "/api/v1/auth/register" @{ username = $marker; password = "correct-password" }).Status -eq 201) "Owner registration failed"
    $login = Request POST "/api/v1/auth/login" @{ username = $marker; password = "correct-password" }
    $token = $login.Json.data.accessToken
    Require (-not [string]::IsNullOrWhiteSpace($token)) "Owner login failed"
    $otherName = $marker + "b"
    Require ((Request POST "/api/v1/auth/register" @{ username = $otherName; password = "correct-password" }).Status -eq 201) "Other registration failed"
    $otherToken = (Request POST "/api/v1/auth/login" @{ username = $otherName; password = "correct-password" }).Json.data.accessToken

    $dataset = Request POST "/api/v1/datasets" @{ name = $marker } $token
    $datasetId = [long]$dataset.Json.data.id
    $radar = Upload $datasetId "RADAR" (Join-Path $temp "radar.csv")
    $infrared = Upload $datasetId "INFRARED" (Join-Path $temp "infrared.csv")
    $fusion = Upload $datasetId "FUSION" (Join-Path $temp "fusion.csv")
    $pending = Upload $datasetId "RADAR" (Join-Path $temp "pending.csv")

    foreach ($id in @($radar, $infrared, $fusion)) { Require ((Request POST "/api/v1/track-files/$id/parse" $null $token).Status -eq 200) "Parse failed" }
    $requestId = "m4-smoke-request-12345678"
    $radarAnalysis = Request POST "/api/v1/track-files/$radar/analyses" @{ abnormalThreshold = 2 } $token $requestId
    Require ($radarAnalysis.Status -eq 201) "Radar analysis failed"
    Require ($radarAnalysis.HeaderRequestId -eq $requestId -and $radarAnalysis.Json.requestId -eq $requestId) "Request ID mismatch"
    Require ([Math]::Abs($radarAnalysis.Json.data.meanError - (7.0 / 3)) -lt 0.000000001) "Radar mean mismatch"
    Require ([Math]::Abs($radarAnalysis.Json.data.rmse - [Math]::Sqrt(25.0 / 3)) -lt 0.000000001) "Radar RMSE mismatch"
    Require ($radarAnalysis.Json.data.abnormalCount -eq 2) "Radar abnormal count mismatch"
    $radarAnalysisId = [long]$radarAnalysis.Json.data.id
    Require ((Request POST "/api/v1/track-files/$infrared/analyses" @{ abnormalThreshold = 1 } $token).Status -eq 201) "Infrared analysis failed"
    Require ((Request POST "/api/v1/track-files/$fusion/analyses" @{ abnormalThreshold = 4 } $token).Status -eq 201) "Fusion analysis failed"

    Require ((Request GET "/api/v1/track-files/$radar/analyses/latest" $null $token).Status -eq 200) "Latest failed"
    Require ((Request GET "/api/v1/track-files/$radar/analyses" $null $token).Json.data.total -eq 1) "History failed"
    Require ((Request GET "/api/v1/analysis-results/$radarAnalysisId/abnormal-intervals" $null $token).Json.data.Count -eq 1) "Intervals failed"
    Require ((Request GET "/api/v1/track-files/$radar/error-series?size=2" $null $token).Json.data.total -eq 3) "Error series failed"
    Require ((Request GET "/api/v1/datasets/$datasetId/analysis-comparison" $null $token).Json.data.Count -eq 3) "Comparison failed"
    Require ((Request POST "/api/v1/track-files/$radar/analyses" @{ abnormalThreshold = 10 } $token).Status -eq 201) "Second analysis failed"
    $comparison = (Request GET "/api/v1/datasets/$datasetId/analysis-comparison" $null $token).Json.data
    Require ($comparison.Count -eq 3 -and ($comparison | Where-Object fileId -eq $radar).abnormalThreshold -eq 10) "Comparison did not keep only latest"
    Require ((Request GET "/api/v1/track-files/$radar/analyses/latest" $null $otherToken).Status -eq 404) "Owner isolation failed"
    Require ((Request POST "/api/v1/track-files/$pending/analyses" @{ abnormalThreshold = 0 } $token).Status -eq 409) "Non-PARSED state was accepted"

    $logText = (Get-Content $log -Raw) + (Get-Content $errorLog -Raw)
    foreach ($secret in @($env:JWT_SECRET, $env:MINIO_ROOT_PASSWORD, $token, "1,0,0,0,0,0,0")) {
        if ($secret) { Require (-not $logText.Contains($secret)) "Sensitive value or CSV content found in log" }
    }
    Write-Output "M4_SMOKE=PASS DATASET=$datasetId FILES=4 ANALYSES=4"
} finally {
    if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id; $process.WaitForExit(15000) | Out-Null }
    $objects = @('SELECT object_name FROM track_file tf JOIN dataset d ON d.id=tf.dataset_id JOIN sys_user u ON u.id=d.user_id WHERE u.username=''' + $marker + ''';' | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=socket -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -N -s')
    foreach ($object in $objects) {
        if ($object) { docker compose exec -T minio sh -c "mc alias set local http://localhost:9000 `"`$MINIO_ROOT_USER`" `"`$MINIO_ROOT_PASSWORD`" >/dev/null && mc rm --force local/$env:MINIO_BUCKET/$object >/dev/null" }
    }
    $cleanup = "DELETE ai FROM abnormal_interval ai JOIN analysis_result ar ON ar.id=ai.analysis_result_id JOIN track_file tf ON tf.id=ar.track_file_id JOIN dataset d ON d.id=tf.dataset_id JOIN sys_user u ON u.id=d.user_id WHERE u.username LIKE '$marker%'; DELETE ar FROM analysis_result ar JOIN track_file tf ON tf.id=ar.track_file_id JOIN dataset d ON d.id=tf.dataset_id JOIN sys_user u ON u.id=d.user_id WHERE u.username LIKE '$marker%'; DELETE tp FROM track_point tp JOIN track_file tf ON tf.id=tp.track_file_id JOIN dataset d ON d.id=tf.dataset_id JOIN sys_user u ON u.id=d.user_id WHERE u.username LIKE '$marker%'; DELETE tf FROM track_file tf JOIN dataset d ON d.id=tf.dataset_id JOIN sys_user u ON u.id=d.user_id WHERE u.username LIKE '$marker%'; DELETE d FROM dataset d JOIN sys_user u ON u.id=d.user_id WHERE u.username LIKE '$marker%'; DELETE FROM sys_user WHERE username LIKE '$marker%';"
    $cleanup | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=socket -u"$MYSQL_USER" -D"$MYSQL_DATABASE"'
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
}
