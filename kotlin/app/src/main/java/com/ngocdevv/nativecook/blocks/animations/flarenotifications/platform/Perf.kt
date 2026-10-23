package com.ngocdevv.nativecook.blocks.animations.flarenotifications.platform

import android.os.Trace

/**
 * Perfetto instrumentation (app sections are recorded when the trace config
 * lists the app's package under `atrace_apps`). Near free when not tracing.
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
}
