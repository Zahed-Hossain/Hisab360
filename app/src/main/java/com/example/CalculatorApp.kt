package com.example

import android.app.Application
import java.io.File

class CalculatorApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Cleanly wipe any stale or corrupted Chromium HTTP Cache directory from previous runs
        // to prevent simple_file_enumerator and simple_index_file disk reconstruction errors.
        try {
            val httpCacheDir = File(cacheDir, "WebView/Default/HTTP Cache")
            if (httpCacheDir.exists()) {
                httpCacheDir.deleteRecursively()
            }
        } catch (_: Throwable) {}
    }
}

