$ErrorActionPreference = "Continue"
$sourceScript = (Resolve-Path "$PSScriptRoot\check-environment.ps1").Path
$powerShell = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
$failures = 0
$executed = 0

function New-Fixture {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("track-env-check-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Path "$root\scripts", "$root\jdk\bin", "$root\fake-bin" | Out-Null
    Copy-Item -LiteralPath $sourceScript -Destination "$root\scripts\check-environment.ps1"

    @'
@echo off
if "%FAKE_JAVA_VERSION%"=="" set "FAKE_JAVA_VERSION=17"
>&2 echo openjdk version "%FAKE_JAVA_VERSION%.0.0"
exit /b 0
'@ | Set-Content -Encoding ascii "$root\jdk\bin\java.cmd"

    @'
@echo off
echo Apache Maven 3.9.11
echo Java version: 17.0.14, vendor: test, runtime: fixture
exit /b 0
'@ | Set-Content -Encoding ascii "$root\mvnw.cmd"

    @'
@echo off
exit /b 0
'@ | Set-Content -Encoding ascii "$root\fake-bin\git.cmd"

    @'
@echo off
if "%1"=="version" if "%FAKE_DOCKER_SERVER_FAIL%"=="1" exit /b 1
if "%1"=="compose" if "%2"=="version" if "%FAKE_COMPOSE_FAIL%"=="1" exit /b 1
exit /b 0
'@ | Set-Content -Encoding ascii "$root\fake-bin\docker.cmd"

    return $root
}

function Invoke-Case {
    param(
        [string]$Name,
        [int]$ExpectedExitCode,
        [scriptblock]$Arrange = {},
        [string]$JavaVersion = "17",
        [bool]$DockerServerFails = $false
    )

    $script:executed++
    $root = New-Fixture
    try {
        & $Arrange $root
        $oldJavaHome = $env:JAVA_HOME
        $oldPath = $env:Path
        $oldJavaVersion = $env:FAKE_JAVA_VERSION
        $oldDockerFailure = $env:FAKE_DOCKER_SERVER_FAIL
        try {
            $env:JAVA_HOME = "$root\jdk"
            $env:Path = "$root\jdk\bin;$root\fake-bin;$env:SystemRoot\System32;$env:SystemRoot"
            $env:FAKE_JAVA_VERSION = $JavaVersion
            $env:FAKE_DOCKER_SERVER_FAIL = if ($DockerServerFails) { "1" } else { "0" }

            & $powerShell -NoProfile -ExecutionPolicy Bypass -File "$root\scripts\check-environment.ps1" *> $null
            $actualExitCode = $LASTEXITCODE
        } finally {
            $env:JAVA_HOME = $oldJavaHome
            $env:Path = $oldPath
            $env:FAKE_JAVA_VERSION = $oldJavaVersion
            $env:FAKE_DOCKER_SERVER_FAIL = $oldDockerFailure
        }

        if ($actualExitCode -ne $ExpectedExitCode) {
            Write-Error "$Name expected exit $ExpectedExitCode but received $actualExitCode"
            $script:failures++
        } else {
            Write-Host "$Name=PASS"
        }
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force
    }
}

Invoke-Case -Name "normal-environment" -ExpectedExitCode 0
Invoke-Case -Name "missing-docker-client" -ExpectedExitCode 1 -Arrange {
    param($root)
    Remove-Item -LiteralPath "$root\fake-bin\docker.cmd"
}
Invoke-Case -Name "missing-git" -ExpectedExitCode 1 -Arrange {
    param($root)
    Remove-Item -LiteralPath "$root\fake-bin\git.cmd"
}
Invoke-Case -Name "wrong-java-version" -ExpectedExitCode 1 -JavaVersion "21"
Invoke-Case -Name "missing-wrapper" -ExpectedExitCode 1 -Arrange {
    param($root)
    Remove-Item -LiteralPath "$root\mvnw.cmd"
}
Invoke-Case -Name "docker-server-unreachable" -ExpectedExitCode 1 -DockerServerFails $true

Write-Host "environment-check-cases=$executed failures=$failures"
exit $(if ($failures -eq 0) { 0 } else { 1 })
