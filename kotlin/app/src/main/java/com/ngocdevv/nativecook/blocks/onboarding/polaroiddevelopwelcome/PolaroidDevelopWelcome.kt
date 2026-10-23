package com.ngocdevv.nativecook.blocks.onboarding.polaroiddevelopwelcome

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Shot(val photo: String, val caption: String)

// The photos are drawn about 130 dp square; 520 px covers that at 3x with the lift.
private fun photo(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=520&h=520&q=80"

// The film in the camera, shot in this order. The captions are written on the border by hand.
private val Shots = listOf(
    Shot(photo("photo-1526478512290-5397e7d2ca6a"), "Route 9, first light"),
    Shot(photo("photo-1597649260564-997729f07c5d"), "Red rock pass"),
    Shot(photo("photo-1578158335529-27b8d78cfea5"), "Van, mile 212"),
    Shot(photo("photo-1557159557-7a93eaadf72a"), "Straight on, 90 mi"),
    Shot(photo("photo-1534540378968-85a7b8fde19f"), "Last of the sun"),
    Shot(photo("photo-1494822493217-c9840aba840c"), "Snowline in July"),
    Shot(photo("photo-1484687742385-1249620c2687"), "Parked for pie"),
    Shot(photo("photo-1573097637683-58e6462d2902"), "Pines all the way"),
)

// Palette: a walnut table under a warm lamp, a cream camera, cream paper and blue ink.
private const val TABLE_LIGHT = 0x57412F
private const val TABLE_DARK = 0x140E0A
private const val PAPER = 0xF5F1E8
private const val PAPER_SHADE = 0xE4DCCD
private const val INK = 0x26365A
private const val CREAM = 0xF4EBDD
private const val SHUTTER = 0xC9553B
private val Cream = Color(0xFFF4EBDD)
private val TableDark = Color(0xFF140E0A)

private fun argb(rgb: Int, alpha: Float = 1f) = ((clamp01(alpha) * 255f).roundToInt() shl 24) or (rgb and 0xFFFFFF)

private fun sans(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        if (weight >= 600) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

/** A colour matrix with offsets in 0…1 for Android, whose offsets run in 0…255. */
private fun colorFilter(m: FloatArray): ColorMatrixColorFilter {
    val values = m.copyOf()
    for (row in 0 until 4) values[row * 5 + 4] *= 255f
    return ColorMatrixColorFilter(ColorMatrix(values))
}

/** The photos as they arrive, and the table's grain once it is made; read by the drawing. */
private class Art {
    var version by mutableIntStateOf(0)
    val bitmaps = arrayOfNulls<Bitmap>(Shots.size)
    var grain: Bitmap? = null
}

/** Paints, shaders and blurs for one screen density; shapes are drawn in dp, text in px. */
private class Paints(val density: Float) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val soft = Paint(Paint.ANTI_ALIAS_FLAG)
    val matrix = Matrix()
    val rect = RectF()
    private val blurs = HashMap<Int, BlurMaskFilter>()

    /** A blur of `sigma` dp, kept for reuse; null before Android 9, where mask filters do not draw. */
    fun blur(sigma: Float): BlurMaskFilter? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val key = (sigma * 10f).roundToInt().coerceAtLeast(1)
        return blurs.getOrPut(key) { BlurMaskFilter(sigmaToRadius(key / 10f), BlurMaskFilter.Blur.NORMAL) }
    }

    val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("cursive", Typeface.BOLD)
        textSize = CAPTION_SIZE * density
        color = argb(INK)
    }
    val captionWidths = Shots.map { shot ->
        val widths = FloatArray(shot.caption.length)
        caption.getTextWidths(shot.caption, widths)
        widths.sum() / density
    }
    val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = sans(700)
        textSize = 8f * density
        letterSpacing = 1.6f / 8f
        color = argb(0x8F7D62)
    }

    // Shaders in a print's own coordinates, about its middle.
    val paper = LinearGradient(
        -PRINT_WIDTH / 2f, -PRINT_HEIGHT / 2f, PRINT_WIDTH / 2f, PRINT_HEIGHT / 2f,
        argb(PAPER), argb(PAPER_SHADE), Shader.TileMode.CLAMP,
    )
    private val photoLeft = -PRINT_WIDTH / 2f + PHOTO_INSET
    private val photoTop = -PRINT_HEIGHT / 2f + PHOTO_INSET
    val sheen = LinearGradient(
        photoLeft, photoTop, photoLeft + PHOTO_SIZE, photoTop + PHOTO_SIZE,
        intArrayOf(argb(0xFFFFFF, 0.15f), argb(0xFFFFFF, 0f), argb(0xFFFFFF, 0.07f)),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
    )

    // The photo of each shot, recorded once, so a colour matrix and a blur can be applied to it as a
    // render effect on Android 12 and later.
    val nodes = HashMap<Int, RenderNode>()
    val recorded = HashMap<Int, Bitmap>()
}

