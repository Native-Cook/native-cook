package com.ngocdevv.nativecook.blocks.onboarding.tradingbotsonboarding

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R

/** A Material icon standing in for the SF Symbol of the SwiftUI block. */
@Composable
internal fun Glyph(vector: ImageVector, size: Float, color: Color = TBColors.ink, modifier: Modifier = Modifier) {
    Icon(vector, contentDescription = null, tint = color, modifier = modifier.size(size.dp))
}

/** Step 1: the wallet card (275 × 375 at x 66.7, y 161.7) over "Your Wallet. / Your Strategy". */
@Composable
internal fun WalletScene(reduced: Boolean) {
    val card by rememberEntrance(reduced, spring = 0.75f to 0.9f)
    val balance = rememberSettle(12_035.0, 12_485.32, 0.89, 0.06, 0.45, 2.2, reduced)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .at(66.7f + 137.5f, 161.7f + 187.5f)
                .graphicsLayer {
                    alpha = card.coerceAtMost(1f)
                    translationY = if (reduced) 0f else (1 - card) * 46.dp.toPx()
                    val scale = if (reduced) 1f else 0.94f + 0.06f * card
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                },
        ) { WalletCard(balance) }
        Heading("Your Wallet.", "Your Strategy", reduced)
        Box(Modifier.at(201f, 681.7f)) {
            Reveal(reduced, delay = 0.35f, y = 6f, blur = 4f) { Caption("Connect your wallet and access the tools\nyou need to manage your trades.") }
        }
    }
}

