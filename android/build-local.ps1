param(
    [switch]$Online,
    [switch]$Test,
    [string]$Variant = 'demoRelease',
    [string]$GradleUserHome = 'D:\Android\.gradle'
)

$ErrorActionPreference = 'Stop'
$savedJavaHome = $env:JAVA_HOME
$savedJavaOptions = $env:JAVA_TOOL_OPTIONS
$buildExitCode = 1
Push-Location $PSScriptRoot
try {
    # Keep Java's Windows Unix-domain socket path short and writable.
    $socketDirectory = Join-Path $PSScriptRoot 'build\java-tmp'
    New-Item -ItemType Directory -Path $socketDirectory -Force | Out-Null
    $env:JAVA_TOOL_OPTIONS = ($savedJavaOptions + ' -Djdk.net.unixdomain.tmpdir="' + $socketDirectory + '"').Trim()
    $jdkCandidates = @(
        (Join-Path $GradleUserHome 'jdks\eclipse_adoptium-21-amd64-windows.2'),
        (Join-Path $env:USERPROFILE '.gradle\jdks\eclipse_adoptium-21-amd64-windows.2')
    )
    foreach ($candidate in $jdkCandidates) {
        if (Test-Path (Join-Path $candidate 'bin\java.exe')) {
            $env:JAVA_HOME = $candidate
            break
        }
    }
    $Variant = $Variant.Substring(0, 1).ToUpperInvariant() + $Variant.Substring(1)
    $testVariant = $Variant -replace 'Release$', 'Debug'
    $assembleTask = ":app:assemble$Variant"
    $buildArguments = @($assembleTask, '--console=plain',
        '--gradle-user-home', $GradleUserHome, '-Pandroid.enableJetifier=false')
    if ($Test) { $buildArguments += ":app:test${testVariant}UnitTest" }
    if (-not $Online) { $buildArguments += '--offline' }
    & .\gradlew.bat @buildArguments
    $buildExitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $savedJavaHome
    $env:JAVA_TOOL_OPTIONS = $savedJavaOptions
    Pop-Location
}
exit $buildExitCode
