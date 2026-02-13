package org.mozilla.fenix.extension.linguasurf

import android.content.Context
import android.util.Log
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.support.webextensions.BuiltInWebExtensionController
import org.json.JSONObject

object LinguaSurfBuiltInExtensionInstaller {
    private const val EXTENSION_ID = "{ec1a757b-3969-4e9a-86e9-c9cd54028a1f}"
    private const val EXTENSION_URL = "resource://android/assets/extensions/linguasurf/"
    private const val APP_BRIDGE_PORT = "linguasurfAppBridge"
    private const val TYPE_SET_SELECTION_BANNER_DISABLED = "SET_SELECTION_BANNER_DISABLED"
    private const val TYPE_GET_SELECTION_BANNER_DISABLED = "GET_SELECTION_BANNER_DISABLED"
    private const val TYPE_APP_LOG_BATCH = "APP_LOG_BATCH"
    private const val LOGCAT_CHUNK_SIZE = 3500
    private const val TAG = "LinguaSurfExtension"

    private val extensionController = BuiltInWebExtensionController(
        EXTENSION_ID,
        EXTENSION_URL,
        APP_BRIDGE_PORT,
    )

    fun installIfSupported(
        runtime: Any,
        appContext: Context,
        onError: ((String, Throwable?) -> Unit)? = null,
    ) {
        val extensionRuntime = runtime as? WebExtensionRuntime ?: return
        extensionController.registerBackgroundMessageHandler(
            LinguaSurfAppBridgeMessageHandler(appContext.applicationContext),
            APP_BRIDGE_PORT,
        )
        extensionController.install(
            runtime = extensionRuntime,
            onSuccess = {
                Log.i(TAG, "LinguaSurf built-in extension installed: ${it.id}")
            },
            onError = { throwable ->
                onError?.invoke("Failed to install LinguaSurf built-in extension", throwable)
            },
        )
    }

    private class LinguaSurfAppBridgeMessageHandler(
        private val appContext: Context,
    ) : MessageHandler {
        override fun onPortConnected(port: Port) {
            Log.i(TAG, "App bridge port connected: ${port.name()}")
        }

        override fun onPortMessage(message: Any, port: Port) {
            val response = handleBridgeMessage(message, "port")
            try {
                port.postMessage(response)
            } catch (throwable: Throwable) {
                Log.w(TAG, "Failed to post app bridge response to port", throwable)
            }
        }

        override fun onMessage(message: Any, source: EngineSession?): Any {
            return handleBridgeMessage(message, "sendNativeMessage")
        }

        private fun handleBridgeMessage(message: Any, source: String): JSONObject {
            val payload = message as? JSONObject
            if (payload == null) {
                Log.w(TAG, "App bridge received non-JSON payload: ${message::class.java.simpleName}")
                return errorResponse("invalid_payload")
            }

            val type = payload.optString("type")
            val entryCount = payload.optJSONArray("entries")?.length() ?: 0
            Log.i(
                TAG,
                "App bridge received message: source=$source, type=$type, entries=$entryCount",
            )

            return when (type) {
                TYPE_SET_SELECTION_BANNER_DISABLED -> {
                    val disabled = payload.optBoolean("disabled", false)
                    LinguaSurfAppSettings.setSystemSelectionBannerDisabled(appContext, disabled)
                    Log.i(TAG, "Selection banner preference updated: disabled=$disabled")
                    JSONObject()
                        .put("ok", true)
                        .put("disabled", disabled)
                }
                TYPE_GET_SELECTION_BANNER_DISABLED -> {
                    val disabled = LinguaSurfAppSettings.isSystemSelectionBannerDisabled(appContext)
                    JSONObject()
                        .put("ok", true)
                        .put("disabled", disabled)
                }
                TYPE_APP_LOG_BATCH -> handleAppLogBatch(payload)
                else -> {
                    Log.w(TAG, "App bridge received unsupported type: $type")
                    errorResponse("unsupported_message_type")
                }
            }
        }

        private fun handleAppLogBatch(payload: JSONObject): JSONObject {
            val entries = payload.optJSONArray("entries")
            if (entries == null) {
                Log.w(TAG, "App bridge APP_LOG_BATCH missing entries array")
                return errorResponse("invalid_log_entries")
            }

            val total = entries.length()
            var accepted = 0

            for (index in 0 until total) {
                val entry = entries.optJSONObject(index)
                if (entry == null) {
                    Log.w(TAG, "App bridge log entry is not JSONObject: index=$index")
                    continue
                }

                try {
                    writeExtensionLogEntry(entry)
                    accepted++
                } catch (throwable: Throwable) {
                    Log.w(TAG, "Failed to write extension log entry: index=$index", throwable)
                }
            }

            Log.i(TAG, "App bridge APP_LOG_BATCH processed: total=$total, accepted=$accepted")

            return JSONObject()
                .put("ok", true)
                .put("accepted", accepted)
        }

        private fun writeExtensionLogEntry(entry: JSONObject) {
            val level = entry.optString("level", "log").lowercase()
            val module = entry.optString("module", "UnknownModule")
            val message = entry.optString("message", "")
            val args = entry.optString("args", "")
            val timestamp = entry.optString("timestamp", "")
            val seq = entry.optInt("seq", -1)

            val seqPart = if (seq >= 0) "[seq=$seq]" else ""
            val tsPart = if (timestamp.isNotBlank()) "[ts=$timestamp]" else ""
            val body = buildString {
                append("[")
                append(level.uppercase())
                append("]")
                append("[")
                append(module)
                append("]")
                append(seqPart)
                append(tsPart)
                append(" ")
                append(message)
                if (args.isNotBlank()) {
                    append(" | args=")
                    append(args)
                }
            }

            logChunked(toAndroidPriority(level), body)
        }

        private fun toAndroidPriority(level: String): Int {
            return when (level) {
                "error" -> Log.ERROR
                "warn" -> Log.WARN
                else -> Log.INFO
            }
        }

        private fun logChunked(priority: Int, message: String) {
            if (message.length <= LOGCAT_CHUNK_SIZE) {
                Log.println(priority, TAG, message)
                return
            }

            val totalChunks = (message.length + LOGCAT_CHUNK_SIZE - 1) / LOGCAT_CHUNK_SIZE
            var start = 0
            var chunkIndex = 1

            while (start < message.length) {
                val end = minOf(start + LOGCAT_CHUNK_SIZE, message.length)
                val chunk = message.substring(start, end)
                Log.println(priority, TAG, "$chunk [chunk $chunkIndex/$totalChunks]")
                start = end
                chunkIndex++
            }
        }

        private fun errorResponse(error: String): JSONObject {
            return JSONObject()
                .put("ok", false)
                .put("error", error)
        }
    }
}
