package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.components

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.theme.CARD_HEIGHT_RATIO
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.theme.CollectionCardBackColors
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.cssColor
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

enum class CollectorMarkFinish { Holographic, Solid }

enum class MarkerVariant { Diamond, Lock }

const val COLLECTION_CARD_ART_WIDTH = 218f
const val COLLECTION_CARD_ART_HEIGHT = COLLECTION_CARD_ART_WIDTH * CARD_HEIGHT_RATIO

// Vector silhouettes traced from the proportions of the marked reference item.
private val PATTERN_PATH_DATA = listOf(
    "M27.1 4.9 L6.2 4.9 L6.9 0 L0 0 L0 8.4 L1.4 5.6 L4.9 6.3 L4.9 43.2 L30.5 13.9 Z",
    "M5.6 66.2 L34 44.6 L56.9 36.2 L88.9 18.8 L102.8 22.3 L152.7 23.7 L164.5 22.3 L174.3 16 L175 22.3 L184 30 L212.4 41.1 L212.4 7 L206.9 4.2 L55.5 4.9 L52.1 12.5 L11.1 54.4 Z",
    "M108.3 34.1 L91.6 36.2 L85.4 42.5 L50.7 52.3 L34 62.7 L20.1 77.3 L11.1 98.3 L11.1 108.7 L19.4 120.5 L19.4 124.7 L4.9 142.8 L4.9 170 L9 185.4 L4.9 186.7 L4.9 212.5 L18.1 216.7 L18.1 209 L12.5 195.8 L16 195.1 L35.4 212.5 L55.5 208.3 L54.2 227.9 L61.1 239 L67.3 241.8 L90.9 239 L97.2 246 L115.2 247.4 L145.1 234.1 L170.1 218.1 L188.8 200 L197.9 186.7 L195.8 174.9 L202.7 157.5 L203.4 140.8 L198.6 127.5 L190.2 118.5 L177.7 117.8 L164.5 105.2 L152 100.3 L136.1 101 L134 103.8 L136.1 111.5 L155.5 115.7 L160.4 127.5 L179.1 142.8 L182.6 151.2 L181.2 163.8 L165.9 171.4 L153.4 188.8 L150.7 188.8 L148.6 183.3 L143.7 181.2 L128.4 198.6 L118 203.5 L102.8 204.9 L89.6 202.1 L86.8 205.6 L82.6 204.9 L77.8 198.6 L56.2 147.7 L51.4 141.5 L43 139.4 L46.5 110.8 L59 83.6 L54.8 72.5 L78.5 60.6 L115.2 54.4 L120.1 51.6 L121.5 46 L145.1 48.8 L164.5 55.7 L169.4 54.4 L163.8 46 L152.7 40.4 Z",
    "M181.9 65.5 L177 71.1 L177 81.5 L192.3 100.3 L212.4 114.3 L212.4 70.4 L193.7 63.4 Z",
    "M154.1 80.8 L126.4 72.5 L115.2 73.2 L107.6 77.3 L90.3 77.3 L81.2 81.5 L69.4 94.8 L61.8 109.4 L62.5 117.1 L68 118.5 L76.4 115 L81.9 110.1 L86.8 97.6 L97.2 96.9 L128.4 81.5 L143 81.5 L153.4 86.4 Z",
    "M213.1 201.4 L211.8 199.3 L206.9 208.3 L187.5 225.1 L179.1 237.6 L164.5 241.8 L152 249.5 L134 271.8 L93 272.5 L86.8 264.8 L58.3 248.8 L35.4 239.7 L11.1 235.5 L4.2 244.6 L4.2 291.3 L6.2 297.5 L21.5 295.4 L31.2 298.2 L70.1 298.2 L84.7 294.1 L115.9 294.8 L152.7 282.2 L175.6 269 L202 244.6 L207.6 236.2 L212.4 219.5 Z",
    "M212.4 288.5 L182.6 289.9 L168.7 297.5 L206.2 298.2 L212.4 296.1 Z",
)

