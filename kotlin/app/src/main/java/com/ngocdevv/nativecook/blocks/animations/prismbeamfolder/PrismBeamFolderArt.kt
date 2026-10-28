package com.ngocdevv.nativecook.blocks.animations.prismbeamfolder

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/*
 Prism Beam Folder — the artwork, drawn in code (a port of PrismBeamFolderArt.swift, same paths):
 the folder (back panel with its tab, a front panel that tilts open) and the four items (peach,
 cherries, aubergine, chili), each in a 48-unit box centred on (0, 0).
*/
internal fun pbfHex(v: Long, a: Float = 1f): Color =
    Color(((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f, a)

private fun ellipse(x: Float, y: Float, w: Float, h: Float) = Path().apply { addOval(Rect(x, y, x + w, y + h)) }

internal object PrismBeamFolderArt {
    private val leafBrushColors = listOf(pbfHex(0x7BD36B), pbfHex(0x2E8B57))

    // Folder (104 × 84, centred on (0, 0); the tab rises to y = -50)

    val back: Path = Path().apply {
        moveTo(-52f, -44f)
        quadraticTo(-52f, -50f, -46f, -50f)
        lineTo(-22f, -50f)
        quadraticTo(-19.5f, -50f, -17f, -47.5f)
        lineTo(-13f, -43.5f)
        quadraticTo(-11.2f, -42f, -9f, -42f)
        lineTo(42f, -42f)
        quadraticTo(52f, -42f, 52f, -32f)
        lineTo(52f, 32f)
        quadraticTo(52f, 42f, 42f, 42f)
        lineTo(-42f, 42f)
        quadraticTo(-52f, 42f, -52f, 32f)
        close()
    }
    private val backBrush = Brush.linearGradient(listOf(pbfHex(0x3A9AD3), pbfHex(0x2C7FB8)), Offset(0f, -50f), Offset(0f, 42f))
    private val insideBrush = Brush.linearGradient(listOf(pbfHex(0x0B2A44, 0f), pbfHex(0x0B2A44, 0.45f)), Offset(0f, -40f), Offset(0f, -28f))

    fun frontPath(o: Float): Path {
        val top = -30f + 8f * o
        val half = 52f + 3f * o
        return Path().apply {
            moveTo(-half, top + 7f)
            quadraticTo(-half, top, -half + 7f, top)
            lineTo(half - 7f, top)
            quadraticTo(half, top, half, top + 7f)
            lineTo(52f, 32f)
            quadraticTo(52f, 42f, 42f, 42f)
            lineTo(-42f, 42f)
            quadraticTo(-52f, 42f, -52f, 32f)
            close()
        }
    }

    fun DrawScope.drawBack() {
        drawPath(back, backBrush)
        drawRect(insideBrush, Offset(-48f, -40f), Size(96f, 12f))
    }

    fun DrawScope.drawFront(o: Float, light: Float) {
        val path = frontPath(o)
        val top = -30f + 8f * o
        drawPath(path, Brush.linearGradient(listOf(pbfHex(0x8EDCF9), pbfHex(0x56BDEC)), Offset(0f, top), Offset(0f, 42f)))
        if (light > 0.001f) {
            drawPath(
                path,
                Brush.linearGradient(listOf(pbfHex(0xFFE9F6, 0.55f * light), pbfHex(0xFFE9F6, 0f)), Offset(0f, top), Offset(0f, top + 40f)),
            )
        }
        drawLine(Color.White.copy(alpha = 0.55f), Offset(-52f - 3f * o + 7f, top + 0.75f), Offset(52f + 3f * o - 7f, top + 0.75f), 1.5f, StrokeCap.Round)
        drawLine(pbfHex(0x2C7FB8, 0.7f), Offset(-42f, 41.25f), Offset(42f, 41.25f), 1.5f)
    }

    // Items

    fun DrawScope.drawItem(kind: Int, a: Float) {
        when (kind) {
            0 -> peach(a)
            1 -> cherries(a)
            2 -> aubergine(a)
            else -> chili(a)
        }
    }

    private fun DrawScope.leaf(p: Path, from: Offset, to: Offset, a: Float) {
        drawPath(p, Brush.linearGradient(leafBrushColors, from, to), alpha = a)
    }

    private val peachBody = ellipse(-19f, -15f, 38f, 36f)
    private val peachBrush = Brush.radialGradient(
        0f to pbfHex(0xFFD08A), 0.55f to pbfHex(0xFF8A5B), 1f to pbfHex(0xE2474F),
        center = Offset(-6f, -6f), radius = 28f,
    )
    private val peachCrease = Path().apply {
        moveTo(1f, -13f)
        quadraticTo(-8f, -4f, -3f, 6f)
    }
    private val peachGlint = ellipse(-13f, -9f, 8f, 6f)
    private val peachLeaf = Path().apply {
        moveTo(2f, -14f)
        quadraticTo(6f, -24f, 16f, -22f)
        quadraticTo(14f, -13f, 2f, -14f)
        close()
    }

    private fun DrawScope.peach(a: Float) {
        drawPath(peachBody, peachBrush, alpha = a)
        drawPath(peachCrease, pbfHex(0xC93A44, 0.5f), alpha = a, style = Stroke(1.6f, cap = StrokeCap.Round))
        drawPath(peachGlint, Color.White.copy(alpha = 0.35f), alpha = a)
        drawLine(pbfHex(0x6B4B2A), Offset(1f, -13f), Offset(-1f, -19f), 2f, StrokeCap.Round, alpha = a)
        leaf(peachLeaf, Offset(6f, -22f), Offset(12f, -14f), a)
    }

    private val cherryStems = Path().apply {
        moveTo(-8f, 2f)
        quadraticTo(-6f, -12f, 6f, -20f)
        moveTo(9f, 4f)
        quadraticTo(12f, -8f, 6f, -20f)
    }
    private val cherryLeaf = Path().apply {
        moveTo(6f, -20f)
        quadraticTo(14f, -25f, 18f, -18f)
        quadraticTo(12f, -14f, 6f, -20f)
        close()
    }
    private val cherryBalls = listOf(-9f to 10f, 9f to 12f).map { (x, y) ->
        Triple(
            ellipse(x - 10f, y - 10f, 20f, 20f),
            Brush.radialGradient(
                0f to pbfHex(0xFF6A7A), 0.6f to pbfHex(0xD0142F), 1f to pbfHex(0x7A0019),
                center = Offset(x - 3f, y - 3f), radius = 14f,
            ),
            ellipse(x - 6f, y - 6.5f, 4.5f, 3.2f),
        )
    }

    private fun DrawScope.cherries(a: Float) {
        drawPath(cherryStems, pbfHex(0x4E7D2B), alpha = a, style = Stroke(2f, cap = StrokeCap.Round))
        leaf(cherryLeaf, Offset(8f, -24f), Offset(16f, -16f), a)
        for ((ball, brush, glint) in cherryBalls) {
            drawPath(ball, brush, alpha = a)
            drawPath(glint, Color.White.copy(alpha = 0.7f), alpha = a)
        }
    }

    private val aubergineBody = Path().apply {
        moveTo(-2f, -14f)
        cubicTo(5f, -15f, 9f, -4f, 10f, 8f)
        cubicTo(11f, 18f, 8f, 25f, 2f, 25f)
        cubicTo(-5f, 25f, -8f, 14f, -9f, 4f)
        cubicTo(-10f, -6f, -8f, -13f, -2f, -14f)
        close()
    }
    private val aubergineBrush = Brush.radialGradient(
        0f to pbfHex(0xA779E0), 0.55f to pbfHex(0x5B2A86), 1f to pbfHex(0x2E1247),
        center = Offset(-3f, 2f), radius = 24f,
    )
    private val aubergineHi = Path().apply {
        moveTo(-5f, -4f)
        quadraticTo(-7f, 8f, -2f, 16f)
    }
    private val aubergineStem = Path().apply {
        addRoundRect(RoundRect(-1.5f, -22f, 1.5f, -14f, CornerRadius(1.5f, 1.5f)))
    }
    private val aubergineCap = Path().apply {
        moveTo(-9f, -11f)
        quadraticTo(-4f, -6f, 0f, -8f)
        quadraticTo(4f, -6f, 9f, -11f)
        quadraticTo(6f, -17f, 0f, -17f)
        quadraticTo(-6f, -17f, -9f, -11f)
        close()
    }
    private val aubergineCapBrush = Brush.linearGradient(listOf(pbfHex(0x6CC46A), pbfHex(0x2F7D3A)), Offset(0f, -17f), Offset(0f, -8f))

    private fun DrawScope.aubergine(a: Float) {
        drawPath(aubergineBody, aubergineBrush, alpha = a)
        drawPath(aubergineHi, Color.White.copy(alpha = 0.4f), alpha = a, style = Stroke(2.5f, cap = StrokeCap.Round))
        drawPath(aubergineStem, pbfHex(0x4E8A3A), alpha = a)
        drawPath(aubergineCap, aubergineCapBrush, alpha = a)
    }

    private val chiliPod = Path().apply {
        moveTo(-8f, -14f)
        cubicTo(10f, -14f, 16f, 8f, 12f, 22f)
        cubicTo(6f, 12f, -2f, -4f, -12f, -8f)
        quadraticTo(-12f, -14f, -8f, -14f)
        close()
    }
    private val chiliBrush = Brush.linearGradient(
        0f to pbfHex(0xFF6A45), 0.5f to pbfHex(0xE0201E), 1f to pbfHex(0xA50F18),
        start = Offset(-8f, -14f), end = Offset(12f, 22f),
    )
    private val chiliHi = Path().apply {
        moveTo(-2f, -10f)
        quadraticTo(8f, -6f, 9f, 8f)
    }
    private val chiliCap = ellipse(-15f, -18f, 11f, 7f)
    private val chiliStem = Path().apply {
        moveTo(-9f, -16f)
        quadraticTo(-8f, -23f, -3f, -24f)
    }

    private fun DrawScope.chili(a: Float) {
        drawPath(chiliPod, chiliBrush, alpha = a)
        drawPath(chiliHi, Color.White.copy(alpha = 0.45f), alpha = a, style = Stroke(2.2f, cap = StrokeCap.Round))
        drawPath(chiliCap, pbfHex(0x4E9A3A), alpha = a)
        drawPath(chiliStem, pbfHex(0x3E7D2B), alpha = a, style = Stroke(2.5f, cap = StrokeCap.Round))
    }
}
