package com.ngocdevv.nativecook.blocks.onboarding.coincardonboarding

import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.*

internal class CoinCardOnboardingAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val sounds: Map<String, Int>
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        sounds = listOf("flip", "page", "tap").associateWith { name ->
            context.assets.openFd("coincardonboarding/coincardonboarding_cco-$name.wav").use { pool.load(it, 1) }
        }
    }
    fun play(name: String) {
        val id = sounds[name] ?: return
        if (id !in loaded || manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        streams[name]?.let { pool.stop(it) }
        val volume = if (name == "tap") 0.5f else 0.7f
        streams[name] = pool.play(id, volume, volume, 1, 0, 1f)
    }
    fun close() { pool.release() }
}

internal class CoinCardOnboardingResources(context: Context) {
    val title = FontFamily(Font("coincardonboarding/coincardonboarding_Outfit-Medium.ttf", context.assets))
    val regular = FontFamily(Font("coincardonboarding/coincardonboarding_Inter-Regular.ttf", context.assets))
    val medium = FontFamily(Font("coincardonboarding/coincardonboarding_Inter-Medium.ttf", context.assets))
    private val small = textPaint(context, "Inter-Medium", 12f)
    private val big = textPaint(context, "Outfit-Medium", 26f)
    val symbol = textPaint(context, "Inter-Bold", 34f)
    val bodies = arrayOf(cardBody(false), cardBody(true))
    val details = arrayOf(cardDetails(false), cardDetails(true))
    val shadow = bitmap(416, 202) { c ->
        c.drawOval(78f, 78f, 338f, 124f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(176, 48, 111); // Android takes a blur radius; RN MakeBlur takes sigma (sigma = radius / sqrt(3) + 0.5).
            maskFilter = BlurMaskFilter(((26f - 0.5f) * sqrt(3f)), BlurMaskFilter.Blur.NORMAL)
        })
    }
    private fun textPaint(context: Context, name: String, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
        typeface = Typeface.createFromAsset(context.assets, "coincardonboarding/coincardonboarding_$name.ttf")
        textSize = size
        fontFeatureSettings = "'kern' 0, 'liga' 0"
    }
    private fun bitmap(w: Int, h: Int, draw: (Canvas) -> Unit): Bitmap {
        val result = Bitmap.createBitmap(w * 3, h * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result); canvas.scale(3f, 3f); draw(canvas)
        return result
    }
    private fun cardBody(front: Boolean) = bitmap(252, 160) { c ->
        val colors = if (front) intArrayOf(0xffe0479a.toInt(), 0xff8e1450.toInt(), 0xff5c0a33.toInt(), 0xffc72f7f.toInt()) else intArrayOf(0xffc7a6ff.toInt(), 0xff9b72ee.toInt(), 0xff7c55d6.toInt(), 0xffb896ff.toInt())
        c.drawRoundRect(0f, 0f, 252f, 160f, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 252f, 160f, colors, floatArrayOf(0f, 0.45f, 0.72f, 1f), Shader.TileMode.CLAMP)
        })
    }
    private fun cardDetails(front: Boolean) = bitmap(252, 160) { c ->
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59ffffff; style = Paint.Style.STROKE; strokeWidth = 1f }
        c.drawRoundRect(0.5f, 0.5f, 251.5f, 159.5f, 17.5f, 17.5f, rim)
        rim.color = Color.WHITE; rim.strokeWidth = 3f; rim.strokeCap = Paint.Cap.ROUND
        repeat(4) { i ->
            val a = i * PI / 4
            c.drawLine((26 - cos(a) * 8).toFloat(), (26 - sin(a) * 8).toFloat(), (26 + cos(a) * 8).toFloat(), (26 + sin(a) * 8).toFloat(), rim)
        }
        small.color = 0xbfffffff.toInt(); c.drawText("Balance", 18f, 108f, small)
        val text = if (front) "$ 24 597" else "$ 2 597"
        big.color = Color.WHITE; c.drawText(text, 18f, 138f, big)
        val widths = FloatArray(text.length); big.getTextWidths(text, widths)
        big.color = 0x8cffffff.toInt(); c.drawText(".36", 18f + widths.sum(), 138f, big)
    }
}
