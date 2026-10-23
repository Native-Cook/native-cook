package com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite

import android.graphics.BlurMaskFilter
import android.graphics.Paint as NativePaint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteMotion.Cup
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteMotion.Invite
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteMotion.Reduced
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteMotion.Scan
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteTheme.Box
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteTheme.Colors
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteTheme.Copy
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteTheme.Radius
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteTheme.Space
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteTheme.Type
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class FindFriendsInviteGeometry(
    val widthDp: Float,
    val heightDp: Float,
    val topInsetDp: Float,
    val density: Float,
    val scaledDensity: Float,
) {
    val dx = widthDp / 2f - FindFriendsInviteTheme.REF_W / 2f
    val dy = topInsetDp - FindFriendsInviteTheme.REF_TOP
    val cardWidth = widthDp - Space.cardMargin * 2f
    val inviteLeft = Space.cardMargin + Space.cardPadInvite
    val inviteTop = Box.invite.y + dy
    val inviteWidth = cardWidth - Space.cardPadInvite * 2f
    val checkLeft = Space.cardMargin + Space.cardPadCheck
    val checkTop = Box.check.y + dy
    val checkWidth = cardWidth - Space.cardPadCheck * 2f

    fun px(value: Float): Float = value * density
    fun y(value: Float): Float = (value + dy) * density
}


internal fun DrawScope.drawFindFriendsInvite(
    clocks: FindFriendsInviteMotion.Clocks,
    geometry: FindFriendsInviteGeometry,
    assets: FindFriendsInviteAssets,
    overlay: FindFriendsInviteOverlay,
) {
    drawPageBackground(geometry)
    drawSearch(geometry, assets)
    drawInviteCard(clocks, geometry, assets)
    drawContactsCard(clocks, geometry, assets)
    drawIntoCanvas { canvas -> overlay.draw(canvas.nativeCanvas, clocks, geometry, assets) }
}

private fun DrawScope.drawPageBackground(geometry: FindFriendsInviteGeometry) {
    val denominator = maxOf(geometry.heightDp, 874f)
    val stops = FindFriendsInviteTheme.Colors.pageStops.map { (position, color) ->
        val location = min(1f, (position + geometry.dy * (position / 874f)) / denominator)
        location to color
    }.toTypedArray()
    drawRect(
        brush = Brush.linearGradient(
            colorStops = stops,
            start = Offset(0f, 0f),
            end = Offset(0f, size.height),
        ),
    )
}

