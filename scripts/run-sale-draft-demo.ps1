$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path -Parent $PSScriptRoot
$localJdk = Join-Path $env:LOCALAPPDATA 'codex-sale-ai-jdk/jdk-17.0.20.1+1'
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath $localJdk)) { $env:JAVA_HOME = $localJdk }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 17 directory.' }
Push-Location -LiteralPath $taskRepo
try {
    & ./gradlew.bat bootRun "--args=--spring.profiles.active=local,sale-draft-demo"
    if ($LASTEXITCODE -ne 0) { throw "Demo exited with code $LASTEXITCODE" }
} finally { Pop-Location }