private val COLLECTOR_MARK_PATH_DATA = listOf(
    "M111.1 138.7 L106.9 140.8 L106.9 142.8 L108.3 144.9 L108.3 154 L106.9 156.8 L107.6 158.9 L111.8 158.9 L110.4 152.6 Z",
    "M97.2 110.1 L92.3 112.2 L89.6 115 L82.6 126.1 L81.2 131.7 L83.3 133.1 L84.7 131.7 L84.7 129.6 L87.5 123.3 L92.3 116.4 L95.8 113.6 L99.3 114.3 L98.6 119.9 L93.7 129.6 L88.2 135.2 L88.2 139.4 L81.9 140.1 L79.1 143.5 L78.5 146.3 L79.1 158.9 L82.6 168.6 L88.2 166.5 L89.6 172.8 L91.6 176.3 L95.8 179.1 L101.4 179.1 L102.1 184 L105.5 188.1 L109 188.8 L111.1 192.3 L115.2 193.7 L118.7 184.7 L130.5 173.5 L134 166.5 L129.1 165.8 L128.4 166.5 L129.1 170 L125 175.6 L122.9 174.9 L120.1 170 L115.2 167.9 L104.8 170.7 L104.1 166.5 L101.4 161.7 L97.2 158.9 L95.1 158.9 L92.3 154.7 L90.3 146.3 L90.3 138 L91.6 135.9 L94.4 137.3 L99.3 156.8 L100.7 157.5 L104.1 154.7 L100 149.8 L95.8 131 L97.9 126.1 L103.4 117.8 L107.6 113.6 L109.7 113.6 L110.4 116.4 L102.1 132.4 L102.8 135.2 L106.2 135.9 L110.4 132.4 L110.4 131.7 L108.3 133.1 L106.2 133.1 L105.5 130.3 L109 124 L110.4 124 L113.9 126.8 L112.5 124 L113.2 121.9 L118.7 122.6 L128.4 127.5 L135.4 135.9 L135.4 142.2 L132.6 142.8 L127.7 138 L123.6 138 L116.6 142.2 L117.3 157.5 L115.9 161 L117.3 161.7 L120.8 161 L119.4 154.7 L119.4 147 L120.8 143.5 L122.9 141.5 L125.7 140.8 L129.1 143.5 L130.5 146.3 L130.5 156.1 L128.4 161 L133.3 161.7 L134 161 L133.3 151.2 L136.8 145.6 L137.5 134.5 L132.6 122.6 L129.1 117.8 L120.8 110.1 L118 108.7 L116.6 109.4 L116.6 110.8 L120.8 113.6 L127.7 120.5 L127.1 122.6 L115.9 118.5 L113.9 118.5 L113.2 117.8 L113.9 112.2 L111.8 110.1 L108.3 110.1 L104.1 113.6 L102.8 113.6 L100.7 110.8 Z",
)

private val COLLECTOR_MARK_HOLE_DATA = listOf(
    "M117.3 170.7 L120.1 173.5 L120.1 177.7 L118 181.9 L112.5 186 L106.9 186 L104.1 181.9 L105.5 176.3 L111.8 170.7 L113.9 170 Z",
    "M83.3 142.8 L84.7 142.8 L86.8 144.9 L90.9 158.2 L90.9 161 L88.2 165.8 L86.8 165.8 L84.7 163.8 L81.9 156.8 L81.2 146.3 Z",
)

/** Parsed once; the SVG data only uses M/L/Z commands. */
object CollectionCardArtworkPaths {
    val pattern: List<Path> by lazy { PATTERN_PATH_DATA.map(::parse) }
    val collectorMark: List<Path> by lazy { COLLECTOR_MARK_PATH_DATA.map(::parse) }
    val collectorMarkHoles: List<Path> by lazy { COLLECTOR_MARK_HOLE_DATA.map(::parse) }

    private fun parse(data: String): Path = PathParser().parsePathString(data).toPath()
}

private val COLLECTOR_MARK_CENTER = Offset(108f, 151f)
private val COLLECTOR_MARK_BASE = listOf("#625f9e", "#7859b6", "#ba54b8", "#ea64ad").map(::cssColor)
private val COLLECTOR_MARK_BASE_POSITIONS = floatArrayOf(0f, 0.34f, 0.7f, 1f)
private val COLLECTOR_MARK_BASE_START = Offset(82f, 194f)
private val COLLECTOR_MARK_BASE_END = Offset(138f, 108f)
private val COLLECTOR_MARK_RAINBOW = listOf(
    "#625ba7", "#8055c2", "#df4cbd", "#ff668e", "#ffad58", "#f4e85d", "#62dda0", "#51c9e2", "#625ba7",
).map(::cssColor)
private val COLLECTOR_MARK_RAINBOW_POSITIONS =
    floatArrayOf(0f, 0.12f, 0.25f, 0.36f, 0.47f, 0.58f, 0.7f, 0.84f, 1f)
