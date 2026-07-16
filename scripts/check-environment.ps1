$ErrorActionPreference = "Continue"
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$status = 0

function Add-Failure {
    param([string]$Message)

    Write-Error $Message
    $script:status = 1
}

Write-Host "== Java on PATH =="
$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if ($null -eq $javaCommand) {
    Add-Failure "Java is not installed or is not on Path."
} else {
    $javaOutput = & $javaCommand.Source -version 2>&1
    $javaExitCode = $LASTEXITCODE
    $javaOutput | ForEach-Object { Write-Host $_ }
    $javaText = $javaOutput -join "`n"
    if ($javaExitCode -ne 0) {
        Add-Failure "Java could not be executed."
    } elseif ($javaText -notmatch 'version "17(?:\.|\")') {
        Add-Failure "Java 17 is required. Set JAVA_HOME and Path to a JDK 17 installation."
    }

    Write-Host "== Java command =="
    Write-Host $javaCommand.Source

    if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        Add-Failure "JAVA_HOME must point to the active JDK 17 installation."
    } else {
        $javaHomeBin = Join-Path $env:JAVA_HOME "bin"
        if (-not (Test-Path -LiteralPath $javaHomeBin -PathType Container)) {
            Add-Failure "JAVA_HOME does not contain a bin directory."
        } else {
            $expectedJavaDirectory = (Resolve-Path -LiteralPath $javaHomeBin).Path.TrimEnd('\')
            $actualJavaDirectory = (Split-Path -Parent $javaCommand.Source).TrimEnd('\')
            if (-not $expectedJavaDirectory.Equals(
                    $actualJavaDirectory,
                    [System.StringComparison]::OrdinalIgnoreCase)) {
                Add-Failure "JAVA_HOME and the active java command do not reference the same JDK."
            }
        }
    }
}

Write-Host "== Maven Wrapper =="
$wrapperPath = Join-Path $projectRoot "mvnw.cmd"
if (-not (Test-Path -LiteralPath $wrapperPath -PathType Leaf)) {
    Add-Failure "Maven Wrapper is missing: mvnw.cmd"
} else {
    $mavenOutput = & $wrapperPath -version 2>&1
    $mavenExitCode = $LASTEXITCODE
    $mavenOutput | ForEach-Object { Write-Host $_ }
    $mavenText = $mavenOutput -join "`n"
    if ($mavenExitCode -ne 0) {
        Add-Failure "Maven Wrapper could not be executed."
    } elseif ($mavenText -notmatch 'Java version: 17(?:\.|,)') {
        Add-Failure "The Maven Wrapper must run on Java 17."
    }
}

Write-Host "== Global Maven (optional) =="
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    mvn -version
} else {
    Write-Host "Global mvn is not installed; Maven Wrapper will be used."
}

Write-Host "== Git =="
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Add-Failure "Git is not installed or is not on Path."
} else {
    git -C $projectRoot status --short --branch
    if ($LASTEXITCODE -ne 0) {
        Add-Failure "Git repository check failed."
    }
}

Write-Host "== Docker =="
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Add-Failure "Docker Client is not installed or is not on Path."
} else {
    docker version
    if ($LASTEXITCODE -ne 0) {
        Add-Failure "Docker Client cannot connect to Docker Server."
    }

    Write-Host "== Docker Compose =="
    docker compose version
    if ($LASTEXITCODE -ne 0) {
        Add-Failure "Docker Compose is unavailable or failed to execute."
    }
}

exit $status
