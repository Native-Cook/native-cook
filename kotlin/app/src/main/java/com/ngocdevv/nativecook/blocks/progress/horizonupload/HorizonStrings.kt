// Block-owned localized copy, resolved once when Android's configuration changes.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import com.ngocdevv.nativecook.R

// This follows the system's primary locale; it does not offer an in-app language switch.
@SuppressLint("AppBundleLocaleChanges")
internal class HorizonStrings(source: Context) {
    // Match i18n.ts: only the first preferred language selects Vietnamese.
    private val context = source.createConfigurationContext(Configuration(source.resources.configuration).apply {
        setLocale(if (locales[0]?.language == "vi") Locale.forLanguageTag("vi") else Locale.ENGLISH)
    })
    val labels = arrayOf(
        context.getString(R.string.horizon_upload_idle), context.getString(R.string.horizon_upload_uploading),
        context.getString(R.string.horizon_upload_paused), context.getString(R.string.horizon_upload_done),
        context.getString(R.string.horizon_upload_stopped),
    )
    val start = context.getString(R.string.horizon_upload_start)
    val pause = context.getString(R.string.horizon_upload_pause)
    val resume = context.getString(R.string.horizon_upload_resume)
    val again = context.getString(R.string.horizon_upload_again)
    val cancel = context.getString(R.string.horizon_upload_cancel)
    val complete = context.getString(R.string.horizon_upload_complete_announcement)
    val canceled = context.getString(R.string.horizon_upload_canceled_announcement)
    val hint = context.getString(R.string.horizon_upload_hint)
    val name = context.getString(R.string.horizon_upload_file_name)
    val size = context.getString(R.string.horizon_upload_file_size)
    fun action(phase: HorizonPhase) = when (phase) {
        HorizonPhase.Idle -> start
        HorizonPhase.Uploading -> pause
        HorizonPhase.Paused -> resume
        else -> again
    }
}
