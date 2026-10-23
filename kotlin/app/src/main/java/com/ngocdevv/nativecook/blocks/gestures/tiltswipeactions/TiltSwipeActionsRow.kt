package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

/** Gesture.Tap's defaults: a tap is a touch that lifts within 500 ms and 10 dp. */
private const val TAP_MAX_MS = 500L
private const val TAP_MAX_DIST = 10f

/** The shadow of the glass slab (SwipeRow.tsx `slab`). Room around the slab for the blur to draw in. */
private val SLAB_BLEED = 40.dp

/**
 * SwipeRow.tsx: a transaction row with trailing actions. Drag left: the row lifts into a glass
 * slab and leans with the finger's velocity; the actions grow in the uncovered strip; past the
 * full-swipe line Delete takes the whole strip.
 */
@Composable
internal fun TiltSwipeRow(
    screen: TiltScreen,
    row: TiltRow,
    flagged: Boolean,
    interactive: Boolean,
    reduceMotion: Boolean,
) {
    val state = remember { screen.createRowState(row) }
    DisposableEffect(state) {
        screen.register(state)
        onDispose { screen.unregister(state) }
    }
    LaunchedEffect(state) { state.playRestore() }
    val item = row.item
    val flagLabel = if (flagged) "Unflag" else "Flag"

    Box(
        modifier = Modifier
            .width(FLAT_WIDTH.dp)
            // The shell: its height follows `height` and the row stays centred in it.
            .layout { measurable, constraints ->
                val rowHeight = TiltLayout.row.dp.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, rowHeight))
                val shell = (rowHeight * state.height.value).roundToInt()
                layout(constraints.maxWidth, shell) { placeable.place(0, (shell - rowHeight) / 2) }
            }
            .graphicsLayer { alpha = state.fade.value }
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "${item.title}, ${item.day}, ${item.amount}${if (flagged) ", flagged" else ""}"
                role = Role.Button
                customActions = listOf(
                    CustomAccessibilityAction(flagLabel) {
                        screen.onFlag(item.key)
                        true
                    },
                    CustomAccessibilityAction("Delete") {
                        state.removeNow()
                        true
                    },
                )
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            ActionStrip(screen, state, flagLabel, interactive)
            RowContent(state, item, flagged, interactive)
        }
    }
}

/** The uncovered strip on the right: Flag and Delete grow in as the row slides away. */
@Composable
private fun ActionStrip(screen: TiltScreen, state: TiltRowState, flagLabel: String, interactive: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = state.strip.value }
            .drawWithContent {
                val stripWidth = max(-state.x.value, 0f).dp.toPx()
                clipRect(left = size.width - stripWidth) { this@drawWithContent.drawContent() }
            },
    ) {
        FlagTile(screen, state, flagLabel, interactive)
        DeleteTile(state, interactive)
    }
}

private val tileTop = ((TiltLayout.row - TiltLayout.tileHeight) / 2f).dp

@Composable
private fun BoxScope.FlagTile(screen: TiltScreen, state: TiltRowState, flagLabel: String, interactive: Boolean) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = tileTop, end = (TiltLayout.gutter + TiltLayout.tile + TiltLayout.tileGap).dp)
            .size(TiltLayout.tile.dp, TiltLayout.tileHeight.dp)
            .graphicsLayer {
                val strip = max(-state.x.value, 0f)
                val p = ((strip - TiltLayout.gutter - TiltLayout.tile - TiltLayout.tileGap) / TiltLayout.tile)
                    .coerceIn(0f, 1f)
                alpha = p * (1f - state.full.value)
                val scale = (0.6f + 0.4f * p) * (1f - 0.3f * state.full.value)
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(TiltLayout.tileRadius.dp))
            .background(TiltColor.plum)
            .clickable(interactionSource = source, indication = null, enabled = interactive) {
                screen.onFlag(state.key)
                screen.setActiveKey("")
            },
    ) {
        TilePress {
            FeatherGlyph(FeatherIcon.Flag, 20f, TiltColor.white)
            TiltLabel(flagLabel, TiltType.tile, TiltColor.white)
        }
    }
}

