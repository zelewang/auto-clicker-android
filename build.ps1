# ============================================================
# 自动连点器 APK 一键打包脚本（无需 Gradle / Android Studio）
# 依赖：Android SDK platform android-34 + build-tools 34.0.0 + JDK
# 用法：powershell -ExecutionPolicy Bypass -File build.ps1
# ============================================================
param(
    [string]$SdkRoot = "C:\Android\Sdk",
    [string]$JdkHome = ""
)

$ErrorActionPreference = "Stop"
$Project = Split-Path -Parent $MyInvocation.MyCommand.Path
$OutDir = Join-Path $Project "out"
New-Item -ItemType Directory -Force $OutDir | Out-Null

# ---- 0. 生成图标（仓库不提交二进制，构建时自动生成）----
if (Get-Command python -ErrorAction SilentlyContinue) {
    python (Join-Path $Project "tools\make_icon.py")
    if ($LASTEXITCODE -ne 0) { throw "图标生成失败（需 Python 或手动运行 tools/make_icon.py）" }
} elseif (-not (Test-Path (Join-Path $Project "res\mipmap-xxxhdpi\ic_launcher.png"))) {
    throw "缺少图标且未安装 Python：请先运行 tools/make_icon.py"
}

# ---- 工具链定位 ----
if ($JdkHome -ne "") {
    $env:JAVA_HOME = $JdkHome
    $env:PATH = "$JdkHome\bin;$env:PATH"
}
$Java = "java"; try { java -version 2>&1 | Select-Object -First 1 | Write-Host } catch {}
$Javac = Get-Command javac -ErrorAction Stop
$Keytool = Get-Command keytool -ErrorAction Stop

$BuildTools = Join-Path $SdkRoot "build-tools\34.0.0"
$PlatformJar = Join-Path $SdkRoot "platforms\android-34\android.jar"
foreach ($p in @($BuildTools, $PlatformJar)) {
    if (-not (Test-Path $p)) { throw "缺少 SDK 组件: $p" }
}

$aapt2 = Join-Path $BuildTools "aapt2.exe"
$d8 = Join-Path $BuildTools "d8.bat"
$zipalign = Join-Path $BuildTools "zipalign.exe"
$apksigner = Join-Path $BuildTools "apksigner.bat"

# ---- 清理 ----
$gen = Join-Path $Project "gen"
$classes = Join-Path $Project "classes"
$dex = Join-Path $Project "dex"
foreach ($d in @($gen, $classes, $dex)) { if (Test-Path $d) { Remove-Item -Recurse -Force $d } }
New-Item -ItemType Directory -Force $gen, $classes, $dex | Out-Null

# ---- 1. 编译资源 ----
$resZip = Join-Path $OutDir "res.zip"
if (Test-Path $resZip) { Remove-Item -Force $resZip }
& $aapt2 compile --dir (Join-Path $Project "res") -o $resZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile 失败" }
Write-Host "[1/6] 资源编译完成"

# ---- 2. 链接资源 + 生成 R.java + 基础 APK ----
$baseApk = Join-Path $OutDir "base.apk"
if (Test-Path $baseApk) { Remove-Item -Force $baseApk }
& $aapt2 link `
    -o $baseApk `
    -I $PlatformJar `
    --manifest (Join-Path $Project "AndroidManifest.xml") `
    -R $resZip `
    --java $gen `
    --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link 失败" }
Write-Host "[2/6] 资源链接完成"

# ---- 3. 编译 Java ----
$srcs = @(Get-ChildItem -Recurse -Filter *.java (Join-Path $Project "src") | ForEach-Object FullName)
$srcs += @(Get-ChildItem -Recurse -Filter R.java $gen | ForEach-Object FullName)
& $Javac.Source -encoding UTF-8 -source 1.8 -target 1.8 `
    -classpath $PlatformJar `
    -d $classes `
    @srcs
if ($LASTEXITCODE -ne 0) { throw "javac 编译失败" }
Write-Host "[3/6] Java 编译完成"

# ---- 4. dex ----
$classFiles = @(Get-ChildItem -Recurse -Filter *.class $classes | ForEach-Object FullName)
& $d8 --release --min-api 24 --lib $PlatformJar --output $dex @classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 dex 失败" }
Write-Host "[4/6] dex 完成"

# ---- 5. 将 classes.dex 打入 APK（STORED，兼容所有 API）----
$env:PYTHONPATH = ""
$dexFile = Join-Path $dex "classes.dex"
python -c @"
import zipfile, sys
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, 'a') as z:
    data = open(dex, 'rb').read()
    z.writestr(zipfile.ZipInfo('classes.dex'), data, compress_type=zipfile.ZIP_STORED)
print('classes.dex added:', len(data), 'bytes')
"@ $baseApk $dexFile
if ($LASTEXITCODE -ne 0) { throw "注入 dex 失败" }
Write-Host "[5/6] dex 注入完成"

# ---- 6. 对齐 + 签名 ----
$aligned = Join-Path $OutDir "aligned.apk"
& $zipalign -f 4 $baseApk $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign 失败" }

$ks = Join-Path $Project "autoclicker.keystore"
if (-not (Test-Path $ks)) {
    & $Keytool.Source -genkeypair -v `
        -keystore $ks -storetype JKS `
        -alias autoclicker -keyalg RSA -keysize 2048 -validity 10950 `
        -storepass autoclicker123 -keypass autoclicker123 `
        -dname "CN=AutoClicker,O=Doubao,C=CN"
    if ($LASTEXITCODE -ne 0) { throw "生成密钥失败" }
}

$finalApk = Join-Path $OutDir "AutoClicker-v1.0.apk"
if (Test-Path $finalApk) { Remove-Item -Force $finalApk }
& $apksigner sign `
    --ks $ks --ks-key-alias autoclicker `
    --ks-pass pass:autoclicker123 --key-pass pass:autoclicker123 `
    --out $finalApk $aligned
if ($LASTEXITCODE -ne 0) { throw "签名失败" }

& $apksigner verify --print-certs $finalApk
if ($LASTEXITCODE -ne 0) { throw "签名校验失败" }

Write-Host ""
Write-Host "==== 打包成功 ===="
Write-Host "APK: $finalApk"
