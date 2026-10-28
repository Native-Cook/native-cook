package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Habitat Specimen Carousel (HabitatSpecimenCarousel.tsx): three flat cut-out creatures on a
// swipeable stage whose habitat is wiped across by a front. Tap a resting specimen for its signature
// move, hold to pet it, drag to change specimen.

private class Type(val label: String, val icon: String, val color: Color)
private class Specimen(
    val name: String, val number: String, val species: String, val element: String, val weight: String, val height: String,
    val asset: String, val types: List<Type>, val copy: String, val stats: List<Int>, val evolution: List<HabitatPanelSpec.Stage>,
)

private val SPECIMENS = listOf(
    Specimen(
        "Charizard", "006", "Flame Pokémon", "fire", "90.5 kg", "1.7 m", "charizard",
        listOf(Type("Fire", MdiPath.FIRE, Color(0xFFEAC28C)), Type("Flying", MdiPath.FEATHER, Color(0xFFC2CBDD))),
        "Charizard flies around the sky in search of powerful opponents. It breathes fire of such great heat that it melts anything. However, it never turns its fiery breath on any opponent weaker than itself.",
        listOf(78, 84, 78, 109, 85, 100),
        listOf(HabitatPanelSpec.Stage("Charmander", "004"), HabitatPanelSpec.Stage("Charmeleon", "005", 16), HabitatPanelSpec.Stage("Charizard", "006", 36)),
    ),
    Specimen(
        "Blastoise", "009", "Shellfish Pokémon", "water", "85.5 kg", "1.6 m", "blastoise",
        listOf(Type("Water", MdiPath.WATER, Color(0xFF9CE0ED))),
        "Blastoise has water spouts that protrude from its shell. The water spouts are very accurate. They can shoot bullets of water with enough accuracy to strike empty cans from over 160 feet away.",
        listOf(79, 83, 100, 85, 105, 78),
        listOf(HabitatPanelSpec.Stage("Squirtle", "007"), HabitatPanelSpec.Stage("Wartortle", "008", 16), HabitatPanelSpec.Stage("Blastoise", "009", 36)),
    ),
    Specimen(
        "Bulbasaur", "001", "Seed Pokémon", "grass", "6.9 kg", "0.7 m", "bulbasaur",
        listOf(Type("Grass", MdiPath.LEAF, Color(0xFFC2E9A2)), Type("Poison", MdiPath.BOTTLE_TONIC_SKULL, Color(0xFFD2B4E6))),
        "A strange seed was planted on its back at birth. The plant sprouts and grows with this Pokémon, soaking up sunlight to store energy for the long journey ahead.",
        listOf(45, 49, 49, 65, 65, 45),
        listOf(HabitatPanelSpec.Stage("Bulbasaur", "001"), HabitatPanelSpec.Stage("Ivysaur", "002", 16), HabitatPanelSpec.Stage("Venusaur", "003", 32)),
    ),
)

private val PRESS_EASING = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val ICON_SWAP = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** A heart on the favourite button's burst, per element. */
private val FAVORITE_BURST = listOf(Color(0xFFF2A766), Color(0xFF7FD3EA), Color(0xFFA9DD7F))

/** Choose Your Ball's belt index, with the specimens: each thumbnail places itself by its distance from centre. */
private const val STRIP_STRIDE = 40f
private const val STRIP_THUMB = 24f
private const val STRIP_SELECTED = 34f

private const val ASSET_DIR = "habitatspecimencarousel"

private fun loadArt(context: Context, name: String): Bitmap =
    context.assets.open("$ASSET_DIR/${ASSET_DIR}_$name.png").use { BitmapFactory.decodeStream(it) }

/** The incoming specimen's hop height in points, and the landed specimen's contact squash, for the shadows. */
private fun liftUniform(motion: HabitatMotion, size: Float): FloatArray {
    if (motion.reduced) return floatArrayOf(0f, 0f)
    val p = wrapPosition(motion.position)
    val f = p - kotlin.math.floor(p)
    val from = kotlin.math.floor(p).toInt() % 3
    return floatArrayOf(
        if (f > 0.0005f) hopLift(f) * HOP_HEIGHT * size else 0f,
        if (motion.landingIndex == from) max(0f, landEnvelope(motion.landing.value)) else 0f,
    )
}

@RequiresApi(33)
private class EnvironmentShader {
    val runtime = RuntimeShader(HABITAT_AGSL)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.shader = runtime }
}

