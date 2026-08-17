param([string]$AcceptanceDatabase = "")

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
if ([string]::IsNullOrWhiteSpace($AcceptanceDatabase)) {
  $AcceptanceDatabase = "track_business_acceptance_" + (Get-Date -Format "yyyyMMddHHmmss")
}
$compose = @("compose", "--env-file", ".env", "-f", "compose.yaml", "-f", "compose.app.yaml")
$base = "http://127.0.0.1:8080"
$prefix = "business-smoke-" + (Get-Date -Format "yyyyMMddHHmmss") + "-" + ([Guid]::NewGuid().ToString("N").Substring(0, 8))
$username = $prefix
$password = "Sm1!" + [Guid]::NewGuid().ToString("N")
$adminUser = "business-acceptance-admin"
$adminPassword = "Ad1!" + [Guid]::NewGuid().ToString("N")
$csv = Join-Path ([IO.Path]::GetTempPath()) ($prefix + ".csv")

function Assert-True([bool]$Condition, [string]$Message) {
  if (-not $Condition) { throw $Message }
}
function Invoke-Compose([string[]]$Arguments) {
  & docker @compose @Arguments
  if ($LASTEXITCODE -ne 0) { throw "Docker Compose command failed" }
}
function Get-EnvValue([string]$Name) {
  $line = [IO.File]::ReadAllLines((Resolve-Path ".env"), [Text.Encoding]::UTF8) |
    Where-Object { $_ -match ("^" + [regex]::Escape($Name) + "=") } | Select-Object -First 1
  if ($null -eq $line) { throw "Required local environment value is missing: $Name" }
  return $line.Substring($line.IndexOf("=") + 1)
}
function Get-Csrf($Session, [string]$Path) {
  $response = Invoke-WebRequest -UseBasicParsing -Uri ($base + $Path) -WebSession $Session
  $match = [regex]::Match($response.Content, 'name="_csrf"[^>]*value="([^"]+)"')
  if (-not $match.Success) { throw "CSRF token missing at $Path" }
  return $match.Groups[1].Value
}
function Login-Web([string]$User, [string]$Secret) {
  $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
  $csrf = Get-Csrf $session "/login"
  $response = Invoke-WebRequest -UseBasicParsing -Uri ($base + "/login") -Method Post `
    -WebSession $session -Body @{username=$User;password=$Secret;_csrf=$csrf}
  Assert-True ($response.BaseResponse.ResponseUri.AbsolutePath -eq "/app/dashboard") "Login failed"
  return $session
}
function Db-Scalar([string]$Sql) {
  $value = $Sql | docker compose exec -T -e "MYSQL_DATABASE=$AcceptanceDatabase" mysql sh -c `
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=socket -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -N -s'
  if ($LASTEXITCODE -ne 0) { throw "Database assertion failed" }
  return (($value | Select-Object -First 1) -as [string]).Trim()
}
function Invoke-Json([string]$Path, [string]$Method, $Body, [string]$Token) {
  $headers = @{Authorization=("Bearer " + $Token)}
  $json = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Compress }
  return Invoke-RestMethod -Uri ($base + $Path) -Method $Method -Headers $headers `
    -ContentType "application/json" -Body $json
}
function Upload-Csv([long]$DatasetId, [string]$Token, [string]$Path) {
  $handler = New-Object System.Net.Http.HttpClientHandler
  $client = New-Object System.Net.Http.HttpClient($handler)
  $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token)
  $form = New-Object System.Net.Http.MultipartFormDataContent
  $bytes = [IO.File]::ReadAllBytes($Path)
  $fileContent = New-Object System.Net.Http.ByteArrayContent -ArgumentList (,$bytes)
  $fileContent.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue("text/csv")
  $form.Add($fileContent, "file", [IO.Path]::GetFileName($Path))
  $response = $client.PostAsync(($base + "/api/v1/datasets/$DatasetId/track-files?trackSource=FUSION"), $form).Result
  try {
    Assert-True $response.IsSuccessStatusCode "CSV upload failed"
    return ($response.Content.ReadAsStringAsync().Result | ConvertFrom-Json)
  } finally { $form.Dispose(); $client.Dispose(); $handler.Dispose() }
}

try {
  $mysqlUser = docker compose exec -T mysql sh -c 'printf "%s" "$MYSQL_USER"'
  $databaseSql = "CREATE DATABASE IF NOT EXISTS $AcceptanceDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON ${AcceptanceDatabase}.* TO '$mysqlUser'@'%';"
  $databaseSql | docker compose exec -T mysql sh -c `
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=socket -uroot'
  if ($LASTEXITCODE -ne 0) { throw "Acceptance database preparation failed" }
  $env:MYSQL_DATABASE = $AcceptanceDatabase
  $env:APP_ADMIN_USERNAME = $adminUser
  $env:APP_ADMIN_PASSWORD = $adminPassword
  Invoke-Compose @("up", "-d", "--no-deps", "--force-recreate", "app")

  $deadline = (Get-Date).AddSeconds(120)
  do {
    $services = @(docker compose ps --format json | ConvertFrom-Json)
    $healthy = @($services | Where-Object { $_.Health -eq "healthy" }).Count
    if ($services.Count -eq 5 -and $healthy -eq 5) { break }
    Start-Sleep -Seconds 2
  } while ((Get-Date) -lt $deadline)
  Assert-True ($services.Count -eq 5) "Expected five Compose services"
  Assert-True ($healthy -eq 5) "A Compose service is unhealthy"

  $admin = Login-Web $adminUser $adminPassword
  $csrf = Get-Csrf $admin "/admin/users/new"
  Invoke-WebRequest -UseBasicParsing -Uri ($base + "/admin/users") -Method Post -WebSession $admin `
    -Body @{username=$username;displayName="Business Smoke";email=($prefix+"@example.invalid");password=$password;role="RESEARCHER";_csrf=$csrf} | Out-Null
  $userId = [long](Db-Scalar "SELECT id FROM sys_user WHERE username='$username'")
  Assert-True ($userId -gt 0) "Researcher was not created"

  $researcher = Login-Web $username $password
  $login = Invoke-RestMethod -Uri ($base + "/api/v1/auth/login") -Method Post -ContentType "application/json" `
    -Body (@{username=$username;password=$password} | ConvertTo-Json -Compress)
  $token = $login.data.accessToken
  Assert-True (-not [string]::IsNullOrWhiteSpace($token)) "JWT login failed"

  [IO.File]::WriteAllText($csv, "time,true_x,true_y,true_z,track_x,track_y,track_z`n0,0,0,0,1,1,1`n1,1,1,1,2,2,2`n2,2,2,2,20,20,20`n", [Text.Encoding]::UTF8)
  $created = Invoke-Json "/api/v1/datasets" "Post" @{name=$prefix;description="business smoke"} $token
  $datasetId = [long]$created.data.id
  $uploaded = Upload-Csv $datasetId $token $csv
  $fileId = [long]$uploaded.data.id
  $parsed = Invoke-Json "/api/v1/track-files/$fileId/parse" "Post" $null $token
  Assert-True ($parsed.data.parseStatus -eq "PARSED") "CSV parse did not finish"
  Assert-True ([long](Db-Scalar "SELECT COUNT(*) FROM track_file WHERE id=$fileId AND dataset_id=$datasetId AND parse_status='PARSED'") -eq 1) "Track metadata missing"

  $detail = Invoke-WebRequest -UseBasicParsing -Uri ($base + "/app/datasets/$datasetId") -WebSession $researcher
  Assert-True ($detail.StatusCode -eq 200 -and $detail.Content.Contains($prefix)) "Dataset detail failed"
  $download = Invoke-WebRequest -UseBasicParsing -Uri ($base + "/app/datasets/$datasetId/download") -WebSession $researcher
  Assert-True ($download.RawContentLength -gt 0) "Original download was empty"

  $task = Invoke-Json "/api/v1/track-files/$fileId/analysis-tasks" "Post" @{abnormalThreshold=5} $token
  $taskId = [long]$task.data.taskId
  Assert-True ([long](Db-Scalar "SELECT COUNT(*) FROM reliable_outbox WHERE aggregate_id=$taskId AND event_type='TASK_PUBLISH'") -eq 1) "Task publication outbox missing"
  $deadline = (Get-Date).AddSeconds(90)
  do {
    Start-Sleep -Milliseconds 500
    $state = Invoke-Json "/api/v1/analysis-tasks/$taskId" "Get" $null $token
  } while ($state.data.status -in @("PENDING","RUNNING") -and (Get-Date) -lt $deadline)
  Assert-True ($state.data.status -eq "SUCCESS") "Analysis task did not succeed"
  $resultId = [long]$state.data.analysisResultId
  Assert-True ([long](Db-Scalar "SELECT COUNT(*) FROM analysis_task WHERE id=$taskId AND analysis_result_id=$resultId AND status='SUCCESS'") -eq 1) "Task result binding is wrong"
  $resultPage = Invoke-WebRequest -UseBasicParsing -Uri ($base + "/app/tasks/$taskId/result") -WebSession $researcher
  Assert-True ($resultPage.StatusCode -eq 200) "Result page failed"
  $resultCsv = Invoke-WebRequest -UseBasicParsing -Uri ($base + "/app/tasks/$taskId/result.csv") -WebSession $researcher
  Assert-True ($resultCsv.RawContentLength -gt 0) "Result CSV was empty"

  $adminGlobal = Invoke-WebRequest -UseBasicParsing -Uri ($base + "/app/tasks") -WebSession $admin
  Assert-True ($adminGlobal.StatusCode -eq 200) "Administrator global task view failed"
  Assert-True ([long](Db-Scalar "SELECT COUNT(*) FROM audit_log WHERE resource_id IN ('$datasetId','$taskId')") -gt 0) "Business audit missing"

  [void](Invoke-Json "/api/v1/datasets/$datasetId" "Delete" $null $token)
  $deadline = (Get-Date).AddSeconds(60)
  do { Start-Sleep -Milliseconds 500; $deleteState = Db-Scalar "SELECT delete_status FROM dataset WHERE id=$datasetId" }
  while ($deleteState -in @("DELETE_PENDING","DELETE_FAILED") -and (Get-Date) -lt $deadline)
  Assert-True ($deleteState -eq "DELETED") "Dataset cleanup did not reach DELETED"
  Assert-True ([long](Db-Scalar "SELECT COUNT(*) FROM reliable_outbox WHERE aggregate_id=$datasetId AND event_type='DATASET_OBJECT_DELETE' AND status='PROCESSED'") -eq 1) "Deletion outbox was not processed"

  $csrf = Get-Csrf $admin "/admin/users"
  Invoke-WebRequest -UseBasicParsing -Uri ($base + "/admin/users/$userId/enabled") -Method Post `
    -WebSession $admin -Body @{enabled="false";_csrf=$csrf} | Out-Null
  $sessionResponse = Invoke-WebRequest -UseBasicParsing -Uri ($base+"/app/dashboard") -WebSession $researcher
  $sessionInvalidated = $sessionResponse.BaseResponse.ResponseUri.AbsolutePath -eq "/login"
  $jwtStatus = try { (Invoke-WebRequest -UseBasicParsing -Uri ($base+"/api/v1/auth/me") -Headers @{Authorization=("Bearer "+$token)}).StatusCode } catch { [int]$_.Exception.Response.StatusCode }
  Assert-True $sessionInvalidated "Disabled Session remained valid"
  Assert-True ($jwtStatus -eq 401) "Disabled JWT remained valid"
  Write-Output "BUSINESS_SMOKE=PASS"
  Write-Output "TASK_TERMINAL_STATUS=SUCCESS"
  Write-Output "DELETE_TERMINAL_STATUS=DELETED"
  Write-Output "TEMPORARY_RECORD_PREFIX=$prefix"
} catch {
  docker compose -f compose.yaml -f compose.app.yaml logs --tail 120 app |
    Select-String -Pattern "ERROR|Exception|Caused by|TemplateInput" -Context 1,4
  throw
} finally {
  Remove-Item -LiteralPath $csv -Force -ErrorAction SilentlyContinue
  $password = $null; $adminPassword = $null; $token = $null
  Remove-Item Env:MYSQL_DATABASE -ErrorAction SilentlyContinue
  Remove-Item Env:APP_ADMIN_USERNAME -ErrorAction SilentlyContinue
  Remove-Item Env:APP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
  Invoke-Compose @("up", "-d", "--no-deps", "--force-recreate", "app")
}
