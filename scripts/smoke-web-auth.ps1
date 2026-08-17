param([string]$AcceptanceDatabase = "")

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($AcceptanceDatabase)) {
  $AcceptanceDatabase = "track_acceptance_" + (Get-Date -Format "yyyyMMddHHmmss")
}
$compose = @("compose", "--env-file", ".env", "-f", "compose.yaml", "-f", "compose.app.yaml")
$base = "http://127.0.0.1:8080"
$adminUsername = "acceptance-admin"
$researcherUsername = "acceptance-researcher"
$adminPassword = "Aa1!" + [Guid]::NewGuid().ToString("N")
$researcherPassword = "Bb2!" + [Guid]::NewGuid().ToString("N")
$resetPassword = "Cc3!" + [Guid]::NewGuid().ToString("N")
$finalPassword = "Dd4!" + [Guid]::NewGuid().ToString("N")

function Invoke-Compose([string[]]$Arguments) {
  & docker @compose @Arguments
  if ($LASTEXITCODE -ne 0) { throw "Docker Compose command failed" }
}

function Get-Csrf($Session, [string]$Path) {
  $response = Invoke-WebRequest -UseBasicParsing -Uri ($base + $Path) -WebSession $Session
  $match = [regex]::Match($response.Content, 'name="_csrf"[^>]*value="([^"]+)"')
  if (-not $match.Success) { throw "CSRF token was not present" }
  return $match.Groups[1].Value
}

function Login-Web([string]$Username, [string]$Password) {
  $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
  $csrf = Get-Csrf $session "/login"
  $response = Invoke-WebRequest -UseBasicParsing -Uri ($base + "/login") -Method Post `
    -WebSession $session -Body @{username=$Username; password=$Password; _csrf=$csrf}
  if ($response.BaseResponse.ResponseUri.AbsolutePath -ne "/app/dashboard") {
    throw "Web login did not reach the dashboard"
  }
  return $session
}

function Test-WebLogin([string]$Username, [string]$Password) {
  try { [void](Login-Web $Username $Password); return $true }
  catch { return $false }
}

function Get-HttpStatus([scriptblock]$Operation) {
  try { return [int](& $Operation).StatusCode }
  catch { return [int]$_.Exception.Response.StatusCode }
}

function Invoke-StatusNoRedirect(
  [string]$Path,
  [string]$Method = "GET",
  $Session = $null,
  [hashtable]$Headers = @{}
) {
  $request = [System.Net.HttpWebRequest]::Create($base + $Path)
  $request.AllowAutoRedirect = $false
  $request.Method = $Method
  if ($null -ne $Session) { $request.CookieContainer = $Session.Cookies }
  foreach ($name in $Headers.Keys) { $request.Headers[$name] = $Headers[$name] }
  if ($Method -eq "POST") {
    $request.ContentType = "application/x-www-form-urlencoded"
    $request.ContentLength = 0
  }
  try { $response = $request.GetResponse() }
  catch [System.Net.WebException] { $response = $_.Exception.Response }
  if ($null -eq $response) { return -1 }
  try { return [int]$response.StatusCode } finally { $response.Close() }
}

function Invoke-DatabaseScalar([string]$Sql) {
  $result = $Sql | docker compose exec -T mysql sh -c `
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=socket -u"$MYSQL_USER" -N -s'
  if ($LASTEXITCODE -ne 0) { throw "Database verification query failed" }
  return ($result | Select-Object -First 1).Trim()
}