@Composable
private fun HabitatEnvironment(motion: HabitatMotion, width: Float, height: Float, heroTop: Float, heroHeight: Float, creatureSize: Float) {
    val density = LocalDensity.current.density
    val shader = remember { if (supportsAgsl()) EnvironmentShader() else null }
    if (shader == null) {
        EnvironmentFallback(motion, width, height)
        return
    }
    Box(
        Modifier.fillMaxSize().drawBehind {
            motion.observe()
            if (Build.VERSION.SDK_INT < 33) return@drawBehind
            val lift = liftUniform(motion, creatureSize)
            val r = shader.runtime
            r.setFloatUniform("resolution", width, height)
            r.setFloatUniform("stage", heroTop, heroHeight)
            r.setFloatUniform("creature", creatureSize, EXCHANGE_DRIFT * width, (heroHeight - creatureSize) / 2)
            r.setFloatUniform("position", motion.position)
            r.setFloatUniform("time", motion.time)
            r.setFloatUniform("influence", motion.influence.value)
            r.setFloatUniform("reduced", if (motion.reduced) 1f else 0f)
            r.setFloatUniform("flare", signatureEnvelope(motion.power.value))
            r.setFloatUniform("flareElement", motion.powerIndex.toFloat())
            r.setFloatUniform("lift", lift[0], lift[1])
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(density, density)
                native.drawRect(0f, 0f, width, height, shader.paint)
                native.restore()
            }
        },
    )
}

/** Below Android 13 there is no runtime shader: the three habitats are gradients that cross-fade. */
@Composable
private fun EnvironmentFallback(motion: HabitatMotion, width: Float, height: Float) {
    val palettes = listOf(
        listOf(Color(0xFF34221B), Color(0xFF68351D), Color(0xFF160F0E)),
        listOf(Color(0xFF103F58), Color(0xFF145766), Color(0xFF081F30)),
        listOf(Color(0xFF173F2A), Color(0xFF315D32), Color(0xFF10281F)),
    )
    Box(Modifier.size(width.dp, height.dp)) {
        palettes.forEachIndexed { index, colors ->
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { motion.observe(); alpha = max(0f, 1 - kotlin.math.abs(specimenDistance(index, motion.position))) }
                    .background(Brush.verticalGradient(0f to colors[0], 0.4f to colors[1], 1f to colors[2])),
            )
        }
    }
}

/** A pressable that dims and sinks a little under the finger (HabitatButton). */
@Composable
private fun HabitatButton(onPress: () -> Unit, reduced: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (pressed) 0.78f else 1f, tween(120, easing = PRESS_EASING), label = "buttonAlpha")
    val pop by animateFloatAsState(if (pressed && !reduced) 0.97f else 1f, tween(120, easing = PRESS_EASING), label = "buttonScale")
    Box(
        modifier
            .widthIn(min = 48.dp).heightIn(min = 48.dp)
            .pointerInput(onPress) {
                detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onPress() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { this.alpha = alpha; scaleX = pop; scaleY = pop }, contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * The favourite icon cross-fades (scale 0.25 -> 1, blur 4 -> 0) and, when it turns on, throws six
 * small sparks in the specimen's colour. Under Reduced Motion only the opacity changes.
 */
@Composable
private fun FavoriteHeart(on: Boolean, size: Float, reduced: Boolean, burst: Color) {
    val density = LocalDensity.current.density
    @Composable
    fun icon(path: String, color: Color, shown: Boolean) {
        val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(300, easing = ICON_SWAP), label = "heartAlpha")
        val pop by animateFloatAsState(if (shown || reduced) 1f else 0.25f, tween(300, easing = ICON_SWAP), label = "heartScale")
        val blur by animateFloatAsState(if (shown || reduced) 0f else 4f, tween(300, easing = ICON_SWAP), label = "heartBlur")
        Box(Modifier.graphicsLayer {
            this.alpha = alpha; scaleX = pop; scaleY = pop
            renderEffect = if (reduced) null else sigmaBlur(blur * density)
        }) { MdiIcon(path, size, color) }
    }
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        icon(MdiPath.HEART_OUTLINE, Color(0xFFD7E3D2), !on)
        icon(MdiPath.HEART, Color(0xFFEDC591), on)
        if (on && !reduced) for (i in 0..5) FavoriteSpark(i, burst, size)
    }
}

