param([string]$BaseUrl = "http://127.0.0.1:8080", [string]$OutputDirectory = "demo-output")
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root $OutputDirectory
New-Item -ItemType Directory -Force $out | Out-Null
$name = "demo" + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$password = "temporary-demo-password"
function Api($method, $path, $body = $null, $token = $null) {
    $headers = if ($token) { @{ Authorization = "Bearer $token" } } else { @{} }
    $p = @{ Method = $method; Uri = "$BaseUrl$path"; Headers = $headers; TimeoutSec = 30 }
    if ($null -ne $body) { $p.ContentType = "application/json"; $p.Body = ($body | ConvertTo-Json -Compress) }
    try { Invoke-RestMethod @p } catch { throw "HTTP request failed: $method $path ($($_.Exception.Message))" }
}
function Upload($dataset, $source, $file, $token) {
    $raw = & curl.exe --fail --silent --show-error --max-time 30 -H "Authorization: Bearer $token" -F "file=@$file;type=text/csv" -F "trackSource=$source" "$BaseUrl/api/v1/datasets/$dataset/track-files"
    if ($LASTEXITCODE -ne 0) { throw "Upload failed for $source" }
    ($raw | ConvertFrom-Json).data.id
}
$health = Api GET "/actuator/health"
if ($health.status -ne "UP") { throw "Application health is not UP" }
Api POST "/api/v1/auth/register" @{ username = $name; password = $password } | Out-Null
$token = (Api POST "/api/v1/auth/login" @{ username = $name; password = $password }).data.accessToken
$dataset = (Api POST "/api/v1/datasets" @{ name = "Synthetic multi-source demo" } $token).data.id
$files = @(
    @{ Source = "RADAR"; Path = "samples/radar-track.csv" },
    @{ Source = "INFRARED"; Path = "samples/infrared-track.csv" },
    @{ Source = "FUSION"; Path = "samples/fusion-track.csv" }
)
$ids = foreach ($f in $files) {
    $id = Upload $dataset $f.Source (Join-Path $root $f.Path) $token
    Api POST "/api/v1/track-files/$id/parse" $null $token | Out-Null
    Api POST "/api/v1/track-files/$id/analyses" @{ abnormalThreshold = 2 } $token | Out-Null
    $id
}
$task = (Api POST "/api/v1/track-files/$($ids[0])/analysis-tasks" @{ abnormalThreshold = 2 } $token).data
$deadline = (Get-Date).AddSeconds(30)
do { Start-Sleep -Milliseconds 300; $state = (Api GET "/api/v1/analysis-tasks/$($task.taskId)" $null $token).data } while ($state.status -notin @("SUCCESS", "FAILED") -and (Get-Date) -lt $deadline)
if ($state.status -ne "SUCCESS") { throw "Asynchronous analysis did not succeed before timeout" }
$latest = (Api GET "/api/v1/track-files/$($ids[0])/analyses/latest" $null $token).data
$comparison = (Api GET "/api/v1/datasets/$dataset/analysis-comparison" $null $token).data
$report = (Api POST "/api/v1/datasets/$dataset/reports" @{ title = "Synthetic track comparison report" } $token).data
$history = (Api GET "/api/v1/datasets/$dataset/reports?page=1&size=20" $null $token).data
$reportPath = Join-Path $out "analysis-report-$($report.reportId).html"
Invoke-WebRequest -UseBasicParsing -TimeoutSec 30 -Headers @{ Authorization = "Bearer $token" } -Uri "$BaseUrl/api/v1/reports/$($report.reportId)/download" -OutFile $reportPath
Write-Output "DEMO=PASS DATASET=$dataset FILES=$($ids.Count) COMPARISON=$($comparison.Count) REPORTS=$($history.total) POINTS=$($latest.pointCount)"
Write-Output "Report saved to: $reportPath"
Write-Output "Temporary demo records are retained for inspection; no token or password was printed."
