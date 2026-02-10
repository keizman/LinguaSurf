param(
  [string]$AndroidRoot = "",
  [string]$AppPackage = "",
  [string]$LockFile = "",
  [switch]$SkipBuildGradleHook
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Insert-ImportLine {
  param(
    [Parameter(Mandatory = $true)] [string]$Content,
    [Parameter(Mandatory = $true)] [string]$ImportLine
  )

  if ($Content.Contains($ImportLine)) {
    return $Content
  }

  $firstImportIndex = $Content.IndexOf("import ")
  if ($firstImportIndex -ge 0) {
    return $Content.Insert($firstImportIndex, "$ImportLine`r`n")
  }

  $packageEndIndex = $Content.IndexOf("`n")
  if ($packageEndIndex -ge 0) {
    return $Content.Insert($packageEndIndex + 1, "`r`n$ImportLine`r`n")
  }

  return "$Content`r`n$ImportLine`r`n"
}

function Insert-InstallerCallBeforeCatch {
  param(
    [Parameter(Mandatory = $true)] [string]$Content
  )

  if ($Content.Contains("LinguaSurfBuiltInExtensionInstaller.installIfSupported")) {
    return $Content
  }

  $pattern = "(?m)^(\s*)\} catch \(e: UnsupportedOperationException\) \{"
  $match = [regex]::Match($Content, $pattern)
  if (-not $match.Success) {
    throw "Could not find catch block in FenixApplication.kt to insert extension bootstrap call."
  }

  $indent = $match.Groups[1].Value
  $insertion = @"
$indent    // LinguaSurf: install bundled translation extension from APK assets.
$indent    LinguaSurfBuiltInExtensionInstaller.installIfSupported(components.core.engine) { message, throwable ->
$indent        logger.error(message, throwable)
$indent    }
"@

  return $Content.Insert($match.Index, ($insertion + "`r`n"))
}

function Append-BuildGradleHook {
  param(
    [Parameter(Mandatory = $true)] [string]$BuildGradlePath
  )

  $startMarker = "/* LinguaSurf extension integration BEGIN */"
  $endMarker = "/* LinguaSurf extension integration END */"

  $content = Get-Content -LiteralPath $BuildGradlePath -Raw -Encoding UTF8
  if ($content.Contains($startMarker)) {
    return
  }

  $block = @"

$startMarker
def linguaSurfRootDir = rootProject.projectDir.parentFile
def linguaSurfIntegrateScript = new File(linguaSurfRootDir, "build-tools/Integrate-AndroidBuiltInExtension.ps1")

tasks.register("integrateLinguaSurfExtension", Exec) {
    onlyIf { linguaSurfIntegrateScript.exists() }
    workingDir linguaSurfRootDir
    commandLine "powershell", "-ExecutionPolicy", "Bypass", "-File", linguaSurfIntegrateScript.absolutePath, "-AndroidRoot", rootProject.projectDir.absolutePath
}

preBuild.dependsOn("integrateLinguaSurfExtension")
$endMarker
"@

  Set-Content -LiteralPath $BuildGradlePath -Value ($content + $block) -Encoding UTF8
}

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

if (-not $AppPackage) {
  $AppPackage = [string]$config.android.appPackage
}
if (-not $AppPackage) {
  $AppPackage = "org.mozilla.fenix"
}

$packagePath = $AppPackage.Replace(".", "\")
$installerDir = Join-Path $resolvedAndroidRoot ("app\src\main\java\{0}\extension\linguasurf" -f $packagePath)
Ensure-Directory -Path $installerDir

$installerFile = Join-Path $installerDir "LinguaSurfBuiltInExtensionInstaller.kt"
$extensionId = [string]$config.extension.id
$runtimeResourceUrl = [string]$config.extension.runtimeResourceUrl
if (-not $runtimeResourceUrl) {
  throw "Missing extension.runtimeResourceUrl in lock file."
}

$installerKotlin = @"
package $AppPackage.extension.linguasurf

import mozilla.components.concept.engine.webextension.WebExtensionRuntime

object LinguaSurfBuiltInExtensionInstaller {
    private const val EXTENSION_ID = "$extensionId"
    private const val EXTENSION_URL = "$runtimeResourceUrl"

    fun installIfSupported(runtime: Any, onError: ((String, Throwable?) -> Unit)? = null) {
        val extensionRuntime = runtime as? WebExtensionRuntime ?: return
        extensionRuntime.installBuiltInWebExtension(
            id = EXTENSION_ID,
            url = EXTENSION_URL,
            onSuccess = { },
            onError = { throwable ->
                onError?.invoke("Failed to install LinguaSurf built-in extension", throwable)
            },
        )
    }
}
"@

Set-Content -LiteralPath $installerFile -Value $installerKotlin -Encoding UTF8

$fenixPathFromConfig = [string]$config.android.fenixApplicationPath
if (-not $fenixPathFromConfig) {
  $fenixPathFromConfig = ("app/src/main/java/{0}/FenixApplication.kt" -f $AppPackage.Replace(".", "/"))
}
$fenixApplicationPath = Join-Path $resolvedAndroidRoot $fenixPathFromConfig
if (-not (Test-Path -LiteralPath $fenixApplicationPath)) {
  throw "FenixApplication.kt not found: $fenixApplicationPath"
}

$fenixContent = Get-Content -LiteralPath $fenixApplicationPath -Raw -Encoding UTF8
$importLine = "import $AppPackage.extension.linguasurf.LinguaSurfBuiltInExtensionInstaller"
$fenixContent = Insert-ImportLine -Content $fenixContent -ImportLine $importLine
$fenixContent = Insert-InstallerCallBeforeCatch -Content $fenixContent
Set-Content -LiteralPath $fenixApplicationPath -Value $fenixContent -Encoding UTF8

if (-not $SkipBuildGradleHook) {
  $appBuildGradle = Join-Path $resolvedAndroidRoot "app\build.gradle"
  if (-not (Test-Path -LiteralPath $appBuildGradle)) {
    throw "app/build.gradle not found: $appBuildGradle"
  }

  Append-BuildGradleHook -BuildGradlePath $appBuildGradle
}

Write-Host "Applied Android extension bootstrap."
Write-Host ("- Android root:       {0}" -f $resolvedAndroidRoot)
Write-Host ("- App package:        {0}" -f $AppPackage)
Write-Host ("- Installer file:     {0}" -f $installerFile)
Write-Host ("- FenixApplication:   {0}" -f $fenixApplicationPath)
if ($SkipBuildGradleHook) {
  Write-Host "- Build.gradle hook:  skipped"
}
else {
  Write-Host ("- Build.gradle hook:  {0}" -f (Join-Path $resolvedAndroidRoot "app\build.gradle"))
}
