package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

@Composable
internal fun EventPassSharePass(
    runtime: EventPassShareRuntime,
    assets: EventPassShareAssets,
    layoutScale: Float,
    pixelScale: Float,
    onFlipCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pass = EventPassShareTheme.pass
    fun dp(value: Float) = (value * layoutScale).dp
    val backVisible = runtime.backVisible.value
    val foilShader = rememberEventPassShareFoilShader()

    Box(
        modifier
            .size(dp(pass.width), dp(pass.height))
            .graphicsLayer {
                runtime.drawRevision.intValue
                val frame = runtime.frame.get()
                scaleX = frame.pressScale.toFloat()
                scaleY = frame.pressScale.toFloat()
                transformOrigin = TransformOrigin.Center
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = if (backVisible) {
                    "Event pass details. Ticket COMMON-0718. Tap to return to poster."
                } else {
                    "Event poster. Tap to show event pass and QR code."
                }
                onClick(label = if (backVisible) "Show event poster" else "Show event pass") {
                    runtime.toggleFace()
                    onFlipCommit()
                    true
                }
            },
    ) {
        PassFront(
            assets = assets,
            runtime = runtime,
            layoutScale = layoutScale,
            pixelScale = pixelScale,
            foilShader = foilShader,
            modifier = Modifier.graphicsLayer {
                runtime.drawRevision.intValue
                val frame = runtime.frame.get()
                val flip = if (frame.quietCrossfade) 0.0 else frame.flipAngle
                rotationX = frame.tiltX.toFloat()
                rotationY = (frame.tiltY + flip).toFloat()
                alpha = max(0.004f, frame.frontOpacity.toFloat())
                cameraDistance = 650f * pixelScale
                transformOrigin = TransformOrigin.Center
            },
        )
        PassBack(
            assets = assets,
            layoutScale = layoutScale,
            modifier = Modifier.graphicsLayer {
                runtime.drawRevision.intValue
                val frame = runtime.frame.get()
                val flip = if (frame.quietCrossfade) 0.0 else frame.flipAngle - 180.0
                rotationX = frame.tiltX.toFloat()
                rotationY = (frame.tiltY + flip).toFloat()
                alpha = frame.backOpacity.toFloat()
                cameraDistance = 650f * pixelScale
                transformOrigin = TransformOrigin.Center
            },
        )
    }
}

@Composable
private fun PassFront(
    assets: EventPassShareAssets,
    runtime: EventPassShareRuntime,
    layoutScale: Float,
    pixelScale: Float,
    foilShader: EventPassShareFoilShader?,
    modifier: Modifier,
) {
    val pass = EventPassShareTheme.pass
    fun dp(value: Float) = (value * layoutScale).dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    fun sp(value: Float) = (value * layoutScale / density.fontScale).sp
    val shape = RoundedCornerShape(dp(pass.radius))
    val posterScale = layoutScale * pass.poster.size / 251f
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = androidx.compose.ui.text.TextStyle(
        fontSize = sp(pass.titleFontSize),
        lineHeight = sp(pass.titleLineHeight),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = sp(-0.55f),
    )
    val measuredTitle = textMeasurer.measure(EventPassShareContent.Title, titleStyle, maxLines = 1).size.width
    val titleAvailable = with(density) { dp(pass.titleWidth).toPx() }
    val titleScale = if (measuredTitle > 0) {
        (titleAvailable / measuredTitle).coerceIn(0.78f, 1f)
    } else {
        1f
    }
    Box(modifier.fillMaxSize().clip(shape).background(Color(56, 58, 64, 102)).border(dp(1f), Color.White.copy(alpha = 0.18f), shape)) {
        EventPassSharePoster(
            assets,
            posterScale,
            modifier = Modifier.offset(dp(pass.poster.left), dp(pass.poster.top)),
        )
        if (foilShader != null) {
            EventPassSharePoster(
                assets,
                posterScale,
                modifier = Modifier
                    .offset(dp(pass.poster.left), dp(pass.poster.top))
                    .graphicsLayer {
                        runtime.drawRevision.intValue
                        foilShader.update(runtime.frame.get(), pixelScale, posterSize = pass.poster.size)
                        renderEffect = foilShader.effect
                    },
            )
        } else {
            EventPassShareFoilFallback(
                runtime = runtime,
                pixelScale = pixelScale * pass.poster.size / 251f,
                modifier = Modifier
                    .offset(dp(pass.poster.left), dp(pass.poster.top))
                    .size(dp(pass.poster.size)),
            )
        }
        Text(
            EventPassShareContent.Title,
            color = Color.White,
            fontSize = sp(pass.titleFontSize * titleScale),
            lineHeight = sp(pass.titleLineHeight * titleScale),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = sp(-0.55f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.offset(dp(pass.titleLeft), dp(pass.titleTop)).width(dp(pass.titleWidth)),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.92f))) {
                    append("Jul 18  ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.62f))) {
                    append("Los Angeles")
                }
            },
            fontSize = sp(pass.dateFontSize),
            lineHeight = sp(pass.dateLineHeight),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.offset(dp(pass.dateLeft), dp(pass.dateTop)).width(dp(pass.dateWidth)),
        )
    }
}

@Composable
private fun PassBack(
    assets: EventPassShareAssets,
    layoutScale: Float,
    modifier: Modifier,
) {
    val pass = EventPassShareTheme.pass
    fun dp(value: Float) = (value * layoutScale).dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    fun sp(value: Float) = (value * layoutScale / density.fontScale).sp
    val shape = RoundedCornerShape(dp(pass.radius))
    Box(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color(56, 58, 64, 102))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.04f), Color.Transparent)))
            .border(dp(1f), Color.White.copy(alpha = 0.18f), shape),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(top = dp(25f)),
        ) {
            Text(
                "COMMUNITY PASS",
                color = Color.White.copy(alpha = 0.80f),
                fontSize = sp(10f),
                lineHeight = sp(13f),
                fontWeight = FontWeight.Bold,
                letterSpacing = sp(1.6f),
            )
            Spacer(Modifier.height(dp(11f)))
            Image(
                bitmap = assets.qrCode,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .size(dp(184f))
                    .clip(RoundedCornerShape(dp(13f)))
                    .background(Color(0xFFFAF9F7))
                    .padding(dp(9f)),
            )
            Spacer(Modifier.height(dp(11f)))
            Text(
                EventPassShareContent.Title,
                color = Color.White,
                fontSize = sp(17f),
                lineHeight = sp(21f),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(dp(11f)))
            Text(
                EventPassShareContent.Ticket,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = sp(11f),
                lineHeight = sp(14f),
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = sp(1f),
            )
            Spacer(Modifier.height(dp(11f)))
            Text(
                "↩  Tap to return",
                color = Color.White.copy(alpha = 0.58f),
                fontSize = sp(10f),
                lineHeight = sp(13f),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