@Composable
private fun FavoriteSpark(index: Int, color: Color, size: Float) {
    val angle = (index / 6f) * Math.PI.toFloat() * 2 - Math.PI.toFloat() / 2 + (index % 2) * 0.22f
    val reach = size * (0.95f + (index % 3) * 0.12f)
    val t = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(520, easing = PRESS_EASING)) }
    Box(
        Modifier
            .size(4.dp)
            .graphicsLayer {
                translationX = cos(angle) * reach * t.value * density
                translationY = sin(angle) * reach * t.value * density
                val s = 1f + (0.4f - 1f) * t.value
                scaleX = s; scaleY = s
                alpha = 1f - t.value
            }
            .background(color, CircleShape),
    )
}

/** The dex number behind the hero, rewritten with the same morph: the shared 0s glide, the last digit swaps. */
@Composable
private fun DexGhost(text: String, reducedMotion: Boolean, width: Float, scale: Float) {
    val fontSize = 168 * scale
    val height = Math.round(fontSize * 1.34f).toFloat()
    val system = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(system.density, fontScale = 1f)) {
        Box(Modifier.offset(0.dp, (-10 * scale).dp).size(width.dp, height.dp).alpha(0.065f)) {
            HabitatMorphingText(
                text = text, fitTexts = SPECIMENS.map { it.number }, fontSize = fontSize.dp, tracking = (-6 * scale).dp,
                width = width.dp, height = height.dp, paddingLeft = 0.dp, color = Color(0xFFC5DAC7), reducedMotion = reducedMotion,
                weight = FontWeight.Bold, center = true,
            )
        }
    }
}

private fun interpolate(x: Float, input: FloatArray, output: FloatArray): Float {
    if (x <= input[0]) return output[0]
    if (x >= input[input.size - 1]) return output[output.size - 1]
    var i = 0
    while (input[i + 1] < x) i++
    return output[i] + (output[i + 1] - output[i]) * (x - input[i]) / (input[i + 1] - input[i])
}

@Composable
private fun SpecimenStrip(motion: HabitatMotion, arts: List<Bitmap>, onPick: (Int) -> Unit) {
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SPECIMENS.forEachIndexed { index, specimen ->
            var pressed by remember { mutableStateOf(false) }
            val bitmap = remember(arts[index]) { arts[index].asImageBitmap() }
            val pop by animateFloatAsState(if (pressed && !motion.reduced) 0.96f else 1f, tween(150, easing = PRESS_EASING), label = "stripScale")
            val dim by animateFloatAsState(if (pressed) 0.8f else 1f, tween(150, easing = PRESS_EASING), label = "stripAlpha")
            Box(
                Modifier
                    .size(STRIP_THUMB.dp)
                    .graphicsLayer {
                        motion.observe()
                        val p = motion.position
                        val slot = specimenDistance(index, if (motion.reduced) jsRound(p) else p)
                        val distance = kotlin.math.abs(slot)
                        alpha = interpolate(distance, floatArrayOf(0f, 1f, 1.5f), floatArrayOf(1f, 0.42f, 0.2f))
                        translationX = slot * STRIP_STRIDE * density
                        val s = interpolate(distance, floatArrayOf(0f, 1f), floatArrayOf(STRIP_SELECTED / STRIP_THUMB, 1f))
                        scaleX = s; scaleY = s
                    }
                    .semantics { contentDescription = "Select ${specimen.name}" }
                    .pointerInput(index) {
                        detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onPick(index) })
                    },
            ) {
                Image(bitmap, null, Modifier.size(STRIP_THUMB.dp).graphicsLayer { scaleX = pop; scaleY = pop; alpha = dim })
            }
        }
    }
}

private fun withAlpha(color: Color, alpha: Float) = color.copy(alpha = alpha)

