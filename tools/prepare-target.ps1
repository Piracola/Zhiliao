param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,
    [string]$OutputRoot,
    [switch]$SkipDecompile,
    [switch]$RunTests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$OutputEncoding = [System.Text.UTF8Encoding]::new()

$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $projectRoot 'targetapp'
}

$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$sdk = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($sdk)) {
    $sdk = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
}
$javaHome = $env:JAVA_HOME
if ([string]::IsNullOrWhiteSpace($javaHome)) {
    $javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
}
if ([string]::IsNullOrWhiteSpace($sdk) -or -not (Test-Path -LiteralPath $sdk)) {
    throw '未找到 Android SDK，请先配置 ANDROID_HOME。'
}
if ([string]::IsNullOrWhiteSpace($javaHome) -or -not (Test-Path -LiteralPath $javaHome)) {
    throw '未找到 JDK，请先配置 JAVA_HOME。'
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"
$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'aapt2.exe') } |
        Sort-Object Name -Descending |
        Select-Object -First 1
if ($null -eq $buildTools) {
    throw 'Android SDK 中没有 Build Tools。'
}
$aapt = Join-Path $buildTools.FullName 'aapt2.exe'
$badging = & $aapt dump badging $apk
if ($LASTEXITCODE -ne 0) {
    throw "无法读取 APK 信息：$apk"
}
$packageLine = $badging | Select-String "^package: name='" | Select-Object -First 1
if ($null -eq $packageLine) {
    throw '无法从 APK 中解析包名和版本号。'
}
$line = $packageLine.Line
if ($line -notmatch "^package: name='([^']+)'") {
    throw '无法从 APK 中解析包名。'
}
$packageName = $Matches[1]
if ($line -notmatch " versionCode='([^']+)'") {
    throw '无法从 APK 中解析 versionCode。'
}
$versionCode = $Matches[1]
if ($line -notmatch " versionName='([^']+)'") {
    throw '无法从 APK 中解析 versionName。'
}
$versionName = $Matches[1]
if ($packageName -ne 'com.zhihu.android') {
    throw "APK 包名不是 com.zhihu.android：$packageName"
}

$jarDir = Join-Path $OutputRoot 'jars'
$sourceDir = Join-Path $OutputRoot "decompiled\$versionName"
New-Item -ItemType Directory -Force -Path $jarDir | Out-Null
$jarPath = Join-Path $jarDir "$versionName $versionCode.jar"

$dex2jar = Get-Command 'd2j-dex2jar.bat' -ErrorAction SilentlyContinue
if ($null -eq $dex2jar) {
    throw '未找到 dex2jar，请把 d2j-dex2jar.bat 所在目录加入 PATH。'
}
& $dex2jar.Source -f -o $jarPath $apk
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $jarPath)) {
    throw 'dex2jar 转换失败。大型 APK 通常需要把 dex2jar 的 Java 内存上限调到 8GB。'
}

if (-not $SkipDecompile) {
    $jadx = Get-Command 'jadx.bat' -ErrorAction SilentlyContinue
    if ($null -eq $jadx) {
        throw '未找到 JADX 命令行工具，请把 jadx 的 bin 目录加入 PATH。'
    }
    & $jadx.Source --no-res --threads-count 8 --output-dir $sourceDir $apk
    if ($LASTEXITCODE -ne 0) {
        Write-Warning 'JADX 有部分方法无法还原，但已生成的类和签名索引通常仍可使用。'
    }
}

Write-Host "已准备知乎 $versionName ($versionCode)"
Write-Host "测试 JAR：$jarPath"
if (-not $SkipDecompile) {
    Write-Host "反编译源码：$sourceDir"
}

if ($RunTests) {
    $env:ZHILIAO_TEST_DIR = $jarDir
    & (Join-Path $projectRoot 'gradlew.bat') testDebugUnitTest --rerun-tasks
    if ($LASTEXITCODE -ne 0) {
        throw '目标版本兼容性测试失败。'
    }
}
