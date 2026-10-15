$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$fontDirectory = Join-Path $projectRoot 'client\src\main\resources\fonts'
New-Item -ItemType Directory -Force -Path $fontDirectory | Out-Null

if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath 'C:\Program Files\Java\jdk-21.0.12.1')) {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'
}
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home' }
if (-not $env:GRADLE_OPTS) {
    $repositoryUri = [uri]'https://repo.maven.apache.org/'
    $proxyAddress = [Net.WebRequest]::GetSystemWebProxy().GetProxy($repositoryUri)
    if ($proxyAddress.Host -ne $repositoryUri.Host) {
        $env:GRADLE_OPTS = "-Dhttps.proxyHost=$($proxyAddress.Host) -Dhttps.proxyPort=$($proxyAddress.Port) -Dhttp.proxyHost=$($proxyAddress.Host) -Dhttp.proxyPort=$($proxyAddress.Port)"
    }
}

$downloads = @(
    @{ File = 'NotoSans.ttf'; Url = 'https://raw.githubusercontent.com/google/fonts/main/ofl/notosans/NotoSans%5Bwdth%2Cwght%5D.ttf' },
    @{ File = 'OFL.txt'; Url = 'https://raw.githubusercontent.com/google/fonts/main/ofl/notosans/OFL.txt' }
)
foreach ($download in $downloads) {
    $destination = Join-Path $fontDirectory $download.File
    if (-not (Test-Path -LiteralPath $destination)) {
        Write-Host ('Downloading ' + $download.File + ' from Google Fonts...')
        $temporaryFile = $destination + '.download'
        try {
            Invoke-WebRequest -UseBasicParsing -Uri $download.Url -OutFile $temporaryFile
            Move-Item -LiteralPath $temporaryFile -Destination $destination
        } finally {
            if (Test-Path -LiteralPath $temporaryFile) { Remove-Item -LiteralPath $temporaryFile }
        }
    }
}
Push-Location $projectRoot
try {
    & '.\gradlew.bat' ':client:prepareClientDependencies' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw 'Client dependencies could not be downloaded. Send the final error text, without passwords.' }
    Write-Host 'Client dependencies and Cyrillic font are ready.'
} finally { Pop-Location }
