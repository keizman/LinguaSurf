import android.content.Context
import android.content.Intent
import android.net.Uri
import mozilla.components.concept.engine.webextension.InstallationMethod
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.feature.intent.processing.IntentProcessor
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.net.getFileName
import mozilla.components.support.utils.toSafeIntent
import org.json.JSONObject
import org.mozilla.fenix.addons.AddonInstallAutomationGate
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile


class AddonInstallIntentProcessor(private val context: Context, private val runtime: WebExtensionRuntime) : IntentProcessor {
    private val logger = Logger("AddonInstallIntentProcessor")

    private fun matches(intent: Intent) =
        intent.data != null
                && intent.scheme?.lowercase(Locale.ROOT) in arrayOf("content", "file")
                && intent.type in arrayOf("application/x-xpinstall", "application/zip")

    override fun process(intent: Intent): Boolean {
        val safeIntent = intent.toSafeIntent()
        val url = safeIntent.dataString

        if (url.isNullOrEmpty() || !matches(intent)) {
            return false
        }

        val uriData = intent.data ?: return false
        return try {
            logger.info("Install request: scheme=${uriData.scheme}, type=${intent.type}, data=$url")
            val file = fromUri(uriData)
            val extensionId = extractExtensionId(file)
            if (intent.getBooleanExtra(EXTRA_AUTO_CONFIRM, false)) {
                AddonInstallAutomationGate.requestAutoConfirm(extensionId)
                logger.info(
                    "Automation mode enabled for install request: " +
                        "extensionId=${extensionId ?: "unknown"}, data=$url",
                )
            }
            val extURI = parseExtension(file)
            installExtension(
                extURI,
                onSuccess = { extension ->
                    val version = extension.getMetadata()?.version ?: "unknown"
                    logger.info("Install success: id=${extension.id}, version=$version, source=$extURI")
                },
                onError = { throwable ->
                    logger.error("Install failed: source=$extURI", throwable)
                },
            )
            true
        } catch (throwable: Throwable) {
            logger.error("Install intent processing failed: data=$url", throwable)
            false
        }
    }

    fun installExtension(
        b64: String,
        onSuccess: ((WebExtension) -> Unit),
        onError: ((Throwable) -> Unit) = {},
    ) {
        runtime.installWebExtension(b64, InstallationMethod.FROM_FILE, onSuccess, onError)
    }

    fun parseExtension(inp: File): String {
        return Uri.fromFile(inp.absoluteFile).toString()
    }

    fun fromUri(uri: Uri): File {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme == "file") {
            val sourcePath = uri.path ?: throw IllegalArgumentException("File URI path is null: $uri")
            val source = File(sourcePath)
            if (!source.exists()) {
                throw IllegalArgumentException("Source file does not exist: $sourcePath")
            }
            val destDir = context.externalCacheDir ?: context.cacheDir
            val dest = File(destDir, source.name.ifBlank { "addon-${System.currentTimeMillis()}.xpi" })
            source.copyTo(dest, overwrite = true)
            return dest
        }

        val name = uri.getFileName(context.contentResolver)
        val destDir = context.externalCacheDir ?: context.cacheDir
        val file = File(destDir, name)
        file.createNewFile()
        val ostream = FileOutputStream(file.absolutePath)
        val istream = context.contentResolver.openInputStream(uri)!!
        istream.copyTo(ostream)
        ostream.close()
        istream.close()
        return file
    }

    private fun extractExtensionId(file: File): String? {
        return runCatching {
            ZipFile(file).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json") ?: return null
                val manifestText = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
                val manifest = JSONObject(manifestText)

                val geckoSettings = manifest.optJSONObject("browser_specific_settings")
                    ?.optJSONObject("gecko")
                    ?: manifest.optJSONObject("applications")?.optJSONObject("gecko")

                geckoSettings?.optString("id")?.takeIf { it.isNotBlank() }
            }
        }.onFailure { throwable ->
            logger.error("Failed to extract extension ID from ${file.absolutePath}", throwable)
        }.getOrNull()
    }

    companion object {
        const val EXTRA_AUTO_CONFIRM = "linguasurf.auto_confirm_install"
    }
}
