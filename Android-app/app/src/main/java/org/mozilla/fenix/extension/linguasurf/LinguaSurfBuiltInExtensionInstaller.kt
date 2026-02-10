package org.mozilla.fenix.extension.linguasurf

import mozilla.components.concept.engine.webextension.WebExtensionRuntime

object LinguaSurfBuiltInExtensionInstaller {
    private const val EXTENSION_ID = "{ec1a757b-3969-4e9a-86e9-c9cd54028a1f}"
    private const val EXTENSION_URL = "resource://android/assets/extensions/linguasurf/"

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
