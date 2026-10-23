package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import java.util.Locale

/** The reference chooses Vietnamese only when the first preferred language is Vietnamese. */
@Composable
@SuppressLint("AppBundleLocaleChanges") // Only selects base English; Vietnamese follows the device locale.
internal fun emberString(@StringRes id: Int, vararg args: Any): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val currentResources = LocalResources.current
    val resources = remember(context, configuration, currentResources) {
        if (configuration.locales[0].language == "vi") {
            currentResources
        } else {
            val config = Configuration(configuration)
            config.setLocale(Locale.ENGLISH)
            context.createConfigurationContext(config).resources
        }
    }
    return resources.getString(id, *args)
}
