package com.ngocdevv.nativecook.blocks.lists.badgevaultgrid

import android.annotation.TargetApi
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.pow

internal class BadgeVaultRenderer(private val resources: android.content.res.Resources) {
    @Volatile private var badgeGpu: BadgeGpu? = null
    @Volatile private var floodGpu: FloodGpu? = null
    @Volatile private var pillGpu: PillGpu? = null
    private val restingBadgeResources = intArrayOf(
        R.drawable.badgevaultgrid_badge_00,
        R.drawable.badgevaultgrid_badge_01,
        R.drawable.badgevaultgrid_badge_02,
        R.drawable.badgevaultgrid_badge_03,
        R.drawable.badgevaultgrid_badge_04,
        R.drawable.badgevaultgrid_badge_05,
        R.drawable.badgevaultgrid_badge_06,
        R.drawable.badgevaultgrid_badge_07,
        R.drawable.badgevaultgrid_badge_08,
        R.drawable.badgevaultgrid_badge_09,
        R.drawable.badgevaultgrid_badge_10,
        R.drawable.badgevaultgrid_badge_11,
        R.drawable.badgevaultgrid_badge_12,
        R.drawable.badgevaultgrid_badge_13,
        R.drawable.badgevaultgrid_badge_14,
        R.drawable.badgevaultgrid_badge_15,
        R.drawable.badgevaultgrid_badge_16,
        R.drawable.badgevaultgrid_badge_17,
    )
    @Volatile private var restingBadges = emptyList<android.graphics.Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val path = Path()

    fun prepareGpu() {
        if (Build.VERSION.SDK_INT < 33 && restingBadges.isEmpty()) {
            restingBadges = restingBadgeResources.map { BitmapFactory.decodeResource(resources, it) }
        }
        if (Build.VERSION.SDK_INT >= 33 && badgeGpu == null) {
            badgeGpu = runCatching { BadgeGpu(resources) }.getOrNull()
            floodGpu = runCatching { FloodGpu() }.getOrNull()
            pillGpu = runCatching { PillGpu() }.getOrNull()
        }
    }

    fun draw(
        canvas: Canvas,
        layout: BadgeVaultLayout,
        engine: BadgeVaultEngine,
        hero: BadgeVaultHero,
        reduced: Boolean,
    ) {
        canvas.drawColor(Color.BLACK)
        val listAlpha = 1f - 0.88f * hero.recede
        val listScale = if (reduced) 1f else 1f - 0.06f * hero.recede
        canvas.save()
        canvas.scale(listScale, listScale, layout.width / 2f, layout.height / 2f)
        paint.alpha = (listAlpha * 255f).roundToInt()
        drawHeader(canvas, layout, engine)
        drawCells(canvas, layout, engine, hero, reduced, listAlpha)
        drawFooter(canvas, layout, engine, listAlpha)
        canvas.restore()
        paint.alpha = 255
        if (hero.index >= 0) {
            val badge = BadgeVaultBadges[hero.index]
            drawFlood(canvas, layout, badge, hero)
            drawBadge(canvas, badge, hero.pose, hero.alpha)
            drawHeroDetails(canvas, layout, engine, badge, hero, reduced)
        }
    }

    private fun drawHeader(canvas: Canvas, layout: BadgeVaultLayout, engine: BadgeVaultEngine) {
        val offset = -engine.scrollY
        label(canvas, "COLLECTION", 24f, layout.safeTop + 12f + offset, 12f, Color.argb(97, 235, 235, 245), medium, align = Paint.Align.LEFT, letterSpacing = 1.2f)
        label(canvas, "Badge Vault", 24f, layout.safeTop + 32f + offset, 34f, Color.WHITE, bold, Paint.Align.LEFT)
        val count = engine.earned.count { it }
        label(canvas, "${BadgeVaultCountWords[count]} earned. Tap one to hold it.", 24f, layout.safeTop + 81f + offset, 15f, Color.argb(153, 235, 235, 245), align = Paint.Align.LEFT)
    }