/** The table's and the camera's shaders, for one layout. */
private class CameraArt(s: Stage) {
    val x = s.cameraX
    val y = s.cameraY
    val w = CAMERA_WIDTH
    val h = CAMERA_HEIGHT
    val lensX = x + w / 2f
    val lensY = y + h * 0.5f
    val buttonX = x + w - 38f
    val buttonY = y + h - 44f
    val seam = y + h * 0.66f
    val body = LinearGradient(0f, y, 0f, y + h, intArrayOf(argb(0xF6EEE1), argb(0xE7DCC9), argb(0xD9CCB6)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    val band = LinearGradient(0f, seam, 0f, y + h, argb(0xD8C9B0), argb(0xC6B498), Shader.TileMode.CLAMP)
    val edge = LinearGradient(
        0f, y, 0f, y + h,
        intArrayOf(argb(0xFFFFFF, 0.8f), argb(0xFFFFFF, 0f), argb(0x000000, 0.25f)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
    )
    val flashLens = LinearGradient(0f, y + 18f, 0f, y + 44f, argb(0xFDFCF8), argb(0xD8D2C6), Shader.TileMode.CLAMP)
    val finder = LinearGradient(x + 27f, y + 21f, x + 57f, y + 41f, argb(0x4A5A72), argb(0x0E1219), Shader.TileMode.CLAMP)
    val ring = LinearGradient(lensX, lensY - LENS, lensX, lensY + LENS, argb(0xFBF6EE), argb(0xCBBDA6), Shader.TileMode.CLAMP)
    val barrel = LinearGradient(lensX, lensY - LENS, lensX, lensY + LENS, argb(0x45403B), argb(0x121110), Shader.TileMode.CLAMP)
    val glass = RadialGradient(
        lensX - 7f, lensY - 9f, LENS - 12f,
        intArrayOf(argb(0x46608A), argb(0x16213A), argb(0x04060A)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
    )
    val button = RadialGradient(
        buttonX - 4f, buttonY - 5f, 14f,
        intArrayOf(argb(0xE57259), argb(SHUTTER), argb(0x9D3A25)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
    )
    val table = RadialGradient(s.width / 2f, s.pileY - 60f, s.height * 0.7f, argb(TABLE_LIGHT), argb(TABLE_DARK), Shader.TileMode.CLAMP)
    val falloff = LinearGradient(0f, s.copyTop - 120f, 0f, s.copyTop + 40f, argb(TABLE_DARK, 0f), argb(TABLE_DARK, 0.85f), Shader.TileMode.CLAMP)
    val bloom = RadialGradient(
        x + w - 53f, y + 31f, s.height * 0.6f,
        intArrayOf(argb(0xFFF8EA), argb(0xFFF1D8, 0.67f), argb(0xFFF1D8, 0f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
    )
}

/**
 * A welcome screen built around an instant camera. It opens with the camera taking a photo: the
 * flash fires and a print slides out of the slot and, still moving, settles onto the table below,
 * where it develops from a dark blank through a washed-out ghost to full colour and a caption writes
 * itself on its border. Tap the shutter for more; hold the top print and shake it to develop it
 * faster, or throw it aside to send it under the pile. A headline, a sub line, a primary and a
 * secondary action sit below.
 *
 * @param autoPlay Snaps two prints, shakes one, throws it under the pile, clears the table and starts over.
 */
@Composable
fun PolaroidDevelopWelcome(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onStart: () -> Unit = {},
    onSignIn: () -> Unit = {},
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val table = remember { Table(Shots.size) }
    val art = remember { Art() }
    val paints = remember(density.density) { Paints(density.density) }
    // Bumped once a frame, so the picture is drawn again.
    var tick by remember { mutableIntStateOf(0) }
    var grainFor by remember { mutableStateOf(Pair(0, 0)) }

    // Read during composition, so the block is laid out again once the insets arrive.
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    SideEffect {
        table.reduced = reduced
        table.feel = object : Feel {
            override fun snapped() {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }

            override fun landed() {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }

            override fun thrown() {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    LaunchedEffect(table, autoPlay) {
        table.looping = autoPlay
        table.clock = 0f
        table.hold = 0f
    }

    LaunchedEffect(table) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) table.frame(dt)
                tick += 1
            }
        }
    }

    LaunchedEffect(art, context) {
        val loader = SingletonImageLoader.get(context)
        Shots.forEachIndexed { i, shot ->
            launch {
                val request = ImageRequest.Builder(context).data(shot.photo).allowHardware(false).build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                art.bitmaps[i] = result.image.toBitmap()
                art.version += 1
            }
        }
    }

    // Grain, so the table reads as a surface rather than a gradient: fine across (one sample a pixel
    // at 0.9 cycles per dp) and long down (one sample a dp at 0.07).
    LaunchedEffect(art, grainFor) {
        val (columns, rows) = grainFor
        if (columns <= 0 || rows <= 0) return@LaunchedEffect
        val pixels = withContext(Dispatchers.Default) { Grain.pixels(columns, rows, 0.9 / density.density, 0.07) }
        art.grain = Bitmap.createBitmap(pixels, columns, rows, Bitmap.Config.ARGB_8888)
        art.version += 1
    }

    BoxWithConstraints(modifier.fillMaxSize().background(TableDark)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val stage = remember(width, height, safeTop, safeBottom) { Stage(width, height, safeTop, safeBottom) }
        val camera = remember(stage) { CameraArt(stage) }
        SideEffect {
            table.stage = stage
            grainFor = Pair((width * density.density).roundToInt(), height.roundToInt())
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Instant camera. Tap the shutter to take a photo."
                    onClick {
                        table.hold = USER_HOLD
                        table.snap(byUser = true)
                        true
                    }
                }
                .pointerInput(table) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density.density
                        val start = down.position
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        table.touchDown(start.x / d, start.y / d)
                        // One recogniser stands in for the race of a pan (after 4 dp) and a tap (a
                        // touch lifted within 500 ms that never turned into a pan).
                        var panning = false
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            val tx = (change.position.x - start.x) / d
                            val ty = (change.position.y - start.y) / d
                            if (!change.pressed) {
                                released = true
                                if (panning) {
                                    change.consume()
                                    val v = tracker.calculateVelocity()
                                    table.panEnd(v.x / d, v.y / d)
                                } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                    table.tapped(change.position.x / d, change.position.y / d)
                                }
                                table.finalize()
                                continue
                            }
                            if (!panning && sqrt(tx * tx + ty * ty) >= PAN_SLOP) {
                                panning = true
                                table.panStart(change.position.x / d, change.position.y / d, tx, ty)
                            }
                            if (panning) {
                                change.consume()
                                val v = tracker.calculateVelocity()
                                table.panUpdate(tx, ty, v.x / d, v.y / d)
                            }
                        }
                        // A finger lost to the system still puts the print back.
                        if (!released) {
                            if (panning) table.panEnd(0f, 0f)
                            table.finalize()
                        }
                    }
                },
        ) {
            tick
            art.version
            drawIntoCanvas { drawTable(it.nativeCanvas, table, stage, camera, art, paints) }
        }

        Copy(table, stage, reduced, tick)
        Actions(table, stage, reduced, tick, onStart, onSignIn)
    }
}

/** An element arriving `delay` s after the screen opened: its opacity and how far it still has to rise. */
private fun arriving(table: Table, delay: Float, reduced: Boolean): Pair<Float, Float> {
    val t = table.entered(delay)
    return Pair(t, if (reduced) 0f else (1f - t) * ENTER_RISE)
}

@Composable
private fun Copy(table: Table, s: Stage, reduced: Boolean, tick: Int) {
    tick
    val (headerAlpha, headerRise) = arriving(table, 0f, reduced)
    val (titleAlpha, titleRise) = arriving(table, ENTER_STAGGER * 2f, reduced)
    val (subAlpha, subRise) = arriving(table, ENTER_STAGGER * 3f, reduced)
    Row(
        Modifier
            .offset(x = 28.dp, y = s.headerTop.dp)
            .graphicsLayer {
                alpha = headerAlpha
                translationY = headerRise * density
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mark()
        Spacer(Modifier.width(8.dp))
        Text("Roadbook", color = Cream, style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.2).sp))
    }
    Text(
        "Every mile,\none frame.",
        color = Cream,
        style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp),
        modifier = Modifier
            .offset(x = 28.dp, y = s.copyTop.dp)
            .width((s.width - 56f).dp)
            .graphicsLayer {
                alpha = titleAlpha
                translationY = titleRise * density
            },
    )
    Text(
        "A travel journal that develops itself, one instant photo for every stop.",
        color = Cream.copy(alpha = 0.62f),
        style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
        modifier = Modifier
            .offset(x = 28.dp, y = (s.copyTop + 76f + 10f).dp)
            .width((s.width - 56f).dp)
            .graphicsLayer {
                alpha = subAlpha
                translationY = subRise * density
            },
    )
}