private val COLLECTOR_MARK_GLINT = listOf(
    "rgba(255,255,255,0)",
    "rgba(255,218,246,0.05)",
    "rgba(255,246,184,0.78)",
    "rgba(255,255,255,0.96)",
    "rgba(171,246,255,0.5)",
    "rgba(255,255,255,0)",
).map(::cssColor)
private val COLLECTOR_MARK_GLINT_POSITIONS = floatArrayOf(0f, 0.37f, 0.46f, 0.5f, 0.56f, 1f)
private const val COLLECTOR_MARK_SWEEP_DELAY = 400L
private const val COLLECTOR_MARK_SWEEP_DURATION = 720
private const val COLLECTOR_MARK_STATIC_PROGRESS = 0.58f
private const val COLLECTOR_MARK_START_ANGLE = -70f
private const val COLLECTOR_MARK_ANGLE_TRAVEL = 190f
private val COLLECTOR_MARK_SWEEP_EASING = CubicBezierEasing(0.22f, 0.72f, 0.22f, 1f)

/** Skia `SweepGradient` with explicit start angle: an Android sweep rotated by a local matrix. */
private class RotatedSweepBrush(
    private val center: Offset,
    colors: List<Color>,
    private val positions: FloatArray,
    private val startAngle: Float,
) : ShaderBrush() {
    private val argb = IntArray(colors.size) { colors[it].toArgb() }

    override fun createShader(size: Size): Shader =
        SweepGradient(center.x, center.y, argb, positions).apply {
            setLocalMatrix(Matrix().apply { setRotate(startAngle, center.x, center.y) })
        }
}

private fun linearStops(colors: List<Color>, positions: FloatArray): Array<Pair<Float, Color>> =
    Array(colors.size) { positions[it] to colors[it] }

/** Holographic seal state driven by the sweep progress (0 → 1). */
class HolographicCollectorMarkState(val progress: Float, val reduceMotion: Boolean) {
    val sweepStart: Float get() = COLLECTOR_MARK_START_ANGLE + progress * COLLECTOR_MARK_ANGLE_TRAVEL
    val rainbowOpacity: Float get() = ((progress - 0.16f) / 0.66f).coerceIn(0f, 1f) * 0.88f
    val glintStart: Offset get() = Offset(20f + progress * 120f, 90f)
    val glintEnd: Offset get() = Offset(80f + progress * 120f, 210f)
    val glintOpacity: Float
        get() = if (reduceMotion) 0.2f else (sin(progress * PI).toFloat() * 0.72f)
}

@Composable
fun rememberHolographicCollectorMarkState(): HolographicCollectorMarkState {
    val reduceMotion = rememberReduceMotion()
    val progress = remember { Animatable(if (reduceMotion) COLLECTOR_MARK_STATIC_PROGRESS else 0f) }

    LaunchedEffect(reduceMotion) {
        progress.snapTo(if (reduceMotion) COLLECTOR_MARK_STATIC_PROGRESS else 0f)
        if (!reduceMotion) {
            delay(COLLECTOR_MARK_SWEEP_DELAY)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(COLLECTOR_MARK_SWEEP_DURATION, easing = COLLECTOR_MARK_SWEEP_EASING),
            )
        }
    }

    return HolographicCollectorMarkState(progress.value, reduceMotion)
}

private val ART_BOUNDS = Rect(0f, 0f, COLLECTION_CARD_ART_WIDTH, COLLECTION_CARD_ART_HEIGHT)

private inline fun DrawScope.layer(alpha: Float, blendMode: BlendMode = BlendMode.SrcOver, block: DrawScope.() -> Unit) {
    val paint = Paint().apply {
        this.alpha = alpha
        this.blendMode = blendMode
    }
    drawContext.canvas.saveLayer(ART_BOUNDS, paint)
    block()
    drawContext.canvas.restore()
}