    private fun drawCells(
        canvas: Canvas,
        layout: BadgeVaultLayout,
        engine: BadgeVaultEngine,
        hero: BadgeVaultHero,
        reduced: Boolean,
        alpha: Float,
    ) {
        BadgeVaultBadges.forEachIndexed { index, badge ->
            val column = index % 3
            val columnLeft = layout.columnLeft(column)
            val columnWidth = layout.columnWidth(column)
            val rowTop = layout.headerHeight + (index / 3) * layout.pitch - engine.scrollY
            if (rowTop >= layout.height || rowTop + layout.pitch <= 0f) return@forEachIndexed
            val selectedAlpha = if (hero.index == index) 0f else 1f
            val pose = engine.cellPose(layout, index, reduced)
            if (badgeGpu != null) drawBadge(canvas, badge, pose, alpha)
            else drawRestingBadge(canvas, index, pose, alpha)
            val nameColor = if (engine.earned[index]) Color.WHITE else Color.argb(153, 235, 235, 245)
            label(canvas, badge.name, columnLeft + columnWidth / 2f, rowTop + layout.nameY, 13f, withAlpha(nameColor, alpha * selectedAlpha), medium)
            if (badge.goal == null || engine.earned[index]) {
                label(canvas, badge.earned ?: BadgeVaultUnlockDate, columnLeft + columnWidth / 2f, rowTop + layout.dateY, 12f, Color.argb((153f * alpha * selectedAlpha).roundToInt(), 235, 235, 245))
            } else {
                label(canvas, "${engine.have[index]} / ${badge.goal.need} ${badge.goal.unit}", columnLeft + columnWidth / 2f, rowTop + layout.dateY, 12f, Color.argb((97f * alpha).roundToInt(), 235, 235, 245))
                val barX = columnLeft + (columnWidth - 72f) / 2f
                paint.color = Color.argb((31f * alpha).roundToInt(), 255, 255, 255)
                canvas.drawRoundRect(barX, rowTop + layout.barY, barX + 72f, rowTop + layout.barY + 4f, 2f, 2f, paint)
                paint.color = withAlpha(badge.a.color().toArgb(), alpha)
                canvas.drawRoundRect(barX, rowTop + layout.barY, barX + 72f * engine.progress(index), rowTop + layout.barY + 4f, 2f, 2f, paint)
            }
        }
    }

    private fun drawFooter(canvas: Canvas, layout: BadgeVaultLayout, engine: BadgeVaultEngine, alpha: Float) {
        val y = layout.footerY - engine.scrollY
        if (y > layout.height + 100f || y < -120f) return
        paint.color = Color.argb((31f * alpha).roundToInt(), 255, 255, 255)
        canvas.drawRect(16f, y, layout.width - 16f, y + 0.5f, paint)
        val count = engine.earned.count { it }
        val remaining = BadgeVaultBadges.size - count
        label(canvas, "$count of 18", 24f, y + 24f, 22f, withAlpha(Color.WHITE, alpha), medium, Paint.Align.LEFT)
        label(canvas, if (remaining > 0) "collected · $remaining in progress" else "collected · all done", 24f, y + 52f, 13f, Color.argb((153f * alpha).roundToInt(), 235, 235, 245), align = Paint.Align.LEFT)
        drawPill(canvas, layout.width - 156f, y + 24f, 132f, 48f, floatArrayOf(.96f, .95f, .92f), engine.pillPress(), remaining == 0, alpha)
        label(canvas, if (remaining > 0) "Log a day" else "All collected", layout.width - 90f, y + 39f, 15f, if (remaining > 0) withAlpha(Color.rgb(11, 11, 12), alpha) else Color.argb((153f * alpha).roundToInt(), 235, 235, 245), medium)
    }

    private fun drawFlood(canvas: Canvas, layout: BadgeVaultLayout, badge: BadgeVaultBadge, hero: BadgeVaultHero) {
        val gpu = floodGpu
        if (gpu != null) {
            gpu.draw(canvas, paint, layout, badge.a, hero)
            return
        }
        val radius = max(layout.width, layout.height) * hero.floodSpread
        paint.shader = android.graphics.RadialGradient(
            hero.originX,
            hero.originY,
            max(1f, radius),
            intArrayOf(withAlpha(badge.a.color().toArgb(), .58f * hero.floodPower), Color.BLACK),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, layout.width, layout.height, paint)
        paint.shader = null
    }

