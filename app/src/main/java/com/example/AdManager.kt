package com.example

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized Google AdMob Manager.
 *
 * Handles:
 * - Centralized Ad Unit configuration via [AdConfig]
 * - Test mode vs Production mode isolation
 * - Mobile Ads SDK background initialization with duplicate init guard
 * - Safe banner ad creation and lifecycle management
 * - Safe interstitial ad preloading and non-blocking display
 * - Robust crash prevention (no app crashes on ad load failure, timeout, or no-fill)
 * - Memory leak prevention (clears callbacks and weak references)
 * - Prepared architecture for privacy/consent flow
 */
object AdManager {
    private const val TAG = "AdManager"

    private val isInitialized = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)
    private val bgExecutor = Executors.newSingleThreadExecutor()

    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false

    /**
     * Initializes Google Mobile Ads SDK safely.
     * Prevents duplicate initialization and handles errors gracefully without blocking the UI thread.
     */
    fun initialize(context: Context, onInitComplete: (() -> Unit)? = null) {
        if (isInitialized.get()) {
            AppLog.d(TAG) { "AdMob SDK already initialized." }
            onInitComplete?.invoke()
            return
        }

        if (isInitializing.getAndSet(true)) {
            AppLog.d(TAG) { "AdMob SDK initialization already in progress." }
            return
        }

        val appContext = context.applicationContext
        bgExecutor.execute {
            try {
                AppLog.d(TAG) { "Initializing MobileAds in background (TestMode: ${AdConfig.isTestMode()})..." }
                MobileAds.initialize(appContext) { status ->
                    isInitialized.set(true)
                    isInitializing.set(false)
                    AppLog.d(TAG) { "AdMob SDK Initialized successfully. Status: $status" }
                    onInitComplete?.invoke()
                }
            } catch (e: Exception) {
                isInitializing.set(false)
                AppLog.e(TAG, { "Failed to initialize AdMob SDK" }, e)
                // App must continue functioning normally on SDK init failure
                onInitComplete?.invoke()
            }
        }
    }

    /**
     * Creates a standard Banner AdView configured with the current environment's Ad Unit ID.
     * Returns null if creation fails, ensuring caller doesn't crash.
     */
    fun createBannerAd(
        context: Context,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((LoadAdError) -> Unit)? = null
    ): AdView? {
        return try {
            val bannerId = AdConfig.getBannerAdUnitId()
            AppLog.d(TAG) { "Creating Banner AdView with adUnitId: $bannerId" }

            val adView = AdView(context).apply {
                adUnitId = bannerId
                setAdSize(AdSize.BANNER)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        AppLog.d(TAG) { "Banner ad loaded successfully." }
                        onLoaded?.invoke()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        AppLog.w(TAG, { "Banner ad failed to load: [${error.code}] ${error.message}" })
                        onFailed?.invoke(error)
                    }

                    override fun onAdOpened() {
                        AppLog.d(TAG) { "Banner ad clicked/opened." }
                    }

                    override fun onAdClosed() {
                        AppLog.d(TAG) { "Banner ad closed." }
                    }
                }
            }

            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
            adView
        } catch (e: Exception) {
            AppLog.e(TAG, { "Error creating Banner AdView" }, e)
            null
        }
    }

    /**
     * Preloads an Interstitial Ad in the background.
     * Never blocks execution, never crashes on network or SDK errors.
     */
    fun loadInterstitial(context: Context) {
        if (interstitialAd != null) {
            AppLog.d(TAG) { "Interstitial ad is already cached and ready." }
            return
        }

        if (isInterstitialLoading) {
            AppLog.d(TAG) { "Interstitial ad is already loading." }
            return
        }

        isInterstitialLoading = true
        val interstitialId = AdConfig.getInterstitialAdUnitId()
        AppLog.d(TAG) { "Loading Interstitial ad with adUnitId: $interstitialId" }

        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                context.applicationContext,
                interstitialId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isInterstitialLoading = false
                        AppLog.d(TAG) { "Interstitial ad loaded successfully." }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                        isInterstitialLoading = false
                        AppLog.w(TAG, { "Interstitial ad failed to load: [${error.code}] ${error.message}" })
                    }
                }
            )
        } catch (e: Exception) {
            isInterstitialLoading = false
            interstitialAd = null
            AppLog.e(TAG, { "Exception while loading Interstitial ad" }, e)
        }
    }

    /**
     * Displays the cached Interstitial ad only at natural user transitions.
     * If no ad is loaded, invokes onDismissed immediately and non-blockingly preloads next ad.
     * The user is NEVER forced to wait.
     */
    fun showInterstitial(activity: Activity, onDismissed: (() -> Unit)? = null) {
        val currentAd = interstitialAd
        if (currentAd == null) {
            AppLog.d(TAG) { "No interstitial ad loaded. Proceeding immediately." }
            loadInterstitial(activity)
            onDismissed?.invoke()
            return
        }

        activity.runOnUiThread {
            try {
                currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        AppLog.d(TAG) { "Interstitial ad dismissed by user." }
                        currentAd.fullScreenContentCallback = null
                        interstitialAd = null
                        loadInterstitial(activity)
                        onDismissed?.invoke()
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        AppLog.w(TAG, { "Interstitial ad failed to show: [${error.code}] ${error.message}" })
                        currentAd.fullScreenContentCallback = null
                        interstitialAd = null
                        loadInterstitial(activity)
                        onDismissed?.invoke()
                    }

                    override fun onAdShowedFullScreenContent() {
                        AppLog.d(TAG) { "Interstitial ad presented." }
                        currentAd.fullScreenContentCallback = null
                        interstitialAd = null
                    }
                }
                currentAd.show(activity)
            } catch (e: Exception) {
                AppLog.e(TAG, { "Error displaying interstitial ad" }, e)
                try {
                    currentAd.fullScreenContentCallback = null
                } catch (_: Exception) {}
                interstitialAd = null
                loadInterstitial(activity)
                onDismissed?.invoke()
            }
        }
    }

    /**
     * Checks if an interstitial ad is currently loaded and ready to present.
     */
    fun isInterstitialReady(): Boolean = interstitialAd != null

    /**
     * Placeholder architecture for Google User Messaging Platform (UMP) / Consent.
     * Ready for CMP/GDPR/CCPA consent gathering when production credentials require it.
     */
    fun requestConsentAndInit(activity: Activity, onComplete: () -> Unit) {
        initialize(activity) {
            loadInterstitial(activity)
            onComplete()
        }
    }
}
