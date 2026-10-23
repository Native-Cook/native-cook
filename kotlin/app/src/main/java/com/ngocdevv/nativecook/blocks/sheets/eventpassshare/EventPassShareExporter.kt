package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

private const val ExportRetentionMillis = 24L * 60L * 60L * 1_000L
private val ExportSequence = AtomicInteger(0)

/**
 * Host setup: register androidx.core.content.FileProvider with authority
 * "${applicationId}.eventpassshare", exported=false, grantUriPermissions=true,
 * and android.support.FILE_PROVIDER_PATHS pointing to eventpassshare_paths.xml.
 * That resource grants access only to the event-pass-share cache subdirectory.
 */
internal fun createEventPassTextShareIntent(context: Context): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, EventPassShareContent.Title)
        putExtra(Intent.EXTRA_TEXT, EventPassShareContent.Details)
    }
    return Intent.createChooser(send, "Share Event")
}

internal suspend fun createEventPassImageShareIntent(
    context: Context,
    assets: EventPassShareAssets,
): Intent = withContext(Dispatchers.IO) {
    val bitmap = renderEventPassExport(assets, scale = 2)
    val directory = File(context.cacheDir, "event-pass-share").apply { mkdirs() }
    val now = System.currentTimeMillis()
    directory.listFiles()?.forEach { candidate ->
        val timestamp = Regex("event-pass-(\\d+)-\\d+\\.png").matchEntire(candidate.name)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        if (timestamp != null && now - timestamp > ExportRetentionMillis) {
            runCatching { candidate.delete() }
        }
    }
    val file = File(directory, "event-pass-$now-${ExportSequence.getAndIncrement()}.png")
    FileOutputStream(file).use { stream ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Unable to encode event pass" }
    }
    val authority = context.packageName + ".eventpassshare"
    val uri = FileProvider.getUriForFile(context, authority, file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_SUBJECT, EventPassShareContent.Title)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, EventPassShareContent.Title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    Intent.createChooser(send, "Share event pass").apply {
        clipData = send.clipData
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun renderEventPassExport(assets: EventPassShareAssets, scale: Int): Bitmap {
    val factor = scale.toFloat()
    val bitmap = Bitmap.createBitmap(362 * scale, 541 * scale, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 0f, 541f * factor, Color.rgb(101, 101, 101), Color.rgb(144, 144, 144), Shader.TileMode.CLAMP)
    canvas.drawRoundRect(RectF(0f, 0f, 362f * factor, 541f * factor), 14f * factor, 14f * factor, paint)
    paint.shader = null

    val pass = RectF(46.5f * factor, 80f * factor, 315.5f * factor, 461f * factor)
    paint.color = Color.argb(31, 255, 255, 255)
    canvas.drawRoundRect(pass, 23f * factor, 23f * factor, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = factor
    paint.color = Color.argb(46, 255, 255, 255)
    canvas.drawRoundRect(pass, 23f * factor, 23f * factor, paint)
    paint.style = Paint.Style.FILL

    canvas.save()
    val posterClip = Path().apply {
        addRoundRect(
            RectF(56f * factor, 89f * factor, 307f * factor, 340f * factor),
            13f * factor,
            13f * factor,
            Path.Direction.CW,
        )
    }
    canvas.clipPath(posterClip)
    paint.color = Color.rgb(250, 249, 247)
    canvas.drawRect(56f * factor, 89f * factor, 307f * factor, 340f * factor, paint)
    drawPoster(canvas, paint, assets, 56f * factor, 89f * factor, factor)
    canvas.restore()

    drawText(canvas, paint, EventPassShareContent.Title, 64f, 379f, 22f, factor, Color.WHITE, Typeface.DEFAULT_BOLD, exactWidth = 235f, tracking = -0.55f)
    drawText(canvas, paint, EventPassShareContent.DateAndPlace, 64f, 444f, 14f, factor, Color.argb(235, 255, 255, 255), Typeface.DEFAULT_BOLD, maxWidth = 235f, minimumScale = 0.78f)
    drawText(canvas, paint, "common✦", 181f, 513f, 20f, factor, Color.argb(79, 255, 255, 255), Typeface.DEFAULT_BOLD, centered = true)
    return bitmap
}

private fun drawPoster(
    canvas: Canvas,
    paint: Paint,
    assets: EventPassShareAssets,
    originX: Float,
    originY: Float,
    scale: Float,
) {
    val green = Color.rgb(22, 79, 60)
    drawText(canvas, paint, "FIELD & FELLOW", originX / scale + 38f, originY / scale + 23f, 7f, scale, green, Typeface.create(Typeface.SERIF, Typeface.BOLD), tracking = -0.2f)
    drawText(canvas, paint, "COMMON\nGROUND", originX / scale + 215f, originY / scale + 21f, 5.3f, scale, Color.rgb(37, 42, 39), Typeface.DEFAULT_BOLD, centered = false, alignRight = true, lineHeight = 5.3f, tracking = 0.8f)
    drawText(canvas, paint, "PAWS, PEOPLE &\nPURPOSE", originX / scale + 38f, originY / scale + 70f, 22f, scale, green, Typeface.DEFAULT, lineHeight = 21f, tracking = -0.7f)
    drawText(canvas, paint, "A COMMUNITY CONVERSATION ON HOW WE SHOW UP FOR SHELTER DOGS", originX / scale + 38f, originY / scale + 100.5f, 4.5f, scale, Color.rgb(38, 50, 45), Typeface.MONOSPACE, tracking = -0.16f)

    val destination = Rect(
        (originX + 38f * scale).toInt(),
        (originY + 105f * scale).toInt(),
        (originX + 183f * scale).toInt(),
        (originY + 134f * scale).toInt(),
    )
    canvas.drawBitmap(assets.portraitStripBitmap, null, destination, paint)
    listOf("ARI", "MAYA", "JULES", "NOAH", "LEA").forEachIndexed { index, name ->
        drawText(canvas, paint, name, originX / scale + 38f + index * 29f, originY / scale + 141f, 4.2f, scale, Color.rgb(49, 54, 50), Typeface.MONOSPACE)
    }
    canvas.save()
    canvas.rotate(-4f, originX + 92f * scale, originY + 171f * scale)
    drawText(canvas, paint, "Saturday July 18", originX / scale + 39f, originY / scale + 174f, 14f, scale, Color.rgb(184, 153, 101), Typeface.create(Typeface.SERIF, Typeface.ITALIC), maxWidth = 108f, minimumScale = 0.72f)
    canvas.restore()
    drawText(canvas, paint, "PANEL  ·  FIRESIDE CHAT\nTRAINING Q&A\nSILENT AUCTION FUNDRAISER\nLOCAL VENDORS", originX / scale + 150f, originY / scale + 164f, 4.5f, scale, Color.rgb(48, 54, 49), Typeface.MONOSPACE, lineHeight = 5f)
    drawText(canvas, paint, "2–4 PM\nGOODFOLK COFFEE, WEST LA", originX / scale + 38f, originY / scale + 204f, 5.5f, scale, Color.rgb(48, 54, 49), Typeface.MONOSPACE, lineHeight = 8f)
    drawText(canvas, paint, "SUPPORTED BY", originX / scale + 38f, originY / scale + 233f, 3.6f, scale, Color.rgb(88, 91, 87), Typeface.MONOSPACE)
    drawText(canvas, paint, "GOOD DOGS", originX / scale + 47f, originY / scale + 246f, 5.2f, scale, Color.rgb(51, 58, 53), Typeface.DEFAULT_BOLD)
    drawText(canvas, paint, "PET KIND", originX / scale + 96f, originY / scale + 246f, 5.5f, scale, Color.rgb(51, 58, 53), Typeface.create(Typeface.SERIF, Typeface.ITALIC))
    val leaf = Path().apply {
        moveTo(originX + 152f * scale, originY + 246f * scale)
        cubicTo(originX + 157f * scale, originY + 237f * scale, originX + 163f * scale, originY + 237f * scale, originX + 168f * scale, originY + 246f * scale)
        cubicTo(originX + 162f * scale, originY + 243f * scale, originX + 158f * scale, originY + 243f * scale, originX + 152f * scale, originY + 246f * scale)
        close()
        moveTo(originX + 160f * scale, originY + 238f * scale)
        lineTo(originX + 160f * scale, originY + 248f * scale)
    }
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = scale
    paint.color = Color.rgb(51, 58, 53)
    canvas.drawPath(leaf, paint)
    paint.style = Paint.Style.FILL
}

private fun drawText(
    canvas: Canvas,
    paint: Paint,
    text: String,
    x: Float,
    baseline: Float,
    textSize: Float,
    scale: Float,
    color: Int,
    typeface: Typeface,
    centered: Boolean = false,
    alignRight: Boolean = false,
    lineHeight: Float = textSize * 1.15f,
    maxWidth: Float? = null,
    minimumScale: Float = 1f,
    exactWidth: Float? = null,
    tracking: Float = 0f,
) {
    paint.shader = null
    paint.style = Paint.Style.FILL
    paint.color = color
    val baseTextSize = textSize * scale
    paint.textSize = baseTextSize
    paint.typeface = typeface
    paint.letterSpacing = if (textSize > 0f) tracking / textSize else 0f
    paint.textScaleX = 1f
    paint.textAlign = when {
        centered -> Paint.Align.CENTER
        alignRight -> Paint.Align.RIGHT
        else -> Paint.Align.LEFT
    }
    val lines = text.split('\n')
    val widestLine = lines.maxOfOrNull { line -> paint.measureText(line) } ?: 0f
    val fittedScale = if (maxWidth != null && widestLine > 0) {
        (maxWidth * scale / widestLine).coerceIn(minimumScale, 1f)
    } else {
        1f
    }
    paint.textSize = baseTextSize * fittedScale
    if (exactWidth != null && widestLine > 0f) {
        paint.textScaleX = exactWidth * scale / widestLine
    }
    lines.forEachIndexed { index, line ->
        canvas.drawText(line, x * scale, (baseline + index * lineHeight * fittedScale) * scale, paint)
    }
}
