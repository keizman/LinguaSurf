Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-Sha256Hex {
  param(
    [Parameter(Mandatory = $true)]
    [string]$FilePath
  )

  $hasCmd = Get-Command -Name Get-FileHash -ErrorAction SilentlyContinue
  if ($hasCmd) {
    return (Get-FileHash -LiteralPath $FilePath -Algorithm SHA256).Hash.ToLowerInvariant()
  }

  $stream = [System.IO.File]::OpenRead($FilePath)
  try {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
      $bytes = $sha.ComputeHash($stream)
      return ([System.BitConverter]::ToString($bytes) -replace "-", "").ToLowerInvariant()
    }
    finally {
      $sha.Dispose()
    }
  }
  finally {
    $stream.Dispose()
  }
}

function Get-LinguaSurfConfig {
  param(
    [Parameter(Mandatory = $true)]
    [string]$LockFilePath
  )

  $resolvedLockFile = Resolve-Path -LiteralPath $LockFilePath -ErrorAction Stop
  $json = Get-Content -LiteralPath $resolvedLockFile -Raw -Encoding UTF8
  $config = $json | ConvertFrom-Json

  if (-not $config.extension.id) {
    throw "Missing extension.id in lock file: $resolvedLockFile"
  }

  return $config
}

function Get-VersionHintFromFileName {
  param(
    [Parameter(Mandatory = $true)]
    [string]$FileName
  )

  $match = [regex]::Match($FileName, "\d+\.\d+\.\d+(?:[-_a-zA-Z0-9]+)?(?=\.xpi|$)")
  if ($match.Success) {
    return $match.Value
  }

  return "unknown"
}

function Resolve-LinguaSurfExtensionArtifact {
  param(
    [Parameter(Mandatory = $true)]
    [pscustomobject]$Config,
    [string]$XpiPath = "",
    [string]$ArtifactDir = "",
    [string]$StrategyOverride = ""
  )

  if ($XpiPath) {
    $resolvedXpi = Resolve-Path -LiteralPath $XpiPath -ErrorAction Stop
    $artifact = Get-Item -LiteralPath $resolvedXpi -ErrorAction Stop
  }
  else {
    $strategy = $StrategyOverride
    if (-not $strategy) {
      $strategy = [string]$Config.source.strategy
    }
    if (-not $strategy) {
      $strategy = "latest-local"
    }

    if ($strategy -eq "pinned") {
      $pinnedPath = [string]$Config.source.pinnedXpiPath
      if (-not $pinnedPath) {
        throw "Strategy is 'pinned' but source.pinnedXpiPath is empty."
      }

      $resolvedXpi = Resolve-Path -LiteralPath $pinnedPath -ErrorAction Stop
      $artifact = Get-Item -LiteralPath $resolvedXpi -ErrorAction Stop
    }
    else {
      $resolvedArtifactDir = $ArtifactDir
      if (-not $resolvedArtifactDir) {
        $resolvedArtifactDir = [string]$Config.source.artifactDir
      }
      if (-not $resolvedArtifactDir) {
        throw "No artifact directory configured. Set source.artifactDir or pass -ArtifactDir."
      }

      $artifactDirInfo = Get-Item -LiteralPath (Resolve-Path -LiteralPath $resolvedArtifactDir -ErrorAction Stop)
      $pattern = [string]$Config.source.filePattern
      if (-not $pattern) {
        $pattern = "*.xpi"
      }

      $artifact = Get-ChildItem -LiteralPath $artifactDirInfo.FullName -File -Filter $pattern |
        Sort-Object -Property LastWriteTimeUtc -Descending |
        Select-Object -First 1

      if (-not $artifact) {
        throw "No XPI found in '$($artifactDirInfo.FullName)' with pattern '$pattern'."
      }
    }
  }

  if ($artifact.Extension -ne ".xpi") {
    throw "Selected artifact is not an .xpi file: $($artifact.FullName)"
  }

  $hashHex = Get-Sha256Hex -FilePath $artifact.FullName

  return [pscustomobject]@{
    path = $artifact.FullName
    fileName = $artifact.Name
    sizeBytes = $artifact.Length
    lastWriteTimeUtc = $artifact.LastWriteTimeUtc.ToString("o")
    versionHint = Get-VersionHintFromFileName -FileName $artifact.Name
    sha256 = $hashHex
  }
}

function Ensure-Directory {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Path
  )

  if (-not (Test-Path -LiteralPath $Path)) {
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
  }
}
