package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.google.android.gms.ads.AdView
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var adView: AdView? = null

    private var pendingPermissionRequest: PermissionRequest? = null
    private var textToSpeech: android.speech.tts.TextToSpeech? = null
    private var isTtsInitializing = false

    private val bgExecutor = Executors.newSingleThreadExecutor()

    private val speechRecognizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\"", "\\\"")
                        .replace("\n", " ")
                    webView?.evaluateJavascript(
                        "if(typeof window.onVoiceSearchResult === 'function') { window.onVoiceSearchResult('$text'); }",
                        null
                    )
                }
            } else {
                webView?.evaluateJavascript(
                    "if(typeof window.onVoiceSearchDismissed === 'function') { window.onVoiceSearchDismissed(); }",
                    null
                )
            }
        }

    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pendingAudioPermissionRequest?.grant(pendingAudioPermissionRequest?.resources)
                launchSpeechRecognizer()
            } else {
                pendingAudioPermissionRequest?.deny()
                val isPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.RECORD_AUDIO)
                if (isPermanentlyDenied) {
                    Toast.makeText(this, "মাইক্রোফোন পারমিশন বন্ধ। সেটিংস থেকে পারমিশন দিন।", Toast.LENGTH_LONG).show()
                }
                webView?.evaluateJavascript(
                    "if(typeof window.onVoiceSearchDismissed === 'function') { window.onVoiceSearchDismissed(); }",
                    null
                )
            }
            pendingAudioPermissionRequest = null
        }

    private var pendingAudioPermissionRequest: PermissionRequest? = null

    private fun launchSpeechRecognizer() {
        try {
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                putExtra(android.speech.RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "আপনার গাণিতিক হিসাব বা প্রশ্নটি বলুন...")
            }
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            AppLog.w("VoiceSearch", { "Native voice recognition not available, fallback to web" }, e)
            webView?.evaluateJavascript("if(typeof window.fallbackWebSpeech === 'function') { window.fallbackWebSpeech(); }", null)
        }
    }

    fun startNativeVoiceSearch() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchSpeechRecognizer()
        } else {
            requestAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    /**
     * Lazy, thread-safe TextToSpeech initializer that does not block startup.
     */
    @Synchronized
    private fun getOrInitTTS(onReady: (android.speech.tts.TextToSpeech) -> Unit) {
        textToSpeech?.let {
            onReady(it)
            return
        }

        if (isTtsInitializing) return
        isTtsInitializing = true

        bgExecutor.execute {
            try {
                val appContext = applicationContext
                val tts = android.speech.tts.TextToSpeech(appContext) { status ->
                    isTtsInitializing = false
                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                        try {
                            textToSpeech?.language = Locale.Builder().setLanguage("bn").setRegion("BD").build()
                        } catch (_: Exception) {}
                        runOnUiThread {
                            textToSpeech?.let { onReady(it) }
                        }
                    }
                }
                textToSpeech = tts
            } catch (e: Exception) {
                isTtsInitializing = false
                AppLog.w("VoiceTTS", { "TTS init failed" }, e)
            }
        }
    }

    fun speakVoice(text: String) {
        if (text.isBlank()) return
        try {
            getOrInitTTS { tts ->
                tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "VoiceResult")
            }
        } catch (e: Exception) {
            AppLog.w("VoiceTTS", { "TTS speak failed" }, e)
        }
    }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pendingPermissionRequest?.grant(pendingPermissionRequest?.resources)
                webView?.evaluateJavascript("if(typeof window.onCameraPermissionGranted === 'function') { window.onCameraPermissionGranted(); }", null)
            } else {
                pendingPermissionRequest?.deny()
                val isPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CAMERA)
                if (isPermanentlyDenied) {
                    Toast.makeText(this, "ক্যামেরা পারমিশন স্থায়ীভাবে বন্ধ। সেটিংস থেকে পারমিশন দিন।", Toast.LENGTH_LONG).show()
                }
                webView?.evaluateJavascript("if(typeof window.onCameraPermissionDenied === 'function') { window.onCameraPermissionDenied($isPermanentlyDenied); }", null)
            }
            pendingPermissionRequest = null
        }

    fun requestCamera() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            webView?.evaluateJavascript("if(typeof window.onCameraPermissionGranted === 'function') { window.onCameraPermissionGranted(); }", null)
        } else {
            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        } catch (_: Exception) {}

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        // Initialize Google Mobile Ads SDK asynchronously in background without blocking UI startup
        AdManager.initialize(applicationContext) {
            AdManager.loadInterstitial(applicationContext)
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

                // Load from cache or asset directly without stale HTTP cache
                cacheMode = WebSettings.LOAD_DEFAULT

                // WebView Security Hardening
                allowFileAccess = true
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                mediaPlaybackRequiresUserGesture = false

                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }

            // Expose minimal, secure JavaScript interfaces with WeakReference to prevent Activity leaks
            val webBridge = WebAppInterface(this@MainActivity, this)
            addJavascriptInterface(webBridge, "AndroidBridge")
            addJavascriptInterface(webBridge, "AndroidInterface")

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    val isCameraResource = request.resources.any {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    }
                    val isAudioResource = request.resources.any {
                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    }

                    if (isCameraResource) {
                        val hasCameraPermission = checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasCameraPermission) {
                            request.grant(request.resources)
                        } else {
                            pendingPermissionRequest = request
                            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    } else if (isAudioResource) {
                        val hasAudioPermission = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasAudioPermission) {
                            request.grant(request.resources)
                        } else {
                            pendingAudioPermissionRequest = request
                            requestAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        request.deny()
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return handleSafeUrlLoading(url)
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url == null) return false
                    return handleSafeUrlLoading(url)
                }

                private fun handleSafeUrlLoading(url: String): Boolean {
                    val uri = Uri.parse(url)
                    val scheme = uri.scheme?.lowercase() ?: return true

                    // 1. Allow internal bundled asset navigation
                    if (scheme == "file" && url.startsWith("file:///android_asset/")) {
                        return false
                    }

                    // 2. Reject hazardous local file or internal scheme access
                    if (scheme == "file" || scheme == "content" || scheme == "javascript") {
                        AppLog.w("WebSecurity", { "Blocked untrusted scheme attempt: $scheme" })
                        return true
                    }

                    // 3. Handle external telephone links
                    if (scheme == "tel") {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, uri)
                            startActivity(intent)
                        } catch (e: Exception) {
                            AppLog.w("WebSecurity", { "Cannot open tel link" }, e)
                        }
                        return true
                    }

                    // 4. Handle Play Store market links
                    if (scheme == "market") {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            startActivity(intent)
                        } catch (e: Exception) {
                            AppLog.w("WebSecurity", { "Cannot open market link" }, e)
                        }
                        return true
                    }

                    // 5. Open trusted external HTTP/HTTPS websites safely in external system browser
                    if (scheme == "http" || scheme == "https") {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            startActivity(intent)
                        } catch (e: Exception) {
                            AppLog.w("WebSecurity", { "Cannot launch external browser for $url" }, e)
                        }
                        return true
                    }

                    // 6. Block all unexpected or arbitrary intent/custom schemes
                    AppLog.w("WebSecurity", { "Rejected untrusted scheme: $scheme" })
                    return true
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    view?.post {
                        try {
                            view.loadUrl("file:///android_asset/index.html")
                        } catch (_: Exception) {}
                    }
                    return true
                }
            }

            // Clear stale disk cache on startup to ensure latest index.html is loaded
            clearCache(true)

            // Always guarantee loading the core application entry point
            loadUrl("file:///android_asset/index.html")
        }
        webView = wv
        rootLayout.addView(wv)

        // Google AdMob Banner Ad Container (bottom dock, never obscuring content)
        val adContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Create Banner AdView using centralized AdManager
        val banner = AdManager.createBannerAd(this)
        if (banner != null) {
            adView = banner
            adContainer.addView(banner)
        }

        rootLayout.addView(adContainer)
        setContentView(rootLayout)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wvRef = webView
                if (wvRef != null) {
                    wvRef.evaluateJavascript(
                        "if(typeof window.handleAndroidBackPressed === 'function') { window.handleAndroidBackPressed(); } else { false; }"
                    ) { result ->
                        val handled = result?.trim()?.equals("true", ignoreCase = true) == true
                        if (!handled) {
                            if (wvRef.canGoBack()) {
                                wvRef.goBack()
                            } else {
                                isEnabled = false
                                onBackPressedDispatcher.onBackPressed()
                                isEnabled = true
                            }
                        }
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
    }

    fun showInterstitial(onDismissed: (() -> Unit)? = null) {
        AdManager.showInterstitial(this, onDismissed)
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
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (_: Exception) {}

        adView?.let { ad ->
            try {
                (ad.parent as? ViewGroup)?.removeView(ad)
                ad.destroy()
            } catch (_: Exception) {}
        }
        adView = null

        webView?.let { wv ->
            try {
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.stopLoading()
                wv.removeJavascriptInterface("AndroidBridge")
                wv.removeJavascriptInterface("AndroidInterface")
                wv.webChromeClient = null
                wv.webViewClient = WebViewClient()
                wv.removeAllViews()
                wv.destroy()
            } catch (_: Exception) {}
        }
        webView = null

        super.onDestroy()
    }
}

