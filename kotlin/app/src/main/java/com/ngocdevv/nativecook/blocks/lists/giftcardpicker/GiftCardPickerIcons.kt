package com.ngocdevv.nativecook.blocks.lists.giftcardpicker

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal enum class GiftCardPickerIconKind {
    Back,
    Close,
    Deposit,
    Analyze,
    Transfer,
    Home,
    Pay,
    Invest,
    Chat,
}

@Composable
internal fun GiftCardPickerIcon(
    kind: GiftCardPickerIconKind,
    modifier: Modifier = Modifier,
    color: Color = GiftCardPickerTheme.Ink,
) {
    val base = when (kind) {
        GiftCardPickerIconKind.Back -> 20f
        GiftCardPickerIconKind.Close -> 18f
        GiftCardPickerIconKind.Deposit,
        GiftCardPickerIconKind.Analyze,
        GiftCardPickerIconKind.Transfer -> 28f
        else -> 26f
    }
    Canvas(modifier) {
        val scaleX = size.width / base
        val scaleY = size.height / base
        withTransform({ scale(scaleX, scaleY, Offset.Zero) }) {
            GiftCardPickerIconPainter.drawIcon(this, kind, color)
        }
    }
}

@Composable
internal fun GiftCardPickerBrandMark(
    stock: GiftCardPickerStockData,
    size: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val scaleX = this.size.width / 100f
        val scaleY = this.size.height / 100f
        withTransform({ scale(scaleX, scaleY, Offset.Zero) }) {
            GiftCardPickerIconPainter.drawBrand(this, stock)
        }
    }
}

private object GiftCardPickerIconPainter {
    private fun svg(data: String): Path = PathParser().parsePathString(data).toPath()

    private val paths = mapOf(
        "back" to svg("M13 3 L6 10 L13 17"),
        "depositOrange" to svg("M15 7.5 h2.5 a6.5 6.5 0 0 1 0 13 H15"),
        "depositInk" to svg("M7.5 6.5 h4 v4.5 h4.5 v4 h-4.5 v4.5 h-4 v-4.5 H3 v-4 h4.5 z"),
        "analyzeInk" to svg("M13 6.5 a9 9 0 1 0 9 9 H13 z"),
        "analyzeOrange" to svg("M16 3.5 a8.5 8.5 0 0 1 8.5 8.5 H16 z"),
        "transferOrange" to svg("M13 7 L20.5 14 L13 21"),
        "transferInk" to svg("M5 7.5 v13 a1 1 0 0 0 1.6 0.8 L14 15 a1.3 1.3 0 0 0 0 -2 L6.6 6.7 A1 1 0 0 0 5 7.5 z"),
        "home" to svg("M4 11.5 L13 4 L22 11.5 V21.5 a1 1 0 0 1 -1 1 H16 v-6 h-6 v6 H5 a1 1 0 0 1 -1 -1 z"),
        "pay" to svg("M3.5 6 v14 L12 13 z M12 6 v14 L21 13 z"),
        "investBars" to svg("M5 22 v-5 M10 22 v-8 M15 22 v-10 M20 22 v-13"),
        "investTrend" to svg("M4 13 C 10 11, 15 8, 21 3.5"),
        "chat" to svg("M5 5 h16 a2 2 0 0 1 2 2 v10 a2 2 0 0 1 -2 2 H11 l-4.5 3.5 V19 H5 a2 2 0 0 1 -2 -2 V7 a2 2 0 0 1 2 -2 z"),
        "petalRed" to svg("M49 49 V27 a22 22 0 0 0 -22 22 z"),
        "petalGreen" to svg("M51 49 H73 a22 22 0 0 0 -22 -22 z"),
        "petalBlue" to svg("M49 51 H27 a22 22 0 0 0 22 22 z"),
        "petalYellow" to svg("M51 51 V73 a22 22 0 0 0 22 -22 z"),
        "ribbonStem" to svg("M34 24 h10 v52 h-10 z"),
        "ribbonBowl" to svg("M44 24 h9 a14 14 0 0 1 0 28 h-9 z"),
        "ribbonLeg" to svg("M44 46 h9 l15 30 h-11 z"),
        "ribbonShade" to svg("M44 24 h4 v52 h-4 z"),
        "lens" to svg("M18 50 C 32 30, 68 30, 82 50 C 68 70, 32 70, 18 50 z"),
        "spiral" to svg("M50 50 m -3 0 a 3 3 0 1 1 6 0 a 8 8 0 1 1 -16 0 a 13 13 0 1 1 26 0"),
    )

    fun drawIcon(scope: DrawScope, kind: GiftCardPickerIconKind, color: Color) {
        when (kind) {
            GiftCardPickerIconKind.Back -> drawBack(scope, color)
            GiftCardPickerIconKind.Close -> drawClose(scope, color)
            GiftCardPickerIconKind.Deposit -> drawDeposit(scope)
            GiftCardPickerIconKind.Analyze -> drawAnalyze(scope)
            GiftCardPickerIconKind.Transfer -> drawTransfer(scope)
            GiftCardPickerIconKind.Home -> drawHome(scope)
            GiftCardPickerIconKind.Pay -> drawPay(scope)
            GiftCardPickerIconKind.Invest -> drawInvest(scope)
            GiftCardPickerIconKind.Chat -> drawChat(scope)
        }
    }

