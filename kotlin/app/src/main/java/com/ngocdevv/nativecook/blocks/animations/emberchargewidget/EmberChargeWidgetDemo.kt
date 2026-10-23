package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.E
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.emberTone
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.space
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.type

@Composable
fun EmberChargeWidgetDemo() {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars ?: false
        val navigation = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = status
            controller?.isAppearanceLightNavigationBars = navigation
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color(emberTone.page))
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Vertical),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (Build.VERSION.SDK_INT >= 33) EmberCharge(autoPlay = true)
        else BasicText(
            emberString(R.string.emberchargewidget_unsupported), Modifier.padding(horizontal = space.near.dp),
            style = TextStyle(
                color = Color(emberTone.sub), fontSize = type.hint.sp, lineHeight = E.line.hint.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
