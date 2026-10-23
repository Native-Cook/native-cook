package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Tone
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

/** Full-screen showcase; autoPlay is permanently disabled by the first touch. */
@Composable
fun ReferralRewardCardDemo() {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    DisposableEffect(window, view) {
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = bars?.isAppearanceLightStatusBars
        val navigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            if (status != null) bars.isAppearanceLightStatusBars = status
            if (navigation != null) bars.isAppearanceLightNavigationBars = navigation
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Tone.page)
            .windowInsetsPadding(
                WindowInsets.systemBars
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Vertical),
            ),
    ) {
        val screenWidth = maxWidth.value
        val width = 2 * floor(min(screenWidth - Size.cardMargin * 2, Size.cardMax.toFloat()) / 2)
        val density = LocalDensity.current.density
        var snap by remember { mutableFloatStateOf(0f) }
        Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .onPlaced {
                        val y = it.positionInParent().y / density
                        snap = round(y) - y
                    }.graphicsLayer { translationY = snap * density },
            ) {
                ReferralRewardCard(
                    width = width.dp,
                    autoPlay = true,
                    modifier = Modifier.padding(start = floor((screenWidth - width) / 2).dp),
                )
                BasicText(
                    stringResource(R.string.referralrewardcard_hint),
                    Modifier.fillMaxWidth().padding(top = 24.dp, start = 24.dp, end = 24.dp),
                    style =
                        TextStyle(
                            color = Color.White.copy(alpha = Tone.pageHintAlpha),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle =
                                LineHeightStyle(
                                    LineHeightStyle.Alignment.Center,
                                    LineHeightStyle.Trim.None,
                                ),
                        ),
                )
            }
        }
    }
}
