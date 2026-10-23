package com.ngocdevv.nativecook.blocks.onboarding.mascotsocialtour

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import com.ngocdevv.nativecook.blocks.onboarding.mascotsocialtour.MascotSocialTourMotion as M
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val BLUE = 0xFF3C71FB.toInt()
private const val GREEN = 0xFF47D87B.toInt()
private const val YELLOW = 0xFFF7B30A.toInt()
private const val PINK = 0xFFF014CC.toInt()
private const val RED = 0xFFE64441.toInt()
private const val INK = 0xFF0A1230.toInt()

/** Static contours are built once; every pose only transforms those contours. */
private object MascotSocialTourPaths {
    val flower = MascotSocialTourContours.flower
    val smallFlower = MascotSocialTourContours.smallFlower
    val redDrop = MascotSocialTourContours.redDrop
    val redBloom = MascotSocialTourContours.bloom
    val bean = Path().apply {
        moveTo(-0.48f, 0.1f)
        cubicTo(-0.6f, -0.2f, -0.36f, -0.5f, -0.1f, -0.49f)
        cubicTo(0.27f, -0.54f, 0.58f, -0.27f, 0.49f, -0.02f)
        cubicTo(0.5f, 0.35f, 0.36f, 0.57f, 0.1f, 0.48f)
        cubicTo(-0.07f, 0.22f, -0.42f, 0.6f, -0.48f, 0.1f)
        close()
    }
    val drop = MascotSocialTourContours.drop
    val roundedTriangle = MascotSocialTourContours.roundedTriangle
    val heart = Path().apply {
        moveTo(0f, 0.45f)
        cubicTo(-0.12f, 0.34f, -0.61f, 0.06f, -0.42f, -0.15f)
        cubicTo(-0.38f, -0.5f, -0.08f, -0.45f, 0f, -0.22f)
        cubicTo(0.12f, -0.5f, 0.45f, -0.45f, 0.42f, -0.15f)
        cubicTo(0.62f, 0.11f, 0.2f, 0.37f, 0f, 0.45f)
        close()
    }
    val memoryLarge = Path().apply {
        moveTo(0.008226f, 0.798017f)
        cubicTo(-0.043648f, 1.008039f, 0.160612f, 1.025120f, 0.316465f, 0.978922f)
        lineTo(0.881571f, 0.811412f)
        cubicTo(1.011260f, 0.772969f, 1.036216f, 0.617018f, 0.947625f, 0.530013f)
        lineTo(0.463247f, 0.054304f)
        cubicTo(0.375095f, -0.032270f, 0.209671f, -0.017579f, 0.177024f, 0.114602f)
        lineTo(0.008226f, 0.798017f)
        close()
    }
    val memorySmall = Path().apply {
        moveTo(0.113345f, 0.000089f)
        cubicTo(-0.003439f, -0.004944f, -0.014833f, 0.197077f, 0.012199f, 0.252632f)
        lineTo(0.338096f, 0.922400f)
        cubicTo(0.413287f, 1.076929f, 0.578986f, 0.967737f, 0.630270f, 0.878484f)
        lineTo(0.989879f, 0.252632f)
        cubicTo(1.025614f, 0.190439f, 0.961646f, 0.036653f, 0.877499f, 0.033026f)
        lineTo(0.113345f, 0.000089f)
        close()
    }
    val noteFlag = Path().apply {
        moveTo(1f, -8f)
        quadTo(6f, -8f, 7f, -2f)
        lineTo(6f, 1f)
        quadTo(4f, -4f, 1f, -4f)
        close()
    }
    val bubble = Path().apply {
        moveTo(-42f, 43f)
        quadTo(-38f, 80f, -29f, 78f)
        lineTo(2f, 57f)
        close()
    }
    val clouds = MascotSocialTourContours.clouds
    val cloudGradients = List(2) { layer ->
        LinearGradient(0f, 535f, 0f, 728f, if (layer == 0) 0xFFF5F9FB.toInt() else 0xFFE6EDF1.toInt(), Color.WHITE, Shader.TileMode.CLAMP)
    }
}

