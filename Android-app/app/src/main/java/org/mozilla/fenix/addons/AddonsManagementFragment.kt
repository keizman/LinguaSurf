/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.addons

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.fonts.FontStyle.FONT_WEIGHT_MEDIUM
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.concept.engine.webextension.InstallationMethod
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonManager
import mozilla.components.feature.addons.AddonManagerException
import mozilla.components.feature.addons.ui.AddonsManagerAdapter
import mozilla.components.feature.addons.ui.AddonsManagerAdapterDelegate
import mozilla.components.feature.addons.ui.translateName
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.view.hideKeyboard
import mozilla.components.support.webextensions.WebExtensionSupport
import org.mozilla.fenix.BrowserDirection
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.R
import org.mozilla.fenix.databinding.FragmentAddOnsManagementBinding
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.openToBrowser
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.runIfFragmentIsAttached
import org.mozilla.fenix.ext.showToolbar
import org.mozilla.fenix.settings.SupportUtils.AMO_HOMEPAGE_FOR_ANDROID
import org.mozilla.fenix.theme.ThemeManager
import java.util.Locale
import kotlin.coroutines.resume
import mozilla.components.feature.addons.R as addonsR

/**
 * Fragment use for managing add-ons.
 */
@Suppress("TooManyFunctions", "LargeClass")
class AddonsManagementFragment : Fragment(R.layout.fragment_add_ons_management) {
    private companion object {
        // Keep built-in extension visible in Add-ons page for LinguaSurf verification/management.
        private const val LINGUASURF_BUILTIN_EXTENSION_ID = "{ec1a757b-3969-4e9a-86e9-c9cd54028a1f}"
        private const val LINGUASURF_BUILTIN_EXTENSION_ID_NO_BRACES = "ec1a757b-3969-4e9a-86e9-c9cd54028a1f"
        private const val LINGUASURF_BUILTIN_EXTENSION_DIR_URL =
            "resource://android/assets/extensions/linguasurf/"
        private const val TARGET_MANAGER = "manager"
        private const val TARGET_LIST = "list"
        private const val TARGET_DETAILS = "details"
        private const val TARGET_SETTINGS = "settings"
        private const val TARGET_OPTIONS = "options"
        private const val EXTRA_OPEN_ADDON_ID = "org.mozilla.fenix.extra.OPEN_ADDON_ID"
        private const val EXTRA_OPEN_ADDON_TARGET = "org.mozilla.fenix.extra.OPEN_ADDON_TARGET"
    }

    private val logger = Logger("AddonsManagementFragment")

    private var binding: FragmentAddOnsManagementBinding? = null

    private var addons: List<Addon> = emptyList()

    private var adapter: AddonsManagerAdapter? = null

    private var addonImportFilePicker: ActivityResultLauncher<Intent>? = null
    private val args by navArgs<AddonsManagementFragmentArgs>()
    private var hasHandledOpenAddonRoute = false