@Composable
private fun BoxScope.DeleteTile(state: TiltRowState, interactive: Boolean) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = tileTop, end = TiltLayout.gutter.dp)
            // Its width: one tile, stretching over the whole strip at a full swipe.
            .layout { measurable, _ ->
                val strip = max(-state.x.value, 0f)
                val stretched = max(strip - TiltLayout.gutter - TiltLayout.tileGap, TiltLayout.tile)
                val width = (TiltLayout.tile + (stretched - TiltLayout.tile) * state.full.value).dp.roundToPx()
                val height = TiltLayout.tileHeight.dp.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                transformOrigin = TransformOrigin(1f, 0.5f)
                val strip = max(-state.x.value, 0f)
                val p = ((strip - TiltLayout.gutter) / TiltLayout.tile).coerceIn(0f, 1f)
                alpha = p
                val scale = 0.6f + 0.4f * p
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(TiltLayout.tileRadius.dp))
            .drawBehind { drawRect(rgbLerp(TiltColor.white, TiltColor.pink, state.red.value)) }
            .clickable(interactionSource = source, indication = null, enabled = interactive) { state.removeNow() },
    ) {
        TilePress {
            Box {
                Box(Modifier.graphicsLayer { alpha = 1f - state.red.value }) {
                    FeatherGlyph(FeatherIcon.Trash2, 20f, TiltColor.pink)
                }
                Box(Modifier.graphicsLayer { alpha = state.red.value }) {
                    FeatherGlyph(FeatherIcon.Trash2, 20f, TiltColor.white)
                }
            }
            Box {
                TiltLabel("Delete", TiltType.tile, TiltColor.pinkDeep, Modifier.graphicsLayer { alpha = 1f - state.red.value })
                TiltLabel("Delete", TiltType.tile, TiltColor.white, Modifier.graphicsLayer { alpha = state.red.value })
            }
        }
    }
}

/** `tilePress`: the tile's content, centred, 4 apart. */
@Composable
private fun TilePress(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) { content() }
}

/** The row itself: translated by the finger, leaning, lifted into a slab. */
@Composable
private fun RowContent(state: TiltRowState, item: TiltTransaction, flagged: Boolean, interactive: Boolean) {
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = state.x.value.dp.toPx()
                rotationY = state.tilt.value
                val scale = 1f + (TiltLean.lift - 1f) * state.lift.value
                scaleX = scale
                scaleY = scale
                cameraDistance = TiltLean.perspective * density
            }
            .onGloballyPositioned { coordinates[0] = it }
            .tiltRowGestures(state, interactive) { coordinates[0] },
    ) {
        Slab(state)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = TiltFlat.rowLeft.dp, end = TiltFlat.rowRight.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(TiltLayout.circle.dp)
                    .graphicsLayer { translationY = TiltDetail.rowIconOffsetY.dp.toPx() }
                    .clip(CircleShape)
                    .background(TiltColor.plum),
                contentAlignment = Alignment.Center,
            ) { FeatherGlyph(item.icon, 24f, item.tint) }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = (TiltLayout.textLeft - TiltFlat.rowLeft - TiltLayout.circle).dp),
            ) {
                TiltLabel(item.title, TiltType.rowTitle, TiltColor.title, maxLines = 1)
                TiltLabel(item.day, TiltType.rowSub, TiltColor.subtitle)
            }
            if (flagged) {
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TiltColor.plum),
                )
            }
            // The amount sits on the title's line, not the row's centre (as in the source).
            TiltLabel(
                text = item.amount,
                style = TiltType.amount,
                color = TiltColor.title,
                modifier = Modifier
                    .align(Alignment.Top)
                    .padding(
                        top = (
                            (TiltLayout.row - TiltType.rowTitle.lineHeight - TiltType.rowSub.lineHeight) / 2f +
                                TiltDetail.rowAmountOffsetY
                            ).dp,
                    ),
                tabular = true,
            )
        }
    }
}

/**
 * Gesture.Race(Pan, Tap). The pan takes the touch after 12 dp sideways unless the finger first
 * travels 10 dp up or down; like React Native Gesture Handler it reads the finger in screen
 * coordinates, so its travel is measured on the screen, not on the tilted plane.
 */
