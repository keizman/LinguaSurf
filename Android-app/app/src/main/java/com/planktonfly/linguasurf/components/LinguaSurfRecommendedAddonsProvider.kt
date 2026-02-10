package com.planktonfly.linguasurf.components

import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonsProvider

/**
 * Local lightweight add-ons provider for LinguaSurf.
 *
 * This avoids AMO collection lookups on every open of the add-ons screen.
 */
class LinguaSurfRecommendedAddonsProvider : AddonsProvider {
    override suspend fun getFeaturedAddons(
        allowCache: Boolean,
        readTimeoutInSeconds: Long?,
        language: String?,
    ): List<Addon> {
        return listOf(UBLOCK_ORIGIN_ADDON)
    }

    private companion object {
        private const val UBLOCK_ID = "uBlock0@raymondhill.net"
        private const val UBLOCK_DOWNLOAD_URL =
            "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
        private const val UBLOCK_DETAIL_URL = "https://addons.mozilla.org/en-US/firefox/addon/ublock-origin/"
        private const val UBLOCK_HOMEPAGE_URL = "https://github.com/gorhill/uBlock#ublock-origin"
        private const val UBLOCK_AUTHOR_URL = "https://addons.mozilla.org/en-US/firefox/user/11423598/"
        private const val DEFAULT_LOCALE = "en-us"

        private val UBLOCK_ORIGIN_ADDON = Addon(
            id = UBLOCK_ID,
            author = Addon.Author(
                name = "Raymond Hill",
                url = UBLOCK_AUTHOR_URL,
            ),
            downloadUrl = UBLOCK_DOWNLOAD_URL,
            version = "latest",
            translatableName = mapOf(DEFAULT_LOCALE to "uBlock Origin"),
            translatableDescription = mapOf(
                DEFAULT_LOCALE to "Efficient wide-spectrum content blocker.",
            ),
            translatableSummary = mapOf(
                DEFAULT_LOCALE to "Block ads, trackers and malicious domains.",
            ),
            homepageUrl = UBLOCK_HOMEPAGE_URL,
            detailUrl = UBLOCK_DETAIL_URL,
            defaultLocale = DEFAULT_LOCALE,
        )
    }
}