private fun DrawScope.drawHolographicCollectorMark(state: HolographicCollectorMarkState) {
    val paths = CollectionCardArtworkPaths.collectorMark
    val baseBrush = Brush.linearGradient(
        colorStops = linearStops(COLLECTOR_MARK_BASE, COLLECTOR_MARK_BASE_POSITIONS),
        start = COLLECTOR_MARK_BASE_START,
        end = COLLECTOR_MARK_BASE_END,
    )
    paths.forEach { drawPath(it, baseBrush) }

    layer(alpha = state.rainbowOpacity) {
        val rainbow = RotatedSweepBrush(
            center = COLLECTOR_MARK_CENTER,
            colors = COLLECTOR_MARK_RAINBOW,
            positions = COLLECTOR_MARK_RAINBOW_POSITIONS,
            startAngle = state.sweepStart,
        )
        paths.forEach { drawPath(it, rainbow) }
    }

    layer(alpha = state.glintOpacity, blendMode = BlendMode.Screen) {
        val glint = Brush.linearGradient(
            colorStops = linearStops(COLLECTOR_MARK_GLINT, COLLECTOR_MARK_GLINT_POSITIONS),
            start = state.glintStart,
            end = state.glintEnd,
        )
        paths.forEach { drawPath(it, glint) }
    }
}

/**
 * Draws the card-back artwork in its 218 × 304.4 design space. Callers apply the scale transform.
 */
fun DrawScope.drawCollectionCardArtwork(
    backgroundColor: Color,
    patternColor: Color,
    sealColor: Color,
    showCollectorMark: Boolean = true,
    holographic: HolographicCollectorMarkState? = null,
) {
    CollectionCardArtworkPaths.pattern.forEach { drawPath(it, patternColor) }

    if (showCollectorMark) {
        if (holographic != null) {
            drawHolographicCollectorMark(holographic)
        } else {
            CollectionCardArtworkPaths.collectorMark.forEach { drawPath(it, sealColor) }
        }
        CollectionCardArtworkPaths.collectorMarkHoles.forEach { drawPath(it, backgroundColor) }
    }
}

@Composable
fun CollectionCardBack(
    width: Dp,
    modifier: Modifier = Modifier,
    collectorMarkFinish: CollectorMarkFinish = CollectorMarkFinish.Solid,
    markerCount: Int = 3,
    markerVariant: MarkerVariant = MarkerVariant.Diamond,
) {
    val scale = width.value / COLLECTION_CARD_ART_WIDTH
    val height = width * CARD_HEIGHT_RATIO
    val radius = (10f * scale).dp
    val markerSize = (22f * scale).dp
    val holographic = if (collectorMarkFinish == CollectorMarkFinish.Holographic) {
        rememberHolographicCollectorMarkState()
    } else {
        null
    }

    Column(
        modifier = modifier.width(width),
        verticalArrangement = Arrangement.spacedBy((10f * scale).dp),
    ) {
        Box(
            modifier = Modifier
                .size(width, height)
                .clip(RoundedCornerShape(radius))
                .background(CollectionCardBackColors.background)
                .border(
                    width = max(1f, 1.4f * scale).dp,
                    color = CollectionCardBackColors.edge,
                    shape = RoundedCornerShape(radius),
                ),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pixelScale = size.width / COLLECTION_CARD_ART_WIDTH
                scale(pixelScale, pivot = Offset.Zero) {
                    drawCollectionCardArtwork(
                        backgroundColor = CollectionCardBackColors.background,
                        patternColor = CollectionCardBackColors.patternDark,
                        sealColor = CollectionCardBackColors.seal,
                        holographic = holographic,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            repeat(markerCount) {
                Box(
                    modifier = Modifier
                        .size(markerSize)
                        .border(
                            width = max(0.7f, scale).dp,
                            color = CollectionCardBackColors.markerBorder,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (markerVariant == MarkerVariant.Lock) {
                        Column(
                            modifier = Modifier.size((10f * scale).dp, (11f * scale).dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((7f * scale).dp, (6f * scale).dp)
                                    .border(
                                        width = max(0.7f, scale).dp,
                                        color = CollectionCardBackColors.markerInk,
                                        shape = RoundedCornerShape((4f * scale).dp),
                                    ),
                            )
                            Box(
                                modifier = Modifier
                                    .offset(y = (-2f * scale).dp)
                                    .size((9f * scale).dp, (6f * scale).dp)
                                    .background(
                                        CollectionCardBackColors.markerInk,
                                        RoundedCornerShape((1.5f * scale).dp),
                                    ),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size((4f * scale).dp)
                                .rotate(45f)
                                .background(
                                    CollectionCardBackColors.markerInk,
                                    RoundedCornerShape((2f * scale).dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFE9EDF3)
@Composable
private fun CollectionCardBackPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CollectionCardBack(width = 160.dp, markerCount = 3, markerVariant = MarkerVariant.Lock)
        CollectionCardBack(width = 160.dp, collectorMarkFinish = CollectorMarkFinish.Holographic)
    }
}
