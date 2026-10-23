package com.ngocdevv.nativecook.blocks.lists.sendmoneycards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import com.ngocdevv.nativecook.R
import kotlin.math.max
import kotlin.math.min

internal class SendMoneyCardsPainter(context: Context, private val density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val regular = ResourcesCompat.getFont(context, R.font.sendmoneycards_poppins_regular)
        ?: Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = ResourcesCompat.getFont(context, R.font.sendmoneycards_poppins_medium)
        ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val semibold = ResourcesCompat.getFont(context, R.font.sendmoneycards_poppins_semibold)
        ?: Typeface.create("sans-serif", Typeface.BOLD)
    private val systemRegular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val systemSemibold = Typeface.create("sans-serif", Typeface.BOLD)
    private val systemBlackItalic = if (Build.VERSION.SDK_INT >= 28) {
        Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 900, true)
    } else {
        Typeface.create("sans-serif", Typeface.BOLD_ITALIC)
    }
    private val bitmaps: Map<Int, Bitmap> = (SendMoneyCardsSpec.Favourites + SendMoneyCardsSpec.Contacts)
        .associate { it.drawable to BitmapFactory.decodeResource(context.resources, it.drawable) }
    private val source = Rect()
    private val destination = RectF()
    private val path = Path()

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        offsetY: Float,
        pose: SendMoneyCardsSpec.Pose,
        reduced: Boolean,
        pressedTarget: String?,
    ) {
        canvas.drawColor(SendMoneyCardsSpec.Screen.toInt())
        canvas.save()
        canvas.scale(density, density)
        val visibleSheet = if (reduced) if (pose.fade > 0.001f) 1f else 0f else pose.sheet
        val pill = SendMoneyCardsSpec.pillBox(if (reduced) 0f else visibleSheet, width, offsetY)
        val tab = SendMoneyCardsSpec.tabBox(if (reduced) 0f else pose.arm, visibleSheet, pill)
        val sheet = SendMoneyCardsSpec.sheetBox(visibleSheet, width, height, offsetY, pose.closing)
        drawHome(canvas, width, offsetY, pose, if (reduced) 1f - pose.fade else 1f)
        drawGoo(canvas, width, height, pill, tab, sheet, pose.fade, reduced)
        drawPill(canvas, width, offsetY, pill, sheet, pose, reduced)
        drawSheet(canvas, width, height, offsetY, sheet, pose, reduced, pressedTarget)
        canvas.restore()
    }

    private fun drawHome(canvas: Canvas, width: Float, offsetY: Float, pose: SendMoneyCardsSpec.Pose, alpha: Float) {
        val alphaSave = canvas.saveLayerAlpha(0f, 0f, width, 2_000f, (255 * alpha).toInt())
        val headerAlpha = 1f - min(1f, pose.sheet * 1.6f)
        val headerSave = canvas.saveLayerAlpha(0f, 0f, width, 180f, (255 * headerAlpha).toInt())
        val buttonTop = offsetY + SendMoneyCardsSpec.HeaderButtonY - SendMoneyCardsSpec.HeaderButton / 2f
        fillCircle(canvas, SendMoneyCardsSpec.HeaderInset + 24f, buttonTop + 24f, 24f, SendMoneyCardsSpec.Chip)
        drawGrid(canvas, SendMoneyCardsSpec.HeaderInset + 13.5f, buttonTop + 13.5f,
            SendMoneyCardsSpec.HeaderGridGlyph, SendMoneyCardsSpec.Ink)
        val addLeft = width - SendMoneyCardsSpec.HeaderInset - SendMoneyCardsSpec.HeaderButton
        fillCircle(canvas, addLeft + 24f, buttonTop + 24f, 24f, SendMoneyCardsSpec.Chip)
        drawIcon(canvas, Icon.Plus, addLeft + 14f, buttonTop + 14f,
            SendMoneyCardsSpec.HeaderPlusGlyph, SendMoneyCardsSpec.Ink, 1.5f)
        canvas.restoreToCount(headerSave)

        canvas.save()
        canvas.translate(0f, if (pose.lift.isFinite()) pose.lift else 0f)
        drawTextTop(canvas, "Drag to send", width / 2f,
            offsetY + SendMoneyCardsSpec.HintY - 11f - SendMoneyCardsSpec.PressLift + pose.hintLift - pose.lift,
            SendMoneyCardsSpec.FontHint, regular, SendMoneyCardsSpec.InkSoft, 22f, Align.Center,
            alpha = pose.arm)
        drawCard(canvas, width, offsetY)
        drawTextTop(canvas, "Last", SendMoneyCardsSpec.ListInset,
            offsetY + SendMoneyCardsSpec.ListTitleY, SendMoneyCardsSpec.FontSectionLead,
            systemRegular, SendMoneyCardsSpec.InkSoft, 26f, tracking = -0.2f)
        drawTextTop(canvas, "Transactions", SendMoneyCardsSpec.ListInset,
            offsetY + SendMoneyCardsSpec.ListTitleY + 26f, SendMoneyCardsSpec.FontSectionTitle,
            systemSemibold, SendMoneyCardsSpec.Ink, 26f, tracking = -0.2f)
        fillCircle(canvas, SendMoneyCardsSpec.ListArrowX,
            offsetY + SendMoneyCardsSpec.ListArrowY, SendMoneyCardsSpec.ListArrowButton / 2f,
            SendMoneyCardsSpec.Chip)
        drawIcon(canvas, Icon.ArrowUpRight,
            SendMoneyCardsSpec.ListArrowX - SendMoneyCardsSpec.ListArrowGlyph / 2f,
            offsetY + SendMoneyCardsSpec.ListArrowY - SendMoneyCardsSpec.ListArrowGlyph / 2f,
            SendMoneyCardsSpec.ListArrowGlyph, SendMoneyCardsSpec.Ink, 1.5f)
        SendMoneyCardsSpec.Payments.forEachIndexed { index, payment ->
            drawPaymentRow(canvas, payment, width, offsetY + SendMoneyCardsSpec.ListTop +
                index * SendMoneyCardsSpec.ListPitch)
        }
        canvas.restore()
        canvas.restoreToCount(alphaSave)
    }

    private fun drawCard(canvas: Canvas, screenWidth: Float, offsetY: Float) {
        val left = SendMoneyCardsSpec.CardInset
        val top = offsetY + SendMoneyCardsSpec.CardTop
        val width = screenWidth - SendMoneyCardsSpec.CardInset * 2f
        fillRoundRect(canvas, left, top, left + width, top + SendMoneyCardsSpec.CardHeight,
            SendMoneyCardsSpec.CardRadius, SendMoneyCardsSpec.Blue)
        drawTextTop(canvas, SendMoneyCardsSpec.Network, left + SendMoneyCardsSpec.CardPadX,
            top + SendMoneyCardsSpec.CardBrandY - 13f, SendMoneyCardsSpec.FontBrand,
            systemBlackItalic, SendMoneyCardsSpec.White, 26f, tracking = -3.4f)
        drawTextTop(canvas, SendMoneyCardsSpec.Balance, left + width - SendMoneyCardsSpec.CardPadRight,
            top + SendMoneyCardsSpec.CardBrandY - 19f, SendMoneyCardsSpec.FontBalance,
            semibold, SendMoneyCardsSpec.White, 44f, Align.Right, tracking = -0.3f)
        fillRoundRect(canvas, left + SendMoneyCardsSpec.CardPadX,
            top + SendMoneyCardsSpec.CardChipY,
            left + SendMoneyCardsSpec.CardPadX + SendMoneyCardsSpec.CardChipWidth,
            top + SendMoneyCardsSpec.CardChipY + SendMoneyCardsSpec.CardChipHeight,
            SendMoneyCardsSpec.CardChipRadius, SendMoneyCardsSpec.CardChip)
        repeat(3) { group ->
            repeat(4) { dot ->
                fillCircle(canvas,
                    left + SendMoneyCardsSpec.CardDotsX + group * SendMoneyCardsSpec.CardGroupPitch +
                        dot * SendMoneyCardsSpec.CardDotPitch,
                    top + SendMoneyCardsSpec.CardDotsCenterY,
                    SendMoneyCardsSpec.CardDotSize / 2f,
                    SendMoneyCardsSpec.White)
            }
        }
        drawTextTop(canvas, SendMoneyCardsSpec.Last4, left + SendMoneyCardsSpec.CardLast4X,
            top + SendMoneyCardsSpec.CardNumberY - 10f, SendMoneyCardsSpec.FontNumber,
            semibold, SendMoneyCardsSpec.White, 20f)
        drawTextTop(canvas, "VALID THRU", left + width - SendMoneyCardsSpec.CardPadRight,
            top + SendMoneyCardsSpec.CardValidY - 5f, SendMoneyCardsSpec.FontTiny,
            medium, SendMoneyCardsSpec.BlueText, 10f, Align.Right, tracking = 0.2f)
        drawTextTop(canvas, SendMoneyCardsSpec.Valid, left + width - SendMoneyCardsSpec.CardValidRight,
            top + SendMoneyCardsSpec.CardNumberY - 9f, SendMoneyCardsSpec.FontValid,
            semibold, SendMoneyCardsSpec.White, 20f, Align.Right)
        drawTextTop(canvas, SendMoneyCardsSpec.Holder, left + SendMoneyCardsSpec.CardPadX,
            top + SendMoneyCardsSpec.CardNameY - 5f, SendMoneyCardsSpec.FontTiny,
            medium, SendMoneyCardsSpec.BlueText, 10f, tracking = 0.2f)
        val actionTop = top + SendMoneyCardsSpec.CardActionY - SendMoneyCardsSpec.CardAction / 2f
        val centers = floatArrayOf(left + 19f + 23f, left + width / 2f, left + width - 19f - 23f)
        val labels = arrayOf("Send to", "Receive", "Add")
        val icons = arrayOf(Icon.ArrowUpLeft, Icon.ArrowDownLeft, Icon.Plus)
        centers.indices.forEach { index ->
            fillCircle(canvas, centers[index], actionTop + 23f, 23f, SendMoneyCardsSpec.BlueLift)
            drawIcon(canvas, icons[index], centers[index] - SendMoneyCardsSpec.CardActionGlyph / 2f,
                actionTop + 23f - SendMoneyCardsSpec.CardActionGlyph / 2f,
                SendMoneyCardsSpec.CardActionGlyph, SendMoneyCardsSpec.White,
                if (icons[index] == Icon.Plus) 1.5f else 1.6f)
            drawTextTop(canvas, labels[index], centers[index],
                actionTop + SendMoneyCardsSpec.CardAction + SendMoneyCardsSpec.CardLabelGap,
                SendMoneyCardsSpec.FontAction, regular, SendMoneyCardsSpec.White, 18f, Align.Center)
        }
    }

    private fun drawPaymentRow(canvas: Canvas, payment: SendMoneyCardsSpec.Payment, width: Float, top: Float) {
        val left = SendMoneyCardsSpec.ListInset
        val rowWidth = width - SendMoneyCardsSpec.ListInset * 2f
        fillRoundRect(canvas, left, top, left + rowWidth, top + SendMoneyCardsSpec.ListRowHeight,
            SendMoneyCardsSpec.ListRowHeight / 2f, SendMoneyCardsSpec.Row)
        val iconCenterX = left + SendMoneyCardsSpec.ListIconPad + SendMoneyCardsSpec.ListIcon / 2f
        val centerY = top + SendMoneyCardsSpec.ListRowHeight / 2f
        fillCircle(canvas, iconCenterX, centerY, SendMoneyCardsSpec.ListIcon / 2f, SendMoneyCardsSpec.RowIcon)
        drawIcon(canvas, Icon.Ledger(payment.incoming),
            iconCenterX - SendMoneyCardsSpec.ListGlyph / 2f,
            centerY - SendMoneyCardsSpec.ListGlyph / 2f,
            SendMoneyCardsSpec.ListGlyph, SendMoneyCardsSpec.InkSoft, 1.5f)
        val textX = left + SendMoneyCardsSpec.ListIconPad + SendMoneyCardsSpec.ListIcon +
            SendMoneyCardsSpec.ListNameGap
        drawTextTop(canvas, payment.merchant, textX, top + 8f,
            SendMoneyCardsSpec.FontRowName, medium, SendMoneyCardsSpec.Ink, 19f)
        drawTextTop(canvas, payment.date, textX, top + 27f,
            SendMoneyCardsSpec.FontRowMeta, regular, SendMoneyCardsSpec.Muted, 17f)
        drawTextTop(canvas, payment.amount, left + rowWidth - SendMoneyCardsSpec.ListPadRight,
            top + 23f, SendMoneyCardsSpec.FontRowAmount, regular, SendMoneyCardsSpec.InkSoft,
            14f, Align.Right)
    }

    private fun drawGoo(
        canvas: Canvas,
        width: Float,
        height: Float,
        pill: SendMoneyCardsSpec.Box,
        tab: SendMoneyCardsSpec.Box,
        sheet: SendMoneyCardsSpec.Box,
        fade: Float,
        reduced: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= 31) {
            paint.reset()
            paint.isAntiAlias = true
            val matrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, SendMoneyCardsSpec.GooAlphaMultiplier,
                SendMoneyCardsSpec.GooAlphaBias * 255f,
            ))
            // Native Paint has no RenderEffect slot; apply the same threshold and edge softening
            // through the layer's color filter and mask filter instead.
            paint.colorFilter = ColorMatrixColorFilter(matrix)
            paint.maskFilter = BlurMaskFilter(SendMoneyCardsSpec.GooBlur * density, BlurMaskFilter.Blur.NORMAL)
            val saved = canvas.saveLayer(0f, 0f, width, height, paint)
            fillBox(canvas, pill, SendMoneyCardsSpec.Black)
            fillBox(canvas, tab, SendMoneyCardsSpec.Black)
            if (!reduced) fillBox(canvas, sheet, SendMoneyCardsSpec.Black)
            canvas.restoreToCount(saved)
            paint.colorFilter = null
            paint.maskFilter = null
        } else {
            fillBox(canvas, pill, SendMoneyCardsSpec.Black)
            fillBox(canvas, tab, SendMoneyCardsSpec.Black)
            if (!reduced) {
                fillBox(canvas, sheet, SendMoneyCardsSpec.Black)
                val neckLeft = max(pill.x, sheet.x + sheet.width * 0.35f)
                val neckRight = min(pill.x + pill.width, sheet.x + sheet.width * 0.65f)
                fillRoundRect(canvas, neckLeft, pill.y + pill.height - 8f,
                    neckRight, sheet.y + min(48f, sheet.height), 22f, SendMoneyCardsSpec.Black)
            }
        }
        if (reduced && fade > 0f) {
            paint.alpha = (255 * fade).toInt()
            fillBox(canvas, sheet, SendMoneyCardsSpec.Black)
            paint.alpha = 255
        }
    }

    private fun drawPill(
        canvas: Canvas,
        width: Float,
        offsetY: Float,
        pill: SendMoneyCardsSpec.Box,
        bloom: SendMoneyCardsSpec.Box,
        pose: SendMoneyCardsSpec.Pose,
        reduced: Boolean,
    ) {
        val shown = if (reduced) 1f - pose.fade else 1f - SendMoneyCardsSpec.clamp((pose.sheet - 0.25f) / 0.25f)
        val open = if (reduced) pose.fade else SendMoneyCardsSpec.clamp((pose.sheet - 0.35f) / 0.2f)
        val clip = Path().apply {
            addRoundRect(RectF(pill.x, pill.y, pill.x + pill.width, pill.y + pill.height),
                max(0f, pill.radius - 0.5f), max(0f, pill.radius - 0.5f), Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        val contentLayer = canvas.saveLayerAlpha(pill.x, pill.y, pill.x + pill.width, pill.y + pill.height,
            (255 * shown).toInt())
        val restLeft = (width - SendMoneyCardsSpec.PillWidth) / 2f
        val restTop = offsetY + SendMoneyCardsSpec.PillTop
        val sendWidth = drawTextTop(canvas, "Send ", restLeft + SendMoneyCardsSpec.PillPadX,
            restTop + 10.5f, SendMoneyCardsSpec.FontPill, regular, SendMoneyCardsSpec.PillMuted, 24f)
        drawTextTop(canvas, "to", restLeft + SendMoneyCardsSpec.PillPadX + sendWidth,
            restTop + 10.5f, SendMoneyCardsSpec.FontPill, medium, SendMoneyCardsSpec.White, 24f)
        val stackWidth = SendMoneyCardsSpec.PillAvatar + SendMoneyCardsSpec.PillAvatarStep * 2f
        val avatarLeft = restLeft + SendMoneyCardsSpec.PillWidth - SendMoneyCardsSpec.PillPadRight - stackWidth
        SendMoneyCardsSpec.Favourites.forEachIndexed { index, person ->
            drawBitmapCircle(canvas, person.drawable,
                avatarLeft + index * SendMoneyCardsSpec.PillAvatarStep,
                restTop + 8.5f, SendMoneyCardsSpec.PillAvatar, crop = true, border = 1.5f)
        }
        canvas.restoreToCount(contentLayer)
        canvas.restore()
        val drop = if (reduced) 0f else max(0f,
            bloom.y + bloom.height - (offsetY + SendMoneyCardsSpec.TabBottom)) *
            SendMoneyCardsSpec.TabArrowFollow
        val arrowLayer = canvas.saveLayerAlpha(
            width / 2f - 16f,
            offsetY + SendMoneyCardsSpec.TabArrowY - 16f + drop,
            width / 2f + 16f,
            offsetY + SendMoneyCardsSpec.TabArrowY + 16f + drop,
            (255 * pose.arm * (1f - open)).toInt(),
        )
        drawIcon(canvas, Icon.ArrowUpLeft, width / 2f - 7.5f,
            offsetY + SendMoneyCardsSpec.TabArrowY - 7.5f + drop,
            15f, SendMoneyCardsSpec.White, 1.5f)
        canvas.restoreToCount(arrowLayer)
    }

    private fun drawSheet(
        canvas: Canvas,
        width: Float,
        height: Float,
        offsetY: Float,
        box: SendMoneyCardsSpec.Box,
        pose: SendMoneyCardsSpec.Pose,
        reduced: Boolean,
        pressedTarget: String?,
    ) {
        val sheetAlpha = if (reduced) pose.fade else 1f
        if (sheetAlpha <= 0f) return
        val radius = max(0f, min(box.radius, min(box.width, box.height) / 2f - 1f))
        val clip = Path().apply {
            addRoundRect(RectF(box.x, box.y, box.x + box.width, box.y + box.height),
                radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        val sheetLayer = canvas.saveLayerAlpha(box.x, box.y, box.x + box.width, box.y + box.height,
            (255 * sheetAlpha).toInt())
        fillRoundRect(canvas, box.x, box.y, box.x + box.width, box.y + box.height,
            radius, SendMoneyCardsSpec.Black)
        val transform = if (reduced) SendMoneyCardsSpec.ContentTransform(1f, 1f, 0f, 0f) else {
            SendMoneyCardsSpec.sheetContentTransform(pose.sheet, pose.closing, offsetY, height)
        }
        val contentLayer = canvas.saveLayerAlpha(0f, 0f, width, height, (255 * transform.opacity).toInt())
        canvas.translate(0f, transform.translateY)
        canvas.scale(transform.scale, transform.scale, width / 2f, height / 2f)
        drawSheetContent(canvas, width, offsetY, pressedTarget)
        canvas.restoreToCount(contentLayer)
        canvas.restoreToCount(sheetLayer)
        canvas.restore()
    }

    private fun drawSheetContent(canvas: Canvas, width: Float, offsetY: Float, pressedTarget: String?) {
        drawTextTop(canvas, "Send", SendMoneyCardsSpec.SheetTitleX,
            offsetY + SendMoneyCardsSpec.SheetTitleY,
            SendMoneyCardsSpec.FontSheetTitle, medium, SendMoneyCardsSpec.SheetMuted,
            SendMoneyCardsSpec.FontSheetTitleLine, tracking = -0.6f)
        drawTextTop(canvas, "Money To", SendMoneyCardsSpec.SheetTitleX,
            offsetY + SendMoneyCardsSpec.SheetTitleY + SendMoneyCardsSpec.FontSheetTitleLine,
            SendMoneyCardsSpec.FontSheetTitle, semibold, SendMoneyCardsSpec.White,
            SendMoneyCardsSpec.FontSheetTitleLine, tracking = -0.6f)
        drawScaled(canvas, width - SendMoneyCardsSpec.SheetCloseX,
            offsetY + SendMoneyCardsSpec.SheetCloseY, pressedTarget == "close") {
            drawIcon(canvas, Icon.Close,
                width - SendMoneyCardsSpec.SheetCloseX - SendMoneyCardsSpec.SheetCloseGlyph / 2f,
                offsetY + SendMoneyCardsSpec.SheetCloseY - SendMoneyCardsSpec.SheetCloseGlyph / 2f,
                SendMoneyCardsSpec.SheetCloseGlyph, SendMoneyCardsSpec.White, 1.7f)
        }
        SendMoneyCardsSpec.Favourites.forEachIndexed { index, person ->
            val centerX = SendMoneyCardsSpec.SheetAvatarX + index * SendMoneyCardsSpec.SheetAvatarStep
            drawBitmapCircle(canvas, person.drawable, centerX - SendMoneyCardsSpec.SheetAvatar / 2f,
                offsetY + SendMoneyCardsSpec.SheetAvatarsY - SendMoneyCardsSpec.SheetAvatar / 2f,
                SendMoneyCardsSpec.SheetAvatar, crop = true)
            drawTextTop(canvas, person.name, centerX,
                offsetY + SendMoneyCardsSpec.SheetAvatarsY + SendMoneyCardsSpec.SheetAvatar / 2f,
                SendMoneyCardsSpec.FontLabel, regular, SendMoneyCardsSpec.White, 17f, Align.Center)
        }
        drawScaled(canvas, SendMoneyCardsSpec.SheetAddNewX,
            offsetY + SendMoneyCardsSpec.SheetAvatarsY, pressedTarget == "add") {
            fillCircle(canvas, SendMoneyCardsSpec.SheetAddNewX,
                offsetY + SendMoneyCardsSpec.SheetAvatarsY,
                SendMoneyCardsSpec.SheetAvatar / 2f, SendMoneyCardsSpec.AddNew)
            drawIcon(canvas, Icon.Plus,
                SendMoneyCardsSpec.SheetAddNewX - 9f,
                offsetY + SendMoneyCardsSpec.SheetAvatarsY - 9f,
                18f, SendMoneyCardsSpec.InkSoft, 1.3f)
            drawTextTop(canvas, "Add New", SendMoneyCardsSpec.SheetAddNewX,
                offsetY + SendMoneyCardsSpec.SheetAvatarsY + SendMoneyCardsSpec.SheetAvatar / 2f,
                SendMoneyCardsSpec.FontLabel, regular, SendMoneyCardsSpec.White, 17f, Align.Center)
        }
        drawTextTop(canvas, "Your", SendMoneyCardsSpec.SheetTitleX,
            offsetY + SendMoneyCardsSpec.SheetContactsTitleY,
            SendMoneyCardsSpec.FontContactsTitle, medium, SendMoneyCardsSpec.SheetMuted,
            SendMoneyCardsSpec.FontContactsLine)
        drawTextTop(canvas, "Contacts", SendMoneyCardsSpec.SheetTitleX,
            offsetY + SendMoneyCardsSpec.SheetContactsTitleY + SendMoneyCardsSpec.FontContactsLine,
            SendMoneyCardsSpec.FontContactsTitle, semibold, SendMoneyCardsSpec.White,
            SendMoneyCardsSpec.FontContactsLine)
        SendMoneyCardsSpec.Contacts.forEachIndexed { index, person ->
            val centerY = offsetY + SendMoneyCardsSpec.SheetContactsTop +
                index * SendMoneyCardsSpec.SheetContactPitch
            drawScaled(canvas, SendMoneyCardsSpec.SheetContactX + 80f, centerY,
                pressedTarget == "contact-$index") {
                drawBitmapCircle(canvas, person.drawable,
                    SendMoneyCardsSpec.SheetContactX - SendMoneyCardsSpec.SheetAvatar / 2f,
                    centerY - SendMoneyCardsSpec.SheetAvatar / 2f,
                    SendMoneyCardsSpec.SheetAvatar, crop = false)
                drawTextTop(canvas, person.name,
                    SendMoneyCardsSpec.SheetContactX + SendMoneyCardsSpec.SheetAvatar / 2f +
                        SendMoneyCardsSpec.SheetNameGap,
                    centerY - 10f, SendMoneyCardsSpec.FontContact, medium,
                    SendMoneyCardsSpec.White, 20f)
            }
        }
        drawScaled(canvas, SendMoneyCardsSpec.SheetButtonX + SendMoneyCardsSpec.SheetButtonWidth / 2f,
            offsetY + SendMoneyCardsSpec.SheetButtonTop + SendMoneyCardsSpec.SheetButtonHeight / 2f,
            pressedTarget == "next") {
            fillRoundRect(canvas, SendMoneyCardsSpec.SheetButtonX,
                offsetY + SendMoneyCardsSpec.SheetButtonTop,
                SendMoneyCardsSpec.SheetButtonX + SendMoneyCardsSpec.SheetButtonWidth,
                offsetY + SendMoneyCardsSpec.SheetButtonTop + SendMoneyCardsSpec.SheetButtonHeight,
                SendMoneyCardsSpec.SheetButtonHeight / 2f, SendMoneyCardsSpec.Blue)
            drawTextTop(canvas, "Next",
                SendMoneyCardsSpec.SheetButtonX + SendMoneyCardsSpec.SheetButtonWidth / 2f,
                offsetY + SendMoneyCardsSpec.SheetButtonTop + 16f,
                14f, medium, SendMoneyCardsSpec.White, 20f, Align.Center)
        }
    }

    private inline fun drawScaled(canvas: Canvas, centerX: Float, centerY: Float, pressed: Boolean, block: () -> Unit) {
        if (!pressed) {
            block()
            return
        }
        canvas.save()
        canvas.scale(SendMoneyCardsSpec.PressScale, SendMoneyCardsSpec.PressScale, centerX, centerY)
        block()
        canvas.restore()
    }

    private enum class Align { Left, Center, Right }
    private sealed interface Icon {
        data object ArrowUpLeft : Icon
        data object ArrowDownLeft : Icon
        data object ArrowUpRight : Icon
        data class Ledger(val incoming: Boolean) : Icon
        data object Plus : Icon
        data object Close : Icon
    }

    private fun drawTextTop(
        canvas: Canvas,
        text: String,
        x: Float,
        top: Float,
        size: Float,
        typeface: Typeface,
        color: Long,
        lineHeight: Float,
        align: Align = Align.Left,
        tracking: Float = 0f,
        alpha: Float = 1f,
    ): Float {
        paint.reset()
        paint.isAntiAlias = true
        paint.isSubpixelText = true
        paint.color = color.toInt()
        paint.alpha = (255 * alpha).toInt()
        paint.textSize = size
        paint.typeface = typeface
        paint.letterSpacing = if (size == 0f) 0f else tracking / size
        paint.textAlign = when (align) {
            Align.Left -> Paint.Align.LEFT
            Align.Center -> Paint.Align.CENTER
            Align.Right -> Paint.Align.RIGHT
        }
        val metrics = paint.fontMetrics
        val glyphHeight = metrics.bottom - metrics.top
        val baseline = top + (lineHeight - glyphHeight) / 2f - metrics.top
        canvas.drawText(text, x, baseline, paint)
        paint.alpha = 255
        return paint.measureText(text)
    }

    private fun fillRoundRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float,
                              radius: Float, color: Long) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = color.toInt()
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
    }

    private fun fillCircle(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, color: Long) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = color.toInt()
        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    private fun fillBox(canvas: Canvas, box: SendMoneyCardsSpec.Box, color: Long) {
        fillRoundRect(canvas, box.x, box.y, box.x + box.width, box.y + box.height, box.radius, color)
    }

    private fun drawBitmapCircle(canvas: Canvas, drawable: Int, left: Float, top: Float,
                                 size: Float, crop: Boolean, border: Float = 0f) {
        val bitmap = bitmaps.getValue(drawable)
        source.set(0, 0, bitmap.width, bitmap.height)
        destination.set(left, top, left + size, top + size)
        path.reset()
        path.addCircle(left + size / 2f, top + size / 2f, size / 2f, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)
        if (crop) {
            canvas.drawBitmap(bitmap, source, destination, paint)
        } else {
            canvas.drawBitmap(bitmap, source, destination, paint)
        }
        canvas.restore()
        if (border > 0f) {
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = border
            paint.color = SendMoneyCardsSpec.Black.toInt()
            canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2f - border / 2f, paint)
        }
    }

    private fun drawGrid(canvas: Canvas, left: Float, top: Float, size: Float, color: Long) {
        val scale = size / 18f
        repeat(3) { row ->
            repeat(3) { column ->
                fillCircle(canvas, left + (3f + column * 6f) * scale,
                    top + (3f + row * 6f) * scale, 1.9f * scale, color)
            }
        }
    }

    private fun drawIcon(canvas: Canvas, icon: Icon, left: Float, top: Float,
                         size: Float, color: Long, stroke: Float) {
        val viewBox = when (icon) {
            is Icon.Ledger -> 20f
            Icon.Close -> 16f
            Icon.Plus -> 18f
            else -> 19f
        }
        val scale = size / viewBox
        path.reset()
        fun move(x: Float, y: Float) = path.moveTo(left + x * scale, top + y * scale)
        fun line(x: Float, y: Float) = path.lineTo(left + x * scale, top + y * scale)
        when (icon) {
            Icon.ArrowUpLeft -> {
                move(15f, 15f); line(4f, 4f); move(4f, 4f); line(4f, 12f); move(4f, 4f); line(12f, 4f)
            }
            Icon.ArrowDownLeft -> {
                move(15f, 4f); line(4f, 15f); move(4f, 15f); line(4f, 7f); move(4f, 15f); line(12f, 15f)
            }
            Icon.ArrowUpRight -> {
                move(4f, 15f); line(15f, 4f); move(15f, 4f); line(7f, 4f); move(15f, 4f); line(15f, 12f)
            }
            is Icon.Ledger -> {
                if (icon.incoming) {
                    move(15f, 3f); line(5f, 13f); move(5f, 13f); line(5f, 6f); move(5f, 13f); line(12f, 13f)
                } else {
                    move(15f, 14f); line(5f, 4f); move(5f, 4f); line(5f, 11f); move(5f, 4f); line(12f, 4f)
                }
                move(3f, 17.5f); line(17f, 17.5f)
            }
            Icon.Plus -> {
                move(9f, 2f); line(9f, 16f); move(2f, 9f); line(16f, 9f)
            }
            Icon.Close -> {
                move(2f, 2f); line(14f, 14f); move(14f, 2f); line(2f, 14f)
            }
        }
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke * scale
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color.toInt()
        canvas.drawPath(path, paint)
    }
}
