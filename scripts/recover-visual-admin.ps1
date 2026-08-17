$ErrorActionPreference = "Stop"
$username = "visual-admin-1784685865930"
$compose = @("compose", "--env-file", ".env", "-f", "compose.yaml", "-f", "compose.app.yaml")
$plainPassword = $null

function Invoke-Compose([string[]]$Arguments) {
  & docker @compose @Arguments
  if ($LASTEXITCODE -ne 0) { throw "Docker Compose command failed" }
}

function Database-Row {
  $sql = "SELECT u.username,u.status,GROUP_CONCAT(r.code ORDER BY r.code),u.password_hash LIKE '`$2%',u.auth_version FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='$username' GROUP BY u.id,u.username,u.status,u.password_hash,u.auth_version;"
  $row = $sql | docker compose exec -T mysql sh -c `
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=socket -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -N -s'
  if ($LASTEXITCODE -ne 0) { throw "Database verification failed" }
  return (($row | Select-Object -First 1) -as [string])
}

try {
  $before = Database-Row
  if ([string]::IsNullOrWhiteSpace($before)) { throw "The specified administrator does not exist" }
  $beforeFields = $before -split "`t"
  if ($beforeFields[2].Split(',') -notcontains "ADMIN") { throw "The specified user is not ADMIN" }
  $runRecovery = $beforeFields[1] -eq "DISABLED"
  if ($beforeFields[1] -ne "DISABLED") {
    $recoveryStateSql = "SELECT CASE WHEN EXISTS(SELECT 1 FROM audit_log a JOIN sys_user u ON a.resource_id=CAST(u.id AS CHAR) WHERE u.username='$username' AND a.request_id='local-admin-recovery' AND a.action='USER_RESTORE') AND EXISTS(SELECT 1 FROM audit_log a JOIN sys_user u ON a.resource_id=CAST(u.id AS CHAR) WHERE u.username='$username' AND a.request_id='local-admin-recovery' AND a.action='USER_ENABLE') AND EXISTS(SELECT 1 FROM audit_log a JOIN sys_user u ON a.resource_id=CAST(u.id AS CHAR) WHERE u.username='$username' AND a.request_id='local-admin-recovery' AND a.action='PASSWORD_RESET') THEN 2 WHEN EXISTS(SELECT 1 FROM audit_log a JOIN sys_user u ON a.resource_id=CAST(u.id AS CHAR) WHERE u.username='$username' AND a.request_id='local-admin-recovery' AND a.action='USER_RESTORE') AND EXISTS(SELECT 1 FROM audit_log a JOIN sys_user u ON a.resource_id=CAST(u.id AS CHAR) WHERE u.username='$username' AND a.request_id='local-admin-recovery' AND a.action='USER_ENABLE') THEN 1 ELSE 0 END;"
    $recoveryState = $recoveryStateSql | docker compose exec -T mysql sh -c `
      'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=socket -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -N -s'
    $recoveryState = (($recoveryState | Select-Object -First 1) -as [string]).Trim()
    if ($LASTEXITCODE -ne 0 -or $recoveryState -notin @("1", "2")) {
      throw "The specified administrator is not DISABLED"
    }
    $runRecovery = $recoveryState -eq "1"
  }

  Invoke-Compose @("build", "app")
  $securePassword = Read-Host "Enter a new password for $username" -AsSecureString
  $credential = [PSCredential]::new($username, $securePassword)
  $plainPassword = $credential.GetNetworkCredential().Password
  if ([string]::IsNullOrWhiteSpace($plainPassword)) { throw "Password must not be blank" }

  if ($runRecovery) {
    $env:ADMIN_RECOVERY_USERNAME = $username
    $env:ADMIN_RECOVERY_PASSWORD = $plainPassword
    $env:TRACK_MAINTENANCE_ADMIN_RECOVERY_ENABLED = "true"
    Invoke-Compose @("run", "--rm", "--no-deps", "-e", "ADMIN_RECOVERY_USERNAME", "-e", "ADMIN_RECOVERY_PASSWORD", "-e", "TRACK_MAINTENANCE_ADMIN_RECOVERY_ENABLED", "app", "--server.port=0")
  }

  Invoke-Compose @("up", "-d", "--no-deps", "--force-recreate", "app")
  $deadline = (Get-Date).AddSeconds(120)
  do {
    $health = docker inspect --format '{{.State.Health.Status}}' track-analysis-platform-app-1
    if ($health -eq "healthy") { break }
    Start-Sleep -Seconds 2
  } while ((Get-Date) -lt $deadline)
  if ($health -ne "healthy") { throw "Application did not become healthy" }

  $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
  $loginPage = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:8080/login" -WebSession $session
  $csrf = [regex]::Match($loginPage.Content, 'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value
  if ([string]::IsNullOrWhiteSpace($csrf)) { throw "Login CSRF token missing" }
  $login = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:8080/login" -Method Post `
    -WebSession $session -Body @{username=$username;password=$plainPassword;_csrf=$csrf}
  if ($login.BaseResponse.ResponseUri.AbsolutePath -ne "/app/dashboard") { throw "Web login failed" }
  $dashboard = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:8080/app/dashboard" -WebSession $session
  if ($dashboard.StatusCode -ne 200) { throw "Dashboard verification failed" }

  $after = Database-Row
  $fields = $after -split "`t"
  if ($fields[1] -ne "ACTIVE" -or $fields[2].Split(',') -notcontains "ADMIN" -or $fields[3] -ne "1") {
    throw "Post-recovery database verification failed"
  }
  Write-Output "ADMIN_USERNAME=$username"
  Write-Output "ADMIN_STATUS=ACTIVE"
  Write-Output "ADMIN_ROLE_VERIFIED=True"
  Write-Output "BCRYPT_VERIFIED=True"
  Write-Output "WEB_LOGIN_VERIFIED=True"
  Write-Output "DASHBOARD_STATUS=200"
} finally {
  Remove-Item Env:ADMIN_RECOVERY_USERNAME -ErrorAction SilentlyContinue
  Remove-Item Env:ADMIN_RECOVERY_PASSWORD -ErrorAction SilentlyContinue
  Remove-Item Env:TRACK_MAINTENANCE_ADMIN_RECOVERY_ENABLED -ErrorAction SilentlyContinue
  $plainPassword = $null
  $securePassword = $null
  $credential = $null
}
