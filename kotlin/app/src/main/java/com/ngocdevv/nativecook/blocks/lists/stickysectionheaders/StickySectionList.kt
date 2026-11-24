package com.ngocdevv.nativecook.blocks.lists.stickysectionheaders

import android.graphics.BlurMaskFilter
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MODE_AUTO = 0
private const val MODE_TOUCH = 1
private const val MODE_RETURN = 2

/** Pill: label scale, odometer geometry. */
private const val PILL_LABEL_SCALE = 0.84f
private const val LBL_W = 200f
private const val DIGIT_W = 9.6f
private const val DIGIT_H = 20f
private const val DOT_W = 5f
private const val DOLLAR_W = 9.5f
private const val PILL_FS = 15f

/** Month index. */
private const val MONTH_ITEM_H = 26f

/** The full bar fades out only once the in-list header is (almost) gone: 46 → 64 dp after sticking. */
private const val BAR_FADE_FROM = 46f
private const val BAR_FADE_TO = 64f
private const val BUBBLE_LH = 26f

/** expo-blur intensity 28 (light), read as a ~10 dp blur radius under a strong paper tint. */
private const val PILL_BLUR = 10f

/** Reanimated withTiming's default curve (ease in-out quad). */
private val TIMING = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

private val SHADOW = Color(0xFF2A2218)

private fun View.lightTap() = performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

/** React Native's interpolateColor: straight RGB blend in sRGB. */
private fun lerpRgb(a: Color, b: Color, u: Float) = Color(
    a.red + (b.red - a.red) * u,
    a.green + (b.green - a.green) * u,
    a.blue + (b.blue - a.blue) * u,
    a.alpha + (b.alpha - a.alpha) * u,
)

/** Clock, touch and index state; only what the draw reads is snapshot state. */
private class StickyCtl {
    val clock = mutableFloatStateOf(0f)
    val mode = mutableIntStateOf(MODE_AUTO)
    val railMonth = mutableIntStateOf(-1)
    val railActive = Animatable(0f)
    var now = 0f
    var lastTouch = 0f
    var dragging = false
    var returnFrom = 0f
    var returnAt = 0f
    var lastK = Int.MIN_VALUE
    var auto = true
    var reduced = false

    fun takeOver() {
        lastTouch = now
        mode.intValue = MODE_TOUCH
    }
}

/** A soft shadow paint (mask blur from API 28), drawn in px. */
private class ShadowPaint(color: Color, alpha: Float, sigmaPx: Float) {
    private val base = alpha
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.copy(alpha = 1f).toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(sigmaPx), BlurMaskFilter.Blur.NORMAL)
    }

    fun draw(scope: DrawScope, l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, alpha: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        paint.alpha = (base * alpha * 255f).roundToInt().coerceIn(0, 255)
        scope.drawIntoCanvas { it.nativeCanvas.drawRoundRect(l, t, r, b, rx, ry, paint) }
    }
}

/**
 * The reusable piece: a sectioned LazyColumn whose headers stick, get pushed by the next one, and once stuck shrink
 * into a frosted pill with a rolling running total; a month index on the right follows and can be dragged to jump.
 * All of it reads one scroll value inside draw lambdas; rows never read it, so they never recompose while scrolling.
 */
