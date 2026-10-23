package com.ngocdevv.nativecook.blocks.charts.nightsleepscore

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale as drawScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class NightSleepScoreReadoutKey(val segmentIndex: Int?, val manual: Boolean, val minute: Int)

internal fun nightSleepScoreReadoutKey(frame: NightSleepScoreFrame, controller: NightSleepScoreController): NightSleepScoreReadoutKey {
    val index = if (frame.selectionPresence > .001) frame.segmentIndex else null
    val manual = index != null && controller.hasManualSelection
    return NightSleepScoreReadoutKey(index, manual, if (manual) controller.selectedMinute?.toInt() ?: 0 else 0)
}

internal fun nightSleepScoreMinuteAt(xDp: Float, scale: Float): Double {
    val c = NightSleepScoreTheme.Card.Chart
    val bounded = min(c.width * scale, max(0f, xDp - c.touchInset * scale))
    return (bounded / (c.width * scale) * NightSleepScoreData.windowMinutes).toDouble()
}

internal class NightSleepScoreCardActions(
    val controller: NightSleepScoreController,
    val haptics: HapticFeedback,
    val now: () -> Double,
    val wake: (Double) -> Unit,
    val onChartVisibility: (Boolean) -> Unit,
    val safeTop: Float,
    val screenHeight: Float,
) {
    fun haptic() = haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    fun select(stage: NightSleepScoreStage) {
        val t = now()
        controller.select(stage, t)
        haptic()
        wake(t)
    }
    fun adjust(forward: Boolean) {
        val t = now()
        controller.adjustStage(forward, t)
        haptic()
        wake(t)
    }
    fun reset() {
        val t = now()
        controller.reset(t)
        haptic()
        wake(t)
    }
}

private fun semanticText(key: NightSleepScoreReadoutKey): String {
    val i = key.segmentIndex ?: return "No stage selected"
    val label = NightSleepScoreData.segments[i].stage.title
    return if (key.manual) "Selected $label, ${key.minute} minutes into the overnight window" else "Night replay, $label"
}

private object StaticChart {
    class Bar(val stage: NightSleepScoreStage, val x: Float, val y: Float, val width: Float)

    private val chart = NightSleepScoreTheme.Card.Chart
    val bars: List<Bar>
    val connector = androidx.compose.ui.graphics.Path()

    init {
        var elapsed = 0
        var prevX = 0f
        var prevY = 0f
        var first = true
        val list = ArrayList<Bar>()
        NightSleepScoreData.segments.forEach { segment ->
            val left = chart.width * elapsed / NightSleepScoreData.windowMinutes
            val y = chart.level(segment.stage)
            if (!first) {
                connector.moveTo(prevX, prevY)
                connector.lineTo(left, y)
            }
            elapsed += segment.minutes
            val right = chart.width * elapsed / NightSleepScoreData.windowMinutes
            list += Bar(segment.stage, left, y - chart.barHeight / 2, max(1.5f, right - left - chart.barGap))
            prevX = right
            prevY = y
            first = false
        }
        bars = list
    }
}

