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
            Log.i(TAG, "App bridge received message: source=$source, type=$type")

            return when (type) {
                "SET_SELECTION_BANNER_DISABLED" -> {
                    val disabled = payload.optBoolean("disabled", false)
                    LinguaSurfAppSettings.setSystemSelectionBannerDisabled(appContext, disabled)
                    Log.i(TAG, "Selection banner preference updated: disabled=$disabled")
                    JSONObject()
                        .put("ok", true)
                        .put("disabled", disabled)
                }
                "GET_SELECTION_BANNER_DISABLED" -> {
                    val disabled = LinguaSurfAppSettings.isSystemSelectionBannerDisabled(appContext)
                    JSONObject()
                        .put("ok", true)
                        .put("disabled", disabled)
                }
                else -> {
                    Log.w(TAG, "App bridge received unsupported type: $type")
                    errorResponse("unsupported_message_type")
                }
            }
        }

        private fun errorResponse(error: String): JSONObject {
            return JSONObject()
                .put("ok", false)
                .put("error", error)
        }
    }
}