private fun DrawScope.drawSearch(geometry: FindFriendsInviteGeometry, assets: FindFriendsInviteAssets) {
    val pillLeft = geometry.px(Box.pill.x)
    val pillTop = geometry.y(Box.pill.y)
    val pillWidth = geometry.px(Box.pill.width)
    val pillHeight = geometry.px(Box.pill.height)
    val pillRadius = geometry.px(Radius.pill)

    drawIntoCanvas { canvas ->
        val shadow = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
            color = Color.Black.copy(alpha = Box.pillShadow.opacity).toArgb()
            maskFilter = BlurMaskFilter(geometry.px(Box.pillShadow.blur), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawRoundRect(
            pillLeft,
            pillTop + geometry.px(Box.pillShadow.dy),
            pillLeft + pillWidth,
            pillTop + geometry.px(Box.pillShadow.dy) + pillHeight,
            pillRadius,
            pillRadius,
            shadow,
        )
    }
    drawRoundRect(
        color = Colors.ink,
        topLeft = Offset(pillLeft, pillTop),
        size = Size(pillWidth, pillHeight),
        cornerRadius = CornerRadius(pillRadius),
    )
    drawText(
        geometry = geometry,
        value = Copy.placeholder,
        x = Box.placeholder.x,
        baseline = Box.placeholder.baseline + geometry.dy,
        token = Type.placeholder,
        typeface = assets.medium,
    )

    val half = Box.close.size / 2f
    val closeCenter = Offset(geometry.px(Box.close.centerX), geometry.y(Box.close.centerY))
    val closeHalf = geometry.px(half)
    drawLine(
        color = Colors.ink,
        start = closeCenter - Offset(closeHalf, closeHalf),
        end = closeCenter + Offset(closeHalf, closeHalf),
        strokeWidth = geometry.px(Box.close.stroke),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Colors.ink,
        start = closeCenter + Offset(closeHalf, -closeHalf),
        end = closeCenter + Offset(-closeHalf, closeHalf),
        strokeWidth = geometry.px(Box.close.stroke),
        cap = StrokeCap.Round,
    )
}


private fun DrawScope.drawInviteCard(
    clocks: FindFriendsInviteMotion.Clocks,
    geometry: FindFriendsInviteGeometry,
    assets: FindFriendsInviteAssets,
) {
    drawRoundRect(
        color = Colors.card1,
        topLeft = Offset(geometry.px(Space.cardMargin), geometry.y(Box.card1.y)),
        size = Size(geometry.px(geometry.cardWidth), geometry.px(Box.card1.height)),
        cornerRadius = CornerRadius(geometry.px(Radius.card)),
    )

    val cupKick = if (clocks.reduced) {
        0.0
    } else {
        Cup.amp * FindFriendsInviteMotion.kick(clocks.cup, Cup.period, Cup.decay, Cup.fadeFrom, Cup.end) +
            Cup.tapAmp * FindFriendsInviteMotion.kick(clocks.cupTap, Cup.period, Cup.decay, Cup.fadeFrom, Cup.end)
    }
    val cupShift = if (clocks.reduced) {
        0.0
    } else {
        Cup.shiftX * FindFriendsInviteMotion.kick(
            clocks.cup - Cup.shiftLag,
            Cup.period,
            Cup.decay,
            Cup.fadeFrom,
            Cup.end,
        )
    }
    val cupCenter = Offset(
        geometry.px(Box.cup.centerX + geometry.dx + cupShift.toFloat()),
        geometry.y(Box.cup.centerY),
    )
    val cupSize = Size(geometry.px(Box.cup.width), geometry.px(Box.cup.height))
    withTransform({
        rotate(cupKick.toFloat(), cupCenter)
    }) {
        drawBitmap(
            bitmap = assets.cup,
            left = cupCenter.x - cupSize.width / 2f,
            top = cupCenter.y - cupSize.height / 2f,
            width = cupSize.width,
            height = cupSize.height,
        )
    }

    drawCenteredText(
        geometry = geometry,
        value = Copy.h1,
        baseline = Box.h1Baseline + geometry.dy,
        token = Type.h1,
        typeface = assets.extra,
    )

    val tau = clocks.invite
    val inviteScale = if (clocks.reduced) {
        1.0
    } else {
        FindFriendsInviteFold.pressScale(tau, Invite.pressIn, Invite.release, Invite.pressScale)
    }
    val shown = clocks.reduced || FindFriendsInviteFold.buttonShown(tau)
    if (!shown) return
    val left = geometry.px(geometry.inviteLeft)
    val top = geometry.px(geometry.inviteTop)
    val width = geometry.px(geometry.inviteWidth)
    val height = geometry.px(Box.invite.height)
    val center = Offset(left + width / 2f, top + height / 2f)
    withTransform({
        scale(inviteScale.toFloat(), inviteScale.toFloat(), center)
    }) {
        drawInviteButton(clocks, geometry, assets, left, top, width, height)
    }
}

private fun DrawScope.drawInviteButton(
    clocks: FindFriendsInviteMotion.Clocks,
    geometry: FindFriendsInviteGeometry,
    assets: FindFriendsInviteAssets,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
) {
    val radius = if (clocks.reduced) {
        geometry.px(Radius.invite)
    } else {
        geometry.px(FindFriendsInviteFold.sheetRadius(clocks.invite, Radius.invite.toDouble()).toFloat())
    }
    val clip = Path().apply {
        addRoundRect(RoundRect(Rect(left, top, left + width, top + height), CornerRadius(radius)))
    }
    // The button's own gradient runs top to bottom, as in the pin.
    clipPath(clip) {
        drawRect(
            brush = Brush.linearGradient(
                colorStops = Colors.inviteStops,
                start = Offset(left, top),
                end = Offset(left, top + height),
            ),
            topLeft = Offset(left, top),
            size = Size(width, height),
        )
    }

    // The label and the plane scale about the button's centre and fade with the fold.
    val label = if (clocks.reduced) Pair(1.0, 1.0) else FindFriendsInviteFold.labelState(clocks.invite)
    val slotAlpha = if (clocks.reduced) {
        1.0 - (1.0 - Reduced.planeDip) * FindFriendsInviteMotion.triangle(clocks.invite / Reduced.inviteEnd)
    } else {
        1.0
    }
    val center = Offset(left + width / 2f, top + height / 2f)
    withTransform({
        scale(label.second.toFloat(), label.second.toFloat(), center)
    }) {
        val slotCenter = Offset(
            geometry.px(Box.plane.x + geometry.dx + Box.plane.size / 2f),
            geometry.y(Box.plane.y + Box.plane.size / 2f),
        )
        drawPlane(
            center = slotCenter,
            sizeDp = Box.plane.size,
            geometry = geometry,
            fill = Colors.white,
            fold = Colors.planeFold,
            rotation = 0f,
            scale = 1f,
            alpha = (slotAlpha * label.first).toFloat(),
        )
        drawText(
            geometry = geometry,
            value = Copy.invite,
            x = Box.inviteLabel.x + geometry.dx,
            baseline = Box.inviteLabel.baseline + geometry.dy,
            token = Type.invite,
            typeface = assets.bold,
            alpha = label.first.toFloat(),
        )
    }
}

private fun DrawScope.drawContactsCard(
    clocks: FindFriendsInviteMotion.Clocks,
    geometry: FindFriendsInviteGeometry,
    assets: FindFriendsInviteAssets,
) {
    drawRoundRect(
        color = Colors.card2,
        topLeft = Offset(geometry.px(Space.cardMargin), geometry.y(Box.card2.y)),
        size = Size(geometry.px(geometry.cardWidth), geometry.px(Box.card2.height)),
        cornerRadius = CornerRadius(geometry.px(Radius.card)),
    )

    // While the contacts are liquid the overlay draws the avatars instead.
    if (clocks.reduced || FindFriendsInviteGoo.avatarsShown(clocks.scan)) {
        Box.avatars.forEach { avatar ->
            drawAvatar(
                geometry = geometry,
                bitmap = assets.head(avatar.source),
                avatar = avatar,
                translateY = 0f,
                scale = 1f,
            )
        }
    }

    drawCenteredText(
        geometry = geometry,
        value = Copy.h2,
        baseline = Box.h2Baseline + geometry.dy,
        token = Type.h2,
        typeface = assets.extra,
    )
    Copy.body.forEachIndexed { index, segments ->
        drawCenteredSegments(
            geometry = geometry,
            segments = segments,
            baseline = Box.bodyBaselines[index] + geometry.dy,
            medium = assets.medium,
            semi = assets.semi,
            suffix = if (index == 2) Copy.lock else null,
        )
    }

    val scale = if (clocks.reduced) {
        1.0
    } else {
        FindFriendsInviteFold.pressScale(clocks.scan, Scan.pressIn, Scan.release, Scan.pressScale)
    }
    val left = geometry.px(geometry.checkLeft)
    val top = geometry.px(geometry.checkTop)
    val width = geometry.px(geometry.checkWidth)
    val height = geometry.px(Box.check.height)
    val center = Offset(left + width / 2f, top + height / 2f)
    withTransform({
        scale(scale.toFloat(), scale.toFloat(), center)
    }) {
        val radius = geometry.px(Radius.check)
        val clip = Path().apply {
            addRoundRect(RoundRect(Rect(left, top, left + width, top + height), CornerRadius(radius)))
        }
        clipPath(clip) {
            drawRect(Colors.buttonBlack, Offset(left, top), Size(width, height))
            val fill = fillPose(clocks.scan, clocks.reduced)
            if (fill.opacity > 0f) {
                drawRoundRect(
                    color = Colors.white.copy(alpha = fill.opacity),
                    topLeft = Offset(left - width * (1f - fill.progress), top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(radius),
                )
            }
        }
        drawCenteredText(
            geometry = geometry,
            value = Copy.check,
            baseline = Box.checkLabelBaseline + geometry.dy,
            token = Type.check,
            typeface = assets.semi,
        )
    }
}

private data class FillPose(val progress: Float, val opacity: Float)

private fun fillPose(time: Double, reduced: Boolean): FillPose {
    if (reduced) {
        return FillPose(
            progress = 1f,
            opacity = (Scan.fillOpacity * FindFriendsInviteMotion.triangle(time / Reduced.scanEnd)).toFloat(),
        )
    }
    val fillEnd = Scan.fillStart + Scan.fill
    val progress = FindFriendsInviteMotion.seg(time, Scan.fillStart, fillEnd)
    val opacity = if (time <= Scan.fillStart) {
        0.0
    } else {
        Scan.fillOpacity * (
            1.0 - FindFriendsInviteMotion.ease(
                FindFriendsInviteMotion.easeOut,
                FindFriendsInviteMotion.seg(time, fillEnd, fillEnd + Scan.fadeDur),
            )
            )
    }
    return FillPose(progress.toFloat(), opacity.toFloat())
}

private fun DrawScope.drawAvatar(
    geometry: FindFriendsInviteGeometry,
    bitmap: ImageBitmap,
    avatar: FindFriendsInviteTheme.AvatarToken,
    translateY: Float,
    scale: Float,
) {
    val diameter = geometry.px(avatar.diameter * scale)
    val center = Offset(
        geometry.px(avatar.centerX + geometry.dx),
        geometry.y(avatar.centerY + translateY),
    )
    drawCircle(avatar.background, diameter / 2f, center)
    val clip = Path().apply {
        addOval(Rect(center - Offset(diameter / 2f, diameter / 2f), Size(diameter, diameter)))
    }
    clipPath(clip) {
        val imageSize = geometry.px(avatar.diameter * avatar.head * scale)
        val imageTop = center.y - imageSize / 2f + geometry.px(avatar.dy * avatar.diameter * scale)
        drawBitmap(
            bitmap = bitmap,
            left = center.x - imageSize / 2f,
            top = imageTop,
            width = imageSize,
            height = imageSize,
        )
    }
}

private fun DrawScope.drawPlane(
    center: Offset,
    sizeDp: Float,
    geometry: FindFriendsInviteGeometry,
    fill: Color,
    fold: Color,
    rotation: Float,
    scale: Float,
    alpha: Float,
) {
    if (alpha <= 0f) return
    val unit = geometry.px(sizeDp + 2f) / 58f
    fun point(x: Float, y: Float): Offset {
        return Offset((x - 28f) * unit, (y - 27.5f) * unit)
    }
    val upper = Path().apply {
        val first = point(56f, 0f)
        moveTo(first.x, first.y)
        val second = point(0f, 22f)
        lineTo(second.x, second.y)
        val third = point(24f, 31f)
        lineTo(third.x, third.y)
        close()
    }
    val lower = Path().apply {
        val first = point(56f, 0f)
        moveTo(first.x, first.y)
        val second = point(24f, 31f)
        lineTo(second.x, second.y)
        val third = point(33f, 55f)
        lineTo(third.x, third.y)
        close()
    }
    withTransform({
        translate(center.x, center.y)
        rotate(rotation, Offset.Zero)
        scale(scale, scale, Offset.Zero)
    }) {
        val shownFill = fill.copy(alpha = alpha)
        val shownFold = fold.copy(alpha = alpha)
        drawPath(upper, shownFill)
        drawPath(lower, shownFill)
        val outline = Stroke(width = 1.5f * unit, join = StrokeJoin.Round)
        drawPath(upper, shownFill, style = outline)
        drawPath(lower, shownFill, style = outline)
        drawLine(
            color = shownFold,
            start = point(55f, 1f),
            end = point(25f, 30f),
            strokeWidth = 1.6f * unit,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawBitmap(
    bitmap: ImageBitmap,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
) {
    drawImage(
        image = bitmap,
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
    )
}

private fun DrawScope.drawText(
    geometry: FindFriendsInviteGeometry,
    value: String,
    x: Float,
    baseline: Float,
    token: FindFriendsInviteTheme.TextToken,
    typeface: Typeface,
    alpha: Float = 1f,
) {
    drawIntoCanvas { canvas ->
        val paint = textPaint(geometry, token, typeface).apply { this.alpha = (alpha * 255f).roundToInt() }
        canvas.nativeCanvas.drawText(value, geometry.px(x), geometry.px(baseline), paint)
    }
}

private fun DrawScope.drawCenteredText(
    geometry: FindFriendsInviteGeometry,
    value: String,
    baseline: Float,
    token: FindFriendsInviteTheme.TextToken,
    typeface: Typeface,
) {
    drawIntoCanvas { canvas ->
        val paint = textPaint(geometry, token, typeface).apply {
            textAlign = NativePaint.Align.CENTER
        }
        canvas.nativeCanvas.drawText(value, size.width / 2f, geometry.px(baseline), paint)
    }
}

private fun DrawScope.drawCenteredSegments(
    geometry: FindFriendsInviteGeometry,
    segments: List<Pair<String, Boolean>>,
    baseline: Float,
    medium: Typeface,
    semi: Typeface,
    suffix: String?,
) {
    drawIntoCanvas { canvas ->
        val pieces = segments.map { (text, strong) ->
            val face = if (strong) semi else medium
            val paint = textPaint(geometry, Type.body, face)
            Triple(text, paint, paint.measureText(text))
        }.toMutableList()
        if (suffix != null) {
            val paint = textPaint(geometry, Type.body, medium)
            pieces += Triple(suffix, paint, paint.measureText(suffix))
        }
        var cursor = size.width / 2f - pieces.sumOf { it.third.toDouble() }.toFloat() / 2f
        pieces.forEach { (text, paint, width) ->
            canvas.nativeCanvas.drawText(text, cursor, geometry.px(baseline), paint)
            cursor += width
        }
    }
}

private fun textPaint(
    geometry: FindFriendsInviteGeometry,
    token: FindFriendsInviteTheme.TextToken,
    typeface: Typeface,
): NativePaint {
    return NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        color = token.color.toArgb()
        textSize = token.size * geometry.scaledDensity
        this.typeface = typeface
        letterSpacing = if (textSize == 0f) 0f else token.letterSpacing * geometry.scaledDensity / textSize
        isSubpixelText = true
    }
}