@Composable
fun StickySectionList(
    sections: List<LedgerSection>,
    months: List<String>,
    monthNames: List<String>,
    listTopHeight: Dp,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    reducedMotion: Boolean = false,
    bottomInset: Dp = 0.dp,
    /** Optional: receives the scroll offset in dp (for a title bar above the list). */
    scrollY: MutableFloatState? = null,
    listTop: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val d = density.density
    val view = LocalView.current
    val styles = rememberHeaderStyles()
    val items = remember(sections) { buildLedgerItems(sections) }

    // Whole-pixel item tops, exactly as the LazyColumn lays them out: index 0 is the list top, then every item, then the footer.
    val layout = remember(items, d, listTopHeight, bottomInset) {
        val heights = IntArray(items.size + 2)
        heights[0] = with(density) { listTopHeight.roundToPx() }
        items.forEachIndexed { i, it -> heights[i + 1] = with(density) { (if (it is LedgerItem.Header) HEADER_H else ROW_H).dp.roundToPx() } }
        heights[items.size + 1] = with(density) { (FOOTER_H.dp + bottomInset).roundToPx() }
        val tops = FloatArray(heights.size + 1)
        for (i in heights.indices) tops[i + 1] = tops[i] + heights[i]
        tops
    }
    val geo = remember(layout, sections, months.size) {
        val sectionTops = ArrayList<Float>()
        items.forEachIndexed { i, it -> if (it is LedgerItem.Header) sectionTops.add(layout[i + 1] / d) }
        val end = layout[items.size + 1] / d
        val footerH = (layout[items.size + 2] - layout[items.size + 1]) / d
        buildGeometry(sections, months.size, sectionTops.toFloatArray(), end, footerH)
    }

    val listState = rememberLazyListState()
    fun scrollPx(): Float {
        val i = listState.firstVisibleItemIndex.coerceIn(0, layout.size - 1)
        return layout[i] + listState.firstVisibleItemScrollOffset
    }
    fun scrollToDp(target: Float) {
        listState.dispatchRawDelta(target * d - scrollPx())
    }

    val ctl = remember { StickyCtl() }
    val auto = autoPlay && !reducedMotion
    SideEffect {
        ctl.auto = auto
        ctl.reduced = reducedMotion
    }
    val scope = rememberCoroutineScope()

    // ---- text, measured once ----
    val measurer = rememberTextMeasurer()
    val labelLayouts = remember(sections, styles) {
        sections.map {
            measurer.measure(
                it.label, styles.label, overflow = TextOverflow.Ellipsis, maxLines = 1,
                constraints = Constraints(maxWidth = (LBL_W * d).roundToInt()),
            )
        }
    }
    val subLayouts = remember(sections, styles) { sections.map { measurer.measure(paymentsLabel(it.txns.size), styles.sub) } }
    val totalLayouts = remember(sections, styles) { sections.map { measurer.measure(formatMoney(-daySpent(it), true), styles.total) } }
    val odoStyle = remember(density) { ledgerStyle(density, PILL_FS, DIGIT_H, FontWeight.SemiBold, INK, tabular = true) }
    val digitLayouts = remember(odoStyle) { List(10) { measurer.measure(it.toString(), odoStyle) } }
    val dollarLayout = remember(odoStyle) { measurer.measure("$", odoStyle) }
    val dotLayout = remember(odoStyle) { measurer.measure(".", odoStyle) }
    val monthStyle = remember(density) { ledgerStyle(density, 12f, 16f, FontWeight.SemiBold, MUTE, tabular = true) }
    val monthLayouts = remember(months, monthStyle) { months.map { measurer.measure(it, monthStyle) } }
    val bubbleStyle = remember(density) { ledgerStyle(density, 19f, BUBBLE_LH, FontWeight.Bold, BG) }
    val bubbleLayouts = remember(monthNames, bubbleStyle) { monthNames.map { measurer.measure(it, bubbleStyle) } }

    // ---- paints and layers ----
    val pillWideShadow = remember(d) { ShadowPaint(SHADOW, 0.14f, 9f * d) }
    val pillTightShadow = remember(d) { ShadowPaint(SHADOW, 0.2f, 1f * d) }
    val markShadow = remember(d) { ShadowPaint(SHADOW, 0.18f, 3f * d) }
    val bubbleShadow = remember(d) { ShadowPaint(SHADOW, 0.2f, 8f * d) }
    val ghostShadow = remember(d) { ShadowPaint(SHADOW, 0.16f, 5f * d) }
    val pillPath = remember { Path() }
    val screenPath = remember { Path() }
    val ghostPath = remember { Path() }
    val blurOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val listLayer = rememberGraphicsLayer()
    val blurLayer = rememberGraphicsLayer()
    val run = remember { FloatArray(3) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // A finger anywhere (list or index) takes over at once, before the list sees the touch.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    ctl.takeOver()
                    ctl.dragging = true
                    do {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                    } while (ev.changes.any { it.pressed })
                    ctl.dragging = false
                    ctl.lastTouch = ctl.now
                }
            },
    ) {
        val wDp = maxWidth.value
        val viewHDp = maxHeight.value
        val wPx = wDp * d

        // ---- clock: the loop scripts the scroll; touch takes over; 2 s idle eases back to the top and restarts ----
        LaunchedEffect(ctl, geo) {
            var last = -1L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (last < 0) 1f / 60f else min((nanos - last) / 1e9f, 0.05f)
                    last = nanos
                    ctl.now += dt
                    if (listState.isScrollInProgress) ctl.lastTouch = ctl.now // flings and momentum count as touch
                    when (ctl.mode.intValue) {
                        MODE_AUTO -> if (ctl.auto) {
                            val t = (ctl.clock.floatValue + dt) % LOOP
                            ctl.clock.floatValue = t
                            scrollToDp(scrollAt(t, geo))
                        }
                        MODE_TOUCH -> if (ctl.auto && !ctl.dragging && ctl.now - ctl.lastTouch > IDLE) {
                            ctl.returnFrom = scrollPx() / d
                            ctl.returnAt = ctl.now
                            ctl.mode.intValue = MODE_RETURN
                        }
                        else -> {
                            val u = min(1f, (ctl.now - ctl.returnAt) / RETURN_LEN)
                            scrollToDp(ctl.returnFrom * (1f - easeInOutCubic(u)))
                            if (u >= 1f) {
                                ctl.clock.floatValue = 0f
                                ctl.mode.intValue = MODE_AUTO
                            }
                        }
                    }
                    val s = scrollPx() / d
                    scrollY?.floatValue = s
                    // Haptic when the stuck header changes hands under a finger (not in autoplay, not from the index).
                    val k = if (ctl.mode.intValue == MODE_TOUCH && ctl.dragging && ctl.railActive.value < 0.5f) sectionAt(s, geo) else Int.MIN_VALUE
                    if (ctl.lastK != Int.MIN_VALUE && k != Int.MIN_VALUE && k != ctl.lastK && k >= 0) view.lightTap()
                    ctl.lastK = k
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (blurOn) {
                        listLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(listLayer)
                    } else {
                        drawContent()
                    }
                },
        ) {
            item(key = "top", contentType = "top") { Box(Modifier.fillMaxWidth().height(listTopHeight)) { listTop() } }
            items(items, key = { it.key }, contentType = { if (it is LedgerItem.Header) 0 else 1 }) { item ->
                when (item) {
                    is LedgerItem.Header -> SectionHeaderRow(item, styles)
                    is LedgerItem.Row -> TransactionRow(item)
                }
            }
            item(key = "footer", contentType = "footer") { Box(Modifier.fillMaxWidth().height(FOOTER_H.dp + bottomInset)) { footer() } }
        }

        // ---- shared drawing of the bar and the paper band (also fed into the pill's frosted backdrop) ----
        fun DrawScope.barAndBand(k: Int, p: Float, bar: Float) {
            if (bar > 0.002f) {
                drawRect(BG, Offset.Zero, Size(wPx, HEADER_H * d), alpha = bar)
                val so = bar * (1f - clamp01(p * 1.6f))
                if (so > 0.002f) {
                    clipRect(SIDE * d, SUB_Y * d, (SIDE + 160f) * d, (SUB_Y + SUB_LH) * d) {
                        drawText(subLayouts[k], topLeft = Offset(SIDE * d, SUB_Y * d), alpha = so)
                    }
                    val right = wPx - (RAIL_W + 4f) * d
                    clipRect(right - 110f * d, TOT_Y * d, right, (TOT_Y + TOT_LH) * d) {
                        val tl = totalLayouts[k]
                        drawText(tl, topLeft = Offset(right - tl.size.width, TOT_Y * d), alpha = so)
                    }
                }
                drawRect(HAIR, Offset(0f, HEADER_H * d - 1f), Size(wPx, 1f), alpha = bar)
            }
            val band = 1f - bar
            if (band > 0.002f) {
                val h = (PILL_TOP + PILL_H + 6f) * d
                drawRect(
                    Brush.verticalGradient(0f to BG, 0.8f to BG, 1f to BG.copy(alpha = 0f), startY = 0f, endY = h),
                    Offset.Zero, Size(wPx, h), alpha = band,
                )
            }
        }

        fun DrawScope.wheel(x: Float, top: Float, place: Int, alpha: Float) {
            if (alpha <= 0.002f) return
            val pos = wheelAt(run[0], run[1], run[2], place)
            clipRect(x * d, top * d, (x + DIGIT_W) * d, (top + DIGIT_H) * d) {
                val i0 = floor(pos).toInt()
                for (j in i0..i0 + 1) {
                    if (j < 0 || j > 19) continue
                    val l = digitLayouts[j % 10]
                    val y = (top + (j - pos) * DIGIT_H) * d
                    if (y > (top + DIGIT_H) * d || y + DIGIT_H * d < top * d) continue
                    drawText(l, topLeft = Offset(x * d + (DIGIT_W * d - l.size.width) / 2f, y), alpha = alpha)
                }
            }
        }

        fun DrawScope.centred(l: TextLayoutResult, x: Float, w: Float, top: Float, alpha: Float) {
            drawText(l, topLeft = Offset(x * d + (w * d - l.size.width) / 2f, top * d), alpha = alpha)
        }

        val pillLeft = (wDp - PILL_W) / 2f
        val railH = months.size * MONTH_ITEM_H
        val railTop = viewHDp / 2f - (railH + 16f) / 2f
        val railLeft = wDp - 4f - (RAIL_W - 6f)
        val bubbleW = max(96f * d, bubbleLayouts.maxOf { it.size.width }.toFloat()) + 36f * d

        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawBehind {
                    val s = scrollPx() / d
                    val k = sectionAt(s, geo)
                    val reduced = ctl.reduced
                    val p = if (reduced) (if (k >= 0 && s > geo.tops[k] + 4f) 1f else 0f) else pillAmount(s, k, geo)
                    // The opaque bar stays until the in-list header has scrolled out from under it, so the two never show at once.
                    val barA = if (k < 0) 1f else 1f - clamp01((s - geo.tops[k] - BAR_FADE_FROM) / (BAR_FADE_TO - BAR_FADE_FROM))
                    val bar = if (reduced) 1f - p else barA
                    val push = pushOffset(s, k, p, geo) * d

                    // ---------------- the sticky header: one copy for every section ----------------
                    if (k >= 0) translate(0f, push) {
                        barAndBand(k, p, bar)

                        // Pill: a frosted capsule grown from the full bar's size.
                        val po = easeInOutCubic(clamp01(p * 1.25f))
                        if (po > 0.002f) {
                            val sx = mix((wDp - 8f) / PILL_W, 1f, p)
                            val sy = mix(HEADER_H / PILL_H, 1f, p)
                            val cx = wPx / 2f
                            val cy = (PILL_TOP + PILL_H / 2f) * d
                            val l = pillLeft * d
                            val t = PILL_TOP * d
                            val r = l + PILL_W * d
                            val b = t + PILL_H * d
                            val rad = PILL_H / 2f * d
                            pillPath.reset()
                            pillPath.addRoundRect(RoundRect(l, t, r, b, CornerRadius(rad)))

                            // Shadows, outside the capsule only (scaled with it, as RN scales the view).
                            scale(sx, sy, Offset(cx, cy)) {
                                clipPath(pillPath, ClipOp.Difference) {
                                    pillWideShadow.draw(this, l, t + 8f * d, r, b + 8f * d, rad, rad, po)
                                    pillTightShadow.draw(this, l, t + 1f * d, r, b + 1f * d, rad, rad, po)
                                }
                            }

                            // Frosted backdrop (API 31+): the list, bar and band behind the capsule, blurred.
                            val hx = PILL_W / 2f * d * sx
                            val hy = PILL_H / 2f * d * sy
                            screenPath.reset()
                            screenPath.addRoundRect(RoundRect(cx - hx, cy - hy, cx + hx, cy + hy, CornerRadius(rad * sx, rad * sy)))
                            if (blurOn) {
                                val m = PILL_BLUR * 3f * d
                                val rl = floor(cx - hx - m)
                                val rt = floor(cy - hy - m)
                                val rw = ceil(hx * 2f + m * 2f).toInt()
                                val rh = ceil(hy * 2f + m * 2f).toInt()
                                blurLayer.renderEffect = BlurEffect(PILL_BLUR * d, PILL_BLUR * d, TileMode.Clamp)
                                blurLayer.alpha = po
                                blurLayer.record(size = IntSize(rw, rh)) {
                                    translate(-rl, -rt) {
                                        translate(0f, -push) { drawLayer(listLayer) }
                                        barAndBand(k, p, bar)
                                    }
                                }
                                clipPath(screenPath) { translate(rl, rt) { drawLayer(blurLayer) } }
                            }

                            scale(sx, sy, Offset(cx, cy)) {
                                val cr = CornerRadius(rad)
                                drawRoundRect(BG.copy(alpha = 0.3f), Offset(l, t), Size(r - l, b - t), cr, alpha = po)
                                drawRoundRect(BG.copy(alpha = 0.35f), Offset(l, t), Size(r - l, b - t), cr, alpha = po)
                                // Without a backdrop blur (below API 31) the tint is heavier, a frosted fallback.
                                val tint = if (blurOn) 0.74f else 0.9f
                                drawRoundRect(Color(250 / 255f, 249 / 255f, 246 / 255f, tint), Offset(l, t), Size(r - l, b - t), cr, alpha = po)
                                drawRoundRect(
                                    Color(40 / 255f, 34 / 255f, 26 / 255f, 0.14f), Offset(l + 0.5f, t + 0.5f), Size(r - l - 1f, b - t - 1f),
                                    CornerRadius(rad - 0.5f), style = Stroke(1f), alpha = po,
                                )
                                clipPath(pillPath) {
                                    drawRect(Color(1f, 1f, 1f, 0.85f), Offset(l + 14f * d, t), Size(PILL_W * d - 28f * d, 1f * d), alpha = po)
                                }
                            }
                        }

                        // Day label: moves from the bar's corner into the capsule, scaled to 0.84.
                        val sc = mix(1f, PILL_LABEL_SCALE, p)
                        val xT = mix(SIDE, pillLeft + 16f, p)
                        val yT = mix(LBL_Y, PILL_TOP + (PILL_H - LBL_LH * PILL_LABEL_SCALE) / 2f, p)
                        translate((xT - SIDE - LBL_W * (1f - sc) / 2f) * d, (yT - LBL_Y - LBL_LH * (1f - sc) / 2f) * d) {
                            scale(sc, sc, Offset((SIDE + LBL_W / 2f) * d, (LBL_Y + LBL_LH / 2f) * d)) {
                                clipRect(SIDE * d, LBL_Y * d, (SIDE + LBL_W) * d, (LBL_Y + LBL_LH) * d) {
                                    drawText(labelLayouts[k], topLeft = Offset(SIDE * d, LBL_Y * d))
                                }
                            }
                        }

                        // Odometer: the running total, right-aligned inside the pill.
                        val oa = easeInOutCubic(clamp01((p - 0.35f) / 0.65f))
                        if (oa > 0.002f) translate(0f, (1f - p) * 6f * d) {
                            runningAt(s, k, geo, run)
                            val v = mix(run[0], run[1], run[2])
                            val hundA = clamp01((v - 9000f) / 1000f)
                            val tensA = clamp01((v - 900f) / 100f)
                            val hidden = 2f - hundA - tensA
                            val odoRight = pillLeft + PILL_W - 14f
                            val xCent = odoRight - DIGIT_W
                            val xDime = xCent - DIGIT_W
                            val xDot = xDime - DOT_W
                            val xOnes = xDot - DIGIT_W
                            val xTens = xOnes - DIGIT_W
                            val xHund = xTens - DIGIT_W
                            val xDollar = xHund - DOLLAR_W
                            val odoTop = PILL_TOP + (PILL_H - DIGIT_H) / 2f
                            centred(dollarLayout, xDollar + hidden * DIGIT_W, DOLLAR_W, odoTop, oa)
                            wheel(xHund, odoTop, 10000, oa * hundA)
                            wheel(xTens, odoTop, 1000, oa * tensA)
                            wheel(xOnes, odoTop, 100, oa)
                            centred(dotLayout, xDot, DOT_W, odoTop, oa)
                            wheel(xDime, odoTop, 10, oa)
                            wheel(xCent, odoTop, 1, oa)
                        }
                    }

                    // ---------------- month index ----------------
                    val ra = ctl.railActive.value
                    val rm = ctl.railMonth.intValue
                    val month = if (ra > 0.5f && rm >= 0) rm.toFloat() else monthAt(s, geo)
                    val markTop = (railTop + 8f + month * MONTH_ITEM_H) * d
                    val markL = (railLeft + 2f) * d
                    val markR = (railLeft + RAIL_W - 6f - 2f) * d
                    val mr = MONTH_ITEM_H / 2f * d
                    markShadow.draw(this, markL, markTop + 3f * d, markR, markTop + 3f * d + MONTH_ITEM_H * d, mr, mr, 1f)
                    drawRoundRect(INK, Offset(markL, markTop), Size(markR - markL, MONTH_ITEM_H * d), CornerRadius(mr))
                    monthLayouts.forEachIndexed { i, l ->
                        val dd = clamp01(abs(month - i))
                        val y = (railTop + 8f + i * MONTH_ITEM_H) * d + (MONTH_ITEM_H * d - l.size.height) / 2f
                        drawText(l, color = lerpRgb(BG, MUTE, dd), topLeft = Offset((railLeft + (RAIL_W - 6f) / 2f) * d - l.size.width / 2f, y))
                    }

                    // Bubble with the full month name beside the finger.
                    if (ra > 0.002f) {
                        val i = max(0, rm)
                        val right = (railLeft + RAIL_W - 6f - (RAIL_W + 4f)) * d
                        val top = (railTop + 8f + (MONTH_ITEM_H - 44f) / 2f + i * MONTH_ITEM_H) * d
                        val left = right - bubbleW
                        val bh = 44f * d
                        translate((1f - ra) * 10f * d, 0f) {
                            scale(mix(0.92f, 1f, ra), Offset(left + bubbleW / 2f, top + bh / 2f)) {
                                bubbleShadow.draw(this, left, top + 8f * d, left + bubbleW, top + bh + 8f * d, 22f * d, 22f * d, ra)
                                drawRoundRect(INK, Offset(left, top), Size(bubbleW, bh), CornerRadius(22f * d), alpha = ra)
                                val tl = bubbleLayouts[i.coerceAtMost(bubbleLayouts.size - 1)]
                                drawText(tl, topLeft = Offset(left + 18f * d, top + (bh - BUBBLE_LH * d) / 2f), alpha = ra)
                            }
                        }
                    }

                    // ---------------- ghost finger (autoplay only) ----------------
                    if (ctl.mode.intValue == MODE_AUTO && ctl.auto) {
                        val t = ctl.clock.floatValue
                        val go = ghostOpacity(t) * 0.85f
                        if (go > 0.002f) {
                            val gy = ghostY(t) * viewHDp * d
                            val gx = wPx / 2f
                            val gr = 22f * d
                            ghostPath.reset()
                            ghostPath.addOval(androidx.compose.ui.geometry.Rect(Offset(gx, gy), gr))
                            clipPath(ghostPath, ClipOp.Difference) {
                                ghostShadow.draw(this, gx - gr, gy - gr + 4f * d, gx + gr, gy + gr + 4f * d, gr, gr, go)
                            }
                            drawCircle(Color(1f, 1f, 1f, 0.5f), gr, Offset(gx, gy), alpha = go)
                            drawCircle(Color(40 / 255f, 34 / 255f, 26 / 255f, 0.16f), gr - 0.5f * d, Offset(gx, gy), alpha = go, style = Stroke(1f * d))
                        }
                    }
                },
        )

        // ---- month index touch: drag or tap to jump (12 dp extra on the left, 4 on the right) ----
        Box(
            Modifier
                .offset((railLeft - 12f).dp, railTop.dp)
                .size((RAIL_W - 6f + 16f).dp, (railH + 16f).dp)
                .pointerInput(geo, viewHDp) {
                    fun railAt(yPx: Float) {
                        val i = floor((yPx / d - 8f) / MONTH_ITEM_H).toInt().coerceIn(0, months.size - 1)
                        if (i != ctl.railMonth.intValue) {
                            ctl.railMonth.intValue = i
                            // Each new month under the finger moves the list there at once.
                            val maxS = max(0f, geo.contentH - viewHDp)
                            scrollToDp(min(geo.monthTops[i], maxS))
                            view.lightTap()
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        ctl.takeOver()
                        ctl.dragging = true
                        ctl.railMonth.intValue = -1
                        scope.launch { ctl.railActive.animateTo(1f, tween(140, easing = TIMING)) }
                        railAt(down.position.y)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            c.consume()
                            ctl.lastTouch = ctl.now
                            railAt(c.position.y)
                        }
                        ctl.dragging = false
                        ctl.lastTouch = ctl.now
                        scope.launch { ctl.railActive.animateTo(0f, tween(260, easing = TIMING)) }
                    }
                },
        )
    }
}
