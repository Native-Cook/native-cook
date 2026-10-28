package com.ngocdevv.nativecook.blocks.lists.emeraldtierleaderboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------------------------

/** CSS-style hex as React Native reads it: "#RRGGBB" or "#RRGGBBAA" (alpha LAST). */
internal fun hex(value: String): Color {
    val digits = value.removePrefix("#")
    val v = digits.toLong(16)
    return if (digits.length == 8) {
        Color(
            red = ((v shr 24) and 0xFF).toInt(), green = ((v shr 16) and 0xFF).toInt(),
            blue = ((v shr 8) and 0xFF).toInt(), alpha = (v and 0xFF).toInt(),
        )
    } else {
        Color(red = ((v shr 16) and 0xFF).toInt(), green = ((v shr 8) and 0xFF).toInt(), blue = (v and 0xFF).toInt())
    }
}

internal val SUPPORTS_AGSL: Boolean get() = Build.VERSION.SDK_INT >= 33

/** A React Native point is one dp; text does not follow the system font scale here (maxFontSizeMultiplier 1 / fixed canvas). */
@Composable
internal fun pt(value: Float): TextUnit = with(LocalDensity.current) { value.dp.toSp() }

@Composable
internal fun EmeraldText(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight? = null,
    lineHeight: Float? = null,
    letterSpacing: Float? = null,
    align: TextAlign = TextAlign.Start,
    tabular: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = pt(size),
        fontWeight = weight,
        fontFamily = FontFamily.Default,
        textAlign = align,
        letterSpacing = if (letterSpacing != null) pt(letterSpacing) else TextUnit.Unspecified,
        lineHeight = if (lineHeight != null) pt(lineHeight) else TextUnit.Unspecified,
        maxLines = maxLines,
        softWrap = maxLines != 1,
        overflow = TextOverflow.Clip,
        style = TextStyle(
            fontFeatureSettings = if (tabular) "tnum" else null,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
    )
}

/** Decodes with halving until the longest side is no more than [maxSide]; one-off, off the main thread. */
private fun decodeAsset(context: Context, name: String, maxSide: Int): Bitmap {
    val source = context.assets.open("emeraldtierleaderboard/$name").use {
        checkNotNull(BitmapFactory.decodeStream(it)) { "Unable to decode Emerald Tier Leaderboard asset: $name" }
    }
    var bitmap = source
    while (max(bitmap.width, bitmap.height) / 2 >= maxSide) {
        val next = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
        if (bitmap !== source) bitmap.recycle()
        bitmap = next
    }
    return bitmap
}

/** The four bundled images (atlas, Theo's portrait, crest, crown), decoded once per screen instance. */
internal class EmeraldAssets(
    val atlas: ImageBitmap, val theo: ImageBitmap, val crest: Bitmap, val crown: Bitmap,
)

internal val LocalEmeraldAssets = staticCompositionLocalOf<EmeraldAssets?> { null }

@Composable
internal fun rememberEmeraldAssets(): EmeraldAssets? {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val state = produceState<EmeraldAssets?>(null, context, density) {
        value = withContext(Dispatchers.Default) {
            // Pre-reduced so the shader and the minified portraits do not alias; sized for the largest draw.
            val crestSide = ceil(112f * density * 2).toInt()
            val crownSide = ceil(34f * density * 2).toInt()
            EmeraldAssets(
                atlas = decodeAsset(context, "emeraldtierleaderboard_leaderboard-atlas.png", 4096).asImageBitmap(),
                theo = decodeAsset(context, "emeraldtierleaderboard_theo-portrait.png", 512).asImageBitmap(),
                crest = decodeAsset(context, "emeraldtierleaderboard_emerald-crest.png", crestSide),
                crown = decodeAsset(context, "emeraldtierleaderboard_gold-crown.png", crownSide),
            )
        }
    }
    return state.value
}

/** Both preferences call for an opaque, clearly separated material. (Android has no Reduce Transparency.) */
@Composable
internal fun rememberEmeraldSolidMaterial(): Boolean {
    val context = LocalContext.current
    var contrast by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        while (true) {
            contrast = Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
            kotlinx.coroutines.delay(1000)
        }
    }
    return contrast
}