@Composable
private fun Actions(table: Table, s: Stage, reduced: Boolean, tick: Int, onStart: () -> Unit, onSignIn: () -> Unit) {
    tick
    val view = LocalView.current
    val (alpha, rise) = arriving(table, ENTER_STAGGER * 4f, reduced)
    val primary = remember { MutableInteractionSource() }
    val primaryPressed by primary.collectIsPressedAsState()
    val press by animateFloatAsState(if (primaryPressed) 1f else 0f, tween(if (primaryPressed) 110 else 220), label = "press")
    val secondary = remember { MutableInteractionSource() }
    val secondaryPressed by secondary.collectIsPressedAsState()
    Column(
        Modifier
            .offset(x = 28.dp, y = s.primaryTop.dp)
            .width((s.width - 56f).dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = rise * density
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .graphicsLayer {
                    scaleX = 1f - press * 0.03f
                    scaleY = 1f - press * 0.03f
                }
                .background(Cream, RoundedCornerShape(28.dp))
                .clickable(interactionSource = primary, indication = null) {
                    table.hold = USER_HOLD
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onStart()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("Start your roadbook", color = TableDark, style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp))
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .graphicsLayer { this.alpha = if (secondaryPressed) 0.55f else 1f }
                .clickable(interactionSource = secondary, indication = null) {
                    table.hold = USER_HOLD
                    onSignIn()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "I already have an account",
                color = Cream.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            )
        }
    }
}

