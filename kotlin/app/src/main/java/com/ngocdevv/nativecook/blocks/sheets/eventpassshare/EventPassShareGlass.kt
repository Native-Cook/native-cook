package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class EventPassShareGlassKind {
    Clear,
    Prominent,
}

/** Android mirrors the RN fallback glass: tinted material, soft API 31 highlight, and a spectral rim. */
@Composable
internal fun EventPassShareGlassSurface(
    kind: EventPassShareGlassKind,
    radius: Dp,
    layoutScale: Float,
    solid: Boolean = false,
) {
    val shape = RoundedCornerShape(radius)
    val density = LocalDensity.current
    val pixelScale = layoutScale * density.density
    val fill = when {
        solid && kind == EventPassShareGlassKind.Clear -> Color(0xFF2E2D30)
        solid -> Color(0xFFFAFAFA)
        kind == EventPassShareGlassKind.Clear -> Color(30, 32, 36, 117)
        else -> Color(250, 250, 252, 240)
    }
    val rim = if (kind == EventPassShareGlassKind.Clear) {
        Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.50f),
                0.55f to Color(0xFFAEEBFF).copy(alpha = 0.08f),
                1f to Color(0xFFFFC2E6).copy(alpha = 0.22f),
            ),
        )
    } else {
        Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.White,
                0.55f to Color(0xFFBDEFFF).copy(alpha = 0.35f),
                1f to Color(0xFFFFD2EA).copy(alpha = 0.70f),
            ),
        )
    }

    Box(Modifier.fillMaxSize().clip(shape).background(fill)) {
        if (!solid && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurRadius = if (kind == EventPassShareGlassKind.Clear) 12f else 9f
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = BlurEffect(
                            blurRadius * pixelScale,
                            blurRadius * pixelScale,
                            TileMode.Clamp,
                        )
                    },
            ) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (kind == EventPassShareGlassKind.Clear) 0.12f else 0.26f),
                            Color.Transparent,
                            Color(0xFF8FEBD7).copy(alpha = 0.10f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp * layoutScale, rim, shape),
        )
    }
}
