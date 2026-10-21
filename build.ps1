<#
.SYNOPSIS
    Build nfc-odm17.zip from module/ + blobs/.

.PARAMETER OutDir
    Output directory, defaults to .\build

.PARAMETER Python
    Python 3 executable, defaults to "python".
#>
[CmdletBinding()]
param(
    [string]$OutDir,
    [string]$Python = "python"
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
if (-not $root) { $root = Split-Path -Parent $MyInvocation.MyCommand.Path }
if (-not $OutDir) { $OutDir = Join-Path $root "build" }

$blobs = @{
    "odm\lib64\nfc_nci.st21nfc.st.so"                = "odm\lib64\nfc_nci.st21nfc.st.so"
    "odm\firmware\st54l_fw.bin"                      = "odm\firmware\st54l_fw.bin"
    "odm\etc\libnfc-hal-st.conf"                     = "odm\etc\libnfc-hal-st.conf"
    "odm\etc\st54l_conf.txt"                         = "odm\etc\st54l_conf.txt"
    "odm\bin\hw\android.hardware.nfc-service-st"     = "odm\bin\hw\android.hardware.nfc-service-st"
    "system\app\Nfc_st\Nfc_st.apk"                   = "system\app\Nfc_st\Nfc_st.apk"
}

$mod = Join-Path $OutDir "module"
Remove-Item -Recurse -Force $mod -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $mod | Out-Null

Write-Host "[1/3] copying module files"
Copy-Item (Join-Path $root "module\*") $mod -Recurse -Force
Copy-Item (Join-Path $root "tools\pack_module.py") $OutDir -Force

Write-Host "[2/3] copying vendor blobs"
foreach ($target in $blobs.Keys) {
    $src = Join-Path $root ("blobs\" + $blobs[$target])
    if (-not (Test-Path $src)) { throw "missing blob: $src`nSee blobs/README.md" }
    $dst = Join-Path $mod $target
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dst) | Out-Null
    Copy-Item $src $dst -Force
}

Write-Host "[3/3] packing"
& $Python (Join-Path $OutDir "pack_module.py") $mod (Join-Path $OutDir "nfc-odm17.zip")
if ($LASTEXITCODE -ne 0) { throw "pack failed" }

Write-Host ""
Write-Host "done: $(Join-Path $OutDir 'nfc-odm17.zip')"
