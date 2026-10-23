// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform

import android.os.Trace

/**
 * Perfetto instrumentation (app sections are recorded when the trace config
 * lists `atrace_apps` with the app's id). Near free when not tracing.
 */
object Perf {
    inline fun <T> section(name: String, block: () -> T): T {
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }

    private val counts = HashMap<String, Long>()

    /** Counts a recomposition of [name] (a counter track `recompose:<name>`). */
    fun recomposed(name: String) {
        val n = (counts[name] ?: 0L) + 1
        counts[name] = n
        Trace.setCounter("recompose:$name", n)
    }

    fun counter(name: String, value: Long) = Trace.setCounter(name, value)
}