    fun drawBrand(scope: DrawScope, stock: GiftCardPickerStockData) {
        when (stock.mark) {
            GiftCardPickerMark.Rings -> drawBrandRings(scope)
            GiftCardPickerMark.Petals -> drawPetals(scope)
            GiftCardPickerMark.Ribbon -> drawRibbon(scope)
            GiftCardPickerMark.Lens -> drawLens(scope)
            GiftCardPickerMark.Wordmark -> drawNova(scope)
        }
    }

    private fun drawBack(scope: DrawScope, color: Color) {
        scope.drawPath(paths.getValue("back"), color, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    private fun drawClose(scope: DrawScope, color: Color) {
        scope.drawLine(color, Offset(2f, 2f), Offset(16f, 16f), 2f, cap = StrokeCap.Round)
        scope.drawLine(color, Offset(16f, 2f), Offset(2f, 16f), 2f, cap = StrokeCap.Round)
    }

    private fun drawDeposit(scope: DrawScope) {
        scope.drawPath(paths.getValue("depositOrange"), GiftCardPickerTheme.Orange, style = Stroke(2.2f, cap = StrokeCap.Round))
        scope.drawPath(paths.getValue("depositInk"), GiftCardPickerTheme.Ink, style = Stroke(2.2f, join = StrokeJoin.Round))
    }

    private fun drawAnalyze(scope: DrawScope) {
        scope.drawPath(paths.getValue("analyzeInk"), GiftCardPickerTheme.Ink, style = Stroke(2.2f, join = StrokeJoin.Round))
        scope.drawPath(paths.getValue("analyzeOrange"), GiftCardPickerTheme.Orange, style = Stroke(2.2f, join = StrokeJoin.Round))
    }

    private fun drawTransfer(scope: DrawScope) {
        scope.drawPath(paths.getValue("transferOrange"), GiftCardPickerTheme.Orange, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        scope.drawPath(paths.getValue("transferInk"), GiftCardPickerTheme.Ink, style = Stroke(2.2f, join = StrokeJoin.Round))
    }

    private fun drawHome(scope: DrawScope) {
        scope.drawPath(paths.getValue("home"), Color(0xFF6D6D72), style = Stroke(2.1f, join = StrokeJoin.Round))
    }

    private fun drawPay(scope: DrawScope) {
        val color = Color(0xFF6D6D72)
        val style = Stroke(2.1f, join = StrokeJoin.Round)
        scope.drawPath(paths.getValue("pay"), color, style = style)
    }

    private fun drawInvest(scope: DrawScope) {
        val orange = GiftCardPickerTheme.Orange
        scope.drawPath(paths.getValue("investBars"), orange, style = Stroke(2.4f, cap = StrokeCap.Round))
        scope.drawPath(paths.getValue("investTrend"), orange, style = Stroke(2.2f, cap = StrokeCap.Round))
    }

    private fun drawChat(scope: DrawScope) {
        val muted = Color(0xFF6D6D72)
        scope.drawPath(paths.getValue("chat"), muted, style = Stroke(2.1f, join = StrokeJoin.Round))
        listOf(8.5f, 13f, 17.5f).forEach { x -> scope.drawCircle(muted, 1.3f, Offset(x, 12f)) }
    }

    private fun drawBrandRings(scope: DrawScope) {
        val whiteStroke = Stroke(7.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        with(scope) {
            withTransform({ rotate(-12f, Offset(37f, 50f)) }) {
                drawRoundRect(Color.White, Offset(18f, 34f), Size(38f, 32f), androidx.compose.ui.geometry.CornerRadius(16f), style = whiteStroke)
            }
            withTransform({ rotate(12f, Offset(63f, 50f)) }) {
                drawRoundRect(Color.White, Offset(44f, 34f), Size(38f, 32f), androidx.compose.ui.geometry.CornerRadius(16f), style = whiteStroke)
            }
        }
    }

    private fun drawPetals(scope: DrawScope) {
        scope.drawPath(paths.getValue("petalRed"), Color(0xFFF35B2C))
        scope.drawPath(paths.getValue("petalGreen"), Color(0xFF8CC63F))
        scope.drawPath(paths.getValue("petalBlue"), Color(0xFF19A9F0))
        scope.drawPath(paths.getValue("petalYellow"), Color(0xFFFFC21A))
    }

    private fun drawRibbon(scope: DrawScope) {
        scope.drawPath(paths.getValue("ribbonStem"), Color(0xFFB20710))
        scope.drawPath(paths.getValue("ribbonBowl"), Color(0xFFE50914))
        scope.drawPath(paths.getValue("ribbonLeg"), Color(0xFFE50914))
        scope.drawPath(paths.getValue("ribbonShade"), Color(0xFF8A050C))
    }

    private fun drawLens(scope: DrawScope) {
        scope.drawPath(paths.getValue("lens"), Color.White, style = Stroke(6f, join = StrokeJoin.Round))
        scope.drawPath(paths.getValue("spiral"), Color.White, style = Stroke(5.5f, cap = StrokeCap.Round))
    }

    private fun drawNova(scope: DrawScope) {
        scope.drawContext.canvas.nativeCanvas.drawText(
            "NOVA",
            50f,
            59f,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 26f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.ITALIC)
                textScaleX = 0.98f
            },
        )
    }
}