/** The app's mark: a print with a road running into it. */
@Composable
private fun Mark() {
    Canvas(Modifier.size(22.dp)) {
        val k = size.width / 22f
        val frame = androidx.compose.ui.graphics.Path().apply {
            moveTo(4f * k, 4f * k)
            lineTo(18f * k, 4f * k)
            quadraticTo(19.5f * k, 4f * k, 19.5f * k, 5.5f * k)
            lineTo(19.5f * k, 18f * k)
            lineTo(2.5f * k, 18f * k)
            lineTo(2.5f * k, 5.5f * k)
            quadraticTo(2.5f * k, 4f * k, 4f * k, 4f * k)
            close()
        }
        drawPath(frame, Cream, style = Stroke(width = 1.9f * k, join = StrokeJoin.Round))
        val road = androidx.compose.ui.graphics.Path().apply {
            moveTo(8.6f * k, 18f * k)
            lineTo(10.4f * k, 9.6f * k)
            moveTo(13.4f * k, 18f * k)
            lineTo(11.6f * k, 9.6f * k)
        }
        drawPath(road, Color(0xFFE0A46A), style = Stroke(width = 1.9f * k, cap = StrokeCap.Round))
    }
}

private fun drawTable(canvas: android.graphics.Canvas, table: Table, s: Stage, camera: CameraArt, art: Art, paints: Paints) {
    val d = paints.density
    canvas.save()
    canvas.scale(d, d)
    val fill = paints.fill

    fill.shader = camera.table
    fill.color = argb(0)
    fill.alpha = 255
    canvas.drawRect(0f, 0f, s.width, s.height, fill)
    fill.shader = null
    art.grain?.let { grain ->
        paints.art.alpha = (0.07f * 255f).roundToInt()
        paints.art.colorFilter = null
        paints.rect.set(0f, 0f, s.width, s.height)
        canvas.drawBitmap(grain, null, paints.rect, paints.art)
        paints.art.alpha = 255
    }
    // The lamp's pool of light falls off towards the copy.
    fill.shader = camera.falloff
    canvas.drawRect(0f, s.copyTop - 120f, s.width, s.height, fill)
    fill.shader = null

    // The hero scales about the camera's top centre, rises into place as the screen opens and gives
    // a little when the camera is pressed.
    val enter = table.entered(0f)
    val heroAlpha = (enter * 255f).roundToInt()
    if (heroAlpha < 255) canvas.saveLayerAlpha(0f, 0f, s.width, s.height, heroAlpha) else canvas.save()
    canvas.translate(s.width / 2f, s.cameraY + if (table.reduced) 0f else (1f - enter) * ENTER_RISE)
    val scale = s.scale * (1f - table.cameraPress.value * 0.015f)
    canvas.scale(scale, scale)
    canvas.translate(-s.width / 2f, -s.cameraY)
    // Prints are hidden above the camera's middle, so one still inside the camera never shows.
    canvas.save()
    canvas.clipRect(-s.width, s.cameraY + 30f, s.width * 2f, s.height * 2f)
    for (print in table.prints) drawPrint(canvas, table, print, art, paints)
    canvas.restore()
    drawCamera(canvas, table, camera, paints)
    canvas.restore()

    val bloom = table.flash.value * if (table.reduced) 0.35f else BLOOM_PEAK
    if (bloom > 0f) {
        fill.shader = camera.bloom
        fill.alpha = (clamp01(bloom) * 255f).roundToInt()
        canvas.drawRect(0f, 0f, s.width, s.height, fill)
        fill.shader = null
        fill.alpha = 255
    }
    canvas.restore()
}

