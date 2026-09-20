package com.example

import android.app.Application
import android.system.Os

class CalculatorApp : Application() {
    companion object {
        init {
            try {
                Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
                Os.setenv("GALLIUM_DRIVER", "softpipe", true)
                Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "softpipe", true)
            } catch (_: Throwable) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
            Os.setenv("GALLIUM_DRIVER", "softpipe", true)
            Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "softpipe", true)
        } catch (_: Throwable) {}
    }
}