@Composable
private fun WalletCard(balance: Double) {
    Panel(
        275f, 375f, 32f,
        fill = listOf(hex(0x1a1a1a), hex(0x161616), hex(0x121212)),
        stroke = listOf(white(0.09f), white(0.03f), white(0.07f)),
        shadow = Triple(Color.Black.copy(alpha = 0.9f), 24f, 16f),
        modifier = Modifier.semantics { contentDescription = "Secure wallet connection. Total wallet balance ${balance.toInt()} dollars" },
    ) {
        Box(Modifier.at(21.8f, 34.8f).size(16.dp).rotate(-45f), contentAlignment = Alignment.Center) { Glyph(Icons.Outlined.Power, 15f) }
        Box(Modifier.leading(35f, 34.8f)) { TBText("Secure Wallet Connection", 13.6f, weight = FontWeight.Medium, tracking = -0.1f) }
        Box(
            Modifier.at(243.8f, 33.8f).size(31.dp)
                .background(hex(0x141414), RoundedCornerShape(10.dp))
                .border(1.dp, white(0.13f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { Glyph(Icons.Filled.Add, 13f) }
        Box(
            Modifier.offset(6.dp, 64.dp).size(263.dp, 311.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 26.dp, bottomEnd = 26.dp)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val brush = shaderBrush(WalletShader) { setFloatUniform("size", size.width, size.height) }
                drawRect(brush ?: Brush.verticalGradient(listOf(hex(0x5a8dfb), hex(0x7d8ff4), hex(0xb08fe0), hex(0x8fd8c4), hex(0x4a5560))))
            }
            ConnectRow()
            Box(Modifier.offset(1.5.dp, 60.3.dp)) { BalanceInset(balance) }
            Box(Modifier.offset((-0.5).dp, 156.dp)) { Holdings() }
        }
    }
}

@Composable
private fun ConnectRow() {
    Box(Modifier.size(263.dp, 52.dp)) {
        Box(Modifier.at(17.3f, 25.3f).size(14.6.dp).background(white(0.88f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            Box(Modifier.offset(y = 2.dp).size(10.dp, 6.dp).background(hex(0x7d8ff4), RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
                Glyph(Icons.Filled.KeyboardArrowUp, 6f, Color.White)
            }
        }
        Box(Modifier.leading(31.6f, 25.3f)) { TBText("Connect Any Wallet", 12f, color = Color.White, tracking = -0.05f) }
        Row(Modifier.at(209.6f, 25.3f)) {
            Logo(Color.White, false) {
                Row(Modifier.offset((-1).dp, (-0.5).dp)) {
                    Box(Modifier.size(3.4.dp, 6.4.dp).background(Color.Black, RoundedCornerShape(1.7.dp)))
                    Box(Modifier.offset(2.6.dp).size(3.4.dp, 6.4.dp).background(Color.Black, RoundedCornerShape(1.7.dp)))
                }
            }
            Logo(hex(0x1b1b1b), true) { Glyph(Icons.Filled.Shield, 13f, hex(0xf04d22)) }
            Logo(hex(0x5236f2), true) { TBText("S", 14f, color = Color.White, weight = FontWeight.Black, italic = true) }
            Logo(hex(0x101010), true) { Glyph(Icons.Filled.Landscape, 10f, Color.White) }
        }
    }
}

@Composable
private fun Logo(fill: Color, overlap: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.offset(x = if (overlap) (-4.1).dp else 0.dp).size(24.3.dp)
            .background(fill, CircleShape).border(1.6.dp, Color.Black, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun BalanceInset(balance: Double) {
    Box(
        Modifier.size(257.dp, 89.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black)
            .drawWithContent {
                drawContent()
                // inner glow along the rim
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 4.dp.toPx()
                        color = hex(0x8696ff, 0.28f).toArgb()
                        maskFilter = BlurMaskFilter(2.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                    val r = 26.dp.toPx()
                    canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
                }
            },
    ) {
        Image(painterResource(R.drawable.tradingbotsonboarding_wallet_icon), null, Modifier.at(16.7f + 22.2f, 20.7f + 23f).size(48.dp))
        Row(Modifier.leading(75f, 33.3f)) {
            TBText("$", 18.5f, color = hex(0x8a8a8a), weight = FontWeight.SemiBold, tracking = -0.1f, modifier = Modifier.alignByBaseline())
            TBText(formatAmount(balance, 2), 18.5f, weight = FontWeight.SemiBold, tracking = -0.1f, tabular = true, modifier = Modifier.alignByBaseline().offset(x = 2.5.dp))
        }
        Box(Modifier.leading(75f, 58f)) { TBText("Total Wallet Balance", 9f, color = hex(0x8c8c8c)) }
    }
}

@Composable
private fun Holdings() {
    Box(Modifier.size(260.dp, 160.dp).clip(RoundedCornerShape(26.dp)).background(hex(0x0a0a0a))) {
        Box(Modifier.at(133f, 23.3f)) { TBText("Your holdings", 9.2f, color = hex(0xe4e4e4)) }
        Box(Modifier.at(19f + 114.7f, 43.6f + 23.35f)) { Holding("Tether", "USDT", "0.22%", hex(0x4fd1b5), dim = false) }
        Box(Modifier.at(19f + 114.7f, 100.6f + 23.35f)) { Holding("SushiSwap", "SUSHI", "5.12%", hex(0xd2647e), dim = true) }
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to hex(0x0a0a0a, 0.55f)))
        })
    }
}

@Composable
private fun Holding(name: String, ticker: String, value: String, tint: Color, dim: Boolean) {
    Box(Modifier.size(229.3.dp, 46.7.dp).graphicsLayer { alpha = if (dim) 0.62f else 1f }.background(hex(0x1c1c1c), RoundedCornerShape(16.dp))) {
        Box(Modifier.at(22.6f, 23.35f).size(25.dp).background(hex(0x262626), CircleShape), contentAlignment = Alignment.Center) {
            if (dim) {
                Box(Modifier.size(11.dp, 7.dp).rotate(-25f).background(Brush.horizontalGradient(listOf(hex(0xff8fb0), hex(0x7c5cff))), RoundedCornerShape(3.5.dp)))
            } else {
                Glyph(Icons.Filled.Shield, 13f, hex(0x45d6b2), Modifier.rotate(180f))
                TBText("T", 8f, color = Color.Black, weight = FontWeight.ExtraBold, modifier = Modifier.offset(y = (-1).dp))
            }
        }
        Row(Modifier.leading(42.6f, 23.35f)) {
            TBText(name, 10.7f, color = hex(0xe2e2e2), modifier = Modifier.alignByBaseline())
            TBText(ticker, 6.5f, color = hex(0x5a5a5a), weight = FontWeight.Medium, modifier = Modifier.alignByBaseline().offset(x = 6.dp))
        }
        Box(Modifier.at(204.1f, 23.35f).size(32.3.dp, 15.3.dp).background(hex(0x2a2a2a), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
            TBText(value, 8.2f, color = tint, weight = FontWeight.Medium, align = TextAlign.Center)
        }
    }
}

