/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.addons

import mozilla.components.support.base.log.logger.Logger

/**
 * Stores one-shot automation hints for debug installs triggered via adb intent.
 */
object AddonInstallAutomationGate {
    private val logger = Logger("AddonInstallAutomationGate")

    private var pendingPermissionsAutoApproveExtensionId: String? = null
    private var pendingPostInstallAutoDismissExtensionId: String? = null
    private var pendingAnyExtension: Boolean = false

    @Synchronized
    fun requestAutoConfirm(extensionId: String?) {
        pendingPermissionsAutoApproveExtensionId = extensionId
        pendingPostInstallAutoDismissExtensionId = extensionId
        pendingAnyExtension = extensionId.isNullOrBlank()
        logger.info(
            "Auto-confirm requested: extensionId=${extensionId ?: "<any>"}, " +
                "permissions=true, postInstall=true",
        )
    }

    @Synchronized
    fun consumeAutoApprovePermissions(extensionId: String): Boolean {
        val matched = pendingAnyExtension || pendingPermissionsAutoApproveExtensionId == extensionId
        if (matched) {
            pendingPermissionsAutoApproveExtensionId = null
            pendingAnyExtension = false
            logger.info("Auto-approved install permissions for extensionId=$extensionId")
        }
        return matched
    }

    @Synchronized
    fun consumeAutoDismissPostInstall(extensionId: String): Boolean {
        val matched = pendingAnyExtension || pendingPostInstallAutoDismissExtensionId == extensionId
        if (matched) {
            pendingPostInstallAutoDismissExtensionId = null
            pendingAnyExtension = false
            logger.info("Auto-dismissed post-install prompt for extensionId=$extensionId")
        }
        return matched
    }
}
