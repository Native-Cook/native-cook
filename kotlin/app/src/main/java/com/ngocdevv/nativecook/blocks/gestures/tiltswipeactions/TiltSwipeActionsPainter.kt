package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import androidx.compose.ui.graphics.toArgb
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal class TiltSwipeActionsPainter(private val assets: AssetManager) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val paperTile = bitmap("tiltswipeactions/tiltswipeactions_paper@3x.png")
    private val guilloche = bitmap("tiltswipeactions/tiltswipeactions_guilloche@3x.png")
    private val fonts = TiltSwipeActionsFonts(assets)
    private var staticKey = ""
    private var staticPage: Bitmap? = null

    fun draw(
        canvas: Canvas,
        scene: TiltSwipeActionsScene,
        density: Float,
        controller: TiltSwipeActionsController,
    ) {
        val widthPx = max(1, (scene.width * density).toInt())
        val heightPx = max(1, (scene.height * density).toInt())
        ensureStatic(scene, density, widthPx, heightPx)
        staticPage?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }

        canvas.save()
        canvas.scale(density, density)
        val pageAlpha = if (controller.menuOpen) 0.5f else 1f
        if (controller.menuOpen) {
            // The card lives in the cached static layer; wash it toward the paper to match the
            // same 50% page fade applied to the live ledger below.
            fill.color = argb(TiltSwipeActionsColor.Paper, 0.5f)
            rect.set(16f, scene.cardTop, scene.width - 16f, scene.cardTop + 128f)
            canvas.drawRoundRect(rect, 20f, 20f, fill)
        }
        canvas.saveLayerAlpha(0f, 0f, scene.width, scene.height, (pageAlpha * 255).toInt())
        drawSection(canvas, scene, controller)
        drawLedger(canvas, scene, controller)
        drawLink(canvas, scene, controller)
        canvas.restore()
        drawToast(canvas, scene, controller)
        drawFlights(canvas, scene, controller)
        drawTabPanel(canvas, scene, controller)
        drawFinger(canvas, scene, controller)
        canvas.restore()
    }

    private fun ensureStatic(scene: TiltSwipeActionsScene, density: Float, widthPx: Int, heightPx: Int) {
        val key = "$widthPx:$heightPx:${scene.cardTop}:$density"
        if (key == staticKey && staticPage != null) return
        staticPage?.recycle()
        staticPage = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also { out ->
            val c = Canvas(out)
            c.drawColor(TiltSwipeActionsColor.Paper.toArgb())
            val tilePx = (240f * density).toInt().coerceAtLeast(1)
            for (y in 0 until heightPx step tilePx) {
                for (x in 0 until widthPx step tilePx) {
                    c.drawBitmap(paperTile, null, Rect(x, y, min(x + tilePx, widthPx), min(y + tilePx, heightPx)), bitmapPaint)
                }
            }
            c.save()
            c.scale(density, density)
            drawCard(c, scene)
            c.restore()
        }
        staticKey = key
    }

    private fun drawCard(canvas: Canvas, scene: TiltSwipeActionsScene) {
        val x = TiltSwipeActionsLayout.GUTTER
        val y = scene.cardTop
        val w = scene.listWidth
        rect.set(x, y, x + w, y + TiltSwipeActionsLayout.CARD_HEIGHT)
        fill.shader = LinearGradient(
            x + w * 0.386f,
            y - TiltSwipeActionsLayout.CARD_HEIGHT * 0.406f,
            x + w * 0.614f,
            y + TiltSwipeActionsLayout.CARD_HEIGHT * 1.406f,
            intArrayOf(AndroidColor.rgb(34, 35, 42), AndroidColor.rgb(23, 24, 29), AndroidColor.rgb(18, 19, 23)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, 20f, 20f, fill)
        fill.shader = null
        canvas.save()
        canvas.clipRect(rect)
        canvas.drawBitmap(guilloche, null, RectF(x, y, x + 370f, y + 128f), bitmapPaint)
        canvas.restore()
        stroke.color = AndroidColor.argb(97, 200, 173, 126)
        stroke.strokeWidth = 0.75f
        rect.inset(6f, 6f)
        canvas.drawRoundRect(rect, 15f, 15f, stroke)

        trackedText(canvas, "PRIVATE ACCOUNT", x + 24f, y + 22f, fonts.sans500, 11f, 1.98f, TiltSwipeActionsColor.Brass.toArgb())
        text(canvas, "···· 4021", x + w - 24f, y + 22f, fonts.sans400, 12f, argb(TiltSwipeActionsColor.Ivory, 0.55f), Paint.Align.RIGHT)
        val baseline = y + 91f
        var cursor = x + 22f
        cursor = drawAdvance(canvas, "$", cursor, baseline - 7f, fonts.bodoni22r, 22f, TiltSwipeActionsColor.Brass.toArgb()) + 3f
        cursor = drawAdvance(canvas, "1", cursor, baseline, fonts.bodoni48, 48f, TiltSwipeActionsColor.Ivory.toArgb()) - 2f
        cursor = drawAdvance(canvas, ",", cursor, baseline, fonts.bodoni48, 48f, TiltSwipeActionsColor.Ivory.toArgb()) - 1f
        cursor = drawAdvance(canvas, "1", cursor, baseline, fonts.bodoni48, 48f, TiltSwipeActionsColor.Ivory.toArgb()) - 4f
        cursor = drawAdvance(canvas, "48", cursor, baseline, fonts.bodoni48, 48f, TiltSwipeActionsColor.Ivory.toArgb()) + 2f
        text(canvas, ".90", cursor, baseline - 4f, fonts.bodoni22, 22f, TiltSwipeActionsColor.Ivory.toArgb())
        text(canvas, "Available today", x + 24f, y + 100f, fonts.sans400, 12f, argb(TiltSwipeActionsColor.Ivory, 0.50f))
    }

    private fun drawSection(canvas: Canvas, scene: TiltSwipeActionsScene, controller: TiltSwipeActionsController) {
        text(canvas, "Latest spending", 16f, scene.sectionTop - 1f, fonts.bodoniItalic26, 26f, TiltSwipeActionsColor.Ink.toArgb())
        val total = weekTotal(Transactions.indices.filterNot { controller.rows[it].deleted }.map { index ->
            val count = effectiveSplit(index, controller)
            if (count >= 2) -shares(Transactions[index].amount, count)[0] else Transactions[index].amount
        })
        text(
            canvas,
            "${moneyText(total)} this week",
            scene.width - 16f,
            scene.sectionTop + 12.33f,
            fonts.sans400,
            13f,
            TiltSwipeActionsColor.Ink2.toArgb(),
            Paint.Align.RIGHT,
        )
    }

    private fun drawLedger(canvas: Canvas, scene: TiltSwipeActionsScene, controller: TiltSwipeActionsController) {
        val shown = min(Transactions.size, if (controller.scriptToast() != null) scene.capacityUnderToast else scene.capacity)
        val sheetHeight = TiltSwipeActionsLayout.LIST_PAD + max(shown, 1) * TiltSwipeActionsLayout.ROW + 3f
        stroke.strokeWidth = 1f
        stroke.color = TiltSwipeActionsColor.Ink.toArgb()
        canvas.drawLine(16f, scene.listTop, scene.width - 16f, scene.listTop, stroke)
        stroke.color = argb(TiltSwipeActionsColor.Ink, 0.22f)
        canvas.drawLine(16f, scene.listTop + sheetHeight, scene.width - 16f, scene.listTop + sheetHeight, stroke)

        val pull = if (controller.playing && controller.scriptTime in 0.7f..2.9f) controller.scriptedPull() else 0f
        val dim = smooth((pull - 24f) / 28f) * 0.72f
        var slot = 0
        Transactions.forEachIndexed { index, transaction ->
            val state = controller.rows[index]
            if (state.deleted) return@forEachIndexed
            if (slot >= shown) return@forEachIndexed
            val y = scene.rowsTop + slot * 72f
            val alpha = if (pull > 0f && index != 2) 1f - dim else 1f
            canvas.saveLayerAlpha(16f, y - 60f, scene.width - 16f, y + 100f, (alpha * 255).toInt())
            drawRow(canvas, scene, controller, index, transaction, y)
            canvas.restore()
            slot++
        }
    }

    private fun drawRow(
        canvas: Canvas,
        scene: TiltSwipeActionsScene,
        controller: TiltSwipeActionsController,
        index: Int,
        item: TiltSwipeActionsTransaction,
        y: Float,
    ) {
        val state = controller.rows[index]
        val scriptedSplit = controller.playing && index == 2 && controller.scriptTime in 0.7f..2.9f
        val scriptedHeal = controller.playing && index == 2 && controller.scriptTime in 2.9f..3.38f
        val healProgress = if (scriptedHeal) smooth((controller.scriptTime - 2.9f) / 0.48f) else 0f
        val pull = when {
            scriptedSplit -> controller.scriptedPull()
            scriptedHeal -> 170f
            else -> state.pull
        }
        val splitTarget = if (pull > 0f) {
            when {
                pull >= 132f -> 3
                pull >= 72f -> 2
                else -> 1
            }
        } else 1
        val tx = effectiveTx(index, controller)
        val split = effectiveSplit(index, controller)
        if (index > 0) {
            stroke.strokeWidth = 1f
            stroke.color = argb(TiltSwipeActionsColor.Ink, 0.10f)
            canvas.drawLine(16f + 52f, y, scene.width - 16f, y, stroke)
        }
        canvas.save()
        canvas.clipRect(16f, y - 32f, scene.width - 16f, y + 104f)
        if (tx < -1f) drawActions(canvas, scene, state.flagged, tx, y)
        if (pull > 1f) {
            val barAlpha = if (scriptedHeal) 1f - smooth((healProgress - 0.4f) / 0.35f) else 1f
            canvas.saveLayerAlpha(0f, y - 32f, scene.width, y + 104f, (barAlpha * 255f).toInt())
            drawSplitBar(canvas, scene, item, y, pull, splitTarget)
            canvas.restore()
        }

        val veil = if (scriptedHeal) 1f - smooth(healProgress / 0.6f) else smooth((pull - 20f) / 36f)
        val follow = 0.6f * min(pull, 72f)
        val leadFade = if (tx < 0f) 1f - smooth((-tx) / 36f) else 1f
        val contentAlpha = ((1f - veil) * leadFade).coerceIn(0f, 1f)
        canvas.saveLayerAlpha(0f, y, scene.width, y + 72f, (contentAlpha * 255).toInt())
        val leadX = tx + follow
        drawCircleIcon(canvas, 16f + leadX + 20f, y + 36f, item.icon, TiltSwipeActionsColor.Ink.toArgb())
        text(canvas, item.title, 16f + 52f + leadX, y + 16f, fonts.sans500, 16f, TiltSwipeActionsColor.Ink.toArgb())
        if (state.flagged) drawGlyph(canvas, TiltSwipeActionsGlyph.Flag, 16f + 52f + leadX + textWidth(item.title, fonts.sans500, 16f) + 7f, y + 19f, 14f, TiltSwipeActionsColor.BrassPaper.toArgb(), 1.5f)
        val subtitle = if (split >= 2) {
            if (split <= 3) "Split with ${People.subList(1, split).joinToString(", ") { it.name }}" else "Split $split ways"
        } else "${item.category} · ${item.day}"
        text(canvas, subtitle, 16f + 52f + leadX, y + 40f, fonts.sans400, 13f, TiltSwipeActionsColor.Ink2.toArgb())
        canvas.restore()

        val amountAlpha = (1f - veil).coerceIn(0f, 1f)
        canvas.saveLayerAlpha(0f, y, scene.width, y + 72f, (amountAlpha * 255).toInt())
        val trailingX = tx + follow - 16f * smooth((-tx) / 144f)
        val shownAmount = if (split >= 2) -shares(item.amount, split)[0] else item.amount
        drawMoneyRight(canvas, shownAmount, scene.width - 16f + trailingX, y + 47f, 20f, 14f, TiltSwipeActionsColor.Ink.toArgb(), TiltSwipeActionsColor.Ink2.toArgb())
        if (split >= 2) text(canvas, "of ${moneyText(item.amount, false)}", scene.width - 16f + trailingX, y + 52f, fonts.sans400, 12f, TiltSwipeActionsColor.Ink2.toArgb(), Paint.Align.RIGHT)
        canvas.restore()
        canvas.restore()

        if (splitTarget >= 2 && pull > 0f && !scriptedHeal) drawHud(canvas, scene, item.amount, y, splitTarget, pull)
    }

    private fun drawActions(canvas: Canvas, scene: TiltSwipeActionsScene, flagged: Boolean, tx: Float, y: Float) {
        val bar = 370f
        val width = -min(tx, 0f)
        val armed = width > scene.listWidth * 0.55f
        val flagLeft = 16f + bar + tx
        val deleteLeft = 16f + bar + tx / 2f + tx / 2f * if (armed) 1f else 0f
        fill.color = TiltSwipeActionsColor.Flag.toArgb()
        canvas.drawRect(flagLeft, y, scene.width + 144f, y + 72f, fill)
        fill.color = TiltSwipeActionsColor.Delete.toArgb()
        canvas.drawRect(deleteLeft, y, scene.width + 144f, y + 72f, fill)
        val opacity = smooth((width - 96f) / 32f)
        if (opacity <= 0f) return
        val flagCenter = 16f + bar + tx + width / 4f
        val deleteCenter = if (armed) 16f + bar + tx + 36f else 16f + bar + tx / 2f + width / 4f
        drawActionMark(canvas, if (flagged) "UNFLAG" else "FLAG", TiltSwipeActionsGlyph.Flag, flagCenter, y, opacity * if (armed) 0f else 1f)
        drawActionMark(canvas, "DELETE", TiltSwipeActionsGlyph.Trash, deleteCenter, y, max(opacity, if (armed) 1f else 0f))
    }

    private fun drawActionMark(canvas: Canvas, label: String, icon: TiltSwipeActionsGlyph, center: Float, y: Float, alpha: Float) {
        if (alpha <= 0f) return
        val color = argb(TiltSwipeActionsColor.Ivory, alpha)
        drawGlyph(canvas, icon, center - 10f, y + 16f, 20f, color, 1.5f)
        trackedText(canvas, label, center, y + 46f, fonts.sans500, 11f, 1.32f, color, Paint.Align.CENTER)
    }

    private fun drawSplitBar(canvas: Canvas, scene: TiltSwipeActionsScene, item: TiltSwipeActionsTransaction, y: Float, pull: Float, count: Int) {
        val alpha = min(1f, pull / 20f)
        val content = smooth((pull - 56f) / 28f)
        val barX = 16f
        val top = y + 4f
        val barWidth = 370f
        val at = 72f + (count - 2) * 60f
        val gap = if (count < 2) 6f else min(8f, 6f + 0.07f * max(pull - at, 0f))
        val pieceWidth = (barWidth - (count - 1) * gap) / count
        for (piece in 0 until count) {
            val left = barX + piece * (pieceWidth + gap)
            rect.set(left, top, left + pieceWidth, top + 64f)
            fill.color = if (piece == 0 && count >= 2) TiltSwipeActionsColor.Ink.toArgb() else TiltSwipeActionsColor.Stock.toArgb()
            canvas.drawRoundRect(rect, if (piece == 0) 14f else 4f, if (piece == 0) 14f else 4f, fill)
            stroke.color = if (piece == 0 && count >= 2) TiltSwipeActionsColor.Ink.toArgb() else argb(TiltSwipeActionsColor.Ink, 0.14f)
            stroke.strokeWidth = 1f
            canvas.drawRoundRect(rect, if (piece == 0) 14f else 4f, if (piece == 0) 14f else 4f, stroke)
            if (content <= 0f) continue
            val foreground = if (piece == 0 && count >= 2) TiltSwipeActionsColor.Ivory.toArgb() else TiltSwipeActionsColor.Ink.toArgb()
            val secondary = if (piece == 0 && count >= 2) argb(TiltSwipeActionsColor.Ivory, 0.60f) else TiltSwipeActionsColor.Ink2.toArgb()
            val brass = if (piece == 0 && count >= 2) TiltSwipeActionsColor.Brass.toArgb() else TiltSwipeActionsColor.BrassPaper.toArgb()
            if (count <= 3) {
                drawMonogram(canvas, People[piece], left + 24f, top + 32f, piece == 0 && count >= 2, content)
                trackedText(canvas, People[piece].name.uppercase(Locale.US), left + 45f, top + 13f, fonts.sans500, 10f, 1.4f, argb(brass, content))
                drawMoneyLeft(canvas, shares(item.amount, count)[piece], left + 45f, top + 50f, 18f, 13f, argb(foreground, content), argb(secondary, content))
            } else {
                drawMonogram(canvas, People[piece], left + pieceWidth / 2f, top + 20f, piece == 0, content, 24f)
                drawMoneyCentered(canvas, shares(item.amount, count)[piece], left + pieceWidth / 2f, top + 56f, argb(foreground, content), argb(secondary, content))
            }
        }
    }

    private fun drawHud(canvas: Canvas, scene: TiltSwipeActionsScene, amount: Float, rowY: Float, count: Int, pull: Float) {
        val progress = smooth((pull - 72f) / 24f)
        if (progress <= 0f) return
        val x = 16f
        val y = rowY - 56f
        val width = lerp(40f, 230f, progress)
        rect.set(x, y, x + width, y + 40f)
        fill.color = TiltSwipeActionsColor.Ink.toArgb()
        canvas.drawRoundRect(rect, 20f, 20f, fill)
        canvas.save()
        canvas.clipRect(rect)
        drawMoneyLeft(canvas, abs(amount), x + 16f, y + 27f, 18f, 13f, TiltSwipeActionsColor.Ivory.toArgb(), argb(TiltSwipeActionsColor.Ivory, 0.60f))
        text(canvas, "÷", x + 92f, y + 11f, fonts.sans400, 16f, argb(TiltSwipeActionsColor.Ivory, 0.60f))
        for (n in 2..5) {
            val cx = x + 126f + (n - 2) * 30f
            if (n == count) {
                fill.color = TiltSwipeActionsColor.Brass.toArgb()
                canvas.drawCircle(cx, y + 20f, 14f, fill)
            }
            text(canvas, "$n", cx, y + 12f, if (n == count) fonts.sans600 else fonts.sans500, 13f, if (n == count) TiltSwipeActionsColor.Ink.toArgb() else argb(TiltSwipeActionsColor.Ivory, 0.60f), Paint.Align.CENTER)
        }
        canvas.restore()
    }

    private fun drawToast(canvas: Canvas, scene: TiltSwipeActionsScene, controller: TiltSwipeActionsController) {
        val toast = controller.scriptToast() ?: return
        val shown = controller.scriptToastProgress()
        if (shown <= 0f) return
        val alpha = smooth((shown - 0.1f) / 0.2f)
        val top = scene.toastTop + (1f - shown) * 16f
        rect.set(16f, top, scene.width - 16f, top + 56f)
        fill.color = argb(TiltSwipeActionsColor.Ink, alpha)
        canvas.drawRoundRect(rect, 16f, 16f, fill)
        val contentAlpha = smooth((shown - 0.55f) / 0.45f)
        val title: String
        val sub: String
        val lead: Float
        when (toast) {
            is TiltSwipeActionsToast.Split -> {
                val item = Transactions[toast.row]
                val parts = shares(item.amount, toast.count)
                val requested = parts.drop(1).sum()
                val who = People.subList(1, toast.count).joinToString(" and ") { it.name }
                title = "Requested ${moneyText(requested, false)}"
                sub = "$who · ${moneyText(parts[1], false)} each"
                lead = 28f + (toast.count - 2) * 18f
            }
            is TiltSwipeActionsToast.Delete -> {
                val item = Transactions[toast.row]
                title = "Transaction deleted"
                sub = "${item.title} · ${moneyText(item.amount)}"
                lead = 28f
                fill.color = TiltSwipeActionsColor.MonoToast.toArgb()
                canvas.drawCircle(42f, top + 28f, 14f, fill)
                drawGlyph(canvas, TiltSwipeActionsGlyph.Trash, 34f, top + 20f, 16f, TiltSwipeActionsColor.Brass.toArgb(), 1.5f)
            }
        }
        val color = { base: androidx.compose.ui.graphics.Color, opacity: Float -> argb(base, opacity * contentAlpha) }
        val textX = 16f + 12f + lead + 12f
        text(canvas, title, textX, top + 10f, fonts.sans500, 15f, color(TiltSwipeActionsColor.Ivory, 1f))
        text(canvas, sub, textX, top + 31f, fonts.sans400, 12f, color(TiltSwipeActionsColor.Ivory, 0.55f))
        drawGlyph(canvas, TiltSwipeActionsGlyph.Undo, scene.width - 98f, top + 20f, 16f, color(TiltSwipeActionsColor.Brass, 1f), 1.5f)
        text(canvas, "Undo", scene.width - 72f, top + 18f, fonts.sans600, 14f, color(TiltSwipeActionsColor.Brass, 1f))
        val remaining = if (controller.playing) ((5.75f - controller.scriptTime) / 2.69f).coerceIn(0f, 1f) else (1f - controller.toastAge / 4f).coerceIn(0f, 1f)
        fill.color = argb(TiltSwipeActionsColor.Brass, 0.45f * alpha)
        canvas.drawRect(32f, top + 55f, 32f + (scene.width - 64f) * remaining, top + 56f, fill)
    }

    private fun drawFlights(canvas: Canvas, scene: TiltSwipeActionsScene, controller: TiltSwipeActionsController) {
        if (!controller.playing || controller.scriptTime !in 3.06f..3.58f) return
        val t = smooth((controller.scriptTime - 3.06f) / 0.36f)
        val rowY = scene.rowsTop + 2f * 72f + 36f
        for (index in 1..2) {
            val sourceX = 16f + index * (370f + 6f) / 3f + 24f
            val targetX = 42f + (index - 1) * 18f
            val stagger = (t - (index - 1) * 0.16f).coerceIn(0f, 1f)
            val x = lerp(sourceX, targetX, stagger)
            val y = lerp(rowY, scene.toastTop + 28f, stagger) - 24f * kotlin.math.sin(Math.PI.toFloat() * stagger)
            drawMonogram(canvas, People[index], x, y, false, 1f)
        }
    }

    private fun drawLink(canvas: Canvas, scene: TiltSwipeActionsScene, controller: TiltSwipeActionsController) {
        val alpha = 1f - min(controller.scriptToastProgress() / 0.1f, 1f)
        trackedText(canvas, "ALL TRANSACTIONS", scene.width / 2f, scene.linkTop, fonts.sans500, 11f, 1.54f, argb(TiltSwipeActionsColor.BrassPaper, alpha), Paint.Align.CENTER)
    }

    private fun drawTabPanel(canvas: Canvas, scene: TiltSwipeActionsScene, controller: TiltSwipeActionsController) {
        fill.color = TiltSwipeActionsColor.TabBar.toArgb()
        canvas.drawRect(0f, scene.barTop, scene.width, scene.height, fill)
        stroke.color = argb(TiltSwipeActionsColor.Ink, 0.10f)
        stroke.strokeWidth = 1f
        canvas.drawLine(0f, scene.barTop, scene.width, scene.barTop, stroke)
        val tabs = arrayOf(TiltSwipeActionsGlyph.Home, TiltSwipeActionsGlyph.Chart, TiltSwipeActionsGlyph.Card)
        for (index in tabs.indices) {
            val cx = 16f + index * 64f + 28f
            drawGlyph(canvas, tabs[index], cx - 12f, scene.barTop + 16f, 24f, if (index == 0) TiltSwipeActionsColor.Ink.toArgb() else TiltSwipeActionsColor.Ink3.toArgb(), 1.5f)
            if (index == 0) {
                fill.color = TiltSwipeActionsColor.BrassDot.toArgb()
                canvas.drawCircle(cx, scene.barTop + 48f, 2f, fill)
            }
        }
        if (controller.menuOpen) {
            fill.color = argb(TiltSwipeActionsColor.Ink, 0.04f)
            canvas.drawRect(0f, 0f, scene.width, scene.height, fill)
            val left = scene.width - 16f - 220f
            val top = scene.barTop - 8f - 172f
            rect.set(left, top, left + 220f, top + 172f)
            fill.color = TiltSwipeActionsColor.Stock.toArgb()
            canvas.drawRoundRect(rect, 16f, 16f, fill)
            stroke.color = argb(TiltSwipeActionsColor.Ink, 0.14f)
            canvas.drawRoundRect(rect, 16f, 16f, stroke)
            val labels = arrayOf("Income", "Expense", "Transfer")
            val glyphs = arrayOf(TiltSwipeActionsGlyph.Plus, TiltSwipeActionsGlyph.Bag, TiltSwipeActionsGlyph.Undo)
            labels.forEachIndexed { index, label ->
                val y = top + 8f + index * 52f
                text(canvas, label, left + 20f, y + 16f, fonts.sans500, 16f, TiltSwipeActionsColor.Ink.toArgb())
                drawGlyph(canvas, glyphs[index], left + 180f, y + 16f, 20f, TiltSwipeActionsColor.Ink2.toArgb(), 1.5f)
            }
        }
        fill.color = TiltSwipeActionsColor.Ink.toArgb()
        canvas.drawCircle(scene.fabLeft + 24f, scene.fabTop + 25f, 24f, fill)
        canvas.save()
        canvas.rotate(if (controller.menuOpen) 45f else 0f, scene.fabLeft + 24f, scene.fabTop + 25f)
        drawGlyph(canvas, TiltSwipeActionsGlyph.Plus, scene.fabLeft + 13f, scene.fabTop + 14f, 22f, TiltSwipeActionsColor.Ivory.toArgb(), 1.75f)
        canvas.restore()
    }

    private fun drawFinger(canvas: Canvas, scene: TiltSwipeActionsScene, controller: TiltSwipeActionsController) {
        val raw = controller.scriptFinger() ?: return
        val t = controller.scriptTime
        val centerX: Float
        val centerY: Float
        when {
            t in 0.7f..2.9f -> {
                centerX = raw.first
                centerY = scene.rowsTop + 2.5f * 72f + 24f
            }
            t in 3.7f..4.25f -> {
                centerX = raw.first
                centerY = scene.rowsTop + 3.5f * 72f
            }
            t in 5.6f..5.75f -> {
                centerX = scene.width - 56f
                centerY = scene.toastTop + 28f
            }
            else -> {
                centerX = 16f + 120f
                centerY = scene.rowsTop + 3.5f * 72f
            }
        }
        fill.color = argb(TiltSwipeActionsColor.Ink, 0.10f)
        canvas.drawCircle(centerX, centerY, 20f, fill)
        stroke.color = argb(TiltSwipeActionsColor.Ink, 0.18f)
        stroke.strokeWidth = 1f
        canvas.drawCircle(centerX, centerY, 20f, stroke)
    }

    private fun effectiveSplit(index: Int, controller: TiltSwipeActionsController): Int {
        if (!controller.playing) return controller.rows[index].split
        return if (index == 2 && controller.scriptTime in 2.9f..<5.75f) 3 else 0
    }

    private fun effectiveTx(index: Int, controller: TiltSwipeActionsController): Float {
        if (!controller.playing) return controller.rows[index].tx
        if (index != 3) return 0f
        val t = controller.scriptTime
        return when {
            t in 3.7f..4.25f -> controller.scriptedOpen()
            t in 4.25f..7.1f -> -144f
            t in 7.1f..7.22f -> -144f
            t in 7.22f..7.58f -> lerp(-144f, 0f, criticalSpring((t - 7.22f) / 0.36f))
            else -> 0f
        }
    }

    private fun drawCircleIcon(canvas: Canvas, cx: Float, cy: Float, glyph: TiltSwipeActionsGlyph, color: Int) {
        stroke.color = argb(TiltSwipeActionsColor.Ink, 0.16f)
        stroke.strokeWidth = 1f
        canvas.drawCircle(cx, cy, 20f, stroke)
        drawGlyph(canvas, glyph, cx - 10f, cy - 10f, 20f, color, 1.5f)
    }

    /** Unit critically-damped response; the close spring is 360 ms at damping ratio 1. */
    private fun criticalSpring(value: Float): Float {
        val t = value.coerceAtLeast(0f) * 7f
        return (1f - (1f + t) * kotlin.math.exp(-t)).coerceIn(0f, 1f)
    }

    private fun drawMonogram(canvas: Canvas, person: TiltSwipeActionsPerson, cx: Float, cy: Float, inked: Boolean, alpha: Float, size: Float = 28f) {
        fill.color = argb(if (inked) TiltSwipeActionsColor.Brass else TiltSwipeActionsColor.Paper, alpha)
        canvas.drawCircle(cx, cy, size / 2f, fill)
        stroke.color = argb(if (inked) TiltSwipeActionsColor.Ink else TiltSwipeActionsColor.Ink, 0.16f * alpha)
        stroke.strokeWidth = 1f
        canvas.drawCircle(cx, cy, size / 2f, stroke)
        text(canvas, person.initials, cx, cy - 5.5f, fonts.bodoni11, if (size < 28f) 9f else 11f, argb(if (inked) TiltSwipeActionsColor.Ink else TiltSwipeActionsColor.Ink, alpha), Paint.Align.CENTER)
    }

    private fun drawMoneyRight(canvas: Canvas, value: Float, right: Float, baseline: Float, wholeSize: Float, centsSize: Float, wholeColor: Int, centsColor: Int) {
        val (whole, cents) = money(value)
        val centsW = textWidth(cents, fonts.bodoni14, centsSize)
        text(canvas, cents, right, baseline - centsSize, fonts.bodoni14, centsSize, centsColor, Paint.Align.RIGHT)
        text(canvas, whole, right - centsW, baseline - wholeSize, fonts.bodoni20, wholeSize, wholeColor, Paint.Align.RIGHT)
    }

    private fun drawMoneyLeft(canvas: Canvas, value: Float, left: Float, baseline: Float, wholeSize: Float, centsSize: Float, wholeColor: Int, centsColor: Int) {
        val (whole, cents) = money(value, false)
        text(canvas, whole, left, baseline - wholeSize, fonts.bodoni20, wholeSize, wholeColor)
        text(canvas, cents, left + textWidth(whole, fonts.bodoni20, wholeSize), baseline - centsSize, fonts.bodoni14, centsSize, centsColor)
    }

    private fun drawMoneyCentered(canvas: Canvas, value: Float, center: Float, baseline: Float, wholeColor: Int, centsColor: Int) {
        val (whole, cents) = money(value, false)
        val total = textWidth(whole, fonts.bodoni20, 18f) + textWidth(cents, fonts.bodoni14, 13f)
        drawMoneyLeft(canvas, value, center - total / 2f, baseline, 18f, 13f, wholeColor, centsColor)
    }

    private fun drawAdvance(canvas: Canvas, value: String, x: Float, baseline: Float, typeface: Typeface, size: Float, color: Int): Float {
        text(canvas, value, x, baseline - size, typeface, size, color)
        return x + textWidth(value, typeface, size)
    }

    private fun text(canvas: Canvas, value: String, x: Float, top: Float, typeface: Typeface, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) {
        fill.shader = null
        fill.style = Paint.Style.FILL
        fill.typeface = typeface
        fill.textSize = size
        fill.color = color
        fill.textAlign = align
        val metrics = fill.fontMetrics
        canvas.drawText(value, x, top - metrics.ascent, fill)
    }

    private fun trackedText(canvas: Canvas, value: String, x: Float, top: Float, typeface: Typeface, size: Float, tracking: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) {
        fill.typeface = typeface
        fill.textSize = size
        fill.color = color
        fill.textAlign = Paint.Align.LEFT
        val total = value.sumOf { fill.measureText(it.toString()).toDouble() }.toFloat() + max(0, value.length - 1) * tracking
        var cursor = when (align) {
            Paint.Align.CENTER -> x - total / 2f
            Paint.Align.RIGHT -> x - total
            else -> x
        }
        val baseline = top - fill.fontMetrics.ascent
        value.forEachIndexed { index, char ->
            canvas.drawText(char.toString(), cursor, baseline, fill)
            cursor += fill.measureText(char.toString()) + if (index == value.lastIndex) 0f else tracking
        }
    }

    private fun textWidth(value: String, typeface: Typeface, size: Float): Float {
        fill.typeface = typeface
        fill.textSize = size
        return fill.measureText(value)
    }

    private fun drawGlyph(canvas: Canvas, glyph: TiltSwipeActionsGlyph, x: Float, y: Float, size: Float, color: Int, width: Float) {
        stroke.color = color
        stroke.strokeWidth = width * size / 24f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        stroke.style = Paint.Style.STROKE
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(size / 24f, size / 24f)
        glyphPaths.getValue(glyph).forEach { canvas.drawPath(it, stroke) }
        canvas.restore()
    }

    private val glyphPaths: Map<TiltSwipeActionsGlyph, List<Path>> by lazy {
        fun paths(vararg source: String) = source.map(PathParser::createPathFromPathData)
        mapOf(
            TiltSwipeActionsGlyph.Bag to paths("M5.5 8.5h13l-.95 10.9a1.7 1.7 0 0 1-1.7 1.6H8.15a1.7 1.7 0 0 1-1.7-1.6z", "M9 11V7a3 3 0 0 1 6 0v4"),
            TiltSwipeActionsGlyph.Bolt to paths("M13.4 2.8 5.6 13.1h5.7l-1.1 8.1 7.8-10.3h-5.7z"),
            TiltSwipeActionsGlyph.Dining to paths("M6.5 3v5a2.5 2.5 0 0 0 5 0V3", "M9 3v4.5", "M9 10.5V21", "M17.5 21V3c-2.1 1-3.5 3.4-3.5 6.4V14h3.5"),
            TiltSwipeActionsGlyph.Tram to paths("M6 7.5a3 3 0 0 1 3-3h6a3 3 0 0 1 3 3V14a3 3 0 0 1-3 3H9a3 3 0 0 1-3-3z", "M6 11h12", "M9.6 20.5 8.4 17M14.4 20.5l1.2-3.5", "M10 4.5V3h4v1.5", "M9.25 14h.01M14.75 14h.01"),
            TiltSwipeActionsGlyph.Ticket to paths("M3.5 7.6A1.6 1.6 0 0 1 5.1 6h13.8a1.6 1.6 0 0 1 1.6 1.6v2.1a2.4 2.4 0 0 0 0 4.6v2.1a1.6 1.6 0 0 1-1.6 1.6H5.1a1.6 1.6 0 0 1-1.6-1.6v-2.1a2.4 2.4 0 0 0 0-4.6z", "M14.5 6.5v1.5M14.5 11.25v1.5M14.5 16v1.5"),
            TiltSwipeActionsGlyph.Dumbbell to paths("M5 7.7a1.2 1.2 0 0 1 1.2-1.2h.6A1.2 1.2 0 0 1 8 7.7v8.6a1.2 1.2 0 0 1-1.2 1.2h-.6A1.2 1.2 0 0 1 5 16.3z", "M16 7.7a1.2 1.2 0 0 1 1.2-1.2h.6A1.2 1.2 0 0 1 19 7.7v8.6a1.2 1.2 0 0 1-1.2 1.2h-.6a1.2 1.2 0 0 1-1.2-1.2z", "M8 12h8M2.75 10v4M21.25 10v4"),
            TiltSwipeActionsGlyph.Users to paths("M15.5 20v-1.5A3.5 3.5 0 0 0 12 15H6.5A3.5 3.5 0 0 0 3 18.5V20", "M9.25 11.5a3.25 3.25 0 1 0 0-6.5 3.25 3.25 0 0 0 0 6.5z", "M21 20v-1.5a3.5 3.5 0 0 0-2.6-3.4M15.4 5.1a3.25 3.25 0 0 1 0 6.3"),
            TiltSwipeActionsGlyph.Flag to paths("M5.5 21V4", "M5.5 4.6c4.2-2 7.4 2 13 0v9c-5.6 2-8.8-2-13 0"),
            TiltSwipeActionsGlyph.Trash to paths("M4 6.5h16", "M9.5 6.5v-2h5v2", "m6.5 0 .9 12.6A1.6 1.6 0 0 0 9 20.5h6a1.6 1.6 0 0 0 1.6-1.4l.9-12.6", "M10.2 10.5v6M13.8 10.5v6"),
            TiltSwipeActionsGlyph.Undo to paths("M8.5 14 4 9.5 8.5 5", "M4 9.5h10.5a5 5 0 0 1 0 10H11"),
            TiltSwipeActionsGlyph.Plus to paths("M12 5.5v13M5.5 12h13"),
            TiltSwipeActionsGlyph.Home to paths("M4 10.4 12 4l8 6.4v8.6a1.5 1.5 0 0 1-1.5 1.5H15v-6H9v6H5.5A1.5 1.5 0 0 1 4 19z"),
            TiltSwipeActionsGlyph.Chart to paths("M4 20.5h16", "M7.5 16.5v-5M12 16.5V6.5M16.5 16.5v-8"),
            TiltSwipeActionsGlyph.Card to paths("M5.5 5.5h13A2.5 2.5 0 0 1 21 8v8a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 16V8a2.5 2.5 0 0 1 2.5-2.5z", "M3 10h18M7 15h3.5"),
        )
    }

    private fun bitmap(path: String): Bitmap = assets.open(path).use(BitmapFactory::decodeStream)

    private fun argb(color: androidx.compose.ui.graphics.Color, alpha: Float): Int = AndroidColor.argb(
        (alpha.coerceIn(0f, 1f) * 255f).toInt(),
        (color.red * 255f).toInt(),
        (color.green * 255f).toInt(),
        (color.blue * 255f).toInt(),
    )

    private fun argb(color: Int, alpha: Float): Int = AndroidColor.argb(
        (alpha.coerceIn(0f, 1f) * 255f).toInt(),
        AndroidColor.red(color),
        AndroidColor.green(color),
        AndroidColor.blue(color),
    )
}

private class TiltSwipeActionsFonts(assets: AssetManager) {
    val bodoni11 = font(assets, "TSABodoni11.ttf")
    val bodoni14 = font(assets, "TSABodoni14.ttf")
    val bodoni20 = font(assets, "TSABodoni20.ttf")
    val bodoni22 = font(assets, "TSABodoni22.ttf")
    val bodoni22r = font(assets, "TSABodoni22R.ttf")
    val bodoni48 = font(assets, "TSABodoni48.ttf")
    val bodoniItalic26 = font(assets, "TSABodoniItalic26.ttf")
    val sans400 = font(assets, "TSAInstrument400.ttf")
    val sans500 = font(assets, "TSAInstrument500.ttf")
    val sans600 = font(assets, "TSAInstrument600.ttf")

    private fun font(assets: AssetManager, name: String): Typeface =
        Typeface.createFromAsset(assets, "tiltswipeactions/tiltswipeactions_$name")
}

