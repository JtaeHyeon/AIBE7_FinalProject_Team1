param([switch]$UseH2, [switch]$UseLocalTestDatabase)
$ErrorActionPreference = 'Stop'
if ($UseH2 -and $UseLocalTestDatabase) { throw 'Choose one database option.' }
$taskRepo = Split-Path -Parent $PSScriptRoot
$localJdk = Join-Path $env:LOCALAPPDATA 'codex-sale-ai-jdk/jdk-17.0.20.1+1'
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath $localJdk)) { $env:JAVA_HOME = $localJdk }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 17 directory.' }
if ($UseH2) {
    Remove-Item Env:SALE_DRAFT_TEST_DB_URL -ErrorAction SilentlyContinue
    Remove-Item Env:SALE_DRAFT_TEST_DB_USER -ErrorAction SilentlyContinue
    Remove-Item Env:SALE_DRAFT_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
} elseif ($UseLocalTestDatabase) {
    $credentialPath = Join-Path $env:LOCALAPPDATA 'codex-sale-ai-postgres/test-credential.xml'
    $testCredential = Import-Clixml -LiteralPath $credentialPath
    $env:SALE_DRAFT_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:55439/pastport_sale_ai_test'
    $env:SALE_DRAFT_TEST_DB_USER = $testCredential.UserName
    $env:SALE_DRAFT_TEST_DB_PASSWORD = $testCredential.GetNetworkCredential().Password
} elseif (-not $env:SALE_DRAFT_TEST_DB_URL -or $env:SALE_DRAFT_TEST_DB_URL -notmatch '^jdbc:postgresql://[^/]+/[^?]*sale_ai_test(?:\?.*)?$') {
    throw 'Set SALE_DRAFT_TEST_DB_URL/USER/PASSWORD for a dedicated PostgreSQL sale_ai_test database, or use -UseH2.'
}
if (-not $UseH2 -and (-not $env:SALE_DRAFT_TEST_DB_USER -or -not $env:SALE_DRAFT_TEST_DB_PASSWORD)) {
    throw 'Set the test DB credentials through environment variables.'
}
Push-Location -LiteralPath $taskRepo
try {
    # A DB change must not reuse a test result from a different database.
    & ./gradlew.bat spotlessCheck test build --rerun-tasks
    if ($LASTEXITCODE -ne 0) { throw "Verification failed with code $LASTEXITCODE" }
} finally { Pop-Location }
