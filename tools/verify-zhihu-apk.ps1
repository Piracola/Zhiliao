param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,
    [string]$ExpectedPackage = 'com.zhihu.android',
    [string[]]$AllowedCertSha256 = @(),
    [int]$MinVersionCode = 0,
    [switch]$WhatIfOnly
)

# 巡检用：只读地校验一个知乎安装包"是不是官方的、是哪个版本"。
# 只做三件事——包名、版本号、签名证书指纹；任何一项对不上就直接失败退出。
# 白名单默认读同目录的 zhihu-trusted-certs.txt（证书指纹是公开信息，直接写在仓库里，
# 不需要谁去后台配变量）；需要临时试别的证书时用 -AllowedCertSha256 覆盖。

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

if (-not (Test-Path -LiteralPath $ApkPath)) {
    throw "找不到 APK：$ApkPath"
}
$apk = (Resolve-Path -LiteralPath $ApkPath).Path

$sdk = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($sdk)) { $sdk = $env:ANDROID_SDK_ROOT }
if ([string]::IsNullOrWhiteSpace($sdk) -and (Test-Path 'C:\Android\android-sdk')) { $sdk = 'C:\Android\android-sdk' }
if ([string]::IsNullOrWhiteSpace($sdk) -or -not (Test-Path -LiteralPath $sdk)) {
    throw '未找到 Android SDK（ANDROID_HOME / ANDROID_SDK_ROOT）'
}

$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'aapt2.exe') } |
        Sort-Object Name -Descending |
        Select-Object -First 1
if (-not $buildTools) {
    throw "SDK 里没有可用的 build-tools（需要 aapt2）：$sdk"
}
$aapt2 = Join-Path $buildTools.FullName 'aapt2.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'

Write-Host "aapt2    : $aapt2"
Write-Host "apksigner: $apksigner"

$badging = & $aapt2 dump badging $apk 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "aapt2 解析失败：$badging"
}
$packageLine = $badging | Where-Object { $_ -like 'package:*' } | Select-Object -First 1
if (-not $packageLine) {
    throw 'APK 里没有 package 信息，可能不是有效安装包'
}
if ($packageLine -notmatch "name='([^']+)'") { throw "无法解析包名：$packageLine" }
$packageName = $Matches[1]
if ($packageLine -notmatch "versionCode='([^']+)'") { throw "无法解析 versionCode：$packageLine" }
$versionCode = [int]$Matches[1]
if ($packageLine -notmatch "versionName='([^']+)'") { throw "无法解析 versionName：$packageLine" }
$versionName = $Matches[1]

Write-Host "包名      : $packageName"
Write-Host "版本      : $versionName ($versionCode)"

if ($packageName -ne $ExpectedPackage) {
    throw "包名不符：期望 $ExpectedPackage，实际 $packageName"
}
if ($versionCode -lt $MinVersionCode) {
    throw "版本过旧：$versionCode < $MinVersionCode"
}

# 签名证书指纹：apksigner 输出的 SHA-256 是冒号大写十六进制，统一成小写无冒号再比
$certOut = & $apksigner verify --print-certs $apk 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "apksigner 校验失败：$certOut"
}
$certs = @()
foreach ($line in $certOut) {
    if ($line -match 'SHA-256 digest:\s*([0-9a-fA-F:]+)') {
        $certs += ($Matches[1] -replace ':', '').ToLowerInvariant()
    }
}
if ($certs.Count -eq 0) {
    throw '没读到签名证书指纹'
}
foreach ($cert in $certs) {
    Write-Host "证书指纹  : $cert"
}

$trustedFile = Join-Path $PSScriptRoot 'zhihu-trusted-certs.txt'
if ($AllowedCertSha256.Count -eq 0 -and (Test-Path -LiteralPath $trustedFile)) {
    $AllowedCertSha256 = Get-Content -LiteralPath $trustedFile |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ -and -not $_.StartsWith('#') }
}

if ($AllowedCertSha256.Count -gt 0) {
    $normalized = $AllowedCertSha256 | ForEach-Object { ($_ -replace ':', '').Trim().ToLowerInvariant() } | Where-Object { $_ }
    $hit = $certs | Where-Object { $normalized -contains $_ }
    if (-not $hit) {
        throw "签名证书不在白名单里：$($certs -join ', ')。若确认是官方换了证书，往 tools/zhihu-trusted-certs.txt 里追加一行再跑。"
    }
    Write-Host '证书白名单  : 通过'
} else {
    Write-Host '证书白名单  : 没有可用的白名单文件，跳过比对（只在取指纹时这么用）'
}

$result = [ordered]@{
    package     = $packageName
    versionName = $versionName
    versionCode = $versionCode
    certSha256  = $certs
    apk         = $apk
}
if ($WhatIfOnly) {
    Write-Host '仅校验，不产出其他文件'
}
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path (Get-Location) 'zhihu-apk-info.json') -Encoding utf8
Write-Host '校验通过'
