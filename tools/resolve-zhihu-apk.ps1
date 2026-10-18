param(
    [string]$Channel = 'zhihuwap64',
    [string]$OutFile = 'zhihu.apk',
    [switch]$DryRun
)

# 巡检用：不写死下载地址，而是问知乎自己的下载入口"现在的最新包在哪"。
# 官方入口 https://www.zhihu.com/client/download/<id>/<channel> 会 302 到 pkg*.zhimg.com 上的真实文件，
# 文件名里就带着版本号，所以连"去页面里找版本"都省了。
#
# 渠道对照（来源：官网下载页的打包参数）：
#   zhihuwap64  arm64-v8a
#   zhihuwap    armeabi-v7a / 通用
# 1355 是官网当前使用的分发包 id，换了我再改这一行。

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

$entry = "https://www.zhihu.com/client/download/1355/$Channel"

Write-Host "询问下载入口：$entry"
# 只用 1KB 的 Range 请求跟随跳转，先拿到最终地址，别为了看地址就下 160MB
$head = curl.exe -s -L --max-time 60 -r 0-1023 -o NUL -w '%{url_effective}' $entry 2>&1
$final = ($head | Select-Object -Last 1).Trim()
if ([string]::IsNullOrWhiteSpace($final)) {
    throw "下载入口没有返回有效地址：$entry"
}
if ($final -notmatch '\.apk($|\?)') {
    throw "最终地址不像安装包（可能被拦到验证页）：$final"
}
Write-Host "最新安装包  ：$final"

# 文件名里带的版本号，顺手解析出来，方便日志和后续比对
$name = [System.IO.Path]::GetFileName(($final -split '\?')[0])
if ($name -match '\((\d+)\)') {
    Write-Host "版本码      ：$($Matches[1])"
}

if ($DryRun) {
    Write-Host '仅解析，不下载'
    return
}

if (Test-Path -LiteralPath $OutFile) { Remove-Item -LiteralPath $OutFile -Force }
Write-Host "开始下载 → $OutFile"
curl.exe -L --fail --retry 3 --retry-delay 5 --max-time 3600 -o $OutFile $final
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $OutFile)) {
    throw "下载失败（curl 退出码 $LASTEXITCODE）"
}
$size = (Get-Item -LiteralPath $OutFile).Length
Write-Host ("下载完成    ：{0:N1} MB" -f ($size / 1MB))
if ($size -lt 50MB) {
    throw "文件只有 $([math]::Round($size / 1MB, 1)) MB，不像完整的知乎安装包"
}
