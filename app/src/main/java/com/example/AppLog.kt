package com.example

import android.util.Log

/**
 * Production-safe logging utility.
 * Suppresses debug and verbose logs in release builds to improve performance,
 * reduce CPU overhead, and avoid exposing internal diagnostics.
 */
object AppLog {
    inline fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message())
        }
    }

    inline fun i(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message())
        }
    }

    inline fun w(tag: String, message: () -> String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.w(tag, message(), throwable)
            } else {
                Log.w(tag, message())
            }
        }
    }

    inline fun e(tag: String, message: () -> String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e(tag, message(), throwable)
            } else {
                Log.e(tag, message())
            }
        }
    }
}