    private fun drawHeroDetails(
        canvas: Canvas,
        layout: BadgeVaultLayout,
        engine: BadgeVaultEngine,
        badge: BadgeVaultBadge,
        hero: BadgeVaultHero,
        reduced: Boolean,
    ) {
        fun reveal(at: Float): Float {
            val duration = if (reduced) 200f else 240f
            val time = hero.age - if (reduced) 0f else at
            return clamp(time / duration) * hero.details * hero.alpha
        }
        val closeAlpha = reveal(520f)
        drawPill(canvas, 16f, layout.safeTop + 4f, 36f, 36f, floatArrayOf(.09f, .09f, .10f), 0f, false, closeAlpha)
        paint.color = Color.argb((235f * closeAlpha).roundToInt(), 255, 255, 255)
        paint.strokeWidth = 2f
        canvas.drawLine(27f, layout.safeTop + 15f, 41f, layout.safeTop + 29f, paint)
        canvas.drawLine(41f, layout.safeTop + 15f, 27f, layout.safeTop + 29f, paint)

        val chipAlpha = reveal(520f)
        val chipText = "${badge.rarity}% OF MEMBERS HOLD THIS"
        text.textSize = 11f
        text.typeface = medium
        val chipWidth = text.measureText(chipText) + 24f
        paint.color = withAlpha(badge.a.color().toArgb(), .14f * chipAlpha)
        canvas.drawRoundRect((layout.width - chipWidth) / 2f, layout.chipY, (layout.width + chipWidth) / 2f, layout.chipY + 28f, 14f, 14f, paint)
        label(canvas, chipText, layout.width / 2f, layout.chipY + 7f, 11f, withAlpha(lighten(badge.a, .35f), chipAlpha), medium, letterSpacing = .9f)

        val cellColumn = badge.tile % 3
        val cellNameX = layout.columnLeft(cellColumn) + layout.columnWidth(cellColumn) / 2f
        val cellNameY = layout.headerHeight + (badge.tile / 3) * layout.pitch - engine.scrollY + layout.nameY
        val transitionAge = engine.now - engine.phaseAt
        val nameProgress = if (reduced) 1f else flightSpring(transitionAge / 1000f, 52f, 8.8f, .72f, .9f)
        val returning = engine.phase == BadgeVaultPhase.Closing
        val nameX = if (returning) lerp(layout.width / 2f, cellNameX, nameProgress) else lerp(cellNameX, layout.width / 2f, nameProgress)
        val nameY = if (returning) lerp(layout.titleY, cellNameY, nameProgress) else lerp(cellNameY, layout.titleY, nameProgress)
        val nameSize = if (returning) lerp(layout.titleSize, 13f, nameProgress) else lerp(13f, layout.titleSize, nameProgress)
        label(canvas, badge.name, nameX, nameY, nameSize, withAlpha(Color.WHITE, hero.alpha), bold)
        label(canvas, "Earned ${badge.earned ?: BadgeVaultUnlockDate}", layout.width / 2f, layout.earnedY, 15f, Color.argb((153f * reveal(640f)).roundToInt(), 235, 235, 245))
        label(canvas, badge.blurb, layout.width / 2f, layout.blurbY, 15f, Color.argb((199f * reveal(700f)).roundToInt(), 235, 235, 245))

        badge.stats.forEachIndexed { index, stat ->
            val a = reveal(760f + index * 40f)
            val left = 32f + (layout.width - 64f) * index / 3f
            val right = 32f + (layout.width - 64f) * (index + 1) / 3f
            if (index > 0) {
                paint.color = Color.argb((31f * a).roundToInt(), 255, 255, 255)
                canvas.drawRect(left, layout.statsY + 6f, left + 0.5f, layout.statsY + 50f, paint)
            }
            val value = if (badge.goal != null && (badge.id == "iron-month" || badge.id == "crowned") && index < 2) {
                if (index == 0) engine.have[badge.tile].toString() else max(0, badge.goal.need - engine.have[badge.tile]).toString()
            } else stat.value
            label(canvas, value, (left + right) / 2f, layout.statsY + 6f, 20f, withAlpha(Color.WHITE, a), medium)
            label(canvas, stat.label, (left + right) / 2f, layout.statsY + 32f, 12f, Color.argb((97f * a).roundToInt(), 235, 235, 245))
        }
        layout.hintY?.let { hintY ->
            val hint = if (reduced) "Swipe down to put it back" else "Drag to turn  ·  Swipe down to put it back"
            label(canvas, hint, layout.width / 2f, hintY, 12f, Color.argb((97f * reveal(900f)).roundToInt(), 235, 235, 245))
        }
        val shareAlpha = reveal(960f)
        drawPill(canvas, 24f, layout.shareY, layout.width - 48f, 56f, badge.a, 0f, false, shareAlpha)
        label(canvas, "Share badge", layout.width / 2f, layout.shareY + 17f, 17f, withAlpha(enamelLabel(badge.a), shareAlpha), medium)
    }