@Composable
private fun SpecimenTypes(motion: HabitatMotion, top: Float, scale: Float, width: Float) {
    val row = 25 * scale
    Box(Modifier.offset(16.dp, top.dp).width((width - 32).dp).zIndex(10f)) {
        TextSwap(motion, row, Modifier.fillMaxWidth(), items = SPECIMENS.map { specimen ->
            {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((8 * scale).dp, Alignment.CenterHorizontally)) {
                    specimen.types.forEach { type ->
                        val shape = RoundedCornerShape((15 * scale).dp)
                        Row(
                            Modifier.height(row.dp)
                                .background(withAlpha(type.color, 8f / 255f), shape)
                                .border(Dp.Hairline, withAlpha(type.color, 0x45 / 255f), shape)
                                .padding(horizontal = (11 * scale).dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MdiIcon(type.icon, 12 * scale, type.color)
                            HText(type.label, 10 * scale, type.color, weight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        })
    }
}

@Composable
private fun SpecimenFacts(motion: HabitatMotion, top: Float, scale: Float, width: Float) {
    val line = 17 * scale
    @Composable
    fun fact(icon: String, label: String, pick: (Specimen) -> String, modifier: Modifier) {
        androidx.compose.foundation.layout.Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                MdiIcon(icon, 15 * scale, Color(0xFFB1C1B0))
                TextSwap(motion, line, Modifier.widthIn(min = (58 * scale).dp), items = SPECIMENS.map { specimen ->
                    { HText(pick(specimen), 12 * scale, Color(0xFFEFF3E4), weight = FontWeight.SemiBold, lineHeight = line) }
                })
            }
            HText(label, 8 * scale, Color(0xFFA5B49D), letterSpacing = 1f)
        }
    }
    Box(
        Modifier.offset((25 * scale).dp, top.dp).size((width - 50 * scale).dp, (44 * scale).dp).zIndex(10f)
            .semantics { contentDescription = "Weight ${SPECIMENS[motion.selectedIndex].weight}, height ${SPECIMENS[motion.selectedIndex].height}" },
    ) {
        Box(Modifier.fillMaxWidth().height(Dp.Hairline).background(Color(0x19D4E4DA)))
        Row(Modifier.fillMaxSize().padding(top = (10 * scale).dp)) {
            fact(MdiPath.WEIGHT_KILOGRAM, "WEIGHT", { it.weight }, Modifier.weight(1f))
            Box(Modifier.fillMaxSize().weight(1f).drawBehind {
                drawRect(Color(0x1AD4E4DA), Offset.Zero, androidx.compose.ui.geometry.Size(Dp.Hairline.toPx().coerceAtLeast(1f), size.height))
            }) {
                fact(MdiPath.RULER, "HEIGHT", { it.height }, Modifier.fillMaxWidth())
            }
        }
    }
}

/** The particle layers: behind the creatures (0) or in front of them (1). */
@Composable
private fun ParticleLayer(motion: HabitatMotion, world: HabitatParticleWorld, layer: Int, layout: HabitatLayout, running: Boolean) {
    if (!supportsAgsl()) return
    val density = LocalDensity.current.density
    val painter = remember(world) { if (Build.VERSION.SDK_INT >= 33) world.Painter() else null }
    Box(
        Modifier.size(layout.width.dp, layout.height.dp).drawBehind {
            motion.observe()
            if (!running || painter == null) return@drawBehind
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(density, density)
                native.clipRect(0f, 0f, layout.width, layout.height)
                painter.draw(native, layer)
                native.restore()
            }
        },
    )
}

@Composable
fun HabitatSpecimenCarousel(autoPlay: Boolean = true, previewTime: Float? = null, previewReducedMotion: Boolean? = null) {
    BlockSystemBars(darkIcons = false)
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val systemReduced = rememberLiveReducedMotion()
    val arts = remember { SPECIMENS.map { loadArt(context, it.asset) } }
    var normals by remember { mutableStateOf<List<Bitmap?>>(listOf(null, null, null)) }
    LaunchedEffect(arts) {
        val baked = withContext(Dispatchers.Default) { arts.map { bakeNormals(it) } }
        normals = baked
    }
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val insetTop = insets.calculateTopPadding().value
    val insetBottom = insets.calculateBottomPadding().value

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF111D19))) {
        val width = maxWidth.value
        val height = maxHeight.value
        val scale = width / 393f
        val fontScale = density.fontScale
        val identityHeight = habitatIdentityTextHeight(scale, fontScale)
        val available = height - insetTop - insetBottom
        val heroHeight = max(190 * scale, min(340 * scale, available - 415 * scale - max(0f, identityHeight - 60 * scale)))
        val heroTop = insetTop + 78 * scale
        val creatureSize = min(357 * scale, heroHeight - 4 * scale)
        // About half the screen of finger travel per specimen: the front leads the finger, so one deliberate
        // swipe turns one page, as on Choose Your Ball.
        val gap = Math.round(width * 0.58f).toFloat()
        val identityTop = heroTop + heroHeight + 29 * scale
        val tabsTop = identityTop + identityHeight + 48 * scale
        val descriptionTop = tabsTop + 44 * scale
        // Weight and height sit on the specimen strip, which takes the footer's centre.
        val factsTop = height - insetBottom - 84 * scale
        val pad = Math.round(creatureSize * CREATURE_PAD).toFloat()
        val layout = HabitatLayout(
            width = width, height = heroTop + heroHeight + 40 * scale, heroTop = heroTop, heroHeight = heroHeight, screenHeight = height,
            scale = scale, size = creatureSize, left = (width - creatureSize) / 2, top = (heroHeight - creatureSize) / 2, pad = pad,
        )

        val reduced = previewReducedMotion ?: systemReduced
        val motion = remember(previewTime) { HabitatMotion(autoPlay, previewTime, previewReducedMotion, systemReduced, gap, width) }
        SideEffect {
            motion.autoPlay = autoPlay
            motion.gap = gap
            motion.width = width
            motion.setReduced(reduced)
        }

        // Feedback: a light tick, then a heavier one with the release after the crouch; a selection tick when a
        // sent specimen lands; a soft one when a hold turns into petting.
        val handler = remember { Handler(Looper.getMainLooper()) }
        SideEffect {
            motion.onSignature = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                handler.postDelayed({ view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }, 215)
            }
            motion.onLand = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            motion.onPet = { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
        }

        // The screen is shown and the app is in the foreground.
        var active by remember { mutableStateOf(false) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, motion) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) { active = true; motion.setActive(true) }
                if (event == Lifecycle.Event.ON_PAUSE) { active = false; motion.setActive(false) }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { active = true; motion.setActive(true) }
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer); motion.setActive(false) }
        }

        // The phone's tilt (gravity) leans the key light and turns which way is up for the particles.
        DisposableEffect(active, reduced) {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) { motion.gravityX = event.values[0]; motion.gravityY = event.values[1] }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (active && !reduced && sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { manager?.unregisterListener(listener) }
        }

        // The particle world runs while the screen is shown and moving; a frozen review frame keeps the
        // analytic particles of the shader instead, so a moment can be scrubbed to.
        val particlesRun = previewTime == null && supportsAgsl() && active && !reduced
        val world = remember { HabitatParticleWorld() }
        val latestLayout by rememberUpdatedState(layout)
        val latestRun by rememberUpdatedState(particlesRun)
        LaunchedEffect(motion, world) {
            var base = -1L
            var last = -1L
            var wasRunning = false
            while (true) {
                androidx.compose.runtime.withFrameNanos { nanos ->
                    if (base < 0) { base = nanos; last = nanos }
                    val dtMs = ((nanos - last) / 1_000_000.0).toFloat()
                    last = nanos
                    motion.frame((nanos - base) / 1_000_000.0, dtMs)
                    if (latestRun) {
                        if (!wasRunning) world.reset()
                        world.step(motion, latestLayout, dtMs, (nanos - base) / 1_000_000.0)
                    }
                    wasRunning = latestRun
                }
            }
        }

        val selectedIndex = motion.selectedIndex
        val selected = SPECIMENS[selectedIndex]
        var favorites by remember { mutableStateOf(listOf(false, false, false)) }
        val favorite = favorites[selectedIndex]
        // A tab the person picks holds until the demo itself moves on to another panel.
        var picked by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        val demoTab = motion.demoTab
        val tab = picked.let { if (it != null && it.second == demoTab) it.first else demoTab }

        // Environment.
        HabitatEnvironment(motion, width, height, heroTop, heroHeight, creatureSize)
        // The particle world, behind the creatures.
        ParticleLayer(motion, world, 0, layout, particlesRun)

        // Header: the previous button and the favourite heart.
        Row(
            Modifier.offset((17 * scale).dp, (insetTop + 1 * scale).dp).width((width - 34 * scale).dp).zIndex(12f),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            HabitatButton(onPress = { motion.select((selectedIndex + 2) % 3) }, reduced = reduced, modifier = Modifier.semantics { contentDescription = "Previous Pokémon" }) {
                MdiIcon(MdiPath.CHEVRON_LEFT, 25 * scale, Color(0xFFF3F4E9))
            }
            HabitatButton(
                onPress = {
                    if (!favorite) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    favorites = favorites.mapIndexed { index, value -> if (index == selectedIndex) !value else value }
                },
                reduced = reduced,
                modifier = Modifier.semantics { contentDescription = "${if (favorite) "Remove" else "Add"} ${selected.name} ${if (favorite) "from" else "to"} favorites" },
            ) { FavoriteHeart(favorite, 22 * scale, reduced, FAVORITE_BURST[selectedIndex]) }
        }
        // Eyebrow.
        Row(
            Modifier.offset((26 * scale).dp, (insetTop + 58 * scale).dp).width((width - 53 * scale).dp).zIndex(10f),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HText("POKÉDEX", 10 * scale, Color(0xFFD0D8C9), weight = FontWeight.Medium, letterSpacing = 3f)
            TextSwap(motion, 17 * scale, Modifier.width((64 * scale).dp), items = SPECIMENS.map { specimen ->
                { HText("#${specimen.number}", 12 * scale, Color(0xFFF1F4E7), Modifier.fillMaxWidth(), FontWeight.SemiBold, letterSpacing = 2f, lineHeight = 17 * scale, align = TextAlign.End, tabular = true) }
            })
        }
        HabitatSignature(motion, 0, layout, particlesRun)
        // The stage: a drag changes specimen, a tap performs the move, a hold pets.
        Box(
            Modifier.offset(0.dp, heroTop.dp).size(width.dp, heroHeight.dp)
                .semantics {
                    contentDescription = "Pokédex carousel, ${selected.name}"
                    customActions = listOf(
                        CustomAccessibilityAction("Next Pokémon") { motion.select((selectedIndex + 1) % 3); true },
                        CustomAccessibilityAction("Previous Pokémon") { motion.select((selectedIndex + 2) % 3); true },
                    )
                }
                .habitatGestures(motion),
        ) {
            DexGhost(selected.number, reduced, width, scale)
            SPECIMENS.forEachIndexed { index, _ ->
                HabitatCreature(
                    index = index, image = arts[index], normals = normals[index], motion = motion, size = creatureSize,
                    left = layout.left, top = layout.top, width = width, heroTop = heroTop, screenHeight = height, scale = scale,
                )
            }
        }
        Box(Modifier.zIndex(19f)) { HabitatAffection(motion, width, heroTop, heroHeight, creatureSize) }
        Box(Modifier.zIndex(11f)) { HabitatHint(heroTop + heroHeight + 2 * scale, scale, reduced, motion.touchedOnce) }
        Box(Modifier.zIndex(20f)) { HabitatSignature(motion, 1, layout, particlesRun) }
        Box(Modifier.zIndex(21f)) { ParticleLayer(motion, world, 1, layout, particlesRun) }
        Box(Modifier.zIndex(10f)) {
            HabitatIdentityText(
                reducedMotion = reduced, scale = scale, fontScale = fontScale, top = identityTop,
                species = SPECIMENS.map { it.species }, names = SPECIMENS.map { it.name }, selectedIndex = selectedIndex, width = width,
            )
        }
        SpecimenTypes(motion, identityTop + identityHeight + 9 * scale, scale, width)
        Box(Modifier.zIndex(10f)) {
            HabitatPanels(
                specs = SPECIMENS.mapIndexed { index, specimen ->
                    HabitatPanelSpec(specimen.name, specimen.number, arts[index], specimen.copy, specimen.stats, specimen.evolution)
                },
                motion = motion, scale = scale, width = width, tabsTop = tabsTop, contentTop = descriptionTop,
                contentHeight = max(24f, factsTop - descriptionTop - 4 * scale), tab = tab,
                onTab = { next -> motion.hold(6000f); picked = next to demoTab }, selectedIndex = selectedIndex,
            )
        }
        SpecimenFacts(motion, factsTop, scale, width)
        // Footer: the region mark, the specimen strip and the dex count.
        Box(
            Modifier.offset((27 * scale).dp, (height - insetBottom - 4 * scale - STRIP_SELECTED).dp).size((width - 54 * scale).dp, STRIP_SELECTED.dp).zIndex(10f),
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MdiIcon(MdiPath.POKEBALL, 14 * scale, Color(0xFFA9B8A3))
                    HText("KANTO REGION", 8 * scale, Color(0xFFA1B297), letterSpacing = 2f)
                }
                TextSwap(motion, 12 * scale, Modifier.width((70 * scale).dp), items = SPECIMENS.map { specimen ->
                    { HText("${specimen.number} / 151", 8 * scale, Color(0xFFA1B297), Modifier.fillMaxWidth(), letterSpacing = 2f, lineHeight = 12 * scale, align = TextAlign.End) }
                })
            }
            SpecimenStrip(motion, arts, motion::select)
        }
    }
}
