package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.hypot

/** The intro and map are rasterized once for a size; only the lens and route draw each frame. */
internal class GlassDiscOnboardingPages private constructor(
    val intro: Bitmap,
    val map: Bitmap,
    val route: Path,
) {
    companion object {
        fun create(context: Context, layout: GlassDiscOnboardingLayout): GlassDiscOnboardingPages {
            val faces = Faces(context)
            val fonts = Fonts(layout, faces)
            val intro = Bitmap.createBitmap(layout.width.toInt(), layout.height.toInt(), Bitmap.Config.ARGB_8888)
            val map = Bitmap.createBitmap(layout.width.toInt(), layout.height.toInt(), Bitmap.Config.ARGB_8888)
            drawIntro(Canvas(intro), layout, fonts)
            drawMap(Canvas(map), layout, fonts)
            drawChrome(Canvas(map), layout, fonts)
            return GlassDiscOnboardingPages(intro, map, routePath(layout))
        }

        private class Faces(context: Context) {
            val regular: Typeface = Typeface.createFromAsset(
                context.assets,
                "glassdisconboarding/glassdisconboarding_Inter-Regular.ttf",
            )
            val medium: Typeface = Typeface.createFromAsset(
                context.assets,
                "glassdisconboarding/glassdisconboarding_Inter-Medium.ttf",
            )
            val semibold: Typeface = Typeface.createFromAsset(
                context.assets,
                "glassdisconboarding/glassdisconboarding_Inter-SemiBold.ttf",
            )
        }

        private class Fonts(layout: GlassDiscOnboardingLayout, faces: Faces) {
            val word = font(layout, GlassDiscOnboardingTheme.Type.Word, faces.semibold)
            val headline = font(layout, GlassDiscOnboardingTheme.Type.Headline, faces.semibold)
            val body = font(layout, GlassDiscOnboardingTheme.Type.Body, faces.regular)
            val street = font(layout, GlassDiscOnboardingTheme.Type.Street, faces.medium)
            val search = font(layout, GlassDiscOnboardingTheme.Type.Search, faces.regular)
            val cardTitle = font(layout, GlassDiscOnboardingTheme.Type.CardTitle, faces.semibold)
            val cardMeta = font(layout, GlassDiscOnboardingTheme.Type.CardMeta, faces.regular)
            val chip = font(layout, GlassDiscOnboardingTheme.Type.Chip, faces.medium)
            val button = font(layout, GlassDiscOnboardingTheme.Type.Button, faces.semibold)
        }

        private fun font(layout: GlassDiscOnboardingLayout, size: Float, face: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GlassDiscOnboardingTheme.Color.Ink
            textSize = layout.dp(size)
            typeface = face
            isSubpixelText = true
        }

        private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
            alpha = color ushr 24
        }

        private fun stroke(color: Int, width: Float) = fill(color).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private fun text(canvas: Canvas, value: String, x: Float, y: Float, paint: Paint, color: Int, align: Int = 0) {
            paint.color = color
            paint.alpha = color ushr 24
            val width = paint.measureText(value)
            val left = when (align) {
                1 -> x - width / 2f
                2 -> x - width
                else -> x
            }
            canvas.drawText(value, left, y, paint)
        }

        private fun shadowBox(
            canvas: Canvas,
            rect: RectF,
            radius: Float,
            blur: Float,
            dy: Float,
        ) {
            val paint = fill(0x291D2B22).apply {
                maskFilter = BlurMaskFilter(blur / 2f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRoundRect(RectF(rect.left, rect.top + dy, rect.right, rect.bottom + dy), radius, radius, paint)
        }

        private fun routePath(layout: GlassDiscOnboardingLayout): Path {
            val width = layout.width
            val height = layout.height
            return Path().apply {
                moveTo(width * 0.2f, height * 0.33f)
                cubicTo(width * 0.3f, height * 0.27f, width * 0.46f, height * 0.25f, width * 0.56f, height * 0.31f)
                cubicTo(width * 0.66f, height * 0.37f, width * 0.62f, height * 0.45f, width * 0.74f, height * 0.5f)
                cubicTo(width * 0.86f, height * 0.55f, width * 0.78f, height * 0.62f, width * 0.6f, height * 0.6f)
                cubicTo(width * 0.42f, height * 0.58f, width * 0.3f, height * 0.6f, width * 0.24f, height * 0.52f)
                cubicTo(width * 0.18f, height * 0.45f, width * 0.12f, height * 0.4f, width * 0.2f, height * 0.33f)
            }
        }

        private fun drawIntro(canvas: Canvas, layout: GlassDiscOnboardingLayout, fonts: Fonts) {
            val width = layout.width
            val height = layout.height
            val d = layout.density
            canvas.drawColor(GlassDiscOnboardingTheme.Color.Page)
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    height * 0.5f,
                    0f,
                    height,
                    intArrayOf(0x00E8642E, 0x0FE8642E),
                    null,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, width, height, glow)

            val margin = layout.dp(GlassDiscOnboardingTheme.Layout.Margin)
            val brandY = layout.top + layout.dp(GlassDiscOnboardingTheme.Layout.BrandY)
            canvas.drawCircle(margin + 14f * d, brandY + 14f * d, 14f * d, fill(GlassDiscOnboardingTheme.Color.Brand))
            val mark = Path().apply {
                moveTo(margin + 7f * d, brandY + 18f * d)
                cubicTo(margin + 9f * d, brandY + 6f * d, margin + 21f * d, brandY + 6f * d, margin + 20f * d, brandY + 13f * d)
                cubicTo(margin + 19f * d, brandY + 20f * d, margin + 11f * d, brandY + 19f * d, margin + 13f * d, brandY + 13f * d)
                cubicTo(margin + 15f * d, brandY + 8f * d, margin + 23f * d, brandY + 12f * d, margin + 21f * d, brandY + 20f * d)
            }
            canvas.drawPath(mark, stroke(GlassDiscOnboardingTheme.Color.Page, 2f * d))
            text(canvas, "strolls", margin + 36f * d, brandY + 21f * d, fonts.word, GlassDiscOnboardingTheme.Color.Ink)

            val headlineY = layout.top + layout.dp(GlassDiscOnboardingTheme.Layout.HeadlineY)
            val headlineLine = layout.dp(GlassDiscOnboardingTheme.Type.HeadlineLine)
            listOf("Walks worth taking,", "five minutes from", "your door.").forEachIndexed { index, line ->
                text(canvas, line, margin, headlineY + index * headlineLine, fonts.headline, GlassDiscOnboardingTheme.Color.Ink)
            }
            val bodyY = headlineY + 3f * headlineLine + 8f * d
            val bodyLine = layout.dp(GlassDiscOnboardingTheme.Type.BodyLine)
            listOf("Routes drawn by people who live there,", "with the coffee stop marked.").forEachIndexed { index, line ->
                text(canvas, line, margin, bodyY + index * bodyLine, fonts.body, GlassDiscOnboardingTheme.Color.Mute)
            }
        }

        private fun drawMap(canvas: Canvas, layout: GlassDiscOnboardingLayout, fonts: Fonts) {
            val width = layout.width
            val height = layout.height
            val d = layout.density
            canvas.drawColor(GlassDiscOnboardingTheme.Color.Block)

            canvas.save()
            canvas.rotate(-14f, width / 2f, height / 2f)
            val span = hypot(width, height)
            val grid = Path()
            for (index in -12..12) {
                val x = index * 52f * d + (index * 7 % 11) * d
                grid.moveTo(width / 2f + x, height / 2f - span)
                grid.lineTo(width / 2f + x, height / 2f + span)
                val y = index * 46f * d + (index * 5 % 9) * d
                grid.moveTo(width / 2f - span, height / 2f + y)
                grid.lineTo(width / 2f + span, height / 2f + y)
            }
            canvas.drawPath(grid, stroke(GlassDiscOnboardingTheme.Color.StreetCase, 8.5f * d))
            canvas.drawPath(grid, stroke(GlassDiscOnboardingTheme.Color.Street, 6.5f * d))
            canvas.restore()

            val park = Path().apply {
                moveTo(width * 0.04f, height * 0.23f)
                cubicTo(width * 0.2f, height * 0.17f, width * 0.46f, height * 0.18f, width * 0.62f, height * 0.24f)
                cubicTo(width * 0.7f, height * 0.27f, width * 0.66f, height * 0.36f, width * 0.52f, height * 0.37f)
                cubicTo(width * 0.36f, height * 0.38f, width * 0.2f, height * 0.4f, width * 0.08f, height * 0.37f)
                cubicTo(width * -0.02f, height * 0.34f, width * -0.02f, height * 0.26f, width * 0.04f, height * 0.23f)
                close()
            }
            canvas.drawPath(park, fill(GlassDiscOnboardingTheme.Color.Park))
            canvas.drawPath(park, stroke(GlassDiscOnboardingTheme.Color.ParkEdge, 1.5f * d))
            val random = DeterministicRandom(7)
            canvas.save()
            canvas.clipPath(park)
            val trees = fill(0xFFB5CF9C.toInt())
            repeat(70) {
                canvas.drawCircle(
                    width * (0.04f + 0.62f * random.next()),
                    height * (0.18f + 0.2f * random.next()),
                    (3f + 4f * random.next()) * d,
                    trees,
                )
            }
            canvas.restore()
            canvas.drawOval(
                RectF(width * 0.3f, height * 0.27f, width * 0.46f, height * 0.315f),
                fill(GlassDiscOnboardingTheme.Color.Water),
            )

            val canal = Path().apply {
                moveTo(-20f * d, height * 0.66f)
                cubicTo(width * 0.3f, height * 0.6f, width * 0.62f, height * 0.7f, width + 20f * d, height * 0.56f)
            }
            canvas.drawPath(canal, stroke(GlassDiscOnboardingTheme.Color.WaterEdge, 44f * d))
            canvas.drawPath(canal, stroke(GlassDiscOnboardingTheme.Color.Water, 40f * d))

            val roads = Path().apply {
                moveTo(width * 0.86f, -20f * d)
                cubicTo(width * 0.8f, height * 0.3f, width * 0.88f, height * 0.6f, width * 0.7f, height + 20f * d)
                moveTo(-20f * d, height * 0.47f)
                cubicTo(width * 0.3f, height * 0.44f, width * 0.6f, height * 0.47f, width + 20f * d, height * 0.42f)
            }
            canvas.drawPath(roads, stroke(GlassDiscOnboardingTheme.Color.MajorCase, 13f * d))
            canvas.drawPath(roads, stroke(GlassDiscOnboardingTheme.Color.Major, 11f * d))

            text(canvas, "KESTREL PARK", width * 0.22f, height * 0.215f, fonts.street, 0xFF6F8D5B.toInt())
            text(canvas, "Wend Canal", width * 0.08f, height * 0.645f, fonts.street, 0xFF5E8AA0.toInt())
            text(canvas, "Marlow Road", width * 0.42f, height * 0.452f, fonts.street, GlassDiscOnboardingTheme.Color.Label)
            canvas.save()
            canvas.rotate(-82f, width * 0.84f, height * 0.2f)
            text(canvas, "Hatch Lane", width * 0.84f, height * 0.2f, fonts.street, GlassDiscOnboardingTheme.Color.Label)
            canvas.restore()
            text(canvas, "Corn Exchange", width * 0.5f, height * 0.79f, fonts.street, GlassDiscOnboardingTheme.Color.Label)
        }

        private fun drawChrome(canvas: Canvas, layout: GlassDiscOnboardingLayout, fonts: Fonts) {
            val d = layout.density
            val edge = layout.dp(GlassDiscOnboardingTheme.Layout.Edge)
            val y = layout.top + layout.dp(GlassDiscOnboardingTheme.Layout.SearchY)
            val height = layout.dp(GlassDiscOnboardingTheme.Layout.SearchHeight)
            val back = RectF(edge, y, edge + height, y + height)
            shadowBox(canvas, back, height / 2f, 14f * d, 4f * d)
            canvas.drawCircle(back.centerX(), back.centerY(), height / 2f, fill(0xB8FFFFFF.toInt()))
            canvas.drawCircle(back.centerX(), back.centerY(), height / 2f - 0.5f * d, stroke(0xE6FFFFFF.toInt(), d))
            val chevron = Path().apply {
                moveTo(back.centerX() + 3f * d, back.centerY() - 7f * d)
                lineTo(back.centerX() - 4f * d, back.centerY())
                lineTo(back.centerX() + 3f * d, back.centerY() + 7f * d)
            }
            canvas.drawPath(chevron, stroke(GlassDiscOnboardingTheme.Color.Ink, 2.2f * d))

            val searchX = edge + height + 8f * d
            val searchWidth = layout.width - edge - searchX
            val search = RectF(searchX, y, searchX + searchWidth, y + height)
            shadowBox(canvas, search, height / 2f, 14f * d, 4f * d)
            canvas.drawRoundRect(search, height / 2f, height / 2f, fill(0xB8FFFFFF.toInt()))
            canvas.drawRoundRect(
                RectF(search.left + 0.5f * d, search.top + 0.5f * d, search.right - 0.5f * d, search.bottom - 0.5f * d),
                height / 2f,
                height / 2f,
                stroke(0xE6FFFFFF.toInt(), d),
            )
            canvas.drawCircle(searchX + 22f * d, y + height / 2f - 1.5f * d, 6f * d, stroke(GlassDiscOnboardingTheme.Color.Mute, 1.8f * d))
            canvas.drawLine(
                searchX + 26.5f * d,
                y + height / 2f + 3f * d,
                searchX + 30f * d,
                y + height / 2f + 6.5f * d,
                stroke(GlassDiscOnboardingTheme.Color.Mute, 1.8f * d),
            )
            text(canvas, "Search walks near you", searchX + 40f * d, y + height / 2f + 5f * d, fonts.search, GlassDiscOnboardingTheme.Color.Mute)

            val card = RectF(
                edge,
                layout.cardY,
                layout.width - edge,
                layout.cardY + layout.dp(GlassDiscOnboardingTheme.Layout.CardHeight),
            )
            shadowBox(canvas, card, 24f * d, 24f * d, 8f * d)
            canvas.drawRoundRect(card, 24f * d, 24f * d, fill(GlassDiscOnboardingTheme.Color.Card))
            canvas.drawRoundRect(
                RectF(card.left + 0.5f * d, card.top + 0.5f * d, card.right - 0.5f * d, card.bottom - 0.5f * d),
                24f * d,
                24f * d,
                stroke(0x141D2B22, d),
            )
            text(canvas, "Canal loop", card.left + 20f * d, card.top + 36f * d, fonts.cardTitle, GlassDiscOnboardingTheme.Color.Ink)
            text(canvas, "3.2 km · 45 min · Easy", card.left + 20f * d, card.top + 58f * d, fonts.cardMeta, GlassDiscOnboardingTheme.Color.Mute)
            canvas.drawCircle(card.right - 30f * d, card.top + 30f * d, 6f * d, fill(GlassDiscOnboardingTheme.Color.Route))
            canvas.drawCircle(card.right - 30f * d, card.top + 30f * d, 9f * d, stroke(0x40E8642E, 3f * d))
            var chipX = card.left + 20f * d
            listOf("Shade", "Coffee stop", "Step-free").forEach { chip ->
                val chipWidth = fonts.chip.measureText(chip) + 20f * d
                canvas.drawRoundRect(
                    RectF(chipX, card.top + 72f * d, chipX + chipWidth, card.top + 98f * d),
                    13f * d,
                    13f * d,
                    fill(0xFFEEF1EC.toInt()),
                )
                text(canvas, chip, chipX + 10f * d, card.top + 89f * d, fonts.chip, GlassDiscOnboardingTheme.Color.Brand)
                chipX += chipWidth + 8f * d
            }
            val button = RectF(card.left + 20f * d, card.bottom - 64f * d, card.right - 20f * d, card.bottom - 20f * d)
            canvas.drawRoundRect(button, 22f * d, 22f * d, fill(GlassDiscOnboardingTheme.Color.Go))
            text(canvas, "Start walk", button.centerX(), button.top + 27f * d, fonts.button, 0xFFFFFFFF.toInt(), align = 1)
        }

        private class DeterministicRandom(seed: Int) {
            private var state = seed.toLong()
            fun next(): Float {
                state = state * 16807L % 2147483647L
                return ((state - 1L).toDouble() / 2147483646.0).toFloat()
            }
        }
    }
}
