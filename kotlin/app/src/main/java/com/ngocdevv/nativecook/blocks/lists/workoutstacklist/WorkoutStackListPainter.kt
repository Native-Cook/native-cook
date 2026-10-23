package com.ngocdevv.nativecook.blocks.lists.workoutstacklist

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.ngocdevv.nativecook.R
import kotlin.math.max
import kotlin.math.min

internal class WorkoutStackListPainter(context: Context) {
    private val resources = context.resources
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clip = Path()

    // android.graphics.Canvas has no clipOval or clipRoundRect: clip to a path of the same shape.
    private fun clipOval(canvas: Canvas, oval: RectF) {
        clip.reset()
        clip.addOval(oval, Path.Direction.CW)
        canvas.clipPath(clip)
    }

    private fun clipRoundRect(canvas: Canvas, rect: RectF, rx: Float, ry: Float) {
        clip.reset()
        clip.addRoundRect(rect, rx, ry, Path.Direction.CW)
        canvas.clipPath(clip)
    }
    private val futura = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val futuraMedium = Typeface.create("sans-serif", Typeface.NORMAL)
    private val poppinsRegular = requireNotNull(ResourcesCompat.getFont(context, R.font.workoutstacklist_poppins_regular))
    private val poppinsMedium = requireNotNull(ResourcesCompat.getFont(context, R.font.workoutstacklist_poppins_medium))
    private val poppinsSemibold = requireNotNull(ResourcesCompat.getFont(context, R.font.workoutstacklist_poppins_semibold))
    private val images = buildMap {
        WorkoutCategories.forEach { put(it.image, decode(it.image)) }
        listOf(
            R.drawable.workoutstacklist_class_antioxidant,
            R.drawable.workoutstacklist_class_hiit,
            R.drawable.workoutstacklist_class_monday,
            R.drawable.workoutstacklist_class_morning,
            R.drawable.workoutstacklist_class_running,
            R.drawable.workoutstacklist_coach_1,
            R.drawable.workoutstacklist_coach_2,
            R.drawable.workoutstacklist_hero_crossfit,
        ).forEach { put(it, decode(it)) }
    }

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        scene: WorkoutStackScene,
        reducedMotion: Boolean,
        pressedCategory: Int?,
        cardPress: Float,
        backPress: Float,
    ) {
        canvas.drawColor(WorkoutStackListSpec.Colors.Background.toArgb())
        drawList(canvas, width, height, scene, pressedCategory, cardPress)
        drawTabBar(canvas, width, height, scene)
        drawDetail(canvas, width, height, scene, reducedMotion, backPress)
    }

    private fun drawList(
        canvas: Canvas,
        width: Float,
        height: Float,
        scene: WorkoutStackScene,
        pressedCategory: Int?,
        cardPress: Float,
    ) {
        val opacity: Float
        val scale: Float
        if (scene.leave > 0f) {
            opacity = scene.leave
            scale = workoutMix(WorkoutStackListSpec.Motion.BackZoom, 1f, scene.leave)
        } else {
            opacity = 1f - workoutSegment(scene.open, 0.25f..0.75f)
            scale = 1f
        }
        if (opacity <= 0f) return
        val save = canvas.saveLayerAlpha(0f, 0f, width, height, (opacity * 255).toInt())
        canvas.scale(scale, scale, width / 2f, height / 2f)
        for (index in WorkoutCategories.indices.reversed()) {
            drawDeckCard(canvas, width, scene, index, pressedCategory, cardPress)
        }
        val avatar = RectF(
            WorkoutStackListSpec.Header.Side,
            WorkoutStackListSpec.Header.Top,
            WorkoutStackListSpec.Header.Side + WorkoutStackListSpec.Header.Avatar,
            WorkoutStackListSpec.Header.Top + WorkoutStackListSpec.Header.Avatar,
        )
        val avatarSave = canvas.save()
        clipOval(canvas, avatar)
        drawCover(canvas, bitmap(R.drawable.workoutstacklist_coach_2), avatar)
        canvas.restoreToCount(avatarSave)
        drawBell(canvas, width - WorkoutStackListSpec.Header.Side - WorkoutStackListSpec.Header.Icon, WorkoutStackListSpec.Header.Top)
        canvas.restoreToCount(save)
    }

    private fun drawDeckCard(
        canvas: Canvas,
        width: Float,
        scene: WorkoutStackScene,
        index: Int,
        pressedCategory: Int?,
        cardPress: Float,
    ) {
        val category = WorkoutCategories[index]
        val pose = workoutDeckPose(index - scene.deck)
        if (pose.opacity <= 0f) return
        if (scene.open > 0f && scene.category == index) return
        val press = if (pressedCategory == index) cardPress else 1f
        val effectiveScale = pose.scale * press
        val cardWidth = WorkoutStackListSpec.Deck.Width * effectiveScale
        val cardHeight = WorkoutStackListSpec.Deck.Height * effectiveScale
        val x = (width - WorkoutStackListSpec.Deck.Width) / 2f + WorkoutStackListSpec.DeckShift +
            (WorkoutStackListSpec.Deck.Width - cardWidth) / 2f
        val rect = RectF(x, pose.top, x + cardWidth, pose.top + cardHeight)
        val save = canvas.saveLayerAlpha(rect, (pose.opacity * 255).toInt())
        clipRoundRect(canvas, rect, WorkoutStackListSpec.Deck.Radius * effectiveScale, WorkoutStackListSpec.Deck.Radius * effectiveScale)
        paint.shader = null
        paint.color = category.color.toArgb()
        paint.alpha = 255
        canvas.drawRect(rect, paint)
        val photoWidth = WorkoutStackListSpec.Deck.Width * WorkoutStackListSpec.Deck.PhotoZoom * effectiveScale
        val photoHeight = photoWidth * 990f / 1173f
        val photo = RectF(
            x + (WorkoutStackListSpec.Deck.Width - WorkoutStackListSpec.Deck.Width * WorkoutStackListSpec.Deck.PhotoZoom) / 2f * effectiveScale,
            pose.top + WorkoutStackListSpec.Deck.PhotoTop * effectiveScale,
            x + (WorkoutStackListSpec.Deck.Width - WorkoutStackListSpec.Deck.Width * WorkoutStackListSpec.Deck.PhotoZoom) / 2f * effectiveScale + photoWidth,
            pose.top + WorkoutStackListSpec.Deck.PhotoTop * effectiveScale + photoHeight,
        )
        drawBitmap(canvas, bitmap(category.image), photo)
        if (pose.fog > 0f) {
            paint.color = AndroidColor.BLACK
            paint.alpha = (pose.fog * 255).toInt()
            canvas.drawRect(rect, paint)
            paint.alpha = 255
        }
        val titleSize = WorkoutStackListSpec.Deck.TitleSize * effectiveScale * pose.titleScale
        val inkTop = pose.top + pose.title * press
        drawTextCentered(canvas, category.name, futura, titleSize, Color.White, rect.centerX(), inkTop, 0f)
        canvas.restoreToCount(save)
    }

    private fun drawTabBar(canvas: Canvas, width: Float, height: Float, scene: WorkoutStackScene) {
        val opacity: Float
        val scale: Float
        if (scene.leave > 0f) {
            opacity = scene.leave
            scale = workoutMix(WorkoutStackListSpec.Motion.BackZoom, 1f, scene.leave)
        } else {
            opacity = 1f - workoutSegment(scene.open, 0.25f..0.75f)
            scale = 1f
        }
        if (opacity <= 0f) return
        val save = canvas.saveLayerAlpha(0f, 0f, width, height, (opacity * 255).toInt())
        canvas.scale(scale, scale, width / 2f, height / 2f)
        val tab = WorkoutStackListSpec.TabBar
        val rect = RectF(tab.Left, tab.Top, tab.Left + tab.Width, tab.Top + tab.Height)
        paint.shader = null
        // Compose has no backdrop blur primitive; use the RN Reduce Transparency fill plus its exact wash.
        paint.color = WorkoutStackListSpec.Colors.TabSolid.toArgb()
        paint.alpha = 255
        canvas.drawRoundRect(rect, tab.Radius, tab.Radius, paint)
        paint.color = WorkoutStackListSpec.Colors.TabWash.toArgb()
        canvas.drawRoundRect(rect, tab.Radius, tab.Radius, paint)
        drawTabIcons(canvas)
        canvas.restoreToCount(save)
    }

    private fun drawTabIcons(canvas: Canvas) {
        val tab = WorkoutStackListSpec.TabBar
        val ink = WorkoutStackListSpec.Colors.TabInk.toArgb()
        val centerY = tab.IconY
        paint.shader = null
        paint.color = ink
        paint.alpha = 255
        val home = Path().apply {
            moveTo(tab.Centers[0], centerY - 10f)
            lineTo(tab.Centers[0] - 10f, centerY - 1f)
            lineTo(tab.Centers[0] - 7f, centerY - 1f)
            lineTo(tab.Centers[0] - 7f, centerY + 10f)
            lineTo(tab.Centers[0] + 7f, centerY + 10f)
            lineTo(tab.Centers[0] + 7f, centerY - 1f)
            lineTo(tab.Centers[0] + 10f, centerY - 1f)
            close()
        }
        canvas.drawPath(home, paint)
        canvas.drawCircle(tab.Centers[0], tab.DotY, 2.4f, paint)

        stroke.color = ink
        stroke.strokeWidth = 2.2f
        stroke.style = Paint.Style.STROKE
        val calendar = RectF(tab.Centers[1] - 9.5f, centerY - 9.5f, tab.Centers[1] + 9.5f, centerY + 9.5f)
        canvas.drawRoundRect(calendar, 2f, 2f, stroke)
        canvas.drawRect(calendar.left, calendar.top + 4f, calendar.right, calendar.top + 8.6f, paint)
        canvas.drawRoundRect(RectF(calendar.left + 3.4f, calendar.top + 11f, calendar.left + 15.4f, calendar.top + 13.4f), 1f, 1f, paint)
        canvas.drawRoundRect(RectF(calendar.left + 3.4f, calendar.top + 15f, calendar.left + 11.4f, calendar.top + 17.4f), 1f, 1f, paint)

        paint.color = AndroidColor.WHITE
        paint.setShadowLayer(8f, 0f, 3f, 0x29000000)
        canvas.drawCircle(tab.Centers[2], centerY, tab.Plus / 2f, paint)
        paint.clearShadowLayer()
        stroke.color = WorkoutStackListSpec.Colors.TabPlusInk.toArgb()
        stroke.strokeWidth = 2.6f
        stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(tab.Centers[2], centerY - 7.5f, tab.Centers[2], centerY + 7.5f, stroke)
        canvas.drawLine(tab.Centers[2] - 7.5f, centerY, tab.Centers[2] + 7.5f, centerY, stroke)

        paint.color = ink
        val bolt = Path().apply {
            moveTo(tab.Centers[3] + 3f, centerY - 11f)
            lineTo(tab.Centers[3] - 7f, centerY + 2f)
            lineTo(tab.Centers[3] - 1f, centerY + 2f)
            lineTo(tab.Centers[3] - 3f, centerY + 11f)
            lineTo(tab.Centers[3] + 7f, centerY - 1f)
            lineTo(tab.Centers[3] + 1f, centerY - 1f)
            close()
        }
        canvas.drawPath(bolt, paint)

        val hubX = tab.Centers[4]
        val nodes = listOf(
            hubX - 8.4f to centerY - 8.4f,
            hubX + 8.4f to centerY - 8.4f,
            hubX - 9.8f to centerY + 3.4f,
            hubX + 5.6f to centerY + 8.6f,
        )
        stroke.color = ink
        stroke.strokeWidth = 2.2f
        for ((x, y) in nodes) {
            canvas.drawLine(hubX, centerY, x, y, stroke)
            canvas.drawCircle(x, y, 2.8f, paint)
        }
        canvas.drawCircle(hubX, centerY, 4.6f, paint)
        paint.color = WorkoutStackListSpec.Colors.TabWash.toArgb()
        canvas.drawCircle(hubX, centerY, 1.8f, paint)
    }

    private fun drawDetail(
        canvas: Canvas,
        width: Float,
        height: Float,
        scene: WorkoutStackScene,
        reducedMotion: Boolean,
        backPress: Float,
    ) {
        val opacity = if (scene.open > 0f) 1f - scene.leave else 0f
        if (opacity <= 0f) return
        val save = canvas.saveLayerAlpha(0f, 0f, width, height, (opacity * 255).toInt())
        val categoryIndex = scene.category.coerceIn(0, WorkoutCategories.lastIndex)
        drawHero(canvas, width, scene, reducedMotion, categoryIndex)
        drawDetailTitle(canvas, width, scene, reducedMotion, WorkoutCategories[categoryIndex].name)
        val reveal = workoutSegment(scene.open, .45f..1f)
        val calendarSave = canvas.saveLayerAlpha(0f, 0f, width, height, (reveal * 255).toInt())
        canvas.translate(0f, WorkoutStackListSpec.Detail.CollapsedShift * (1f - scene.scroll) + if (reducedMotion) 0f else 12f * (1f - reveal))
        drawCalendar(canvas, scene)
        canvas.restoreToCount(calendarSave)
        val cardSave = canvas.saveLayerAlpha(0f, 0f, width, height, (reveal * 255).toInt())
        canvas.translate(0f, if (reducedMotion) 0f else 12f * (1f - reveal))
        drawCards(canvas, width, scene)
        canvas.restoreToCount(cardSave)
        drawDetailHeader(canvas, width, scene, backPress)
        canvas.restoreToCount(save)
    }

    private fun drawHero(canvas: Canvas, width: Float, scene: WorkoutStackScene, reduced: Boolean, categoryIndex: Int) {
        val source = workoutDeckPose(2f)
        val sourceWidth = WorkoutStackListSpec.Deck.Width * source.scale
        val sourceHeight = WorkoutStackListSpec.Deck.Height * source.scale
        val open = if (reduced) 1f else scene.open
        val rect = RectF(
            workoutMix((width - sourceWidth) / 2f, 0f, open),
            workoutMix(source.top, 0f, open) - WorkoutStackListSpec.Detail.HeroScrollAway * scene.scroll,
            0f,
            0f,
        )
        rect.right = rect.left + workoutMix(sourceWidth, width, open)
        rect.bottom = rect.top + workoutMix(sourceHeight, WorkoutStackListSpec.Detail.HeroHeight, open)
        val radius = workoutMix(WorkoutStackListSpec.Deck.Radius * source.scale, 0f, open)
        val save = canvas.saveLayerAlpha(rect, ((if (reduced) scene.open else 1f) * 255f).toInt())
        clipRoundRect(canvas, rect, radius, radius)
        drawCover(canvas, bitmap(WorkoutCategories[categoryIndex].image), rect)
        val portraitOpacity = workoutSegment(scene.open, 0.2f..0.8f)
        if (portraitOpacity > 0f) {
            val portraitSave = canvas.saveLayerAlpha(rect, (portraitOpacity * 255).toInt())
            val resource = if (categoryIndex == WorkoutStackListSpec.Crossfit) R.drawable.workoutstacklist_hero_crossfit else WorkoutCategories[categoryIndex].image
            drawCover(canvas, bitmap(resource), rect)
            paint.shader = LinearGradient(
                rect.left,
                rect.top + rect.height() * .55f,
                rect.left,
                rect.bottom,
                intArrayOf(Color.Transparent.toArgb(), WorkoutStackListSpec.Colors.Background.copy(alpha = .85f).toArgb()),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(rect, paint)
            paint.shader = null
            canvas.restoreToCount(portraitSave)
        }
        canvas.restoreToCount(save)
    }

    private fun drawDetailTitle(canvas: Canvas, width: Float, scene: WorkoutStackScene, reduced: Boolean, name: String) {
        val source = workoutDeckPose(2f)
        val open = if (reduced) 1f else scene.open
        val scale = workoutMix(WorkoutStackListSpec.Deck.TitleSize * source.scale / WorkoutStackListSpec.Detail.TitleSize, 1f, open)
        val x = WorkoutStackListSpec.Detail.TitleLeft + workoutMix(width / 2f - 80f, 0f, open)
        val y = WorkoutStackListSpec.Detail.TitleInkTop - WorkoutStackListSpec.Deck.TitleAscent * WorkoutStackListSpec.Detail.TitleSize +
            workoutMix(source.top + 84.3f - WorkoutStackListSpec.Detail.TitleInkTop, 0f, open) -
            WorkoutStackListSpec.Detail.HeroScrollAway * scene.scroll
        val save = canvas.saveLayerAlpha(0f, 0f, width, 900f, ((if (reduced) scene.open else 1f) * 255).toInt())
        drawText(canvas, name, futura, WorkoutStackListSpec.Detail.TitleSize * scale, Color.White, x, y, WorkoutStackListSpec.Detail.TitleTracking * scale)
        canvas.restoreToCount(save)
    }

    private fun drawCalendar(canvas: Canvas, scene: WorkoutStackScene) {
        val detail = WorkoutStackListSpec.Detail
        val cell = (WorkoutStackListSpec.ScreenWidth - detail.WeekSide * 2f) / 7f
        WorkoutStackListSpec.Letters.forEachIndexed { index, letter ->
            drawTextCentered(canvas, letter, poppinsMedium, detail.LetterSize, if (index == 2) Color.White else WorkoutStackListSpec.Colors.Dim, detail.WeekSide + cell * index + cell / 2f, detail.LettersTop - 10f, 0f)
        }
        val selectedX = detail.WeekSide + cell * 2f + (cell - detail.Selected) / 2f + cell * scene.day
        paint.color = WorkoutStackListSpec.Colors.Red.toArgb()
        paint.shader = null
        canvas.drawRoundRect(RectF(selectedX, detail.NumbersTop - detail.Selected / 2f, selectedX + detail.Selected, detail.NumbersTop + detail.Selected / 2f), detail.SelectedRadius, detail.SelectedRadius, paint)
        WorkoutStackListSpec.Weeks.forEachIndexed { rowIndex, week ->
            val save = if (rowIndex > 0) canvas.saveLayerAlpha(0f, 0f, WorkoutStackListSpec.ScreenWidth, 900f, (scene.month * 255).toInt()) else canvas.save()
            if (rowIndex > 0) canvas.translate(0f, -12f * (1f - scene.month))
            val rowTop = detail.NumbersTop - 12f + detail.MonthRow * rowIndex
            week.forEachIndexed { column, day ->
                if (day != 0) {
                    drawTextCentered(canvas, day.toString(), poppinsSemibold, detail.NumberSize, if (day in WorkoutStackListSpec.DimDays) WorkoutStackListSpec.Colors.Dim else Color.White, detail.WeekSide + cell * column + cell / 2f, rowTop + 2f, 0f)
                    val markers = WorkoutStackListSpec.Dots[day].orEmpty()
                    val total = max(markers.length - 1, 0) * 7f
                    markers.forEachIndexed { dotIndex, marker ->
                        paint.color = if (marker == 'g') WorkoutStackListSpec.Colors.DotGreen.toArgb() else WorkoutStackListSpec.Colors.DotRed.toArgb()
                        canvas.drawCircle(detail.WeekSide + cell * column + cell / 2f - total / 2f + dotIndex * 7f, rowTop + 24f, 2f, paint)
                    }
                }
            }
            canvas.restoreToCount(save)
        }
        paint.color = WorkoutStackListSpec.Colors.Grabber.toArgb()
        val grabberY = detail.GrabberTop + detail.MonthRow * 3.3f * scene.month
        canvas.drawRoundRect(RectF((WorkoutStackListSpec.ScreenWidth - detail.GrabberWidth) / 2f, grabberY, (WorkoutStackListSpec.ScreenWidth + detail.GrabberWidth) / 2f, grabberY + 3f), 1.5f, 1.5f, paint)
    }

    private fun drawCards(canvas: Canvas, width: Float, scene: WorkoutStackScene) {
        if (scene.month < 1f) {
            val save = canvas.saveLayerAlpha(0f, 0f, width, 900f, ((1f - scene.month) * 255).toInt())
            drawText(canvas, "Live Class Schedule", poppinsSemibold, 15f, WorkoutStackListSpec.Colors.Label, WorkoutStackListSpec.Detail.ListSide, WorkoutStackListSpec.Detail.LabelTop + WorkoutStackListSpec.Detail.ListScroll * (1f - scene.scroll), 0f)
            canvas.restoreToCount(save)
        }
        drawCardSet(canvas, width, scene, WorkoutDayEight, 0)
        drawCardSet(canvas, width, scene, WorkoutDayNine, 1)
    }

    private fun drawCardSet(canvas: Canvas, width: Float, scene: WorkoutStackScene, cards: List<WorkoutClassCard>, set: Int) {
        cards.forEachIndexed { index, card ->
            val visible = if (set == 0) scene.day < 1f else scene.day > 0f
            if (!visible) return@forEachIndexed
            val horizontal = if (set == 0) -width * scene.day else width * (1f - scene.day)
            val listScale = if (index == 0) workoutMix(1f, WorkoutStackListSpec.Detail.LiveScale, scene.scroll) else 1f
            val listTop = if (index == 0) {
                workoutMix(WorkoutStackListSpec.Detail.LiveRest, WorkoutStackListSpec.Detail.ListTops[0], scene.scroll)
            } else {
                WorkoutStackListSpec.Detail.ListTops[index] + WorkoutStackListSpec.Detail.ListScroll * (1f - scene.scroll)
            }
            val top = workoutMix(listTop, WorkoutStackListSpec.Detail.StackTops[index], scene.month)
            val scale = workoutMix(listScale, WorkoutStackListSpec.Detail.StackScales[index], scene.month)
            val height = WorkoutStackListSpec.Detail.Heights[index]
            val save = canvas.save()
            canvas.translate(WorkoutStackListSpec.Detail.ListSide + horizontal, top + height * (scale - 1f) / 2f)
            canvas.scale(scale, scale)
            drawClassCard(canvas, card, height)
            canvas.restoreToCount(save)
        }
    }

    private fun drawClassCard(canvas: Canvas, card: WorkoutClassCard, height: Float) {
        val rect = RectF(0f, 0f, WorkoutStackListSpec.Detail.CardWidth, height)
        val save = canvas.save()
        clipRoundRect(canvas, rect, WorkoutStackListSpec.Detail.CardRadius, WorkoutStackListSpec.Detail.CardRadius)
        paint.shader = LinearGradient(0f, 0f, rect.right, rect.bottom, card.colors.first.copy(alpha = .902f).toArgb(), card.colors.second.copy(alpha = .902f).toArgb(), Shader.TileMode.CLAMP)
        canvas.drawRect(rect, paint)
        paint.shader = null
        if (card.kind == WorkoutClassKind.Live && card.key == "live8") drawLiveArt(canvas, height)
        card.image?.let { resource ->
            val size = height + 10f
            drawBitmap(canvas, bitmap(resource), RectF(WorkoutStackListSpec.Detail.CardWidth + 6f - size, -10f, WorkoutStackListSpec.Detail.CardWidth + 6f, height))
        }
        when (card.kind) {
            WorkoutClassKind.Live -> drawLiveContent(canvas, card, height)
            WorkoutClassKind.Quiz -> drawQuizContent(canvas, card, height)
            WorkoutClassKind.Session -> drawSessionContent(canvas, card)
        }
        canvas.restoreToCount(save)
    }

    private fun drawLiveArt(canvas: Canvas, height: Float) {
        val width = WorkoutStackListSpec.Detail.CardWidth
        paint.shader = LinearGradient(0f, 0f, width, height * .3f, intArrayOf(Color(0xFFB86CD8).toArgb(), Color(0xFF8F94FE).toArgb(), Color(0xFF6CD2FC).toArgb()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = LinearGradient(0f, 0f, width, height, Color(0xFFF22589).toArgb(), Color(0xFF8C0242).toArgb(), Shader.TileMode.CLAMP)
        val sy = height / 180f
        val mat = Path().apply {
            moveTo(150f, 0f); lineTo(width - 50f, 0f); lineTo(width - 70f, 60f * sy)
            lineTo(width - 105f, 110f * sy); lineTo(190f, 80f * sy); close()
        }
        canvas.drawPath(mat, paint)
        paint.shader = null
        paint.color = Color(0xFF0A5A2A).toArgb()
        val leaf = Path().apply {
            moveTo(width - 92f, height); quadTo(width - 70f, 110f * sy, width, 120f * sy); lineTo(width, height); close()
        }
        canvas.drawPath(leaf, paint)
        stroke.color = Color(0xFF1E7A3E).toArgb()
        stroke.strokeWidth = 3f
        val vein = Path().apply { moveTo(width - 60f, height); quadTo(width - 30f, 140f * sy, width, 150f * sy) }
        canvas.drawPath(vein, stroke)
    }

    private fun drawLiveContent(canvas: Canvas, card: WorkoutClassCard, height: Float) {
        val tagWidth = measuredWidth("LIVE", futura, 12f, .4f) + 12f
        val hint = "Starting now, tap to join!"
        val hintWidth = measuredWidth(hint, futuraMedium, 12f, 0f)
        val rowHeight = max(fontLineHeight(futura, 12f) + 4f, fontLineHeight(futuraMedium, 12f))
        val rowWidth = tagWidth + 8f + hintWidth + 10f
        paint.color = Color.White.copy(alpha = .28f).toArgb()
        canvas.drawRoundRect(RectF(12f, 12f, 12f + rowWidth, 12f + rowHeight), 6f, 6f, paint)
        paint.color = Color.White.toArgb()
        canvas.drawRoundRect(RectF(12f, 12f, 12f + tagWidth, 12f + rowHeight), 4f, 4f, paint)
        drawText(canvas, "LIVE", futura, 12f, WorkoutStackListSpec.Colors.Tag, 18f, 14f, .4f)
        drawText(canvas, hint, futuraMedium, 12f, Color.White, 12f + tagWidth + 8f, 14f, 0f)
        drawMultiline(canvas, card.title, futura, 24f, 29f, Color.White, 16f, 56f, .4f)
        card.coach?.let { drawCoach(canvas, it, height) }
    }

    private fun drawQuizContent(canvas: Canvas, card: WorkoutClassCard, height: Float) {
        val firstWidth = drawTag(canvas, card.time.orEmpty(), 16f, true)
        drawTag(canvas, card.length.orEmpty(), 16f + firstWidth + 6f, false)
        drawMultiline(canvas, card.title, futura, 22f, 28f, Color.White, 16f, 52f, .4f)
        card.coach?.let { drawCoach(canvas, it, height) }
    }

    private fun drawTag(canvas: Canvas, text: String, x: Float, filled: Boolean): Float {
        val width = measuredWidth(text, futuraMedium, 13f, 0f) + 16f
        val rect = RectF(x, 16f, x + width, 44f)
        if (filled) {
            paint.color = WorkoutStackListSpec.Colors.Tag.toArgb()
            canvas.drawRoundRect(rect, 6f, 6f, paint)
        } else {
            stroke.color = Color.White.copy(alpha = .6f).toArgb()
            stroke.strokeWidth = 1f
            canvas.drawRoundRect(rect, 6f, 6f, stroke)
        }
        drawText(canvas, text, futuraMedium, 13f, Color.White, x + 8f, 21f, 0f)
        return width
    }

    private fun drawCoach(canvas: Canvas, coach: WorkoutCoach, height: Float) {
        val avatar = RectF(16f, height - 52f, 50f, height - 18f)
        val save = canvas.save()
        clipOval(canvas, avatar)
        drawCover(canvas, bitmap(coach.image), avatar)
        canvas.restoreToCount(save)
        stroke.color = Color.White.copy(alpha = .7f).toArgb()
        stroke.strokeWidth = 1.5f
        canvas.drawOval(avatar, stroke)
        drawText(canvas, coach.name, futuraMedium, 14f, Color.White, 60f, height - 43f, 0f)
    }

    private fun drawSessionContent(canvas: Canvas, card: WorkoutClassCard) {
        drawMultiline(canvas, card.title, futura, 30f, 38f, Color.White, 16f, 26f, .5f)
        val selectWidth = measuredWidth("Select Session", futura, 13f, 0f) + 48f
        paint.color = Color.White.toArgb()
        canvas.drawRoundRect(RectF(18f, 128f, 18f + selectWidth, 164f), 18f, 18f, paint)
        drawText(canvas, "Select Session", futura, 13f, card.colors.first, 42f, 138f, 0f)
        stroke.color = Color.White.toArgb()
        stroke.strokeWidth = 1.8f
        stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawCircle(29f, 191f, 9f, stroke)
        canvas.drawLine(29f, 186.5f, 29f, 191f, stroke)
        canvas.drawLine(29f, 191f, 32f, 193f, stroke)
        drawText(canvas, "Time", futuraMedium, 12f, Color.White, 50f, 180f, 0f)
        drawText(canvas, card.length.orEmpty(), futura, 12f, Color.White, 50f, 195f, 0f)
        val gap = 6f
        val chipWidth = (WorkoutStackListSpec.Detail.CardWidth - 28f - gap * 3f) / 4f
        WorkoutStackListSpec.SessionTimes.forEachIndexed { index, value ->
            val left = 14f + index * (chipWidth + gap)
            paint.color = Color.White.copy(alpha = .22f).toArgb()
            canvas.drawRoundRect(RectF(left, 225f, left + chipWidth, 261f), 6f, 6f, paint)
            drawTextCentered(canvas, value, futuraMedium, 13f, Color.White, left + chipWidth / 2f, 235f, 0f)
        }
    }

    private fun drawDetailHeader(canvas: Canvas, width: Float, scene: WorkoutStackScene, backPress: Float) {
        val opacity = workoutSegment(scene.open, .5f..1f)
        val save = canvas.saveLayerAlpha(0f, 0f, width, 120f, (opacity * 255).toInt())
        val centerX = WorkoutStackListSpec.Header.Side + 12f
        val centerY = WorkoutStackListSpec.Header.Top + 12f
        canvas.scale(backPress, backPress, centerX, centerY)
        stroke.color = Color.White.toArgb()
        stroke.strokeWidth = 2.4f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        val back = Path().apply {
            moveTo(WorkoutStackListSpec.Header.Side + 15f, WorkoutStackListSpec.Header.Top + 5f)
            lineTo(WorkoutStackListSpec.Header.Side + 8f, WorkoutStackListSpec.Header.Top + 12f)
            lineTo(WorkoutStackListSpec.Header.Side + 15f, WorkoutStackListSpec.Header.Top + 19f)
        }
        canvas.drawPath(back, stroke)
        canvas.scale(1f / backPress, 1f / backPress, centerX, centerY)
        if (scene.month > 0f) {
            val monthSave = canvas.saveLayerAlpha(0f, 0f, width, 120f, (scene.month * 255).toInt())
            drawTextCentered(canvas, "April", poppinsSemibold, 20f, Color.White, width / 2f, WorkoutStackListSpec.Header.Top, 0f)
            canvas.restoreToCount(monthSave)
        }
        drawBell(canvas, width - WorkoutStackListSpec.Header.Side - WorkoutStackListSpec.Header.Icon, WorkoutStackListSpec.Header.Top)
        canvas.restoreToCount(save)
    }

    private fun drawBell(canvas: Canvas, x: Float, y: Float) {
        paint.color = Color.White.toArgb()
        paint.shader = null
        val bell = Path().apply {
            moveTo(x + 12f, y + 3f)
            cubicTo(x + 15.5f, y + 3f, x + 18f, y + 5.5f, x + 18f, y + 9f)
            lineTo(x + 18f, y + 13f); lineTo(x + 20f, y + 16f); lineTo(x + 4f, y + 16f)
            lineTo(x + 6f, y + 13f); lineTo(x + 6f, y + 9f)
            cubicTo(x + 6f, y + 5.5f, x + 8.5f, y + 3f, x + 12f, y + 3f); close()
        }
        canvas.drawPath(bell, paint)
        canvas.drawOval(RectF(x + 9.5f, y + 19f, x + 14.5f, y + 21.5f), paint)
        paint.color = WorkoutStackListSpec.Colors.Red.toArgb()
        canvas.drawCircle(x + 18f, y + 5f, 3.4f, paint)
    }

    private fun drawMultiline(canvas: Canvas, text: String, typeface: Typeface, size: Float, lineHeight: Float, color: Color, x: Float, y: Float, tracking: Float) {
        text.split('\n').forEachIndexed { index, line -> drawText(canvas, line, typeface, size, color, x, y + index * lineHeight, tracking) }
    }

    private fun drawText(canvas: Canvas, text: String, typeface: Typeface, size: Float, color: Color, x: Float, top: Float, tracking: Float) {
        configureText(typeface, size, color)
        val baseline = top - paint.fontMetrics.ascent
        if (tracking == 0f) {
            canvas.drawText(text, x, baseline, paint)
        } else {
            var cursor = x
            text.forEach { character ->
                val value = character.toString()
                canvas.drawText(value, cursor, baseline, paint)
                cursor += paint.measureText(value) + tracking
            }
        }
    }

    private fun drawTextCentered(canvas: Canvas, text: String, typeface: Typeface, size: Float, color: Color, centerX: Float, top: Float, tracking: Float) {
        configureText(typeface, size, color)
        val width = paint.measureText(text) + max(text.length - 1, 0) * tracking
        drawText(canvas, text, typeface, size, color, centerX - width / 2f, top, tracking)
    }

    private fun configureText(typeface: Typeface, size: Float, color: Color) {
        paint.shader = null
        paint.typeface = typeface
        paint.textSize = size
        paint.color = color.toArgb()
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }

    private fun measuredWidth(text: String, typeface: Typeface, size: Float, tracking: Float): Float {
        configureText(typeface, size, Color.White)
        return paint.measureText(text) + max(text.length - 1, 0) * tracking
    }

    private fun fontLineHeight(typeface: Typeface, size: Float): Float {
        configureText(typeface, size, Color.White)
        return paint.fontMetrics.descent - paint.fontMetrics.ascent
    }

    private fun drawCover(canvas: Canvas, bitmap: Bitmap, rect: RectF) {
        val scale = max(rect.width() / bitmap.width, rect.height() / bitmap.height)
        val sourceWidth = rect.width() / scale
        val sourceHeight = rect.height() / scale
        val left = (bitmap.width - sourceWidth) / 2f
        val top = (bitmap.height - sourceHeight) / 2f
        drawBitmap(canvas, bitmap, Rect(left.toInt(), top.toInt(), (left + sourceWidth).toInt(), (top + sourceHeight).toInt()), rect)
    }

    private fun drawBitmap(canvas: Canvas, bitmap: Bitmap, rect: RectF) {
        drawBitmap(canvas, bitmap, Rect(0, 0, bitmap.width, bitmap.height), rect)
    }

    private fun drawBitmap(canvas: Canvas, bitmap: Bitmap, source: Rect, rect: RectF) {
        paint.shader = null
        paint.alpha = 255
        paint.color = AndroidColor.WHITE
        canvas.drawBitmap(bitmap, source, rect, paint)
    }

    private fun bitmap(resource: Int): Bitmap = requireNotNull(images[resource])

    private fun decode(resource: Int): Bitmap {
        return requireNotNull(BitmapFactory.decodeResource(resources, resource)) { "Missing Workout Stack List image resource $resource" }
    }
}