/** The sleep card: day card, night sky, text that turns light when the sky reaches it, ring, legend, hypnogram, readout. */
@Composable
internal fun NightSleepScoreSleepCard(
    scale: Float,
    sample: State<NightSleepScoreSample>,
    readout: State<NightSleepScoreReadoutKey>,
    actions: NightSleepScoreCardActions,
    modifier: Modifier = Modifier,
) {
    val theme = NightSleepScoreTheme
    val card = NightSleepScoreTheme.Card
    val at = NightSleepScoreTheme.Card.At
    fun front(position: Double): Float = NightSleepScoreMotion.nightAt(sample.value.sky.night, position).toFloat()
    fun tint(day: Color, night: Color, position: Double): () -> Color = { nightSleepScoreMix(day, night, front(position)) }
    val shape = RoundedCornerShape((card.radius * scale).dp)

    Box(
        modifier.size((card.width * scale).dp, (card.height * scale).dp)
            .shadow((12 * scale).dp, shape, ambientColor = theme.shadow, spotColor = theme.shadow)
            .clip(shape).background(theme.card),
    ) {
        NightSleepScoreSkyBackdrop(scale, sample)
        Canvas(Modifier.size((card.width * scale).dp, (card.height * scale).dp).clearAndSetSemantics { }) {
            val color = nightSleepScoreMix(NightSleepScoreTheme.Day.border, NightSleepScoreTheme.Night.border, sample.value.sky.night.toFloat())
            drawRoundRect(color, Offset(.5f * density, .5f * density), Size(size.width - density, size.height - density), CornerRadius(card.radius * scale * density), Stroke(density))
        }

        // Text column.
        Column(Modifier.offset((16 * scale).dp, (18 * scale).dp).clearAndSetSemantics { }) {
            @Composable fun line(size: Float, lineHeight: Float, weight: FontWeight) = nightSleepScoreStyle(
                nightSleepScoreSp(size, scale), nightSleepScoreSp(lineHeight, scale), weight,
            )
            NightSleepScoreText("Sleep", line(15f, 18f, FontWeight.Normal), Modifier.offset(0.dp, (-.7f * scale).dp), color = tint(NightSleepScoreTheme.Day.secondary, NightSleepScoreTheme.Night.secondary, at.label))
            NightSleepScoreText("High Score", line(19f, 24f, FontWeight.Bold), Modifier.offset(0.dp, (1.4f * scale).dp), color = tint(NightSleepScoreTheme.Day.ink, NightSleepScoreTheme.Night.ink, at.title))
            NightSleepScoreText("Duration", line(15f, 18f, FontWeight.Normal), Modifier.offset(0.dp, (14.4f * scale).dp), color = tint(NightSleepScoreTheme.Day.secondary, NightSleepScoreTheme.Night.secondary, at.duration))
            NightSleepScoreText("7h 12m", line(15f, 18f, FontWeight.SemiBold), Modifier.offset(0.dp, (16.4f * scale).dp), color = tint(NightSleepScoreTheme.Day.ink, NightSleepScoreTheme.Night.ink, at.durationValue))
        }

        // Score ring: the day arcs fade out as the night ring takes over.
        val ring = NightSleepScoreTheme.Card.Ring
        Box(Modifier.offset(((ring.cx - 56) * scale).dp, ((ring.cy - 56) * scale).dp).size((112 * scale).dp).clearAndSetSemantics { contentDescription = "Sleep score 84" }) {
            Canvas(Modifier.size((112 * scale).dp).graphicsLayer { alpha = 1f - front(at.ring) }) {
                val k = scale * density
                drawScale(k, k, pivot = Offset.Zero) {
                    drawCircle(NightSleepScoreTheme.Day.divider, ring.radius, Offset(56f, 56f), style = Stroke(1f))
                    fun seg(from: Float, to: Float, color: Color) = drawPath(
                        nightSleepScoreArc(56f, 56f, ring.radius, from - .25f, to - .25f), color, style = Stroke(16f, cap = StrokeCap.Round),
                    )
                    seg(.75f, .965f, theme.teal)
                    seg(.025f, .19f, theme.orange)
                    seg(.25f, .69f, theme.blue)
                }
            }
            NightSleepScoreText(
                "84", nightSleepScoreStyle(nightSleepScoreSp(28.5f, scale), nightSleepScoreSp(34f, scale), FontWeight.Bold)
                    .copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                Modifier.width((112 * scale).dp).offset((-1 * scale).dp, (38.8f * scale).dp), color = tint(NightSleepScoreTheme.Day.ink, NightSleepScoreTheme.Night.ink, at.ring),
            )
        }

        Divider(scale, 134f, at.divider1, sample)
        Legend(scale, sample, readout, actions)
        Chart(scale, sample, readout, actions)
        NightSleepScoreOverlay(scale, sample)
        Divider(scale, 247f, at.divider2, sample)
        Readout(scale, sample, readout, actions)
    }
}