try {
  $mysqlUser = docker compose exec -T mysql sh -c 'printf "%s" "$MYSQL_USER"'
  $databaseSql = "CREATE DATABASE IF NOT EXISTS $AcceptanceDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON ${AcceptanceDatabase}.* TO '$mysqlUser'@'%';"
  $databaseSql | docker compose exec -T mysql sh -c `
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=socket -uroot'
  if ($LASTEXITCODE -ne 0) { throw "Acceptance database preparation failed" }

  $env:MYSQL_DATABASE = $AcceptanceDatabase
  $env:APP_ADMIN_USERNAME = $adminUsername
  $env:APP_ADMIN_PASSWORD = $adminPassword
  Invoke-Compose @("up", "-d", "--no-deps", "--force-recreate", "app")

  $deadline = (Get-Date).AddSeconds(120)
  do {
    $health = (docker compose -f compose.yaml -f compose.app.yaml ps --format json | ConvertFrom-Json |
      Where-Object { $_.Service -eq "app" }).Health
    if ($health -eq "healthy") { break }
    Start-Sleep -Seconds 2
  } while ((Get-Date) -lt $deadline)
  if ($health -ne "healthy") { throw "Acceptance app did not become healthy" }

  $loginStatus = (Invoke-WebRequest -UseBasicParsing -Uri ($base + "/login")).StatusCode
  $adminSession = Login-Web $adminUsername $adminPassword
  $dashboard = Invoke-WebRequest -UseBasicParsing -Uri ($base + "/app/dashboard") -WebSession $adminSession
  $dashboardIdentity = $dashboard.Content.Contains($adminUsername)

  $newUserCsrf = Get-Csrf $adminSession "/admin/users/new"
  $createBody = @{
    username = $researcherUsername
    displayName = "Acceptance Researcher"
    email = "acceptance@example.invalid"
    password = $researcherPassword
    role = "RESEARCHER"
    _csrf = $newUserCsrf
  }
  $created = Invoke-WebRequest -UseBasicParsing -Uri ($base + "/admin/users") -Method Post -WebSession $adminSession -Body $createBody
  $createSucceeded = $created.BaseResponse.ResponseUri.AbsolutePath -eq "/admin/users"
  $userListUri = "{0}/admin/users?keyword={1}" -f $base, [uri]::EscapeDataString($researcherUsername)
  $userList = Invoke-WebRequest -UseBasicParsing -Uri $userListUri -WebSession $adminSession
  $listed = $userList.Content.Contains($researcherUsername)

  $userId = Invoke-DatabaseScalar "SELECT id FROM ${AcceptanceDatabase}.sys_user WHERE username='$researcherUsername';"
  $bcryptStored = Invoke-DatabaseScalar "SELECT password_hash LIKE '`$2%' FROM ${AcceptanceDatabase}.sys_user WHERE id=$userId;"
  $createAudit = Invoke-DatabaseScalar "SELECT COUNT(*) FROM ${AcceptanceDatabase}.audit_log WHERE action='USER_CREATE' AND resource_id='$userId';"

  $researcherSession = Login-Web $researcherUsername $researcherPassword
  $researcherDashboard = (Invoke-WebRequest -UseBasicParsing -Uri ($base + "/app/dashboard") -WebSession $researcherSession).StatusCode
  $researcherAdminStatus = Get-HttpStatus { Invoke-WebRequest -UseBasicParsing -Uri ($base + "/admin/users") -WebSession $researcherSession -MaximumRedirection 0 }
  $adminUsersStatus = (Invoke-WebRequest -UseBasicParsing -Uri ($base + "/admin/users") -WebSession $adminSession).StatusCode
  $adminCookie = $adminSession.Cookies.GetCookies($base)["SESSION"].Value
  $researcherCookie = $researcherSession.Cookies.GetCookies($base)["SESSION"].Value
  $cookiesDiffer = $adminCookie -ne $researcherCookie

  $sessionKeys = docker compose exec -T redis sh -c `
    'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw --scan --pattern "track-analysis:web-session:*"'
  $sessionKeyCount = @($sessionKeys).Count
  $adminIndex = docker compose exec -T redis sh -c `
    'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw SCARD "track-analysis:web-session:index:org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME:acceptance-admin"'
  $researcherIndex = docker compose exec -T redis sh -c `
    'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw SCARD "track-analysis:web-session:index:org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME:acceptance-researcher"'

  $loginJson = @{username=$researcherUsername; password=$researcherPassword} | ConvertTo-Json -Compress
  $restLogin = Invoke-RestMethod -Uri ($base + "/api/v1/auth/login") -Method Post -ContentType "application/json" -Body $loginJson
  $jwt = $restLogin.data.accessToken
  $jwtHeaders = @{Authorization="Bearer $jwt"}
  $jwtMe = (Invoke-WebRequest -UseBasicParsing -Uri ($base + "/api/v1/auth/me") -Headers $jwtHeaders).StatusCode
  $jwtBusiness = (Invoke-WebRequest -UseBasicParsing -Uri ($base + "/api/v1/datasets?page=1&size=1") -Headers $jwtHeaders).StatusCode
  $sessionApi = Get-HttpStatus { Invoke-WebRequest -UseBasicParsing -Uri ($base + "/api/v1/auth/me") -WebSession $researcherSession }
  $jwtWeb = Invoke-StatusNoRedirect "/app/dashboard" -Headers $jwtHeaders
  $webNoCsrf = Invoke-StatusNoRedirect "/app/change-password" -Method "POST" -Session $researcherSession

  $beforeDisable = Invoke-DatabaseScalar "SELECT auth_version FROM ${AcceptanceDatabase}.sys_user WHERE id=$userId;"
  $disableCsrf = Get-Csrf $adminSession "/admin/users"
  Invoke-WebRequest -UseBasicParsing -Uri ($base + "/admin/users/$userId/enabled") -Method Post `
    -WebSession $adminSession -Body @{enabled="false"; _csrf=$disableCsrf} | Out-Null
  $disabledState = Invoke-DatabaseScalar "SELECT CONCAT(status,':',auth_version) FROM ${AcceptanceDatabase}.sys_user WHERE id=$userId;"
  $oldSessionAfterDisable = Invoke-StatusNoRedirect "/app/dashboard" -Session $researcherSession
  $oldJwtAfterDisable = Get-HttpStatus { Invoke-WebRequest -UseBasicParsing -Uri ($base + "/api/v1/auth/me") -Headers $jwtHeaders }
  $disabledLoginRejected = -not (Test-WebLogin $researcherUsername $researcherPassword)
  $adminStillValid = (Invoke-WebRequest -UseBasicParsing -Uri ($base + "/admin/users") -WebSession $adminSession).StatusCode
  $disableAudit = Invoke-DatabaseScalar "SELECT COUNT(*) FROM ${AcceptanceDatabase}.audit_log WHERE action='USER_DISABLE' AND resource_id='$userId';"
  $researcherIndexAfterDisable = docker compose exec -T redis sh -c `
    'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw SCARD "track-analysis:web-session:index:org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME:acceptance-researcher"'

  $enableCsrf = Get-Csrf $adminSession "/admin/users"
  Invoke-WebRequest -UseBasicParsing -Uri ($base + "/admin/users/$userId/enabled") -Method Post `
    -WebSession $adminSession -Body @{enabled="true"; _csrf=$enableCsrf} | Out-Null
  $newResearcherSession = Login-Web $researcherUsername $researcherPassword
  $newRestLogin = Invoke-RestMethod -Uri ($base + "/api/v1/auth/login") -Method Post -ContentType "application/json" -Body $loginJson
  $newJwt = $newRestLogin.data.accessToken
  $oldJwtAfterEnable = Get-HttpStatus { Invoke-WebRequest -UseBasicParsing -Uri ($base + "/api/v1/auth/me") -Headers $jwtHeaders }

  $resetCsrf = Get-Csrf $adminSession "/admin/users/$userId"
  Invoke-WebRequest -UseBasicParsing -Uri ($base + "/admin/users/$userId/password") -Method Post `
    -WebSession $adminSession -Body @{password=$resetPassword; _csrf=$resetCsrf} | Out-Null
  $newJwtAfterReset = Get-HttpStatus { Invoke-WebRequest -UseBasicParsing -Uri ($base + "/api/v1/auth/me") -Headers @{Authorization="Bearer $newJwt"} }
  $resetSessionStatus = Invoke-StatusNoRedirect "/app/dashboard" -Session $newResearcherSession
  $oldPasswordRejectedAfterReset = -not (Test-WebLogin $researcherUsername $researcherPassword)
  $resetLogin = Login-Web $researcherUsername $resetPassword

  $profileCsrf = Get-Csrf $resetLogin "/app/profile"
  $profileResponse = Invoke-WebRequest -UseBasicParsing -Uri ($base + "/app/change-password") -Method Post `
    -WebSession $resetLogin -Body @{currentPassword=$resetPassword; newPassword=$finalPassword;
      confirmPassword=$finalPassword; _csrf=$profileCsrf}
  $selfChangePath = $profileResponse.BaseResponse.ResponseUri.AbsolutePath
  $resetPasswordRejectedAfterSelfChange = -not (Test-WebLogin $researcherUsername $resetPassword)
  $finalSession = Login-Web $researcherUsername $finalPassword
  $finalDashboard = (Invoke-WebRequest -UseBasicParsing -Uri ($base + "/app/dashboard") -WebSession $finalSession).StatusCode

  $roleCsrf = Get-Csrf $adminSession "/admin/users/$userId"
  Invoke-WebRequest -UseBasicParsing -Uri ($base + "/admin/users/$userId") -Method Post `
    -WebSession $adminSession -Body @{displayName="Acceptance Researcher"; email="acceptance@example.invalid";
      role="ADMIN"; _csrf=$roleCsrf} | Out-Null
  $roleAudit = Invoke-DatabaseScalar "SELECT COUNT(*) FROM ${AcceptanceDatabase}.audit_log WHERE action='USER_UPDATE' AND resource_id='$userId';"
  $passwordAudits = Invoke-DatabaseScalar "SELECT COUNT(*) FROM ${AcceptanceDatabase}.audit_log WHERE action IN ('PASSWORD_RESET','PASSWORD_CHANGE') AND resource_id='$userId';"

  Write-Output "LOGIN_GET_STATUS=$loginStatus"
  Write-Output "ADMIN_DASHBOARD_STATUS=$($dashboard.StatusCode)"
  Write-Output "ADMIN_IDENTITY_PRESENT=$dashboardIdentity"
  Write-Output "WEB_USER_CREATE_SUCCESS=$createSucceeded"
  Write-Output "WEB_USER_LISTED=$listed"
  Write-Output "PASSWORD_BCRYPT=$bcryptStored"
  Write-Output "USER_CREATE_AUDIT_COUNT=$createAudit"
  Write-Output "RESEARCHER_DASHBOARD_STATUS=$researcherDashboard"
  Write-Output "RESEARCHER_ADMIN_STATUS=$researcherAdminStatus"
  Write-Output "ADMIN_USERS_STATUS=$adminUsersStatus"
  Write-Output "SESSION_COOKIES_DIFFER=$cookiesDiffer"
  Write-Output "REDIS_SESSION_NAMESPACE=track-analysis:web-session"
  Write-Output "REDIS_SESSION_KEY_COUNT=$sessionKeyCount"
  Write-Output "ADMIN_PRINCIPAL_INDEX_MEMBERS=$adminIndex"
  Write-Output "RESEARCHER_PRINCIPAL_INDEX_MEMBERS=$researcherIndex"
  Write-Output "JWT_ME_STATUS=$jwtMe"
  Write-Output "JWT_BUSINESS_STATUS=$jwtBusiness"
  Write-Output "SESSION_ONLY_API_STATUS=$sessionApi"
  Write-Output "JWT_ONLY_WEB_STATUS=$jwtWeb"
  Write-Output "WEB_POST_WITHOUT_CSRF_STATUS=$webNoCsrf"
  Write-Output "AUTH_VERSION_BEFORE_DISABLE=$beforeDisable"
  Write-Output "DISABLED_STATE=$disabledState"
  Write-Output "OLD_SESSION_AFTER_DISABLE_STATUS=$oldSessionAfterDisable"
  Write-Output "OLD_JWT_AFTER_DISABLE_STATUS=$oldJwtAfterDisable"
  Write-Output "ADMIN_SESSION_AFTER_DISABLE_STATUS=$adminStillValid"
  Write-Output "DISABLED_LOGIN_REJECTED=$disabledLoginRejected"
  Write-Output "RESEARCHER_INDEX_MEMBERS_AFTER_DISABLE=$researcherIndexAfterDisable"
  Write-Output "DISABLE_AUDIT_COUNT=$disableAudit"
  Write-Output "OLD_JWT_AFTER_ENABLE_STATUS=$oldJwtAfterEnable"
  Write-Output "PRE_RESET_JWT_AFTER_RESET_STATUS=$newJwtAfterReset"
  Write-Output "PRE_RESET_SESSION_AFTER_RESET_STATUS=$resetSessionStatus"
  Write-Output "OLD_PASSWORD_AFTER_RESET_REJECTED=$oldPasswordRejectedAfterReset"
  Write-Output "SELF_CHANGE_REDIRECT_PATH=$selfChangePath"
  Write-Output "RESET_PASSWORD_AFTER_SELF_CHANGE_REJECTED=$resetPasswordRejectedAfterSelfChange"
  Write-Output "FINAL_PASSWORD_DASHBOARD_STATUS=$finalDashboard"
  Write-Output "ROLE_CHANGE_AUDIT_COUNT=$roleAudit"
  Write-Output "PASSWORD_AUDIT_COUNT=$passwordAudits"
} finally {
  Remove-Item Env:MYSQL_DATABASE -ErrorAction SilentlyContinue
  Remove-Item Env:APP_ADMIN_USERNAME -ErrorAction SilentlyContinue
  Remove-Item Env:APP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
  Invoke-Compose @("up", "-d", "--no-deps", "--force-recreate", "app")
}
