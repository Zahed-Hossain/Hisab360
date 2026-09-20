package com.example

import android.app.Application
import android.system.Os

class CalculatorApp : Application() {
    companion object {
        init {
            try {
                Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
                Os.setenv("GALLIUM_DRIVER", "llvmpipe", true)
                Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "llvmpipe", true)
                Os.setenv("MESA_GL_VERSION_OVERRIDE", "3.0", true)
                Os.setenv("MESA_DEBUG", "0", true)
                Os.setenv("LIBGL_DEBUG", "quiet", true)
            } catch (_: Throwable) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
            Os.setenv("GALLIUM_DRIVER", "llvmpipe", true)
            Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "llvmpipe", true)
            Os.setenv("MESA_GL_VERSION_OVERRIDE", "3.0", true)
            Os.setenv("MESA_DEBUG", "0", true)
            Os.setenv("LIBGL_DEBUG", "quiet", true)
        } catch (_: Throwable) {}

        // Sanitize stale or corrupted Chromium WebView Code Cache to prevent simple_file_enumerator errors
        try {
            val codeCacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (codeCacheDir.exists()) {
                codeCacheDir.listFiles()?.forEach { file ->
                    try {
                        if (!file.canRead() || file.length() == 0L) {
                            file.delete()
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }
}
