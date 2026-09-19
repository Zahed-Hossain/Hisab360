package com.example

/**
 * Centralized AdMob Configuration.
 *
 * All AdMob IDs (Application ID, Banner, Interstitial, Rewarded)
 * are managed from this single centralized location.
 *
 * Environment Isolation:
 * - DEBUG / DEVELOPMENT: Automatically uses Google's official Android test ad unit IDs.
 * - RELEASE / PRODUCTION: Uses the developer's real AdMob ad unit IDs.
 *
 * NOTE: Never accidentally serves production ads in debug/dev builds.
 */
object AdConfig {

    // =========================================================================
    // 1. Google's Official Test Ad Unit IDs (FOR TESTING & DEVELOPMENT ONLY)
    // =========================================================================
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"

    // =========================================================================
    // 2. Developer's Real Production AdMob IDs (FOR PRODUCTION RELEASE ONLY)
    // =========================================================================
    // Replace with your real AdMob unit IDs when deploying release builds
    const val PROD_APP_ID = "ca-app-pub-5034627477793952~5986870521"
    const val PROD_BANNER_AD_UNIT_ID = "ca-app-pub-5034627477793952/9072198864"
    const val PROD_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5034627477793952/5132953856"
    // Optional Rewarded Ad Unit ID (only activated if a genuine optional reward exists)
    const val PROD_REWARDED_AD_UNIT_ID = ""

    /**
     * Determines whether the app is currently operating in test mode.
     * Evaluates BuildConfig.DEBUG.
     */
    fun isTestMode(): Boolean {
        return BuildConfig.DEBUG
    }

    /**
     * Returns the active AdMob Banner Ad Unit ID based on the environment.
     */
    fun getBannerAdUnitId(): String {
        return if (isTestMode()) {
            TEST_BANNER_AD_UNIT_ID
        } else {
            PROD_BANNER_AD_UNIT_ID
        }
    }

    /**
     * Returns the active AdMob Interstitial Ad Unit ID based on the environment.
     */
    fun getInterstitialAdUnitId(): String {
        return if (isTestMode()) {
            TEST_INTERSTITIAL_AD_UNIT_ID
        } else {
            PROD_INTERSTITIAL_AD_UNIT_ID
        }
    }

    /**
     * Returns the active AdMob Rewarded Ad Unit ID based on the environment.
     */
    fun getRewardedAdUnitId(): String {
        return if (isTestMode()) {
            TEST_REWARDED_AD_UNIT_ID
        } else {
            PROD_REWARDED_AD_UNIT_ID
        }
    }
}