@Composable
private fun Divider(scale: Float, top: Float, position: Double, sample: State<NightSleepScoreSample>) {
    val theme = NightSleepScoreTheme
    Canvas(Modifier.offset((16 * scale).dp, (top * scale).dp).size((338 * scale).dp, max(1f, scale).dp).clearAndSetSemantics { }) {
        drawRect(nightSleepScoreMix(NightSleepScoreTheme.Day.divider, NightSleepScoreTheme.Night.divider, NightSleepScoreMotion.nightAt(sample.value.sky.night, position).toFloat()))
    }
}

// MARK: Legend

@Composable
private fun Legend(scale: Float, sample: State<NightSleepScoreSample>, readout: State<NightSleepScoreReadoutKey>, actions: NightSleepScoreCardActions) {
    val theme = NightSleepScoreTheme
    val at = NightSleepScoreTheme.Card.At
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentSelect by rememberUpdatedState(actions::select)
    val stages = NightSleepScoreStage.entries
    val key = readout.value
    val sp15 = nightSleepScoreSp(15f, scale)
    val sp18 = nightSleepScoreSp(18f, scale)
    Box(
        Modifier.offset((16 * scale).dp, (149.6f * scale).dp).size((135 * scale).dp, (88 * scale).dp)
            .pointerInput(scale, density, touchSlop) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var canceled = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if ((change.position - down.position).getDistance() > touchSlop * scale || change.isConsumed) canceled = true
                        if (!change.pressed) {
                            if (event.type == PointerEventType.Release && !canceled) {
                                val rowHeight = with(density) { (22.dp * scale).toPx() }
                                currentSelect(stages[(down.position.y / rowHeight).toInt().coerceIn(0, stages.lastIndex)])
                            }
                            break
                        }
                    }
                }
            }
            .clearAndSetSemantics {
                contentDescription = "Sleep stage legend"
                stateDescription = "Awake 14 minutes, REM 63 minutes, Core 333 minutes, Deep 36 minutes. ${semanticText(key)}"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    key.segmentIndex?.let { stages.indexOf(NightSleepScoreData.segments[it].stage).toFloat() } ?: 0f, 0f..3f,
                )
                customActions = listOf(
                    CustomAccessibilityAction("Next stage") { actions.adjust(true); true },
                    CustomAccessibilityAction("Previous stage") { actions.adjust(false); true },
                    CustomAccessibilityAction("Inspect Awake") { actions.select(NightSleepScoreStage.AWAKE); true },
                    CustomAccessibilityAction("Inspect REM") { actions.select(NightSleepScoreStage.REM); true },
                    CustomAccessibilityAction("Inspect Core") { actions.select(NightSleepScoreStage.CORE); true },
                    CustomAccessibilityAction("Inspect Deep") { actions.select(NightSleepScoreStage.DEEP); true },
                )
            },
    ) {
        // Highlight pill that follows the stage under the playhead (a width of 145 from x = -6).
        Canvas(Modifier.offset((-6 * scale).dp, 0.dp).size((145 * scale).dp, (88 * scale).dp)) {
            val s = sample.value
            val i = s.frame.segmentIndex?.takeIf { s.frame.selectionPresence > .001 }
            val stageIndex = i?.let { stages.indexOf(NightSleepScoreData.segments[it].stage) } ?: 0
            val color = nightSleepScoreMix(NightSleepScoreTheme.Day.highlight, NightSleepScoreTheme.Night.highlight, NightSleepScoreMotion.nightAt(s.sky.night, at.legend[stageIndex]).toFloat())
            drawRoundRect(
                color.copy(alpha = (.12 * s.frame.selectionPresence).toFloat()), Offset(0f, stageIndex * 22f * scale * this.density),
                Size(size.width, 22f * scale * this.density), CornerRadius(11f * scale * this.density),
            )
        }
        Column {
            stages.forEachIndexed { i, stage ->
                val minutes = NightSleepScoreData.total(stage)
                val duration = if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
                Row(Modifier.fillMaxWidth().height((22 * scale).dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                    val style = { w: FontWeight -> nightSleepScoreStyle(sp15, sp18, w) }
                    NightSleepScoreText(stage.title, style(FontWeight.SemiBold), maxLines = 1) {
                        nightSleepScoreMix(NightSleepScoreTheme.Day.stages.color(stage), NightSleepScoreTheme.Night.stages.color(stage), NightSleepScoreMotion.nightAt(sample.value.sky.night, at.legend[i]).toFloat())
                    }
                    NightSleepScoreText(duration, style(FontWeight.Bold), maxLines = 1) {
                        nightSleepScoreMix(NightSleepScoreTheme.Day.ink, NightSleepScoreTheme.Night.ink, NightSleepScoreMotion.nightAt(sample.value.sky.night, at.legend[i]).toFloat())
                    }
                }
            }
        }
    }
}