    private val browsingModeManager by lazy {
        (activity as HomeActivity).browsingModeManager
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logger.info("View created for AddonsManagementFragment")
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentAddOnsManagementBinding.bind(view)
        bindRecyclerView()
        setupMenu()
        addonImportFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                result: ActivityResult ->
            if(result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let{uri ->
                    requireComponents.intentProcessors.addonInstallIntentProcessor.fromUri(uri)
                        .let{ tmpFile ->
                            val extURI = requireComponents.intentProcessors.addonInstallIntentProcessor.parseExtension(tmpFile)
                            requireComponents.intentProcessors.addonInstallIntentProcessor.installExtension(
                                extURI,
                                onSuccess = {
                                    val installedState = provideAddonManager().toInstalledState(it)
                                    val ao = Addon.newFromWebExtension(it, installedState)
                                    runIfFragmentIsAttached {
                                        adapter?.updateAddon(ao)
                                        binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                                    }
                                }
                            )
                        }
                }
            }
        }
    }

    private fun setupMenu() {
        val menuHost = requireActivity() as MenuHost

        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                    val menuResId = resources.getIdentifier("addons_menu", "menu", requireContext().packageName)
                    if (menuResId == 0) {
                        logger.warn("Add-ons menu resource not found for this build variant")
                        return
                    }
                    inflater.inflate(menuResId, menu)

                    val searchMenuId = resources.getIdentifier("search", "id", requireContext().packageName)
                    if (searchMenuId == 0) {
                        logger.warn("Add-ons search menu id not found for this build variant")
                        return
                    }

                    val searchItem = menu.findItem(searchMenuId)
                    if (searchItem == null) {
                        logger.warn("Add-ons search menu item missing in inflated menu")
                        return
                    }
                    val searchView: SearchView = searchItem.actionView as SearchView
                    searchView.imeOptions = EditorInfo.IME_ACTION_DONE
                    searchView.queryHint = getString(R.string.addons_search_hint)

                    searchView.setOnQueryTextListener(
                        object : SearchView.OnQueryTextListener {
                            override fun onQueryTextSubmit(query: String): Boolean {
                                searchAddons(query.trim())
                                return false
                            }

                            override fun onQueryTextChange(newText: String): Boolean {
                                searchAddons(newText.trim())
                                return false
                            }
                        },
                    )
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    // Handle the menu selection
                    val deleteCacheMenuId = resources.getIdentifier("addons_delete_cache", "id", requireContext().packageName)
                    val sideloadMenuId = resources.getIdentifier("addons_sideload", "id", requireContext().packageName)
                    val searchMenuId = resources.getIdentifier("search", "id", requireContext().packageName)
                    return when (menuItem.itemId) {
                        deleteCacheMenuId -> {
                            showAlertDialog()
                            true
                        }
                        sideloadMenuId -> {
                            if (sideloadMenuId == 0) {
                                return false
                            }
                            installFromFile()
                            true
                        }
                        searchMenuId -> {
                            true
                        }
                        else -> {true}
                    }
                }
            },
            viewLifecycleOwner, Lifecycle.State.RESUMED,
        )
    }

    private fun installFromFile() {
        val intent = Intent()
            .setType("application/x-xpinstall")
            .setAction(Intent.ACTION_GET_CONTENT)

        addonImportFilePicker!!.launch(intent)
    }

    private fun showAlertDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        builder
            .setMessage(R.string.confirm_addons_delete_cache)
            .setPositiveButton(R.string.confirm_addons_delete_cache_yes) { _, _ ->
                requireComponents.clearAddonCache()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            .setNegativeButton(R.string.confirm_addons_delete_cache_no) { _, _ ->
                // User cancelled the dialog.
            }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    private fun searchAddons(addonSearchText: String): Boolean {
        if (adapter == null) {
            return false
        }

        val searchedAddons = arrayListOf<Addon>()
        addons.forEach { addon ->
            val names = addon.translatableName
            val language = Locale.getDefault().language
            names[language]?.let { name ->
                if (name.lowercase().contains(addonSearchText.lowercase())) {
                    searchedAddons.add(addon)
                }
            }
            val description = addon.translatableDescription
            description[language]?.let { desc ->
                if (desc.lowercase().contains(addonSearchText.lowercase())) {
                    if (!searchedAddons.contains(addon)) {
                        searchedAddons.add(addon)
                    }
                }
            }
        }
        updateUI(searchedAddons)

        return true
    }

    private fun updateUI(searchedAddons: List<Addon>) {
        adapter?.updateAddons(searchedAddons)

        if (searchedAddons.isEmpty()) {
            binding?.addOnsEmptyMessage?.visibility = View.VISIBLE
            binding?.addOnsList?.visibility = View.GONE
        } else {
            binding?.addOnsEmptyMessage?.visibility = View.GONE
            binding?.addOnsList?.visibility = View.VISIBLE
        }
    }


    override fun onResume() {
        logger.info("Resumed AddonsManagementFragment")

        super.onResume()
        showToolbar(getString(R.string.preferences_extensions))
        view?.hideKeyboard()
    }

    override fun onDestroyView() {
        logger.info("Destroyed view for AddonsManagementFragment")

        super.onDestroyView()
        // letting go of the resources to avoid memory leak.
        adapter = null
        binding = null
    }

    @Suppress("CognitiveComplexMethod")
    private fun bindRecyclerView() {
        logger.info("Binding recycler view for AddonsManagementFragment")

        val managementView = AddonsManagementView(
            navController = findNavController(),
            onInstallButtonClicked = ::installAddon,
            onMoreAddonsButtonClicked = ::openAMO,
            onLearnMoreClicked = { link, addon ->
                binding?.root?.openLearnMoreLink(link, addon)
            },
        )

        val recyclerView = binding?.addOnsList
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        val shouldRefresh = adapter != null


        logger.info("AddonsManagementFragment should refresh? $shouldRefresh")

        // If the fragment was launched to install an "external" add-on from AMO, we deactivate
        // the cache to get the most up-to-date list of add-ons to match against.
        lifecycleScope.launch(IO) {
            try {
                logger.info("AddonsManagementFragment asking for addons")

                val addonManager = requireContext().components.addonManager
                addons = addonManager.getAddons()

                // AddonManager intentionally hides built-in extensions. Append LinguaSurf built-in extension.
                val builtInExt = findLinguaSurfBuiltInExtension()
                builtInExt?.let {
                    if (addons.none { it.id == builtInExt.id }) {
                        val installedState = addonManager.toInstalledState(builtInExt)
                        addons = addons + Addon.newFromWebExtension(builtInExt, installedState)
                    }
                }
                if (builtInExt == null) {
                    logger.warn("LinguaSurf built-in extension not found in WebExtensionSupport/runtime")
                } else {
                    logger.info("LinguaSurf built-in extension found: ${builtInExt.id}")
                }
                lifecycleScope.launch(Dispatchers.Main) {
                    runIfFragmentIsAttached {
                        if (!shouldRefresh) {
                            adapter = AddonsManagerAdapter(
                                addonsManagerDelegate = managementView,
                                addons = addons,
                                style = createAddonStyle(requireContext()),
                                store = requireComponents.core.store,
                            )
                        }
                        binding?.addOnsProgressBar?.isVisible = false
                        binding?.addOnsEmptyMessage?.isVisible = false

                        recyclerView?.adapter = adapter
                        recyclerView?.accessibilityDelegate = object : View.AccessibilityDelegate() {
                            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                                super.onInitializeAccessibilityNodeInfo(host, info)

                                adapter?.let {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        info.collectionInfo = AccessibilityNodeInfo.CollectionInfo(
                                            it.itemCount,
                                            1,
                                            false,
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        info.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
                                            it.itemCount,
                                            1,
                                            false,
                                        )
                                    }
                                }
                            }
                        }

                        if (shouldRefresh) {
                            adapter?.updateAddons(addons)
                        }

                        handleOpenAddonRouteIfNeeded(addons)
                    }
                }
            } catch (e: AddonManagerException) {
                lifecycleScope.launch(Dispatchers.Main) {
                    runIfFragmentIsAttached {
                        binding?.let {
                            showSnackBar(
                                it.root,
                                getString(addonsR.string.mozac_feature_addons_failed_to_query_extensions),
                            )
                        }
                        binding?.addOnsProgressBar?.isVisible = false
                        binding?.addOnsEmptyMessage?.isVisible = true
                    }
                }
            }
        }
    }

    private fun handleOpenAddonRouteIfNeeded(allAddons: List<Addon>) {
        if (hasHandledOpenAddonRoute) {
            return
        }
        hasHandledOpenAddonRoute = true
        val openAddonTarget = requestedOpenAddonTarget()
        val openAddonId = requestedOpenAddonId()

        when (openAddonTarget) {
            TARGET_MANAGER -> Unit
            TARGET_LIST -> showInstalledAddonIdList(allAddons)
            TARGET_DETAILS, TARGET_SETTINGS, TARGET_OPTIONS -> openAddonByIdTarget(
                allAddons = allAddons,
                addonId = openAddonId,
                target = openAddonTarget,
            )
            else -> {
                logger.warn("Unknown addon deep link target: $openAddonTarget")
            }
        }

        clearOpenAddonRouteExtras()
    }

    private fun showInstalledAddonIdList(allAddons: List<Addon>) {
        val installedAddons = allAddons.filter { it.isInstalled() }
        if (installedAddons.isEmpty()) {
            binding?.let {
                showSnackBar(it.root, getString(R.string.addons_deeplink_no_installed_extensions))
            }
            return
        }

        val lines = installedAddons.joinToString(separator = "\n") { addon ->
            val name = addon.translateName(requireContext())
            "$name -> ${addon.id}"
        }
        logger.info("Installed extensions (name -> id)\n$lines")

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.addons_deeplink_installed_extensions_title)
            .setMessage(lines)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun openAddonByIdTarget(
        allAddons: List<Addon>,
        addonId: String?,
        target: String,
    ) {
        val requestedId = addonId?.trim().orEmpty()
        if (requestedId.isEmpty()) {
            binding?.let {
                showSnackBar(it.root, getString(R.string.addons_deeplink_requires_extension_id))
            }
            return
        }

        val addon = allAddons.firstOrNull { it.isInstalled() && addonIdsMatch(it.id, requestedId) }
        if (addon == null) {
            binding?.let {
                showSnackBar(
                    it.root,
                    getString(R.string.addons_deeplink_extension_not_found, requestedId),
                )
            }
            return
        }

        val openTarget = when (target) {
            TARGET_SETTINGS -> TARGET_SETTINGS
            TARGET_OPTIONS -> TARGET_OPTIONS
            else -> TARGET_DETAILS
        }

        val direction = NavGraphDirections.actionGlobalToInstalledAddonDetailsFragment(
            addon = addon,
            openTarget = openTarget,
        )
        findNavController().navigate(direction)
    }

    private fun addonIdsMatch(left: String, right: String): Boolean {
        return normalizeAddonId(left) == normalizeAddonId(right)
    }

    private fun normalizeAddonId(id: String): String {
        return id.trim().removePrefix("{").removeSuffix("}").lowercase(Locale.ROOT)
    }

    private fun requestedOpenAddonTarget(): String {
        val targetFromIntent = activity?.intent?.getStringExtra(EXTRA_OPEN_ADDON_TARGET)
        val rawTarget = targetFromIntent ?: args.openAddonTarget
        return rawTarget.lowercase(Locale.ROOT)
    }

    private fun requestedOpenAddonId(): String? {
        val idFromIntent = activity?.intent?.getStringExtra(EXTRA_OPEN_ADDON_ID)
        return idFromIntent ?: args.openAddonId
    }

    private fun clearOpenAddonRouteExtras() {
        activity?.intent?.removeExtra(EXTRA_OPEN_ADDON_ID)
        activity?.intent?.removeExtra(EXTRA_OPEN_ADDON_TARGET)
    }

    private suspend fun findLinguaSurfBuiltInExtension(): WebExtension? {
        val fromSupportMap = WebExtensionSupport.installedExtensions.values.firstOrNull {
            isLinguaSurfBuiltInExtensionId(it.id)
        }
        if (fromSupportMap != null) {
            return fromSupportMap
        }

        // Gecko runtime APIs must be called on the main thread (must have Looper/Handler).
        return withContext(Dispatchers.Main) {
            val runtime = requireContext().components.core.engine as? WebExtensionRuntime ?: return@withContext null
            val fromRuntime = listLinguaSurfBuiltInFromRuntime(runtime)
            if (fromRuntime != null) {
                return@withContext fromRuntime
            }
            logger.info("LinguaSurf built-in extension missing in runtime list, trying installBuiltInWebExtension")
            installLinguaSurfBuiltInInRuntime(runtime)
        }
    }

    private fun isLinguaSurfBuiltInExtensionId(id: String): Boolean {
        val normalized = id.trim().removePrefix("{").removeSuffix("}").lowercase(Locale.ROOT)
        return normalized == LINGUASURF_BUILTIN_EXTENSION_ID_NO_BRACES
    }

    private suspend fun listLinguaSurfBuiltInFromRuntime(runtime: WebExtensionRuntime): WebExtension? {
        return suspendCancellableCoroutine { continuation ->
            try {
                runtime.listInstalledWebExtensions(
                    onSuccess = { extensions ->
                        val builtIn = extensions.firstOrNull { isLinguaSurfBuiltInExtensionId(it.id) }
                        if (continuation.isActive) {
                            continuation.resume(builtIn)
                        }
                    },
                    onError = {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    },
                )
            } catch (_: Throwable) {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    private suspend fun installLinguaSurfBuiltInInRuntime(runtime: WebExtensionRuntime): WebExtension? {
        val attempts = listOf(
            LINGUASURF_BUILTIN_EXTENSION_ID to LINGUASURF_BUILTIN_EXTENSION_DIR_URL,
            LINGUASURF_BUILTIN_EXTENSION_ID_NO_BRACES to LINGUASURF_BUILTIN_EXTENSION_DIR_URL,
        )

        for ((id, url) in attempts) {
            logger.info("LinguaSurf install attempt: id=$id, url=$url")
            val installed = installBuiltInAttempt(runtime, id, url)
            if (installed != null) {
                logger.info("LinguaSurf install attempt succeeded: installedId=${installed.id}")
                return installed
            }
        }

        return listLinguaSurfBuiltInFromRuntime(runtime)
    }

    private suspend fun installBuiltInAttempt(
        runtime: WebExtensionRuntime,
        id: String,
        url: String,
    ): WebExtension? {
        return suspendCancellableCoroutine { continuation ->
            try {
                runtime.installBuiltInWebExtension(
                    id = id,
                    url = url,
                    onSuccess = { extension ->
                        if (continuation.isActive) {
                            continuation.resume(extension)
                        }
                    },
                    onError = { throwable ->
                        logger.warn(
                            "LinguaSurf install attempt failed: id=$id, url=$url, " +
                                "error=${throwable::class.java.simpleName}: ${throwable.message}",
                        )
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    },
                )
            } catch (throwable: Throwable) {
                logger.warn(
                    "LinguaSurf install attempt threw: id=$id, url=$url, " +
                        "error=${throwable::class.java.simpleName}: ${throwable.message}",
                )
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    private fun createAddonStyle(context: Context): AddonsManagerAdapter.Style {
        val sectionsTypeFace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Typeface.create(Typeface.DEFAULT, FONT_WEIGHT_MEDIUM, false)
        } else {
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        return AddonsManagerAdapter.Style(
            sectionsTextColor = ThemeManager.resolveAttribute(R.attr.textPrimary, context),
            addonNameTextColor = ThemeManager.resolveAttribute(R.attr.textPrimary, context),
            addonSummaryTextColor = ThemeManager.resolveAttribute(R.attr.textSecondary, context),
            sectionsTypeFace = sectionsTypeFace,
            addonAllowPrivateBrowsingLabelDrawableRes = R.drawable.ic_add_on_private_browsing_label,
        )
    }

    @VisibleForTesting
    internal fun provideAddonManager(): AddonManager {
        return requireContext().components.addonManager
    }

    internal fun installAddon(addon: Addon) {
        binding?.addonProgressOverlay?.overlayCardView?.visibility = View.VISIBLE

        if (requireComponents.appStore.state.mode.isPrivate) {
            binding?.addonProgressOverlay?.overlayCardView?.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.fx_mobile_private_layer_color_3,
                ),
            )
        }

        val installOperation = provideAddonManager().installAddon(
            url = addon.downloadUrl,
            installationMethod = InstallationMethod.MANAGER,
            onSuccess = {
                runIfFragmentIsAttached {
                    adapter?.updateAddon(it)
                    binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                }
            },
            onError = { _ ->
                binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
            },
        )
        binding?.addonProgressOverlay?.cancelButton?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {
                val safeBinding = binding
                // Hide the installation progress overlay once cancellation is successful.
                if (installOperation.cancel().await()) {
                    safeBinding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                }
            }
        }
    }

    private fun openAMO() {
        findNavController().openToBrowser()
        requireComponents.useCases.fenixBrowserUseCases.loadUrlOrSearch(
            searchTermOrURL = AMO_HOMEPAGE_FOR_ANDROID,
            newTab = true,
        )
    }
}
