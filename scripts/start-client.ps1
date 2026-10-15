param(
    [string]$ServerUrl = 'http://127.0.0.1:8080'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousGradleHome = $env:GRADLE_USER_HOME
$previousJavaHome = $env:JAVA_HOME
try {
    if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath 'C:\Program Files\Java\jdk-21.0.12.1')) {
        $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'
    }
    if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home' }
    Push-Location $projectRoot
    try {
        & '.\gradlew.bat' ':client:run' "--args=--server-url=$ServerUrl" '--offline' '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw 'Client could not start. Check the error above; downloads are prepared by scripts/prepare-client.ps1.' }
    } finally { Pop-Location }
} finally {
    $env:GRADLE_USER_HOME = $previousGradleHome
    $env:JAVA_HOME = $previousJavaHome
}