internal class MascotSocialTourRenderer(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private var cachedScene: MascotSocialTourScene? = null
    private val titleFont = Typeface.createFromAsset(context.assets, "mascotsocialtour/mascotsocialtour_TitanOne-Regular.ttf")
    private val bodyFont = Typeface.create("sans-serif", Typeface.NORMAL)
    private val loginFont = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun draw(canvas: Canvas, time: Double) {
        val scene = cachedScene ?: MascotSocialTourScene(canvas, paint).also { cachedScene = it }
        scene.canvas = canvas
        scene.rect(0.0, 0.0, 393.0, 852.0, Color.WHITE)
        scene.clouds(time)
        scene.story(time)
        val pages = M.pages(time)
        if (pages.from != pages.to) {
            scene.saved(alpha = 1 - pages.progress * 0.2) {
                canvas.translate((-393 * pages.progress).toFloat(), 0f)
                copy(pages.from)
            }
        }
        scene.saved {
            if (pages.from != pages.to) canvas.translate((393 * (1 - pages.progress)).toFloat(), 0f)
            copy(pages.to)
        }
        var left = 196.5 - (16 + 5.8 * 4 + 5 * 4) / 2
        repeat(5) { index ->
            val width = if (index == pages.active) 16.0 else 5.8
            scene.rect(left, 86.1, width, 5.8, if (index == pages.active) 0xFF2084F8.toInt() else 0xFFE8EAEE.toInt(), 2.9)
            left += width + 5
        }
        scene.rect(296.5, 71.5, 73.0, 35.0, 0xFFF0F5FF.toInt(), 17.5)
        scene.text("Login", 333.0, 89.0, 15.0, 18.0, loginFont, 0xFF2384F7.toInt())
        scene.text(
            "By joining Nookly you acknowledge that you have read and\nagree to Nookly's Terms & Conditions and Privacy Policy.",
            196.5, 795.0, 11.0, 19.1, bodyFont, 0xFF88898E.toInt(),
        )
    }

    private fun MascotSocialTourScene.copy(page: Int) {
        saved {
            canvas.translate(196.5f, 626.5f)
            canvas.scale(1.15f, 1f)
            text(M.titles[page], 0.0, 0.0, 37.0, 44.0, titleFont, 0xFF080D28.toInt())
        }
        text(M.bodies[page], 196.5, 716.0, 16.0, 25.1, bodyFont, 0xFF66676B.toInt())
    }
}

private enum class MascotSocialTourKind { Blue, Green, Yellow, Pink, Red }

private class MascotSocialTourScene(var canvas: Canvas, private val paint: Paint) {
    private var opacity = 1.0

    inline fun saved(alpha: Double = 1.0, block: MascotSocialTourScene.() -> Unit) {
        val previous = opacity
        canvas.save()
        opacity *= alpha
        try {
            block()
        } finally {
            opacity = previous
            canvas.restore()
        }
    }

    private fun prepare(color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = (Color.alpha(color) * opacity).roundToInt().coerceIn(0, 255)
    }

