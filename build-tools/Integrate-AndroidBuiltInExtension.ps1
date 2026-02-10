param(
  [string]$AndroidRoot = "",
  [string]$LockFile = "",
  [string]$XpiPath = "",
  [string]$SourceDir = "",
  [string]$ArtifactDir = "",
  [string]$Strategy = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = Split-Path -Parent $scriptDir

if (-not $LockFile) {
  $LockFile = Join-Path $repoRoot "version-lock\extension.lock.json"
}

. (Join-Path $scriptDir "lib\ExtensionTools.ps1")

$config = Get-LinguaSurfConfig -LockFilePath $LockFile

if (-not $AndroidRoot) {
  $AndroidRoot = [string]$config.android.appRoot
}
if (-not $AndroidRoot) {
  $AndroidRoot = Join-Path $repoRoot "Android-app"
}

$resolvedAndroidRoot = (Resolve-Path -LiteralPath $AndroidRoot -ErrorAction Stop).Path
$assetRelativePath = [string]$config.extension.assetRelativePath
if (-not $assetRelativePath) {
  throw "Missing extension.assetRelativePath in lock file."
}

$targetXpiPath = Join-Path $resolvedAndroidRoot $assetRelativePath
$targetXpiDir = Split-Path -Parent $targetXpiPath
Ensure-Directory -Path $targetXpiDir

$metadataFileName = [string]$config.extension.metadataFileName
if (-not $metadataFileName) {
  $metadataFileName = "extension-bundle.json"
}
$metadataPath = Join-Path $targetXpiDir $metadataFileName

if (-not $SourceDir) {
  $effectiveStrategy = $Strategy
  if (-not $effectiveStrategy) {
    $effectiveStrategy = [string]$config.source.strategy
  }
  if ($effectiveStrategy -eq "pinned-dir") {
    $SourceDir = [string]$config.source.pinnedDirPath
  }
}

if ($SourceDir) {
  $resolvedSourceDir = (Resolve-Path -LiteralPath $SourceDir -ErrorAction Stop).Path
  $sourceManifestPath = Join-Path $resolvedSourceDir "manifest.json"
  if (-not (Test-Path -LiteralPath $sourceManifestPath)) {
    throw "SourceDir does not contain manifest.json: $resolvedSourceDir"
  }

  Get-ChildItem -LiteralPath $targetXpiDir -Force -ErrorAction SilentlyContinue | ForEach-Object {
    Remove-Item -LiteralPath $_.FullName -Recurse -Force
  }

  Get-ChildItem -LiteralPath $resolvedSourceDir -Force | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $targetXpiDir -Recurse -Force
  }

  $manifestPath = Join-Path $targetXpiDir "manifest.json"
  if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Failed to copy manifest.json into $targetXpiDir"
  }

  $assetRelativeDir = (Split-Path $assetRelativePath -Parent).Replace("\", "/")

  $metadata = [ordered]@{
    extensionId = [string]$config.extension.id
    runtimeResourceUrl = [string]$config.extension.runtimeResourceUrl
    bundledDirectory = [ordered]@{
      relativeDir = $assetRelativeDir
      sourcePath = $resolvedSourceDir
      sourceLastWriteTimeUtc = (Get-Item -LiteralPath $resolvedSourceDir).LastWriteTimeUtc.ToString("o")
      manifestRelativePath = "$assetRelativeDir/manifest.json"
      manifestPresent = $true
    }
    integratedAtUtc = [DateTime]::UtcNow.ToString("o")
    integratedByScript = "build-tools/Integrate-AndroidBuiltInExtension.ps1"
  }
  
  $metadata | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $metadataPath -Encoding UTF8

  Write-Host "Integrated extension into Android app."
  Write-Host ("- Android root: {0}" -f $resolvedAndroidRoot)
  Write-Host ("- Source dir:   {0}" -f $resolvedSourceDir)
  Write-Host ("- Target dir:   {0}" -f $targetXpiDir)
  Write-Host ("- Metadata:     {0}" -f $metadataPath)
}
else {
  $artifact = Resolve-LinguaSurfExtensionArtifact -Config $config -XpiPath $XpiPath -ArtifactDir $ArtifactDir -StrategyOverride $Strategy

  Copy-Item -LiteralPath $artifact.path -Destination $targetXpiPath -Force

  $extractTempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("linguasurf_ext_" + [guid]::NewGuid().ToString("N"))
  Ensure-Directory -Path $extractTempDir

  try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($artifact.path, $extractTempDir)

    $xpiFileName = [System.IO.Path]::GetFileName($targetXpiPath)
    Get-ChildItem -LiteralPath $targetXpiDir -Force | Where-Object { $_.Name -ne $xpiFileName } | ForEach-Object {
      Remove-Item -LiteralPath $_.FullName -Recurse -Force
    }

    Get-ChildItem -LiteralPath $extractTempDir -Force | ForEach-Object {
      Copy-Item -LiteralPath $_.FullName -Destination $targetXpiDir -Recurse -Force
    }
  }
  finally {
    if (Test-Path -LiteralPath $extractTempDir) {
      Remove-Item -LiteralPath $extractTempDir -Recurse -Force
    }
  }

  $manifestPath = Join-Path $targetXpiDir "manifest.json"
  if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Failed to unpack extension manifest.json into $targetXpiDir"
  }

  $assetRelativeDir = (Split-Path $assetRelativePath -Parent).Replace("\", "/")

  $metadata = [ordered]@{
    extensionId = [string]$config.extension.id
    runtimeResourceUrl = [string]$config.extension.runtimeResourceUrl
    bundledXpi = [ordered]@{
      relativePath = $assetRelativePath.Replace("\", "/")
      fileName = [System.IO.Path]::GetFileName($targetXpiPath)
      sourcePath = $artifact.path
      versionHint = $artifact.versionHint
      sha256 = $artifact.sha256
      sizeBytes = $artifact.sizeBytes
      sourceLastWriteTimeUtc = $artifact.lastWriteTimeUtc
    }
    unpackedExtension = [ordered]@{
      relativeDir = $assetRelativeDir
      manifestRelativePath = "$assetRelativeDir/manifest.json"
      manifestPresent = $true
    }
    integratedAtUtc = [DateTime]::UtcNow.ToString("o")
    integratedByScript = "build-tools/Integrate-AndroidBuiltInExtension.ps1"
  }

  $metadata | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $metadataPath -Encoding UTF8

  Write-Host "Integrated extension into Android app."
  Write-Host ("- Android root: {0}" -f $resolvedAndroidRoot)
  Write-Host ("- Source XPI:   {0}" -f $artifact.path)
  Write-Host ("- Target XPI:   {0}" -f $targetXpiPath)
  Write-Host ("- Unpacked dir: {0}" -f $targetXpiDir)
  Write-Host ("- Metadata:     {0}" -f $metadataPath)
}
