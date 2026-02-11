/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.gecko.selection

import android.app.Activity
import android.content.Context
import android.view.MenuItem
import androidx.annotation.VisibleForTesting
import mozilla.components.concept.engine.selection.SelectionActionDelegate
import org.mozilla.geckoview.BasicSelectionActionDelegate

/**
 * An adapter between the GV [BasicSelectionActionDelegate] and a generic [SelectionActionDelegate].
 *
 * @param customDelegate handles as much of this logic as possible.
 */
open class GeckoSelectionActionDelegate(
    activity: Activity,
    @get:VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val customDelegate: SelectionActionDelegate,
) : BasicSelectionActionDelegate(activity) {
    private val customActions: Array<String> by lazy { customDelegate.getAllActions() }
    private val suppressDefaultActions: Boolean
        get() = customActions.isEmpty()

    companion object {
        /**
         * @returns a [GeckoSelectionActionDelegate] if [customDelegate] is non-null and [context]
         * is an instance of [Activity]. Otherwise, returns null.
         */
        fun maybeCreate(context: Context, customDelegate: SelectionActionDelegate?): GeckoSelectionActionDelegate? {
            return if (context is Activity && customDelegate != null) {
                GeckoSelectionActionDelegate(context, customDelegate)
            } else {
                null
            }
        }
    }

    override fun getAllActions(): Array<String> {
        if (suppressDefaultActions) {
            return customActions
        }

        return customDelegate.sortedActions(super.getAllActions() + customActions)
    }

    override fun isActionAvailable(id: String): Boolean {
        val selectedText = mSelection?.text

        val customActionIsAvailable = !selectedText.isNullOrEmpty() &&
            customDelegate.isActionAvailable(id, selectedText)

        if (suppressDefaultActions) {
            return customActionIsAvailable
        }

        return customActionIsAvailable ||
            super.isActionAvailable(id)
    }

    override fun prepareAction(id: String, item: MenuItem) {
        val title = customDelegate.getActionTitle(id)
        if (title == null) {
            if (!suppressDefaultActions) {
                super.prepareAction(id, item)
            }
            return
        }

        item.title = title
    }

    override fun performAction(id: String, item: MenuItem): Boolean {
        // Temporary, removed once https://bugzilla.mozilla.org/show_bug.cgi?id=1694983 is fixed
        try {
            val selectedText = mSelection?.text ?: return if (suppressDefaultActions) false else super.performAction(id, item)

            if (suppressDefaultActions) {
                return customDelegate.performAction(id, selectedText)
            }

            return customDelegate.performAction(id, selectedText) || super.performAction(id, item)
        } catch (e: SecurityException) {
            return false
        }
    }
}
