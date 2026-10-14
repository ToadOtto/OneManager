# Script to synchronize all raw assets into mod resource directories
$baseDir = $PSScriptRoot
$assetsDir = Join-Path $baseDir "assets"
$resDir = Join-Path $baseDir "src\main\resources\assets\pcmanager"

$mobEffectDir = Join-Path $resDir "textures\mob_effect"
$blockTexDir = Join-Path $resDir "textures\block"
$itemTexDir = Join-Path $resDir "textures\item"
$blockModelDir = Join-Path $resDir "models\block"

New-Item -ItemType Directory -Force -Path $mobEffectDir | Out-Null
New-Item -ItemType Directory -Force -Path $blockTexDir | Out-Null
New-Item -ItemType Directory -Force -Path $itemTexDir | Out-Null
New-Item -ItemType Directory -Force -Path $blockModelDir | Out-Null

# 1. Sync mob effect icons
if (Test-Path (Join-Path $assetsDir "ancestor_burden")) {
    Get-ChildItem -Path (Join-Path $assetsDir "ancestor_burden") -Filter "*.png" | ForEach-Object {
        Copy-Item -Path $_.FullName -Destination (Join-Path $mobEffectDir "ancestor_burden.png") -Force
        Copy-Item -Path $_.FullName -Destination (Join-Path $mobEffectDir "ancestral_burden.png") -Force
    }
}

# 2. Sync remains item textures
if (Test-Path (Join-Path $assetsDir "remains")) {
    Get-ChildItem -Path (Join-Path $assetsDir "remains") -Filter "*.png" | ForEach-Object {
        Copy-Item -Path $_.FullName -Destination (Join-Path $itemTexDir $_.Name) -Force
    }
}

# 3. Sync grave block textures
if (Test-Path (Join-Path $assetsDir "grave")) {
    Get-ChildItem -Path (Join-Path $assetsDir "grave") -Filter "*.png" | ForEach-Object {
        Copy-Item -Path $_.FullName -Destination (Join-Path $blockTexDir $_.Name) -Force
    }
}

# 4. Sync tomb_workbench block textures
if (Test-Path (Join-Path $assetsDir "tomb_workbench")) {
    Get-ChildItem -Path (Join-Path $assetsDir "tomb_workbench") -Filter "*.png" | ForEach-Object {
        Copy-Item -Path $_.FullName -Destination (Join-Path $blockTexDir $_.Name) -Force
    }
}

# 5. Sync cremation_furnace block textures and models
if (Test-Path (Join-Path $assetsDir "cremation_furnace")) {
    Get-ChildItem -Path (Join-Path $assetsDir "cremation_furnace") -Filter "*.png" | ForEach-Object {
        Copy-Item -Path $_.FullName -Destination (Join-Path $blockTexDir $_.Name) -Force
    }
    Get-ChildItem -Path (Join-Path $assetsDir "cremation_furnace") -Filter "*.json" | ForEach-Object {
        Copy-Item -Path $_.FullName -Destination (Join-Path $blockModelDir $_.Name) -Force
    }
}

Write-Output "Assets synchronized successfully."
