param(
  [string]$LockFile = "",
  [string]$XpiPath = "",
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
$artifact = Resolve-LinguaSurfExtensionArtifact -Config $config -XpiPath $XpiPath -ArtifactDir $ArtifactDir -StrategyOverride $Strategy

$artifact | ConvertTo-Json -Depth 4