// MARK: Hypnogram

@Composable
private fun Chart(scale: Float, sample: State<NightSleepScoreSample>, readout: State<NightSleepScoreReadoutKey>, actions: NightSleepScoreCardActions) {
    val theme = NightSleepScoreTheme
    val chart = NightSleepScoreTheme.Card.Chart
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val currentWake by rememberUpdatedState(actions.wake)
    val currentVisibility by rememberUpdatedState(actions.onChartVisibility)
    val key = readout.value

    Canvas(Modifier.offset((chart.left * scale).dp, (chart.top * scale).dp).size((chart.width * scale).dp, (chart.height * scale).dp).clipToBounds().clearAndSetSemantics { }) {
        val s = sample.value
        val presence = s.frame.selectionPresence.toFloat()
        val night = s.sky.night
        val staticOpacity = (1 - .35f * presence) * (1 - .86f * NightSleepScoreMotion.nightAt(night, NightSleepScoreTheme.Card.At.chart).toFloat())
        val dayFade = 1f - NightSleepScoreMotion.nightAt(night, NightSleepScoreTheme.Card.At.chart).toFloat()
        val k = scale * this.density
        drawScale(k, k, pivot = Offset.Zero) {
            layer(staticOpacity) {
                chart.grid.forEach { y ->
                    drawLine(
                        chart.gridColor, Offset(0f, y), Offset(chart.width, y), .9f,
                        pathEffect = PathEffect.dashPathEffect(chart.gridDash),
                    )
                }
                drawRoundRect(
                    chart.coreTrack, Offset(0f, chart.level(NightSleepScoreStage.CORE) - chart.barHeight / 2),
                    Size(chart.width, chart.barHeight), CornerRadius(chart.barRadius),
                )
                drawPath(StaticChart.connector, chart.connectorColor, style = Stroke(chart.connectorWidth))
                StaticChart.bars.forEach { bar ->
                    val radius = CornerRadius(min(chart.barRadius, bar.width / 2))
                    if (bar.stage == NightSleepScoreStage.AWAKE) {
                        val c = NightSleepScoreTheme.Day.stages.awake
                        drawRoundRect(Brush.verticalGradient(listOf(c, c.copy(alpha = .35f)), bar.y, bar.y + chart.barHeight), Offset(bar.x, bar.y), Size(bar.width, chart.barHeight), radius)
                    } else {
                        drawRoundRect(NightSleepScoreTheme.Day.stages.color(bar.stage), Offset(bar.x, bar.y), Size(bar.width, chart.barHeight), radius)
                    }
                }
            }
            val index = s.frame.segmentIndex
            if (index != null && presence > 0) {
                val bar = StaticChart.bars[index]
                drawRoundRect(
                    NightSleepScoreTheme.Day.stages.color(bar.stage).copy(alpha = dayFade), Offset(bar.x, bar.y), Size(bar.width, chart.barHeight),
                    CornerRadius(chart.barRadius),
                )
            }
            val x = s.frame.cursorProgress.toFloat() * chart.width
            drawRoundRect(NightSleepScoreTheme.Day.accent.copy(alpha = .12f * presence * dayFade), Offset(x, 1f), Size(7f, 80f), CornerRadius(4f))
            drawRoundRect(NightSleepScoreTheme.Day.ink.copy(alpha = .9f * presence * dayFade), Offset(x, 1f), Size(1.5f, 80f), CornerRadius(1f))
        }
    }

    // The touch surface: begin on touch down, follow a horizontal drag, end on release; a vertical drag scrolls the feed.
    Box(
        Modifier.offset(((chart.left - chart.touchInset) * scale).dp, ((chart.top - 9) * scale).dp)
            .size(((chart.width + 2 * chart.touchInset) * scale).dp, ((chart.height + 16) * scale).dp)
            .pointerInput(actions.controller, scale, viewConfiguration, density) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val owner = down.id
                    val atDown = actions.now()
                    actions.controller.beginScrub(nightSleepScoreMinuteAt(down.position.x / density.density, scale), atDown)
                    currentWake(atDown)
                    var completed = false
                    var canceledByScroll = false
                    var horizontal = false
                    val slop = viewConfiguration.touchSlop
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == owner } ?: break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!horizontal && change.isConsumed && abs(dy) > abs(dx)) {
                                canceledByScroll = true
                                break
                            }
                            if (!horizontal && change.pressed) {
                                if (abs(dx) > slop && abs(dx) > abs(dy)) {
                                    horizontal = true
                                    change.consume()
                                } else if (abs(dy) > slop && abs(dy) > abs(dx)) {
                                    canceledByScroll = true
                                    break
                                }
                            }
                            val minute = nightSleepScoreMinuteAt(change.position.x / density.density, scale)
                            if (horizontal && change.pressed) {
                                actions.controller.updateScrub(minute)
                                change.consume()
                                currentWake(actions.now())
                            }
                            if (!change.pressed) {
                                if (event.type == PointerEventType.Release && !canceledByScroll && actions.controller.fingerDown) {
                                    actions.controller.updateScrub(minute)
                                    val releasedAt = actions.now()
                                    actions.controller.endScrub(releasedAt)
                                    currentWake(releasedAt)
                                    actions.haptic()
                                    completed = true
                                }
                                break
                            }
                        }
                    } finally {
                        if (!completed && actions.controller.fingerDown) {
                            val t = actions.now()
                            actions.controller.cancelHeldTouch(t)
                            currentWake(t)
                        }
                    }
                }
            }
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                val top = with(density) { rect.top.toDp().value }
                val bottom = with(density) { rect.bottom.toDp().value }
                currentVisibility(bottom > actions.safeTop && top < actions.screenHeight - 105f * scale)
            }
            .clearAndSetSemantics {
                contentDescription = "Sleep stage timeline, 23:18 to 06:44"
                stateDescription = semanticText(key)
                progressBarRangeInfo = ProgressBarRangeInfo(key.segmentIndex?.toFloat() ?: 0f, 0f..NightSleepScoreData.segments.lastIndex.toFloat())
                customActions = listOf(
                    CustomAccessibilityAction("Next sleep stage") { actions.adjust(true); true },
                    CustomAccessibilityAction("Previous sleep stage") { actions.adjust(false); true },
                    CustomAccessibilityAction("Inspect Awake") { actions.select(NightSleepScoreStage.AWAKE); true },
                    CustomAccessibilityAction("Inspect REM") { actions.select(NightSleepScoreStage.REM); true },
                    CustomAccessibilityAction("Inspect Core") { actions.select(NightSleepScoreStage.CORE); true },
                    CustomAccessibilityAction("Inspect Deep") { actions.select(NightSleepScoreStage.DEEP); true },
                )
            },
    )
}

