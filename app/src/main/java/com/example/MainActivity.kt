package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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

class MainActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var adView: AdView? = null
    private var interstitialAd: InterstitialAd? = null

    private var pendingPermissionRequest: PermissionRequest? = null

    private val speechRecognizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0].replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
                    webView?.evaluateJavascript("if(typeof window.onVoiceSearchResult === 'function') { window.onVoiceSearchResult('$text'); }", null)
                }
            } else {
                webView?.evaluateJavascript("if(typeof window.onVoiceSearchDismissed === 'function') { window.onVoiceSearchDismissed(); }", null)
            }
        }

    fun startNativeVoiceSearch() {
        try {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                putExtra(android.speech.RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "আপনার গাণিতিক হিসাব বা প্রশ্নটি বলুন...")
            }
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.w("VoiceSearch", "Native voice recognition not available, fallback to web", e)
            webView?.evaluateJavascript("if(typeof window.fallbackWebSpeech === 'function') { window.fallbackWebSpeech(); }", null)
        }
    }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pendingPermissionRequest?.grant(pendingPermissionRequest?.resources)
            } else {
                pendingPermissionRequest?.deny()
            }
            pendingPermissionRequest = null
        }

    fun requestCamera() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Google Mobile Ads SDK
        try {
            MobileAds.initialize(this) { status ->
                Log.d("AdMob", "AdMob SDK Initialized: $status")
                runOnUiThread {
                    loadInterstitialAd()
                }
            }
        } catch (e: Exception) {
            Log.e("AdMob", "Failed to initialize AdMob SDK", e)
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F8FAFC"))
        }

        val wv = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )

            // Use software rendering to avoid Mesa DRM rendernode error on container/emulator environments
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            @Suppress("DEPRECATION")
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                mediaPlaybackRequiresUserGesture = false
            }

            addJavascriptInterface(WebAppInterface(this@MainActivity, this), "AndroidBridge")

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    val hasCameraPermission = checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasCameraPermission) {
                        request.grant(request.resources)
                    } else {
                        pendingPermissionRequest = request
                        requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    view?.post {
                        view.loadUrl("file:///android_asset/calculator_app.html")
                    }
                    return true
                }
            }

            loadUrl("file:///android_asset/calculator_app.html")
        }
        webView = wv
        rootLayout.addView(wv)

        // Google AdMob Banner Ad Container
        val adContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        try {
            val bannerAd = AdView(this).apply {
                // Official AdMob test banner ad unit ID
                adUnitId = "ca-app-pub-5034627477793952/9072198864"
                setAdSize(AdSize.BANNER)
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d("AdMob", "Banner Ad Loaded successfully")
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w("AdMob", "Banner Ad Failed to load: ${error.message}")
                    }
                }
            }
            adView = bannerAd
            adContainer.addView(bannerAd)
            val adRequest = AdRequest.Builder().build()
            bannerAd.loadAd(adRequest)
        } catch (e: Exception) {
            Log.e("AdMob", "Error creating banner AdView", e)
        }

        rootLayout.addView(adContainer)
        setContentView(rootLayout)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView?.canGoBack() == true) {
                    webView?.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    fun loadInterstitialAd() {
        // Official AdMob test interstitial ad unit ID
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            "ca-app-pub-5034627477793952/5132953856",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d("AdMob", "Interstitial Ad Loaded successfully")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.w("AdMob", "Interstitial Ad Failed to load: ${error.message}")
                }
            }
        )
    }

    fun showInterstitial() {
        runOnUiThread {
            if (interstitialAd != null) {
                interstitialAd?.show(this)
                interstitialAd = null
                loadInterstitialAd()
            } else {
                Log.d("AdMob", "Interstitial Ad not ready yet, loading new one")
                loadInterstitialAd()
            }
        }
    }

    fun showInterstitialForQR(): Boolean {
        val ad = interstitialAd
        if (ad != null) {
            runOnUiThread {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d("AdMob", "QR Interstitial Ad dismissed by user")
                        interstitialAd = null
                        loadInterstitialAd()
                        notifyQRAdCompleted(true)
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.w("AdMob", "QR Interstitial Ad failed to show: ${adError.message}")
                        interstitialAd = null
                        loadInterstitialAd()
                        notifyQRAdCompleted(false)
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("AdMob", "QR Interstitial Ad displayed successfully")
                    }
                }
                try {
                    ad.show(this)
                } catch (e: Exception) {
                    Log.e("AdMob", "Error displaying interstitial", e)
                    interstitialAd = null
                    loadInterstitialAd()
                    notifyQRAdCompleted(false)
                }
            }
            return true
        } else {
            Log.d("AdMob", "Interstitial Ad not loaded yet for QR")
            runOnUiThread {
                loadInterstitialAd()
            }
            return false
        }
    }

    private fun notifyQRAdCompleted(success: Boolean) {
        runOnUiThread {
            webView?.evaluateJavascript("if (typeof window.onAdCompletedForQR === 'function') { window.onAdCompletedForQR($success); }", null)
        }
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
    }

    override fun onDestroy() {
        adView?.destroy()
        adView = null
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}

class WebAppInterface(private val activity: Activity?, private val webView: WebView) {
    @JavascriptInterface
    fun printPage() {
        activity?.runOnUiThread {
            try {
                val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("Calculator_Print")
                printManager?.print("Calculator_QR_Code", printAdapter, PrintAttributes.Builder().build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JavascriptInterface
    fun showInterstitialAd() {
        if (activity is MainActivity) {
            activity.showInterstitial()
        }
    }

    @JavascriptInterface
    fun showQRGenerationAd(): Boolean {
        if (activity is MainActivity) {
            return activity.showInterstitialForQR()
        }
        return false
    }

    @JavascriptInterface
    fun isAdMobSupported(): Boolean {
        return true
    }

    @JavascriptInterface
    fun shareApp(title: String, text: String) {
        activity?.runOnUiThread {
            try {
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TITLE, title)
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                val shareIntent = android.content.Intent.createChooser(sendIntent, title)
                activity.startActivity(shareIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JavascriptInterface
    fun rateOnPlayStore() {
        activity?.runOnUiThread {
            try {
                val packageName = activity.packageName
                val uri = android.net.Uri.parse("market://details?id=$packageName")
                val goToMarket = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                activity.startActivity(goToMarket)
            } catch (e: Exception) {
                try {
                    val packageName = activity?.packageName ?: "com.example"
                    val uri = android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    val goToMarket = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                    activity?.startActivity(goToMarket)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        }
    }

    @JavascriptInterface
    fun requestCameraPermission() {
        activity?.runOnUiThread {
            if (activity is MainActivity) {
                activity.requestCamera()
            }
        }
    }

    @JavascriptInterface
    fun startVoiceRecognition() {
        activity?.runOnUiThread {
            if (activity is MainActivity) {
                activity.startNativeVoiceSearch()
            }
        }
    }
}