    private fun drawBadge(canvas: Canvas, badge: BadgeVaultBadge, pose: BadgeVaultPose, alpha: Float) {
        val gpu = badgeGpu
        if (gpu != null) {
            gpu.draw(canvas, paint, badge, pose, alpha)
            return
        }
        drawBadgeFallback(canvas, badge, pose, alpha)
    }

    private fun drawRestingBadge(canvas: Canvas, index: Int, pose: BadgeVaultPose, alpha: Float) {
        val extent = 67.5f * pose.r / 50f
        paint.alpha = (alpha * pose.present * 255f).roundToInt()
        val bitmap = restingBadges.getOrNull(index)
        if (bitmap == null) {
            drawBadgeFallback(canvas, BadgeVaultBadges[index], pose, alpha)
            return
        }
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(pose.cx - extent, pose.cy - extent, pose.cx + extent, pose.cy + extent),
            paint,
        )
        paint.alpha = 255
    }

    private fun drawBadgeFallback(canvas: Canvas, badge: BadgeVaultBadge, pose: BadgeVaultPose, alpha: Float) {
        val radius = pose.r
        path.reset()
        repeat(6) { index ->
            val angle = PI / 3.0 * index - PI / 6.0
            val x = pose.cx + cos(angle).toFloat() * radius
            val y = pose.cy + sin(angle).toFloat() * radius * 1.08f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        paint.shader = LinearGradient(pose.cx - radius, pose.cy - radius, pose.cx + radius, pose.cy + radius, lighten(badge.a, .42f), badge.b.color().toArgb(), Shader.TileMode.CLAMP)
        paint.alpha = (alpha * pose.present * 255f).roundToInt()
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, radius * .085f)
        paint.color = Color.rgb(196, 204, 211)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        label(canvas, badge.name.take(1), pose.cx, pose.cy - radius * .28f, radius * .62f, withAlpha(Color.WHITE, alpha * pose.present), bold)
        paint.alpha = 255
    }

    private fun drawPill(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, tint: FloatArray, pressAmount: Float, dark: Boolean, alpha: Float) {
        val gpu = pillGpu
        if (gpu != null) {
            gpu.draw(canvas, paint, x, y, width, height, tint, pressAmount, dark, alpha)
            return
        }
        paint.shader = LinearGradient(x, y, x, y + height, lighten(tint, .34f), tint.color().toArgb(), Shader.TileMode.CLAMP)
        paint.alpha = (alpha * 255f).roundToInt()
        canvas.drawRoundRect(x, y, x + width, y + height, height / 2f, height / 2f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.rgb(196, 204, 211)
        canvas.drawRoundRect(x + 1f, y + 1f, x + width - 1f, y + height - 1f, height / 2f - 1f, height / 2f - 1f, paint)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    private fun label(
        canvas: Canvas,
        value: String,
        x: Float,
        top: Float,
        size: Float,
        color: Int,
        face: Typeface = Typeface.create("sans-serif", Typeface.NORMAL),
        align: Paint.Align = Paint.Align.CENTER,
        letterSpacing: Float = 0f,
    ) {
        text.textSize = size
        text.color = color
        text.typeface = face
        text.textAlign = align
        text.letterSpacing = if (size > 0f) letterSpacing / size else 0f
        canvas.drawText(value, x, top - text.fontMetrics.ascent, text)
    }

    private fun withAlpha(color: Int, alpha: Float) = Color.argb((Color.alpha(color) * clamp(alpha)).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun lighten(color: FloatArray, amount: Float) = Color.rgb(
        ((color[0] + (1f - color[0]) * amount) * 255f).roundToInt(),
        ((color[1] + (1f - color[1]) * amount) * 255f).roundToInt(),
        ((color[2] + (1f - color[2]) * amount) * 255f).roundToInt(),
    )

    private fun enamelLabel(color: FloatArray): Int {
        fun linear(channel: Float) = if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        val luminance = .2126f * linear(color[0]) + .7152f * linear(color[1]) + .0722f * linear(color[2])
        return if (luminance > .45f) Color.rgb(11, 11, 12) else Color.WHITE
    }

    @TargetApi(33)
    private class BadgeGpu(resources: android.content.res.Resources) {
        private val shader = RuntimeShader(BadgeVaultAgsl.Badge)
        private val atlas = BitmapFactory.decodeResource(resources, R.drawable.badgevaultgrid_badge_atlas)
        private val atlasShader = BitmapShader(atlas, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        init { shader.setInputShader("uAtlas", atlasShader) }

        fun draw(canvas: Canvas, paint: Paint, badge: BadgeVaultBadge, pose: BadgeVaultPose, alpha: Float) {
            val sx = sin(-pose.tiltX)
            val cx = cos(-pose.tiltX)
            val sy = sin(pose.spin + pose.tiltY)
            val cy = cos(pose.spin + pose.tiltY)
            val sz = sin(-pose.roll)
            val cz = cos(-pose.roll)
            shader.setFloatUniform("uCentre", pose.cx, pose.cy)
            shader.setFloatUniform("uR", pose.r)
            shader.setFloatUniform("uRot", floatArrayOf(cz * cy, sz * cy, -sy, cz * sy * sx - sz * cx, sz * sy * sx + cz * cx, cy * sx, cz * sy * cx + sz * sx, sz * sy * cx - cz * sx, cy * cx))
            shader.setFloatUniform("uA", badge.a)
            shader.setFloatUniform("uB", badge.b)
            shader.setFloatUniform("uC", badge.c)
            shader.setFloatUniform("uTile", badge.tile.toFloat())
            shader.setFloatUniform("uEnv", pose.envX, pose.envY)
            shader.setFloatUniform("uSheen", pose.sheen)
            shader.setFloatUniform("uFill", pose.fill)
            shader.setFloatUniform("uWave", pose.wave)
            shader.setFloatUniform("uTime", pose.time)
            shader.setFloatUniform("uLift", pose.lift)
            shader.setFloatUniform("uPresent", pose.present)
            shader.setFloatUniform("uSocket", pose.socket)
            paint.shader = shader
            paint.alpha = (alpha * 255f).roundToInt()
            val extent = pose.r * 1.35f
            canvas.drawRect(pose.cx - extent, pose.cy - extent, pose.cx + extent, pose.cy + extent, paint)
            paint.shader = null
            paint.alpha = 255
        }
    }

    @TargetApi(33)
    private class FloodGpu {
        private val shader = RuntimeShader(BadgeVaultAgsl.Flood)
        fun draw(canvas: Canvas, paint: Paint, layout: BadgeVaultLayout, color: FloatArray, hero: BadgeVaultHero) {
            shader.setFloatUniform("uSize", layout.width, layout.height)
            shader.setFloatUniform("uOrigin", hero.originX, hero.originY)
            shader.setFloatUniform("uCentre", hero.pose.cx, hero.pose.cy)
            shader.setFloatUniform("uR", hero.pose.r)
            shader.setFloatUniform("uColour", color)
            shader.setFloatUniform("uSpread", hero.floodSpread)
            shader.setFloatUniform("uPower", hero.floodPower)
            shader.setFloatUniform("uRays", hero.floodRays)
            shader.setFloatUniform("uSince", hero.floodSince)
            shader.setFloatUniform("uTime", hero.age / 1000f)
            paint.shader = shader
            canvas.drawRect(0f, 0f, layout.width, layout.height, paint)
            paint.shader = null
        }
    }

    @TargetApi(33)
    private class PillGpu {
        private val shader = RuntimeShader(BadgeVaultAgsl.Pill)
        fun draw(canvas: Canvas, paint: Paint, x: Float, y: Float, width: Float, height: Float, tint: FloatArray, press: Float, dark: Boolean, alpha: Float) {
            canvas.save()
            canvas.translate(x, y)
            shader.setFloatUniform("uSize", width, height)
            shader.setFloatUniform("uPress", press)
            shader.setFloatUniform("uSheen", -1f)
            shader.setFloatUniform("uDark", if (dark) 1f else 0f)
            shader.setFloatUniform("uTint", tint)
            paint.shader = shader
            paint.alpha = (alpha * 255f).roundToInt()
            canvas.drawRect(0f, 0f, width, height, paint)
            paint.shader = null
            paint.alpha = 255
            canvas.restore()
        }
    }
}