/** Draws [block] into an offscreen layer whose alpha is [alpha] (a group opacity). */
private fun DrawScope.layer(alpha: Float, block: DrawScope.() -> Unit) {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply { this.alpha = alpha }
        canvas.saveLayer(Rect(Offset(-10000f, -10000f), Size(20000f, 20000f)), paint)
        block()
        canvas.restore()
    }
}

// MARK: Readout

@Composable
private fun Readout(scale: Float, sample: State<NightSleepScoreSample>, readout: State<NightSleepScoreReadoutKey>, actions: NightSleepScoreCardActions) {
    val theme = NightSleepScoreTheme
    val at = NightSleepScoreTheme.Card.At
    val swap = NightSleepScoreMotion.readoutSwap
    val key = readout.value
    fun front(position: Double) = NightSleepScoreMotion.nightAt(sample.value.sky.night, position).toFloat()
    Box(Modifier.offset((16 * scale).dp, (262 * scale).dp).size((338 * scale).dp, (52 * scale).dp)) {
        NightSleepScoreText(
            "You only had a few interruptions last night which helped you get a high sleep score.",
            nightSleepScoreStyle(nightSleepScoreSp(15f, scale), nightSleepScoreSp(20f, scale)),
            Modifier.fillMaxWidth().graphicsLayer { alpha = NightSleepScoreMotion.smootherStep((swap - sample.value.frame.selectionPresence) / swap).toFloat() },
        ) { nightSleepScoreMix(NightSleepScoreTheme.Day.ink, NightSleepScoreTheme.Night.ink, front(at.readout)) }
        val index = key.segmentIndex
        if (index != null) {
            val stage = NightSleepScoreData.segments[index].stage.title
            Column(Modifier.fillMaxWidth().graphicsLayer { alpha = NightSleepScoreMotion.smootherStep((sample.value.frame.selectionPresence - (1 - swap)) / swap).toFloat() }) {
                NightSleepScoreText(
                    if (key.manual) "$stage · ${NightSleepScoreData.clockTime(key.minute.toDouble())}" else "Night replay · $stage",
                    nightSleepScoreStyle(nightSleepScoreSp(15f, scale), nightSleepScoreSp(20f, scale), FontWeight.SemiBold), maxLines = 1,
                ) { nightSleepScoreMix(NightSleepScoreTheme.Day.ink, NightSleepScoreTheme.Night.ink, front(at.readout)) }
                NightSleepScoreText(
                    "23:18–06:44 overnight window", nightSleepScoreStyle(nightSleepScoreSp(13f, scale), nightSleepScoreSp(17f, scale)), maxLines = 1,
                ) { nightSleepScoreMix(NightSleepScoreTheme.Day.secondary, NightSleepScoreTheme.Night.secondary, front(at.readoutSub)) }
            }
            if (key.manual) {
                Box(
                    Modifier.align(Alignment.TopEnd).size((44 * scale).dp)
                        .graphicsLayer { alpha = NightSleepScoreMotion.smootherStep((sample.value.frame.selectionPresence - (1 - swap)) / swap).toFloat() }
                        .clickable(role = Role.Button, onClickLabel = "Done") { actions.reset() }
                        .semantics { contentDescription = "Done" },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    NightSleepScoreText(
                        "Done", nightSleepScoreStyle(nightSleepScoreSp(14f, scale), nightSleepScoreSp(18f, scale), FontWeight.SemiBold), maxLines = 1,
                    ) { nightSleepScoreMix(NightSleepScoreTheme.Day.accent, NightSleepScoreTheme.Night.accent, front(at.readout)) }
                }
            }
        }
    }
}