/** One print: its shadow, the paper, the developing photo with its sheen, the caption and its shade. */
private fun drawPrint(canvas: android.graphics.Canvas, table: Table, print: PrintState, art: Art, paints: Paints) {
    val d = paints.density
    val pose = table.pose(print)
    if (pose.alpha <= 0.001f || !pose.x.isFinite() || !pose.y.isFinite()) return
    val fill = paints.fill
    val layered = pose.alpha < 0.999f
    if (layered) canvas.saveLayerAlpha(-10000f, -10000f, 10000f, 10000f, (pose.alpha * 255f).roundToInt()) else canvas.save()
    canvas.translate(pose.x, pose.y)
    canvas.rotate(pose.tilt)
    canvas.scale(1f + pose.lift, 1f + pose.lift)

    val left = -PRINT_WIDTH / 2f
    val top = -PRINT_HEIGHT / 2f
    val lifted = max(0f, pose.lift)
    val shadowAlpha = clamp01(0.55f - lifted * 3f)
    val blur = paints.blur(4f + lifted * 140f)
    if (shadowAlpha > 0f && blur != null) {
        paints.soft.maskFilter = blur
        paints.soft.color = argb(0, shadowAlpha)
        val dy = 3f + lifted * 160f
        canvas.drawRoundRect(left + 3f, top + 3f + dy, -left - 3f, -top - 3f + dy, 3f, 3f, paints.soft)
    }
    fill.shader = paints.paper
    fill.color = argb(0)
    fill.alpha = 255
    canvas.drawRoundRect(left, top, -left, -top, 2.5f, 2.5f, fill)
    fill.shader = null

    val photoLeft = left + PHOTO_INSET
    val photoTop = top + PHOTO_INSET
    canvas.save()
    canvas.clipRect(photoLeft, photoTop, photoLeft + PHOTO_SIZE, photoTop + PHOTO_SIZE)
    fill.color = argb(0x2A2420)
    canvas.drawRect(photoLeft, photoTop, photoLeft + PHOTO_SIZE, photoTop + PHOTO_SIZE, fill)
    val bitmap = art.bitmaps.getOrNull(print.shot)
    if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
        val matrix = developMatrix(print.develop)
        val sigma = DEVELOP_BLUR * (1f - print.develop).pow(2)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canvas.isHardwareAccelerated) {
            val size = (PHOTO_SIZE * d).roundToInt()
            val node = paints.nodes.getOrPut(print.shot) { RenderNode("print${print.shot}") }
            // Recorded again when its photo changes, and when the system dropped a node it was not drawing.
            if (paints.recorded[print.shot] !== bitmap || node.width != size || !node.hasDisplayList()) {
                node.setPosition(0, 0, size, size)
                val recording = node.beginRecording(size, size)
                coverMatrix(paints.matrix, bitmap, 0f, 0f, size.toFloat())
                paints.art.alpha = 255
                paints.art.colorFilter = null
                recording.drawBitmap(bitmap, paints.matrix, paints.art)
                node.endRecording()
                paints.recorded[print.shot] = bitmap
            }
            // The colour matrix and a fading blur that keeps the photo's edges.
            val effect = if (print.develop >= 1f) {
                null
            } else if (sigma > 0.01f) {
                val radius = sigmaToRadius(sigma * d)
                RenderEffect.createColorFilterEffect(colorFilter(matrix), RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            } else {
                RenderEffect.createColorFilterEffect(colorFilter(matrix))
            }
            node.setRenderEffect(effect)
            canvas.save()
            canvas.translate(photoLeft, photoTop)
            canvas.scale(1f / d, 1f / d)
            canvas.drawRenderNode(node)
            canvas.restore()
        } else {
            // Before Android 12 the colour matrix still develops the photo, without the blur.
            coverMatrix(paints.matrix, bitmap, photoLeft, photoTop, PHOTO_SIZE)
            paints.art.alpha = 255
            paints.art.colorFilter = if (print.develop >= 1f) null else colorFilter(matrix)
            canvas.drawBitmap(bitmap, paints.matrix, paints.art)
            paints.art.colorFilter = null
        }
    }
    // A faint sheen across the glossy face of the film, and its edge.
    fill.shader = paints.sheen
    fill.color = argb(0)
    fill.alpha = 255
    canvas.drawRect(photoLeft, photoTop, photoLeft + PHOTO_SIZE, photoTop + PHOTO_SIZE, fill)
    fill.shader = null
    paints.stroke.strokeWidth = 1f
    paints.stroke.color = argb(0, 0.2f)
    canvas.drawRect(photoLeft, photoTop, photoLeft + PHOTO_SIZE, photoTop + PHOTO_SIZE, paints.stroke)
    canvas.restore()

    // The caption is written left to right once the picture has come through.
    val reveal = smooth((print.develop - 0.62f) / 0.3f)
    if (reveal > 0f && print.shot in Shots.indices) {
        canvas.save()
        canvas.clipRect(photoLeft, PRINT_HEIGHT / 2f - 44f, photoLeft + (paints.captionWidths[print.shot] + 12f) * reveal, PRINT_HEIGHT / 2f)
        canvas.translate(photoLeft + 4f, PRINT_HEIGHT / 2f - 15f)
        canvas.rotate(Math.toDegrees(-0.025).toFloat())
        canvas.scale(1f / d, 1f / d)
        canvas.drawText(Shots[print.shot].caption, 0f, 0f, paints.caption)
        canvas.restore()
    }
    // Prints lower in the pile sit in the shade of the ones above.
    if (pose.dim > 0f) {
        fill.color = argb(0x1A1009, pose.dim)
        canvas.drawRoundRect(left, top, -left, -top, 2.5f, 2.5f, fill)
    }
    canvas.restore()
}

