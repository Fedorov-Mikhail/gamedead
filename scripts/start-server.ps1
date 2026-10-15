param(
    [string]$ServerDbUrl = 'jdbc:postgresql://localhost:5432/gamedead',
    [string]$ServerDbUsername = 'gamedead_app'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousPassword = $env:GAMEDEAD_DB_PASSWORD
$previousUrl = $env:GAMEDEAD_DB_URL
$previousUser = $env:GAMEDEAD_DB_USERNAME
$previousGradleHome = $env:GRADLE_USER_HOME
$secretPointer = [IntPtr]::Zero
try {
    if (-not $env:GRADLE_USER_HOME) {
        $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home'
    }
    $secret = Read-Host 'Пароль пользователя PostgreSQL gamedead_app' -AsSecureString
    $secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    $env:GAMEDEAD_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
    $env:GAMEDEAD_DB_URL = $ServerDbUrl
    $env:GAMEDEAD_DB_USERNAME = $ServerDbUsername
    Push-Location $projectRoot
    try {
        & '.\gradlew.bat' ':server:bootRun' '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw 'Сервер завершился с ошибкой. Проверь настройки Java и PostgreSQL.' }
    } finally { Pop-Location }
} finally {
    if ($secretPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer) }
    $env:GAMEDEAD_DB_PASSWORD = $previousPassword
    $env:GAMEDEAD_DB_URL = $previousUrl
    $env:GAMEDEAD_DB_USERNAME = $previousUser
    $env:GRADLE_USER_HOME = $previousGradleHome
}