// ---------------------------------------------------------------------------------------------
// Glass (EmeraldGlass.tsx, EmeraldLightSurface.tsx)
// ---------------------------------------------------------------------------------------------

internal enum class GlassKind { green, smoke, selected, row }

private class GlassMaterial(val solid: Color, val light: Float)

private val MATERIAL = mapOf(
    GlassKind.green to GlassMaterial(hex("#0A1B18"), .32f),
    GlassKind.smoke to GlassMaterial(hex("#081513"), .28f),
    GlassKind.selected to GlassMaterial(hex("#153B2D"), 0f),
    GlassKind.row to GlassMaterial(hex("#0A1715"), .13f),
)

internal fun DrawScope.drawShader(shader: RuntimeShader, width: Float, height: Float) {
    val brush = object : ShaderBrush() {
        override fun createShader(size: Size): android.graphics.Shader = shader
    }
    // Shader coordinates are dp, like the Skia canvases.
    scale(density, Offset.Zero) { drawRect(brush, size = Size(width, height)) }
}

/** The inky glass with its lower light reservoir. Static: response and phase stay 0. */
@Composable
internal fun EmeraldLightSurface(modifier: Modifier, radius: Float, light: Float) {
    if (SUPPORTS_AGSL) {
        val shader = remember { RuntimeShader(EMERALD_LIGHT_GLASS_AGSL) }
        Canvas(modifier) {
            val w = size.width / density
            val h = size.height / density
            shader.setFloatUniform("size", w, h)
            shader.setFloatUniform("radius", radius)
            shader.setFloatUniform("light", light)
            shader.setFloatUniform("response", 0f)
            shader.setFloatUniform("phase", 0f)
            drawShader(shader, w, h)
        }
    } else {
        // Below API 33: the same two pools of light under the lower lip, as plain gradients.
        Canvas(modifier) {
            val w = size.width
            val h = size.height
            val right = Color(.42f, .83f, .63f, min(1f, light * 0.9f))
            val left = Color(.075f, .40f, .42f, min(1f, light * 0.9f))
            drawRect(
                Brush.radialGradient(listOf(right, Color.Transparent), Offset(w * .90f, h * 1.06f), max(w * .43f, 1f)),
            )
            drawRect(
                Brush.radialGradient(listOf(left, Color.Transparent), Offset(w * .16f, h * 1.10f), max(w * .55f, 1f)),
            )
        }
    }
}

/** Layout is measured once; the bounded surface never draws or filters its labels. */
@Composable
internal fun EmeraldGlass(kind: GlassKind, radius: Float, solid: Boolean, modifier: Modifier = Modifier) {
    val material = MATERIAL.getValue(kind)
    val shape = RoundedCornerShape(radius.dp)
    // A selected control is an inset on one glass plane, not another luminous glass layer.
    if (kind == GlassKind.selected) {
        val base = modifier
            .clip(shape)
            .background(if (solid) material.solid else hex("#67DFA51A"))
            .border(Dp.Hairline, if (solid) hex("#D6FFEA80") else hex("#BAF7DB3D"), shape)
        Box(base) {
            if (!solid) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(hex("#D9FFEF0A"), hex("#51D99F0D"), hex("#073C2C26"))),
                    ),
                )
                // The top hairline is brighter than the rest (borderTopColor).
                val hairline = with(LocalDensity.current) { 1.toDp() }
                Box(
                    Modifier.align(Alignment.TopCenter).padding(horizontal = (radius * .6f).dp)
                        .fillMaxWidth().height(hairline).background(hex("#D6FFEA66")),
                )
            }
        }
        return
    }
    Box(
        modifier
            .clip(shape)
            .background(material.solid)
            .then(if (solid) Modifier.border(Dp.Hairline, hex("#D6FFEA70"), shape) else Modifier),
    ) {
        if (!solid) EmeraldLightSurface(Modifier.fillMaxSize(), radius, material.light)
    }
}