    fun rect(x: Double, y: Double, w: Double, h: Double, color: Int, radius: Double = 0.0) {
        prepare(color)
        canvas.drawRoundRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), radius.toFloat(), radius.toFloat(), paint)
    }

    private fun oval(x: Double, y: Double, w: Double, h: Double, color: Int) {
        prepare(color)
        canvas.drawOval(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
    }

    private fun path(path: Path, color: Int) {
        prepare(color)
        canvas.drawPath(path, paint)
    }

    fun text(value: String, x: Double, y: Double, size: Double, lineHeight: Double, font: Typeface, color: Int) {
        prepare(color)
        paint.typeface = font
        paint.textSize = size.toFloat()
        paint.textAlign = Paint.Align.CENTER
        val lines = value.split('\n')
        val centerBaseline = y - (paint.ascent() + paint.descent()) / 2
        val firstBaseline = centerBaseline - (lines.size - 1) * lineHeight / 2
        lines.forEachIndexed { index, line -> canvas.drawText(line, x.toFloat(), (firstBaseline + index * lineHeight).toFloat(), paint) }
    }

    fun clouds(time: Double) {
        val home = 1 - M.ease(M.segment(time, 9.45, 9.85))
        repeat(2) { layer ->
            saved {
                canvas.translate((-time * (if (layer == 0) 9 else 38) * home).toFloat(), 0f)
                prepare(Color.WHITE)
                paint.shader = MascotSocialTourPaths.cloudGradients[layer]
                canvas.drawPath(MascotSocialTourPaths.clouds[layer], paint)
                paint.shader = null
            }
        }
    }

    fun story(time: Double) {
        val first = if (time < 1.98) 1 - M.ease(M.segment(time, 1.82, 1.98)) else M.ease(M.segment(time, 9.50, 9.85))
        if (first > 0.001) saved(alpha = first) { hangout(if (time > 9.45) 0.0 else time) }
        val second = M.ease(M.segment(time, 2.02, 2.12)) * (1 - M.ease(M.segment(time, 4.30, 4.48)))
        if (second > 0.001) saved(alpha = second) { connection(time) }
        val third = M.ease(M.segment(time, 4.48, 4.65)) * (1 - M.ease(M.segment(time, 6.95, 7.22)))
        if (third > 0.001) saved(alpha = third) { space(time) }
        val fourth = M.ease(M.segment(time, 7.20, 7.48)) * (1 - M.ease(M.segment(time, 9.45, 9.78)))
        if (fourth > 0.001) saved(alpha = fourth) { memories(time) }
    }

    private fun mascot(kind: MascotSocialTourKind, x: Double, y: Double, w: Double, h: Double, angle: Double = 0.0, blink: Double = 0.0, highlight: Double = 0.0, look: Double = 0.0, bodyTurn: Double = 0.0) {
        if (w <= 0.01 || h <= 0.01) return
        saved {
            canvas.translate(x.toFloat(), y.toFloat())
            canvas.rotate(angle.toFloat())
            canvas.scale(w.toFloat(), h.toFloat())
            val contour = when (kind) {
                MascotSocialTourKind.Blue -> MascotSocialTourPaths.flower
                MascotSocialTourKind.Green -> MascotSocialTourPaths.roundedTriangle
                MascotSocialTourKind.Yellow -> MascotSocialTourPaths.smallFlower
                MascotSocialTourKind.Pink, MascotSocialTourKind.Red -> MascotSocialTourPaths.redDrop
            }
            val fill = when (kind) {
                MascotSocialTourKind.Blue -> BLUE
                MascotSocialTourKind.Green -> GREEN
                MascotSocialTourKind.Yellow -> YELLOW
                MascotSocialTourKind.Pink -> PINK
                MascotSocialTourKind.Red -> RED
            }
            saved { canvas.rotate(bodyTurn.toFloat()); path(contour, fill) }
            if (highlight > 0) saved(alpha = highlight) {
                canvas.clipPath(contour)
                oval(0.02, -0.3, 0.5, 0.7, Color.WHITE)
            }
            val face = faces[kind.ordinal]
            val small = w < 80
            val eyeWidth = if (kind == MascotSocialTourKind.Pink && small) 0.10 else if (kind == MascotSocialTourKind.Red && small) 0.11 else face[3]
            val normalHeight = if (kind == MascotSocialTourKind.Pink && small) 0.09 else if (kind == MascotSocialTourKind.Red && small) 0.14 else face[4]
            val eyeHeight = M.mix(normalHeight, 0.025, blink)
            for (side in listOf(-1.0, 1.0)) {
                rect(face[0] + look + side * face[2] - eyeWidth / 2, face[1] - eyeHeight / 2, eyeWidth, eyeHeight, Color.WHITE, eyeWidth / 2)
            }
        }
    }

    private val faces = arrayOf(
        doubleArrayOf(-0.0851,-0.0043,0.0793,0.1087,0.1449),
        doubleArrayOf(-0.1688,0.0234,0.1044,0.1368,0.1517),
        doubleArrayOf(-0.1978,0.0515,0.0972,0.13,0.1442),
        doubleArrayOf(0.113,0.028,0.1164,0.141,0.1084),
        doubleArrayOf(0.0859,-0.0144,0.1103,0.1675,0.18),
    )

    private fun hand(x: Double, y: Double, w: Double = 20.0, h: Double = 24.0, fill: Int, angle: Double = 0.0) {
        transformed(MascotSocialTourPaths.bean, x, y, w, h, angle, fill)
    }

    private fun shadow(x: Double, y: Double, w: Double, h: Double, alpha: Double = 0.65) {
        saved(alpha = alpha) { oval(x - w / 2, y - h / 2, w, h, 0xFFECECFF.toInt()) }
    }

    private fun phone(x: Double, y: Double, w: Double, h: Double, angle: Double, upright: Double = 1.0, flash: Double = 0.0) {
        saved {
            canvas.translate(x.toFloat(), y.toFloat())
            canvas.rotate(angle.toFloat())
            rect(-w / 2 - 1.7, -h / 2 + 1.6, w, h, 0xFFB7BEDC.toInt(), 4.0)
            rect(-w / 2, -h / 2, w, h, INK, 4.0)
            if (upright < 0.99) saved(alpha = 1 - upright) {
                rect(-w / 2 + 2.2, -h / 2 + 2, w - 4.4, h - 4, 0xFFDDF0FE.toInt(), 3.0)
                oval(-w * 0.15, -h * 0.15, w * 0.34, h * 0.32, Color.WHITE)
            }
            if (upright > 0) saved(alpha = upright) {
                rect(w / 2 - 12, -h / 2 + 3, 9.0, 9.0, 0xFF2A4380.toInt(), 2.0)
                oval(w / 2 - 9.8, -h / 2 + 5.2, 4.6, 4.6, Color.WHITE)
            }
            if (flash > 0) saved(alpha = flash) { oval(w / 2 - 10.8, -h / 2 + 4.2, 6.6, 6.6, Color.WHITE) }
        }
    }

    private fun hangout(t: Double) {
        val x = M.key(t, 0.0 to 137.8, 0.25 to 152.6, 0.38 to 154.7, 0.65 to 175.5, 1.1 to 186.7, 1.38 to 102.5, 1.65 to 102.5, 1.9 to 130.8)
        val y = M.key(t, 0.0 to 258.3, 0.25 to 237.7, 0.38 to 242.1, 0.65 to 318.8, 1.1 to 334.3, 1.38 to 268.0, 1.65 to 268.0, 1.9 to 287.2)
        val angle = M.key(t, 0.0 to -12.6, 0.65 to -1.7, 1.2 to -5.8, 1.65 to 12.3, 1.9 to -7.6)
        val sinkRed = M.ease(M.segment(t, 0.04, 0.45))
        val sinkYellow = M.ease(M.segment(t, 0.01, 0.37))
        if (sinkRed < 1) {
            val s = 1 - sinkRed
            mascot(MascotSocialTourKind.Red, M.mix(96.9, 235.0, sinkRed), M.mix(415.1, 447.0, sinkRed), 89.7 * s, 89.8 * s, -35 - 35 * sinkRed)
            hand(155.0, 381.0, 24 * s, 23 * s, RED, -25.0)
        }
        if (sinkYellow < 1) {
            val s = 1 - sinkYellow
            mascot(MascotSocialTourKind.Yellow, M.mix(267.5, 235.0, sinkYellow), M.mix(325.5, 445.0, sinkYellow), 75.6 * s, 79.5 * s, 22.4 + 60 * sinkYellow)
        }
        val reappear = M.ease(M.segment(t, 1.53, 1.91))
        if (reappear > 0) mascot(MascotSocialTourKind.Yellow, M.mix(235.0, 282.0, reappear), M.mix(446.0, 279.0, reappear), 65.7 * reappear, 74.6 * reappear, 7.5)
        val held = M.ease(M.segment(t, 0.65, 0.98)) * (1 - M.ease(M.segment(t, 1.25, 1.55)))
        shadow(235.0, 459.0, 73 * (1 - held * 0.2), 20 * (1 - held * 0.2), 0.6 * (1 - held * 0.6))
        phone(M.mix(235.0, 252.0, held), M.mix(450.0, 375.0, held), M.mix(49.0, 30.0, held), M.mix(22.0, 48.0, held), M.mix(-5.0, 6.0, held), held)
        mascot(MascotSocialTourKind.Blue, x, y, 115.4, 109.2, angle)
        hand(x - 57, y + 60, 25.0, 30.0, BLUE, -20.0)
        hand(M.mix(x + 78, 240.0, held), M.mix(y + 12, 381.0, held), 28.0, 28.0, BLUE, 15.0)
        repeat(5) { index ->
            val p = M.segment(t, 1.43 + index * 0.065, 2.06 + index * 0.02)
            if (p > 0 && p < 1) {
                val side = if (index % 2 == 0) -1.0 else 1.0
                saved(alpha = sin(PI * p)) {
                    note(235 + side * (10 + 80 * p), 441 - 140 * p, 0.6 + 0.5 * p, side * 25, listOf(GREEN, PINK, BLUE, RED, GREEN)[index])
                }
            }
        }
    }

    private data class Friend(val kind: MascotSocialTourKind, val x: Double, val y: Double, val angle: Double, val width: Double, val height: Double)
    private val friends = listOf(
        Friend(MascotSocialTourKind.Green,105.7,227.0,13.6,99.7,97.1),
        Friend(MascotSocialTourKind.Pink,301.4,241.2,-15.8,89.0,108.3),
        Friend(MascotSocialTourKind.Red,93.2,403.1,-21.1,91.5,94.7),
        Friend(MascotSocialTourKind.Yellow,302.0,399.1,23.5,102.9,115.5),
    )
    private fun connection(t: Double) {
        val x = M.key(t, 2.0 to 196.5, 2.125 to 234.2, 2.25 to 242.9, 2.375 to 236.6, 2.5 to 206.6, 2.625 to 149.6, 2.75 to 140.0, 2.875 to 147.1, 3.0 to 173.6, 3.125 to 194.0, 3.25 to 196.5, 4.4 to 196.5)
        val y = M.key(t, 2.0 to 316.0, 2.125 to 283.0, 2.25 to 269.6, 2.375 to 277.8, 2.5 to 295.8, 2.625 to 280.6, 2.75 to 272.4, 2.875 to 317.4, 3.0 to 352.9, 3.125 to 328.0, 3.25 to 324.5, 4.4 to 322.0)
        val width = M.key(t, 2.0 to 3.0, 2.125 to 130.0, 2.25 to 156.0, 2.375 to 147.0, 2.5 to 142.0, 2.75 to 142.0, 2.875 to 161.0, 3.0 to 188.0, 3.125 to 217.0, 3.25 to 231.0, 3.375 to 228.0, 3.5 to 212.0, 3.625 to 178.0, 4.4 to 178.0)
        val height = M.key(t, 2.0 to 3.0, 2.125 to 123.0, 2.25 to 147.0, 2.375 to 133.0, 2.5 to 120.0, 2.75 to 135.0, 2.875 to 141.0, 3.0 to 150.0, 3.125 to 197.0, 3.25 to 220.0, 3.375 to 216.0, 3.5 to 201.0, 3.625 to 169.0, 4.4 to 168.0)
        val blink = sin(PI * M.segment(t, 2.41, 2.59))
        val look = M.key(t, 2.0 to 0.0, 2.25 to 0.15, 2.75 to -0.04, 3.2 to 0.13, 4.4 to 0.13)
        val leave = 1.0 - M.ease(M.segment(t, 4.30, 4.48))
        friends.forEachIndexed { index, friend ->
            val p = max(0.0, M.spring(max(0.0, t - 3.2 - index * 0.045)))
            val bob = 2.5 * sin((t - 3.2) * 2 * PI + index) * M.segment(t, 3.65, 3.9)
            mascot(friend.kind, M.mix(196.5, friend.x, p), M.mix(316.0, friend.y, p) + bob,
                friend.width * leave * min(1.0, p * 1.8), friend.height * leave * min(1.0, p * 1.8), friend.angle * p,
                M.segment(t, 3.31, 3.39) * (1 - M.segment(t, 3.42, 3.5)), look = if (index == 2) -0.15 else 0.0, bodyTurn = if (index == 2) -90.0 else 0.0)
        }
        mascot(MascotSocialTourKind.Blue, x, y, width * leave, height * leave, M.key(t, 2.0 to 0.0, 3.6 to 0.0, 4.3 to -11.0), blink.pow(3), look = look)
    }

    private val leftFlap = Path()
    private val rightFlap = Path()

    private fun space(t: Double) {
        val born = M.spring(max(0.0, t - 4.47))
        val end = 1 - M.ease(M.segment(t, 6.95, 7.22))
        saved {
            canvas.translate(196.5f, 326f)
            val scale = max(0.001, born * end).toFloat()
            canvas.scale(scale, scale)
            canvas.translate(-196.5f, -326f)
            val flap = M.ease(M.segment(t, 4.65, 5.22))
            val flapRight = M.ease(M.segment(t, 4.71, 5.28))
            rect(120.0, 218.0, 151.0, 213.0, YELLOW, 9.0)
            rect(120.0, 242.0, 151.0, 165.0, 0xFFF1F6F8.toInt())
            leftFlap.rewind()
            leftFlap.moveTo(120f, 242f)
            leftFlap.lineTo(M.mix(196.0, 92.0, flap).toFloat(), M.mix(242.0, 229.0, flap).toFloat())
            leftFlap.quadTo(M.mix(200.0, 87.0, flap).toFloat(), 323f, M.mix(196.0, 92.0, flap).toFloat(), M.mix(407.0, 390.0, flap).toFloat())
            leftFlap.lineTo(120f, 407f)
            leftFlap.close()
            path(leftFlap, 0xFFFFECB0.toInt())
            rightFlap.rewind()
            rightFlap.moveTo(271f, 242f)
            rightFlap.lineTo(M.mix(196.0, 299.0, flapRight).toFloat(), M.mix(242.0, 227.0, flapRight).toFloat())
            rightFlap.quadTo(M.mix(192.0, 305.0, flapRight).toFloat(), 323f, M.mix(196.0, 299.0, flapRight).toFloat(), M.mix(407.0, 390.0, flapRight).toFloat())
            rightFlap.lineTo(271f, 407f)
            rightFlap.close()
            path(rightFlap, 0xFFFFECB0.toInt())
            shadow(82.0, 255.0, 32.0, 7.0)
            shadow(265.0, 483.0, 46.0, 10.0)
            mascot(MascotSocialTourKind.Pink, M.key(t, 4.48 to 73.0, 5.4 to 70.5, 6.1 to 83.3, 6.7 to 72.1), M.key(t, 4.48 to 231.0, 5.4 to 212.2, 6.1 to 214.9, 6.7 to 224.6), 54.0, 64.0, M.key(t, 4.48 to -15.4, 5.4 to -15.9, 6.1 to -36.4, 6.7 to -0.7), 0.0, 0.0, 0.0)
            mascot(MascotSocialTourKind.Red, M.key(t, 4.48 to 286.0, 5.4 to 305.5, 6.1 to 256.0, 6.7 to 265.1), M.key(t, 4.48 to 423.4, 5.4 to 421.3, 6.1 to 438.1, 6.7 to 428.3), 66.0, 66.0, M.key(t, 4.48 to 14.8, 5.4 to 17.2, 6.1 to -24.8, 6.7 to -8.9), M.key(t, 4.8 to 0.0, 6.1 to 0.0, 6.7 to 0.85, 6.95 to 0.0), 0.0, -0.17)
            if (t > 5.7) {
                val gx = M.key(t, 5.7 to 81.0, 5.75 to 76.1, 5.875 to 72.0, 6.0 to 77.2, 6.125 to 102.1, 6.25 to 129.9, 6.375 to 147.1, 6.5 to 148.5)
                val gy = M.key(t, 5.7 to 274.0, 5.75 to 269.4, 5.875 to 281.4, 6.0 to 284.7, 6.125 to 298.3, 6.25 to 313.6, 6.375 to 323.7, 6.5 to 324.2)
                val bx = M.key(t, 5.7 to 306.0, 5.75 to 308.4, 5.875 to 322.0, 6.0 to 315.5, 6.125 to 287.9, 6.25 to 256.8, 6.375 to 239.3, 6.5 to 236.6)
                val by = M.key(t, 5.7 to 392.0, 5.75 to 388.4, 5.875 to 376.4, 6.0 to 372.5, 6.125 to 355.3, 6.25 to 337.1, 6.375 to 325.0, 6.5 to 324.2)
                rect(gx - 23.5, gy - 23.5, 47.0, 47.0, GREEN, 7.0)
                oval(bx - 25.5, by - 25.5, 51.0, 51.0, BLUE)
            }
            hand(M.key(t, 4.8 to 178.0, 5.4 to 114.0, 6.1 to 138.6, 6.7 to 137.5), M.key(t, 4.8 to 286.0, 5.4 to 246.7, 6.1 to 227.3, 6.7 to 283.8), 25.0, 28.0, PINK, -15.0)
            hand(M.key(t, 4.8 to 181.0, 5.4 to 82.4, 6.1 to 63.0, 6.7 to 110.8), M.key(t, 4.8 to 353.0, 5.4 to 274.0, 6.1 to 300.0, 6.7 to 302.1), 25.0, 28.0, PINK, 18.0)
            hand(M.key(t, 4.8 to 211.0, 5.4 to 255.0, 6.1 to 219.4, 6.7 to 242.9), M.key(t, 4.8 to 320.0, 5.4 to 345.0, 6.1 to 371.2, 6.7 to 368.2), 29.0, 27.0, RED, 15.0)
            hand(M.key(t, 4.8 to 207.0, 5.4 to 300.0, 6.1 to 340.0, 6.7 to 272.7), M.key(t, 4.8 to 383.0, 5.4 to 412.0, 6.1 to 374.0, 6.7 to 375.8), 25.0, 28.0, RED, 10.0)
        }
    }

    private fun memories(t: Double) {
        val entrance = max(0.0, M.spring(max(0.0, t - 7.2)))
        val bob = sin((t - 7.45) * 2 * PI) * 3
        val flash = pulse(t, 7.86, 0.18) + pulse(t, 8.55, 0.18) + pulse(t, 9.22, 0.18)
        shadow(112.0, 473.0, 44.0, 7.0, 0.45)
        mascot(MascotSocialTourKind.Pink, 109.3, 373.0 + bob, 118.6 * entrance, 136.6 * entrance, M.key(t, 7.48 to -2.8, 7.78 to -2.8, 8.3 to -15.4, 8.9 to -13.0, 9.4 to -2.8), 0.0, 0.23 + flash * 0.25, 0.06)
        phone(M.key(t, 7.48 to 149.8, 7.85 to 174.0, 8.9 to 174.0, 9.4 to 160.0), M.key(t, 7.48 to 425.8, 7.85 to 402.0, 8.3 to 399.5, 8.9 to 405.0, 9.4 to 416.0) + bob, 31.0 * entrance, 52.0 * entrance, -9.0 + 4.0 * flash, 1.0, flash * 0.6)
        val redIn = M.ease(M.segment(t, 7.96, 8.13))
        val pinkIn = M.ease(M.segment(t, 8.9, 9.2))
        val tint = if (redIn < 0.5) 0xFFFFFBEE.toInt() else if (pinkIn < 0.5) 0xFFFFF0EF.toInt() else 0xFFFFF0FB.toInt()
        val bubbleScale = entrance * (1 + flash * 0.055)
        saved {
            canvas.translate(252f, (255 - bob * 0.6).toFloat())
            canvas.scale(bubbleScale.toFloat(), bubbleScale.toFloat())
            oval(-88.0, -63.0, 176.0, 126.0, tint)
            path(MascotSocialTourPaths.bubble, tint)
            saved(alpha = 1 - redIn) {
                canvas.rotate(-16f)
                rect(-31.0, -31.0, 62.0, 62.0, YELLOW, 7.0)
            }
            if (redIn > 0) saved(alpha = redIn * (1 - pinkIn)) {
                transformed(MascotSocialTourPaths.redBloom, 0.0, 0.0, 80.8, 85.2, 0.0, RED)
            }
            if (pinkIn > 0) saved(alpha = pinkIn) {
                memoryPlay(MascotSocialTourPaths.memoryLarge, -10.0, -9.558013, 74.0, 83.0)
                memoryPlay(MascotSocialTourPaths.memorySmall, 48.5, 0.441987, 49.0, 51.0)
            }
        }
    }

    private fun memoryPlay(shape: Path, x: Double, y: Double, w: Double, h: Double) {
        saved {
            canvas.translate((x - w / 2).toFloat(), (y - h / 2).toFloat())
            canvas.scale(w.toFloat(), h.toFloat())
            path(shape, PINK)
        }
    }

    private fun pulse(t: Double, center: Double, duration: Double): Double {
        val p = M.segment(t, center - duration / 2, center + duration / 2)
        return if (p > 0 && p < 1) sin(PI * p) else 0.0
    }

    private fun transformed(path: Path, x: Double, y: Double, w: Double, h: Double, angle: Double = 0.0, fill: Int) {
        saved {
            canvas.translate(x.toFloat(), y.toFloat())
            canvas.rotate(angle.toFloat())
            canvas.scale(w.toFloat(), h.toFloat())
            path(path, fill)
        }
    }

    private fun note(x: Double, y: Double, scale: Double, angle: Double, fill: Int) {
        saved {
            canvas.translate(x.toFloat(), y.toFloat())
            canvas.rotate(angle.toFloat())
            canvas.scale(scale.toFloat(), scale.toFloat())
            oval(-5.0, 5.0, 7.0, 5.0, fill)
            rect(0.0, -8.0, 2.0, 15.0, fill)
            path(MascotSocialTourPaths.noteFlag, fill)
        }
    }
}