/** Sets `matrix` to draw `bitmap` cropped to fill a square of `size` at (x, y). */
private fun coverMatrix(matrix: Matrix, bitmap: Bitmap, x: Float, y: Float, size: Float) {
    val iw = bitmap.width.toFloat()
    val ih = bitmap.height.toFloat()
    val fit = max(size / iw, size / ih)
    matrix.setScale(fit, fit)
    matrix.postTranslate(x + size / 2f - iw * fit / 2f, y + size / 2f - ih * fit / 2f)
}

/**
 * The instant camera, front on: a cream body with a darker lower band, a big lens in a ringed
 * barrel, a ribbed flash window, a viewfinder, a terracotta shutter button and the film slot.
 */
private fun drawCamera(canvas: android.graphics.Canvas, table: Table, c: CameraArt, paints: Paints) {
    val d = paints.density
    val fill = paints.fill
    val soft = paints.soft
    val stroke = paints.stroke
    val x = c.x
    val y = c.y
    val w = c.w
    val h = c.h
    val r = CAMERA_RADIUS
    val shutter = table.shutter.value
    val flash = table.flash.value
    canvas.save()
    // The shutter sinks into the body; the whole camera gives a tiny jolt.
    canvas.translate(0f, shutter * 1.5f)

    // Contact shadow on the table.
    paints.blur(18f)?.let { blur ->
        soft.maskFilter = blur
        soft.color = argb(0, 0.6f)
        canvas.drawRoundRect(x + 10f, y + 18f, x + w - 10f, y + 12f + h, r, r, soft)
    }
    fill.shader = c.body
    fill.color = argb(0)
    fill.alpha = 255
    canvas.drawRoundRect(x, y, x + w, y + h, r, r, fill)
    // The lower band, a shade darker, split from the top by a fine seam.
    canvas.save()
    canvas.clipRect(x, c.seam, x + w, y + h)
    fill.shader = c.band
    canvas.drawRoundRect(x, y, x + w, y + h, r, r, fill)
    canvas.restore()
    fill.shader = null
    fill.color = argb(0xA8977C, 0.7f)
    canvas.drawRect(x + 4f, c.seam, x + w - 4f, c.seam + 1f, fill)
    fill.color = argb(0xFFFFFF, 0.35f)
    canvas.drawRect(x + 4f, c.seam + 1f, x + w - 4f, c.seam + 2f, fill)
    // Light along the top edge, shade along the bottom.
    stroke.shader = c.edge
    stroke.strokeWidth = 1.5f
    stroke.color = argb(0)
    stroke.alpha = 255
    canvas.drawRoundRect(x + 0.75f, y + 0.75f, x + w - 0.75f, y + h - 0.75f, r - 0.75f, r - 0.75f, stroke)
    stroke.shader = null

    // Viewfinder, top left, and the maker's plate on the lower band.
    fill.color = argb(0x2B2622)
    canvas.drawRoundRect(x + 24f, y + 18f, x + 60f, y + 44f, 7f, 7f, fill)
    fill.shader = c.finder
    fill.color = argb(0)
    fill.alpha = 255
    canvas.drawRoundRect(x + 27f, y + 21f, x + 57f, y + 41f, 5f, 5f, fill)
    fill.shader = null
    fill.color = argb(0xFFFFFF, 0.4f)
    canvas.drawRoundRect(x + 30f, y + 23f, x + 39f, y + 26.5f, 1.75f, 1.75f, fill)
    canvas.save()
    canvas.translate(x + 24f, y + h - 28f)
    canvas.scale(1f / d, 1f / d)
    canvas.drawText("ROADBOOK", 0f, 0f, paints.plate)
    canvas.restore()

    // Flash window, top right, with its ribbed lens; it lights up when it fires.
    fill.color = argb(0xB7AD9D)
    canvas.drawRoundRect(x + w - 84f, y + 16f, x + w - 22f, y + 46f, 7f, 7f, fill)
    fill.shader = c.flashLens
    fill.color = argb(0)
    fill.alpha = 255
    canvas.drawRoundRect(x + w - 82f, y + 18f, x + w - 24f, y + 44f, 6f, 6f, fill)
    fill.shader = null
    fill.color = argb(0xB5AD9F, 0.75f)
    for (k in 0 until 7) canvas.drawRect(x + w - 76.5f + k * 7.5f, y + 19f, x + w - 75.5f + k * 7.5f, y + 43f, fill)
    if (flash > 0f) {
        paints.blur(14f)?.let { blur ->
            soft.maskFilter = blur
            soft.color = argb(0xFFFFFF, flash)
            canvas.drawRoundRect(x + w - 94f, y + 8f, x + w - 12f, y + 54f, 14f, 14f, soft)
        }
        fill.color = argb(0xFFFFFF, flash)
        canvas.drawRoundRect(x + w - 82f, y + 18f, x + w - 24f, y + 44f, 6f, 6f, fill)
    }

    // Lens: a cream outer ring, a dark barrel with fine rings, and the glass.
    paints.blur(8f)?.let { blur ->
        soft.maskFilter = blur
        soft.color = argb(0, 0.28f)
        canvas.drawCircle(c.lensX, c.lensY + 4f, LENS + 8f, soft)
    }
    fill.shader = c.ring
    fill.color = argb(0)
    fill.alpha = 255
    canvas.drawCircle(c.lensX, c.lensY, LENS + 6f, fill)
    fill.shader = c.barrel
    canvas.drawCircle(c.lensX, c.lensY, LENS, fill)
    fill.shader = null
    fill.color = argb(0x0E0D0C)
    canvas.drawCircle(c.lensX, c.lensY, LENS - 8f, fill)
    stroke.strokeWidth = 1.2f
    stroke.color = argb(0x5A554F)
    canvas.drawCircle(c.lensX, c.lensY, LENS - 11f, stroke)
    fill.shader = c.glass
    fill.color = argb(0)
    fill.alpha = 255
    canvas.drawCircle(c.lensX, c.lensY, LENS - 17f, fill)
    fill.shader = null
    fill.color = argb(0x020306, 0.85f)
    canvas.drawCircle(c.lensX, c.lensY, 10f, fill)
    fill.color = argb(0xFFFFFF, 0.4f)
    canvas.drawCircle(c.lensX - 12f, c.lensY - 13f, 6.5f, fill)
    fill.color = argb(0xFFFFFF, 0.28f)
    canvas.drawCircle(c.lensX + 11f, c.lensY + 12f, 2.2f, fill)

    // Shutter button, with its ready pulse breathing out every 2.4 s.
    val pulse = (table.time % 2.4f) / 2.4f
    val pulseAlpha = (1f - pulse) * 0.5f * table.readyGlow
    if (pulseAlpha > 0.001f) {
        stroke.strokeWidth = 2f
        stroke.color = argb(SHUTTER, pulseAlpha)
        canvas.drawCircle(c.buttonX, c.buttonY, 15f + pulse * 14f, stroke)
    }
    fill.color = argb(0xB9A78B)
    canvas.drawCircle(c.buttonX, c.buttonY, 15f, fill)
    fill.color = argb(0x8B7A61, 0.6f)
    canvas.drawCircle(c.buttonX, c.buttonY, 13.5f, fill)
    fill.shader = c.button
    fill.color = argb(0)
    fill.alpha = 255
    canvas.drawCircle(c.buttonX, c.buttonY, 11f - shutter * 2f, fill)
    fill.shader = null

    // The film slot along the bottom edge.
    fill.color = argb(0x120F0C)
    canvas.drawRoundRect(x + w / 2f - PRINT_WIDTH / 2f - 9f, y + h - 13f, x + w / 2f + PRINT_WIDTH / 2f + 9f, y + h - 6f, 3.5f, 3.5f, fill)
    fill.color = argb(0xFFFFFF, 0.3f)
    canvas.drawRect(x + w / 2f - PRINT_WIDTH / 2f - 6f, y + h - 6f, x + w / 2f + PRINT_WIDTH / 2f + 6f, y + h - 5f, fill)
    canvas.restore()
}