// ---------------------------------------------------------------------------------------------
// Art material (EmeraldMaterials.tsx): a bitmap with a diagonal sheen, fit "contain"
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ArtMaterial(bitmap: Bitmap?, width: Float, height: Float, power: Float = 0f, phase: Float = 0f, modifier: Modifier = Modifier) {
    if (bitmap == null) {
        Box(modifier.size(width.dp, height.dp))
        return
    }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    if (SUPPORTS_AGSL) {
        val shader = remember { RuntimeShader(EMERALD_ART_AGSL) }
        val bitmapShader = remember(bitmap, width, height) {
            val s = min(width / bitmap.width, height / bitmap.height)
            val matrix = Matrix().apply {
                setScale(s, s)
                postTranslate((width - bitmap.width * s) / 2, (height - bitmap.height * s) / 2)
            }
            BitmapShader(bitmap, Shader.TileMode.DECAL, Shader.TileMode.DECAL).apply {
                setLocalMatrix(matrix)
                setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            }
        }
        Canvas(modifier.size(width.dp, height.dp)) {
            shader.setInputShader("artwork", bitmapShader)
            shader.setFloatUniform("size", width, height)
            shader.setFloatUniform("phase", phase)
            shader.setFloatUniform("power", power)
            drawShader(shader, width, height)
        }
    } else {
        Canvas(modifier.size(width.dp, height.dp)) {
            val s = min(size.width / image.width, size.height / image.height)
            val dw = image.width * s
            val dh = image.height * s
            drawImage(
                image,
                dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// RectButton
// ---------------------------------------------------------------------------------------------

private val BUTTON_UNDERLAY = hex("#BDFBE0")
private const val BUTTON_ACTIVE_OPACITY = .12f

/** RectButton with BUTTON_FEEDBACK: an underlay of #BDFBE0 at 12% while pressed. */
@Composable
internal fun EmeraldButton(
    modifier: Modifier,
    radius: Float,
    label: String?,
    role: Role = Role.Button,
    selected: Boolean? = null,
    onPress: () -> Unit,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier
            .semantics {
                this.role = role
                if (label != null) contentDescription = label
                if (selected != null) this.selected = selected
            }
            .pointerInput(onPress) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onPress() },
                )
            },
    ) {
        Box(
            Modifier.matchParentSize().clip(shape)
                .background(if (pressed) BUTTON_UNDERLAY.copy(alpha = BUTTON_ACTIVE_OPACITY) else Color.Transparent),
        )
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Pan (react-native-gesture-handler Gesture.Pan with activeOffset / failOffset / maxPointers 1)
// ---------------------------------------------------------------------------------------------

/**
 * Translations and velocities are reported in screen dp: local px times [toScreen]
 * (the block lives inside a scaled layer, and RN reports the gesture in screen points).
 * The pan watches on the Initial pass and only consumes once it has activated, so buttons
 * underneath still get their taps.
 */
internal fun Modifier.emeraldPan(
    key: Any?,
    enabled: () -> Boolean,
    vertical: Boolean,
    activeOffset: Float,
    failOffset: Float,
    toScreen: Float,
    onStart: (Float) -> Unit,
    onUpdate: (Float) -> Unit,
    onEnd: (Float) -> Unit,
    onFinalize: () -> Unit,
): Modifier = pointerInput(key, vertical, activeOffset, failOffset, toScreen) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabled()) return@awaitEachGesture
        val id: PointerId = down.id
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var active = false
        var finished = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!active && event.changes.count { it.pressed } > 1) return@awaitEachGesture // maxPointers(1)
                val change = event.changes.firstOrNull { it.id == id } ?: return@awaitEachGesture
                tracker.addPosition(change.uptimeMillis, change.position)
                val dx = (change.position.x - down.position.x) * toScreen
                val dy = (change.position.y - down.position.y) * toScreen
                val primary = if (vertical) dy else dx
                val cross = if (vertical) dx else dy
                if (change.changedToUp()) {
                    if (active) {
                        change.consume()
                        val v = tracker.calculateVelocity()
                        finished = true
                        onEnd((if (vertical) v.y else v.x) * toScreen)
                    }
                    return@awaitEachGesture
                }
                if (!active) {
                    if (abs(cross) > failOffset) return@awaitEachGesture
                    if (abs(primary) > activeOffset) {
                        active = true
                        onStart(primary)
                    } else {
                        continue
                    }
                }
                change.consume()
                onUpdate(primary)
            }
        } finally {
            // Cancelled mid-drag (lifecycle, a parent taking over): finalize without an end.
            if (active && !finished) onFinalize()
        }
    }
}