/**
 * Memory-safe WebAppInterface using WeakReferences to avoid Activity or WebView leaks.
 */
class WebAppInterface(activity: MainActivity, webView: WebView) {
    private val activityRef = WeakReference(activity)
    private val webViewRef = WeakReference(webView)

    @JavascriptInterface
    fun printPage() {
        val act = activityRef.get() ?: return
        val wv = webViewRef.get() ?: return
        act.runOnUiThread {
            try {
                val printManager = act.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val printAdapter = wv.createPrintDocumentAdapter("Calculator_Print")
                printManager?.print("Calculator_QR_Code", printAdapter, PrintAttributes.Builder().build())
            } catch (e: Exception) {
                AppLog.w("WebAppInterface", { "Print document failed" }, e)
            }
        }
    }

    @JavascriptInterface
    fun showInterstitialAd() {
        val act = activityRef.get() ?: return
        act.showInterstitial()
    }

    @JavascriptInterface
    fun showQRGenerationAd(): Boolean {
        // Ads are not allowed to gate utility generation per Google Play policy
        return false
    }

    @JavascriptInterface
    fun isAdMobSupported(): Boolean {
        return true
    }

    @JavascriptInterface
    fun shareApp(title: String, text: String) {
        val act = activityRef.get() ?: return
        act.runOnUiThread {
            try {
                val safeTitle = title.take(100)
                val safeText = text.take(1000)
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TITLE, safeTitle)
                    putExtra(Intent.EXTRA_TEXT, safeText)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, safeTitle)
                act.startActivity(shareIntent)
            } catch (e: Exception) {
                AppLog.w("WebAppInterface", { "Share app failed" }, e)
            }
        }
    }

    @JavascriptInterface
    fun rateOnPlayStore() {
        val act = activityRef.get() ?: return
        act.runOnUiThread {
            try {
                val packageName = act.packageName
                val uri = Uri.parse("market://details?id=$packageName")
                val goToMarket = Intent(Intent.ACTION_VIEW, uri)
                act.startActivity(goToMarket)
            } catch (e: Exception) {
                try {
                    val packageName = act.packageName ?: "com.example"
                    val uri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    val goToMarket = Intent(Intent.ACTION_VIEW, uri)
                    act.startActivity(goToMarket)
                } catch (e2: Exception) {
                    AppLog.w("WebAppInterface", { "Open Play Store failed" }, e2)
                }
            }
        }
    }

    @JavascriptInterface
    fun requestCameraPermission() {
        val act = activityRef.get() ?: return
        act.runOnUiThread {
            act.requestCamera()
        }
    }

    @JavascriptInterface
    fun startVoiceRecognition() {
        val act = activityRef.get() ?: return
        act.runOnUiThread {
            act.startNativeVoiceSearch()
        }
    }

    @JavascriptInterface
    fun speakText(text: String) {
        val act = activityRef.get() ?: return
        act.runOnUiThread {
            val sanitized = text.take(500)
            act.speakVoice(sanitized)
        }
    }

    @JavascriptInterface
    fun exitApp() {
        val act = activityRef.get() ?: return
        act.runOnUiThread {
            act.finish()
        }
    }
}