private fun Modifier.tiltRowGestures(
    state: TiltRowState,
    interactive: Boolean,
    coordinates: () -> LayoutCoordinates?,
): Modifier = pointerInput(state, interactive) {
    val dpPerPixel = 1f / density
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val layout = coordinates()
        if (!interactive || layout == null || !layout.isAttached) return@awaitEachGesture
        val origin = layout.localToRoot(down.position)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, origin)
        var active = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val lifted = !change.pressed
                val position = layout.localToRoot(change.position)
                val translationX = (position.x - origin.x) * dpPerPixel
                val translationY = (position.y - origin.y) * dpPerPixel
                if (!lifted) tracker.addPosition(change.uptimeMillis, position)
                val velocityX = tracker.calculateVelocity().x * dpPerPixel
                if (!active) {
                    if (lifted) {
                        val quick = change.uptimeMillis - down.uptimeMillis <= TAP_MAX_MS
                        if (quick && hypot(translationX, translationY) <= TAP_MAX_DIST) state.tap()
                        break
                    }
                    if (abs(translationY) >= TiltSwipe.failOffsetY) break
                    if (abs(translationX) >= TiltSwipe.activeOffsetX) {
                        active = true
                        state.begin()
                        change.consume()
                        state.move(translationX, velocityX, false)
                    }
                } else {
                    change.consume()
                    if (lifted) {
                        state.end(velocityX)
                        break
                    }
                    state.move(translationX, velocityX, false)
                }
            }
        } finally {
            // onFinalize: a cancelled drag ends without velocity (nothing happens after a normal end).
            if (active) state.end(0f)
        }
    }
}

/**
 * The glass slab the row lifts into while the finger holds it: a frosted rounded rectangle with
 * a hairline edge, a soft shadow and a specular sheen that slides against the row.
 */
@Composable
private fun Slab(state: TiltRowState) {
    val shadow = remember { TiltShadow(TiltColor.slabShadow, TiltDetail.slabShadowRadius) }
    val clip = remember { Path() }
    Box(
        modifier = Modifier
            .at(TiltDetail.slabInsetX, TiltDetail.slabInsetY)
            .size(
                (FLAT_WIDTH - TiltDetail.slabInsetX * 2f).dp,
                (TiltLayout.row - TiltDetail.slabInsetY * 2f).dp,
            )
            .bleed(SLAB_BLEED)
            .graphicsLayer { alpha = state.lift.value }
            .drawBehind {
                surface(SLAB_BLEED) { x, y, w, h ->
                    val radius = TiltLayout.rowRadius
                    shadow.draw(
                        this,
                        x,
                        y + TiltDetail.slabShadowY,
                        w,
                        h,
                        radius,
                        TiltDetail.slabShadowOpacity,
                    )
                    drawRoundRect(TiltColor.slab, Offset(x, y), Size(w, h), CornerRadius(radius))
                    val hairline = 1f / density
                    drawRoundRect(
                        color = TiltColor.slabEdge,
                        topLeft = Offset(x + hairline / 2f, y + hairline / 2f),
                        size = Size(w - hairline, h - hairline),
                        cornerRadius = CornerRadius(radius - hairline / 2f),
                        style = Stroke(hairline),
                    )
                    drawSheen(state, x, y, w, h, radius, clip)
                }
            },
    )
}

private val SHEEN_SKEW = tan(Math.toRadians(TiltDetail.sheenSkewDegrees.toDouble())).toFloat()

/** The sheen: a 90 wide band, skewed -20 degrees, sliding 0.6 of the row's travel; clipped to the slab. */
private fun DrawScope.drawSheen(
    state: TiltRowState,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    radius: Float,
    clip: Path,
) {
    val lean = min(abs(state.tilt.value) / TiltLean.max, 1f)
    val strength = state.lift.value * (0.5f + lean * 0.5f)
    if (strength <= 0f) return
    val left = TiltDetail.sheenStartX + state.x.value * TiltTiming.sheen
    clip.rewind()
    clip.addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(radius)))
    val skew = Matrix().apply {
        // x' = x + k * (y - centreY): skewX about the band's centre line.
        this[0, 1] = SHEEN_SKEW
        this[0, 3] = -SHEEN_SKEW * (h / 2f)
    }
    clipPath(clip) {
        withTransform({
            translate(x, y)
            transform(skew)
        }) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = TiltColor.sheen.map { it.copy(alpha = it.alpha * strength) },
                    startX = left,
                    endX = left + TiltDetail.sheenWidth,
                ),
                topLeft = Offset(left, 0f),
                size = Size(TiltDetail.sheenWidth, h),
            )
        }
    }
}
