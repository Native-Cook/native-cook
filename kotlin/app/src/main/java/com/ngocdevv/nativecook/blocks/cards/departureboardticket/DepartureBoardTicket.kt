package com.ngocdevv.nativecook.blocks.cards.departureboardticket

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class TicketField(val label: String, val value: String)

/** @param code Drawn in board dots; letters A to Z. */
data class TicketAirport(val code: String, val name: String, val city: String)

private const val WIDTH = 328f
private const val HEIGHT = 574f
private const val RADIUS = 24f

// Where the perforation runs.
private const val TEAR_Y = 480f

// Radius of the half circles cut out of both sides, and of the holes of the perforation.
private const val NOTCH = 13f
private const val HOLE = 3f
private const val HOLES = 28
private const val HOLE_INSET = 26f

private const val PAD = 30f
private const val STUB_PAD = 22f
private const val FIELD_INSET = 14f

// The shadows' canvases overflow the ticket by this much on every side.
private val BLEED = 64.dp

// The board: a 5 x 7 grid of square dots per letter.
private const val CELL = 8f
private const val DOT = 6.6f
private const val LETTER_GAP = 9f
private const val ORIGIN_TOP = 104f
private const val DESTINATION_TOP = 299f
private const val SMALL_CELL = 1.55f
private const val SMALL_DOT = 1.15f
private const val BARCODE_WIDTH = 92f

private val Pale = Color(0xFFB9D6FF)

// The aurora starts to wash out into the pale blue here.
private const val PALE_FROM = 440f
private val StubInk = Color(0xFF1F2F5C)
private val StubMute = Color(0xFF6C85B0)

// A flap: every FLAP_STEP seconds a letter shows another glyph, FLAPS times, and each letter
// (then each field) starts two steps after the one before it.
private const val FLAP_STEP = 0.075f
private const val FLAPS = 8
private const val FLAP_STAGGER = 2
private const val FIELD_FLAPS = 6

// Steps until the last of six letters has landed.
private const val FLAP_STEPS = 18
private const val GRID_IN = 0.12f
private const val GRID_OUT = 0.45f

// The stub. It tears off once it is pulled TEAR_AT points down.
private const val TEAR_AT = 46f

// Degrees the stub swings on its top left corner before it tears off.
private const val SWING = 6.5f

// Torn off, it hangs this far below the ticket, at this angle, and follows the pull by FOLLOW.
private const val DROP = 20f
private const val TILT = 2.5f
private const val FOLLOW = 0.5f

// The spring the stub's drop and angle chase their targets on (mass 1).
private const val STIFFNESS = 240f
private const val DAMPING = 19f

// autoPlay loop (seconds): the board flaps, a finger pulls the stub down and off, then lets go.
private const val FLAP_AT = 0.8f
private const val TEAR_START = 2.5f
private const val PULL_TIME = 1f
private const val PULL_MAX = 84f
private const val RELEASE_AT = 4.7f
private const val LOOP = 7f

// After a touch the loop waits this long before it starts again from rest.
private const val USER_HOLD = 2f

// Without the loop the board flaps once, this long after the ticket appears.
private const val APPEAR_FLAP_AT = 0.3f

private class AuroraLight(color: Color, val x: Float, val y: Float, val radius: Float, val driftX: Float, val driftY: Float, val phase: Float) {
    val brush = Brush.radialGradient(
        0f to color,
        0.5f to color.copy(alpha = 0.5f),
        1f to color.copy(alpha = 0f),
        center = Offset(x, y),
        radius = radius,
    )
}

// Three lights that circle slowly, once per loop: violet on the right, royal blue in the middle, cyan on the left.
private val Aurora = listOf(
    AuroraLight(Color(0xFF5A35C8), 300f, 392f, 150f, 14f, -12f, 2.2f),
    AuroraLight(Color(0xFF1B2FD0), 170f, 424f, 176f, -20f, 10f, 1f),
    AuroraLight(Color(0xFF19C8F0), 14f, 462f, 150f, 16f, 9f, 0f),
)

// Bars and gaps of the barcode, in turn, in units.
private val Barcode = intArrayOf(2, 1, 1, 3, 1, 2, 1, 1, 2, 3, 1, 1, 2, 1, 3, 1, 1, 2, 2, 1, 1, 3, 1, 2, 1, 1, 2, 1, 3, 1, 2, 1, 1, 2, 1)

// 5 x 7 dot letters A to Z, one number per row with the left dot as the high bit.
private val Glyphs = arrayOf(
    intArrayOf(14, 17, 17, 31, 17, 17, 17), intArrayOf(30, 17, 17, 30, 17, 17, 30), intArrayOf(14, 17, 16, 16, 16, 17, 14),
    intArrayOf(30, 17, 17, 17, 17, 17, 30), intArrayOf(31, 16, 16, 30, 16, 16, 31), intArrayOf(31, 16, 16, 30, 16, 16, 16),
    intArrayOf(14, 17, 16, 23, 17, 17, 14), intArrayOf(17, 17, 17, 31, 17, 17, 17), intArrayOf(14, 4, 4, 4, 4, 4, 14),
    intArrayOf(7, 2, 2, 2, 2, 18, 12), intArrayOf(17, 18, 20, 24, 20, 18, 17), intArrayOf(16, 16, 16, 16, 16, 16, 31),
    intArrayOf(17, 27, 21, 21, 17, 17, 17), intArrayOf(17, 25, 21, 19, 17, 17, 17), intArrayOf(14, 17, 17, 17, 17, 17, 14),
    intArrayOf(30, 17, 17, 30, 16, 16, 16), intArrayOf(14, 17, 17, 17, 21, 18, 13), intArrayOf(30, 17, 17, 30, 20, 18, 17),
    intArrayOf(15, 16, 16, 14, 1, 1, 30), intArrayOf(31, 4, 4, 4, 4, 4, 4), intArrayOf(17, 17, 17, 17, 17, 17, 14),
    intArrayOf(17, 17, 17, 17, 17, 10, 4), intArrayOf(17, 17, 17, 21, 21, 27, 17), intArrayOf(17, 17, 10, 4, 10, 17, 17),
    intArrayOf(17, 17, 10, 4, 4, 4, 4), intArrayOf(31, 1, 2, 4, 8, 16, 31),
)
private val BlankGlyph = IntArray(7)

// How far a flapping letter or digit is from the one it lands on, step after step. Fixed, so every
// flap shows the same glyphs.
private val LetterShifts = intArrayOf(7, 19, 3, 14, 23, 10, 5, 17, 12, 21)
private val DigitShifts = intArrayOf(3, 7, 1, 8, 5, 9, 2, 6)

private fun easeInOut(t: Float) = t * t * (3f - 2f * t)

/** How far the loop's finger has pulled the stub down at [t] seconds. */
private fun scriptPull(t: Float): Float {
    if (t < TEAR_START || t >= RELEASE_AT) return 0f
    return PULL_MAX * easeInOut(min(1f, (t - TEAR_START) / PULL_TIME))
}

/** How visible the unlit dots are [elapsed] seconds into a flap. */
private fun gridGlow(elapsed: Float): Float {
    val rise = (elapsed / GRID_IN).coerceIn(0f, 1f)
    val fall = ((elapsed - FLAP_STEPS * FLAP_STEP) / GRID_OUT).coerceIn(0f, 1f)
    return rise * (1f - fall)
}

private fun glyphRows(letter: Char) = if (letter in 'A'..'Z') Glyphs[letter - 'A'] else BlankGlyph

/** The glyph the board shows for [letter], the [position]-th letter on the ticket, [step] steps into a flap. */
private fun flapGlyph(letter: Char, position: Int, step: Int): IntArray {
    if (letter !in 'A'..'Z') return BlankGlyph
    val final = letter - 'A'
    val local = step - position * FLAP_STAGGER
    if (local < 0 || local >= FLAPS) return Glyphs[final]
    return Glyphs[(final + LetterShifts[(position + local) % LetterShifts.size]) % 26]
}

/** A field's value [step] steps into a flap: its digits and letters show others until the field lands. */
private fun flapText(value: String, field: Int, step: Int): String {
    val local = step - (field + 1) * FLAP_STAGGER
    if (local < 0 || local >= FIELD_FLAPS) return value
    return buildString {
        value.forEachIndexed { n, character ->
            val letter = LetterShifts[(n + local) % LetterShifts.size]
            append(
                when (character) {
                    in '0'..'9' -> '0' + (character - '0' + DigitShifts[(n + local) % DigitShifts.size]) % 10
                    in 'A'..'Z' -> 'A' + (character - 'A' + letter) % 26
                    in 'a'..'z' -> 'a' + (character - 'a' + letter) % 26
                    else -> character
                },
            )
        }
    }
}

/**
 * The ticket's outline cut in two along the perforation: a rounded rectangle with a half circle
 * taken out of each side and a row of holes between them. The stub is in its own coordinates.
 */
private class TicketPieces {
    val body: Path
    val stub: Path

    init {
        val outline = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, WIDTH, HEIGHT), CornerRadius(RADIUS))) }
        val holes = Path().apply {
            addOval(Rect(Offset(0f, TEAR_Y), NOTCH))
            addOval(Rect(Offset(WIDTH, TEAR_Y), NOTCH))
            val pitch = (WIDTH - HOLE_INSET * 2f) / (HOLES - 1)
            for (n in 0 until HOLES) addOval(Rect(Offset(HOLE_INSET + pitch * n, TEAR_Y), HOLE))
        }
        fun cut(y: Float, height: Float): Path {
            val half = Path().apply { addRect(Rect(0f, y, WIDTH, y + height)) }
            return Path.combine(PathOperation.Difference, Path.combine(PathOperation.Intersect, outline, half), holes)
        }
        body = cut(0f, TEAR_Y)
        stub = cut(TEAR_Y, HEIGHT - TEAR_Y).apply { translate(Offset(0f, -TEAR_Y)) }
    }
}

/** What is printed on the stub in ink: the route in small dots with its arrow, and the barcode. */
private class StubPrint(origin: String, destination: String) {
    val dots = Path()

    /** Where the arrow between the two codes starts. */
    val arrowX: Float

    init {
        arrowX = smallCode(origin, STUB_PAD)
        smallCode(destination, arrowX + 26f)
        val unit = BARCODE_WIDTH / Barcode.sum()
        var barX = WIDTH - STUB_PAD - BARCODE_WIDTH
        Barcode.forEachIndexed { n, width ->
            if (n % 2 == 0) dots.addRect(Rect(barX, 22f, barX + width * unit, 62f))
            barX += width * unit
        }
    }

    private fun smallCode(code: String, x: Float): Float {
        var left = x
        for (letter in code) {
            val rows = glyphRows(letter)
            for (row in 0 until 7) {
                for (column in 0 until 5) {
                    if (rows[row] and (16 shr column) == 0) continue
                    dots.addRect(Rect(Offset(left + SMALL_CELL * column, 22f + SMALL_CELL * row), Size(SMALL_DOT, SMALL_DOT)))
                }
            }
            left += SMALL_CELL * 5f + 2.6f
        }
        return left - 2.6f
    }
}

/** A path filled with a soft edge, for the shadows of the two pieces. Drawn in dp units. */
private class SoftPath(private val path: Path, private val color: Color, sigma: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }

    fun draw(canvas: android.graphics.Canvas, alpha: Float) {
        paint.color = color.copy(alpha = alpha).toArgb()
        canvas.drawPath(path.asAndroidPath(), paint)
    }
}

/**
 * Per-frame state: the loop's clock, when the board last flapped, and the stub chasing its target
 * pose on a spring.
 */
private class TicketSim {
    var clock = 0f
    var looping = false
    var touching = false

    /** Seconds the loop still waits after a touch. */
    var hold = 0f
    var flapAt = -10f

    /** How far the stub is pulled down, in dp, by a finger or by the loop. */
    var pull = 0f
    var detached = false
    var drop = 0f
    var dropSpeed = 0f
    var angle = 0f
    var spin = 0f

    // Written last in a frame: reading it is what redraws the ticket.
    val time = mutableFloatStateOf(0f)

    fun advance(dt: Float, calm: Boolean) {
        val now = time.floatValue + dt
        if (touching) {
            hold = USER_HOLD
        } else if (hold > 0f) {
            hold -= dt
            // Time is up: the loop starts again from rest.
            if (hold <= 0f && looping) clock = 0f
        } else if (looping) {
            var t = clock + dt
            if (t >= LOOP) t -= LOOP
            if (clock < FLAP_AT && t >= FLAP_AT) flapAt = now
            pull = scriptPull(t)
            detached = pull >= TEAR_AT
            clock = t
        }

        // Attached, the stub swings on its top left corner; torn off, it hangs below the ticket.
        val dropTarget = if (detached) DROP + (pull - TEAR_AT) * FOLLOW else 0f
        val angleTarget = if (detached) TILT else SWING * easeInOut(min(1f, pull / TEAR_AT))
        if (calm) {
            drop = dropTarget
            angle = angleTarget
        } else {
            dropSpeed += (STIFFNESS * (dropTarget - drop) - DAMPING * dropSpeed) * dt
            drop += dropSpeed * dt
            spin += (STIFFNESS * (angleTarget - angle) - DAMPING * spin) * dt
            angle += spin * dt
        }
        time.floatValue = now
    }

    /** A touch takes the stub out of the loop's hands. */
    fun takeOver() {
        hold = USER_HOLD
        pull = 0f
        detached = false
    }
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun ticketText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val FieldLabelStyle = ticketText(6.5f, 8f, FontWeight.SemiBold, rgba(255, 255, 255, 0.9f), 0.9f)
private val FieldValueStyle = ticketText(12.5f, 15f, FontWeight.Bold, Color.White)
private val AirportNameStyle = ticketText(11.5f, 14f, FontWeight.SemiBold, Color.White)
private val AirportCityStyle = ticketText(7.5f, 9f, FontWeight.Medium, rgba(255, 255, 255, 0.45f), 1.2f)
private val AirlineStyle = ticketText(9.5f, 11.5f, FontWeight.SemiBold, rgba(255, 255, 255, 0.78f), 2.2f)
private val FlightStyle = ticketText(12.5f, 15f, FontWeight.Bold, Color.White)
private val CabinChipStyle = ticketText(7.5f, 9f, FontWeight.Bold, Color.White, 1.2f)
private val StubDateStyle = ticketText(12f, 14.5f, FontWeight.Bold, StubMute)
private val StubCabinStyle = ticketText(6.5f, 8f, FontWeight.SemiBold, StubMute, 1.6f)

/**
 * A tall boarding pass: a black top that runs into a drifting aurora, two airport codes drawn in
 * square dots like a departure board, and a pale stub under a perforated line. The codes and the
 * five fields flap through other glyphs before they land, on appear and on a tap; dragging the stub
 * down tears it off along the perforation, and letting go snaps it back.
 *
 * @param autoPlay Flaps the board, tears the stub off and lets it snap back, in a loop.
 * @param date The date as the stub spells it.
 * @param onPress Called when the ticket is tapped.
 * @param onTear Called when a drag tears the stub off.
 */
@Composable
fun DepartureBoardTicket(
    autoPlay: Boolean = false,
    fields: List<TicketField> = listOf(
        TicketField("GATE", "42"),
        TicketField("SEAT", "11A"),
        TicketField("BOARDING", "22:10"),
        TicketField("DEPARTURE", "22:55"),
        TicketField("DATE", "4 Sep 26"),
    ),
    origin: TicketAirport = TicketAirport("HKG", "Hong Kong International Airport", "HONG KONG"),
    destination: TicketAirport = TicketAirport("LHR", "London Heathrow Airport", "LONDON, UNITED KINGDOM"),
    airline: String = "NORTHWIND AIR",
    flight: String = "NW 29",
    cabin: String = "ECONOMY",
    date: String = "4 Sep 2026",
    onPress: (() -> Unit)? = null,
    onTear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val calm = rememberReducedMotion()
    val auto = autoPlay && !calm
    val view = LocalView.current
    val torn by rememberUpdatedState(onTear)
    val originCode = origin.code.uppercase()
    val destinationCode = destination.code.uppercase()

    val sim = remember { TicketSim() }

    LaunchedEffect(auto) {
        sim.looping = auto
        sim.clock = 0f
        sim.hold = 0f
        // Without the loop the board flaps once as the ticket appears.
        if (!auto && !calm && sim.time.floatValue == 0f) sim.flapAt = APPEAR_FLAP_AT
    }

    LaunchedEffect(calm) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            sim.advance(min((frame - last) / 1e9f, 0.064f), calm)
            last = frame
        }
    }

    // Steps into the flap, held between -1 and FLAP_STEPS, where every glyph is the final one. Under
    // reduced motion the board never flaps.
    val step by remember(calm) {
        derivedStateOf {
            if (calm) FLAP_STEPS else floor((sim.time.floatValue - sim.flapAt) / FLAP_STEP).toInt().coerceIn(-1, FLAP_STEPS)
        }
    }

    val pieces = remember { TicketPieces() }
    val print = remember(originCode, destinationCode) { StubPrint(originCode, destinationCode) }
    val bodyShadow = remember(pieces) { SoftPath(pieces.body, Color(0xFF182048), sigma = 18f) }
    val stubShadow = remember(pieces) { SoftPath(pieces.stub, Color(0xFF182048), sigma = 18f) }
    val lit = remember { Path() }
    val dark = remember { Path() }

    // The stub turns on its top left corner, then drops.
    val stubPose = Modifier
        .offset(0.dp, TEAR_Y.dp)
        .size(WIDTH.dp, (HEIGHT - TEAR_Y).dp)
        .graphicsLayer {
            // Reading the clock is what moves the stub with every frame.
            sim.time.floatValue
            transformOrigin = TransformOrigin(0f, 0f)
            rotationZ = sim.angle
            translationY = sim.drop.dp.toPx()
        }

    Box(
        modifier.size(WIDTH.dp, HEIGHT.dp).clearAndSetSemantics {
            val details = fields.joinToString { "${it.label} ${it.value}" }
            contentDescription = "$airline $flight, ${origin.name} to ${destination.name}, $cabin, $date. $details"
        },
    ) {
        Box(Modifier.size(WIDTH.dp, TEAR_Y.dp).bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    translate(x, y + 14f) { drawIntoCanvas { bodyShadow.draw(it.nativeCanvas, 0.2f) } }
                }
            }
        }
        Box(stubPose) {
            Box(Modifier.matchParentSize().bleed(BLEED)) {
                Canvas(Modifier.fillMaxSize()) {
                    surface(BLEED) { x, y, _, _ ->
                        // Reading the clock is what redraws the shadow with every frame.
                        sim.time.floatValue
                        val lift = (sim.drop / DROP).coerceIn(0f, 1f)
                        translate(x, y + 14f + 8f * lift) { drawIntoCanvas { stubShadow.draw(it.nativeCanvas, 0.2f + 0.14f * lift) } }
                    }
                }
            }
        }

        // The ticket above the perforation: the aurora, the two codes, and the text over them.
        Box(
            Modifier
                .size(WIDTH.dp, TEAR_Y.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    sim.takeOver()
                    sim.flapAt = sim.time.floatValue
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onPress?.invoke()
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                scale(density, density, pivot = Offset.Zero) {
                    val time = sim.time.floatValue
                    val turn = if (calm) 0f else time / LOOP * TAU
                    val sinceFlap = time - sim.flapAt
                    clipPath(pieces.body) {
                        drawRect(Color.Black, size = Size(WIDTH, TEAR_Y))
                        for (light in Aurora) {
                            translate(light.driftX * sin(turn + light.phase), light.driftY * cos(turn + light.phase)) {
                                drawCircle(light.brush, light.radius, Offset(light.x, light.y))
                            }
                        }
                        // The colours wash out into the stub's pale blue just above the perforation.
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Pale.copy(alpha = 0f),
                                0.5f to Pale.copy(alpha = 0.3f),
                                1f to Pale,
                                startY = PALE_FROM,
                                endY = TEAR_Y,
                            ),
                            topLeft = Offset(0f, PALE_FROM),
                            size = Size(WIDTH, TEAR_Y - PALE_FROM),
                        )
                        lit.rewind()
                        dark.rewind()
                        addCode(lit, dark, originCode, 0, ORIGIN_TOP, step)
                        addCode(lit, dark, destinationCode, originCode.length, DESTINATION_TOP, step)
                        // The unlit dots of the board show faintly while it flaps.
                        if (!calm) drawPath(dark, Color.White.copy(alpha = 0.1f * gridGlow(sinceFlap)))
                        drawPath(lit, Color.White)
                    }
                }
            }

            Row(Modifier.offset(FIELD_INSET.dp, 14.dp).size((WIDTH - FIELD_INSET * 2f).dp, 34.dp)) {
                fields.forEachIndexed { index, field ->
                    Box(
                        Modifier.weight(4f + max(field.label.length, field.value.length)).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index > 0) {
                            Box(Modifier.align(Alignment.CenterStart).size(1.dp, 26.dp).background(rgba(255, 255, 255, 0.28f)))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(field.label, style = FieldLabelStyle, maxLines = 1)
                            Spacer(Modifier.height(5.dp))
                            BasicText(flapText(field.value, index, step), style = FieldValueStyle, maxLines = 1)
                        }
                    }
                }
            }

            for ((airport, top) in listOf(origin to ORIGIN_TOP, destination to DESTINATION_TOP)) {
                Column(
                    Modifier.offset(PAD.dp, (top + CELL * 7f + 24f).dp).width((WIDTH - PAD * 2f).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(airport.name, style = AirportNameStyle, maxLines = 1)
                    Spacer(Modifier.height(7.dp))
                    BasicText(airport.city, style = AirportCityStyle, maxLines = 1)
                }
            }

            Column(Modifier.offset(PAD.dp, 427.dp)) {
                BasicText(airline, style = AirlineStyle, maxLines = 1)
                Spacer(Modifier.height(5.dp))
                BasicText(flight, style = FlightStyle, maxLines = 1)
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset((-PAD).dp, 427.dp)
                    .height(20.dp)
                    .background(rgba(255, 255, 255, 0.24f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(cabin, style = CabinChipStyle, maxLines = 1)
            }
        }

        // The part under the perforation: the route in small dots, the date and a barcode.
        Box(stubPose) {
            Canvas(Modifier.fillMaxSize()) {
                scale(density, density, pivot = Offset.Zero) {
                    val height = HEIGHT - TEAR_Y
                    clipPath(pieces.stub) {
                        drawRect(Brush.verticalGradient(listOf(Pale, Color(0xFFA9C8F7)), startY = 0f, endY = height), size = Size(WIDTH, height))
                        // A last trace of the aurora in the bottom left corner.
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Color(0x804A5CC4), Color(0x004A5CC4)),
                                center = Offset(0f, height),
                                radius = 110f,
                            ),
                            110f,
                            Offset(0f, height),
                        )
                        drawPath(print.dots, StubInk)
                        val x = print.arrowX
                        drawLine(StubInk, Offset(x + 7f, 27.25f), Offset(x + 19f, 27.25f), 1.1f)
                        drawPath(
                            Path().apply {
                                moveTo(x + 15.5f, 24f)
                                lineTo(x + 19f, 27.25f)
                                lineTo(x + 15.5f, 30.5f)
                            },
                            StubInk,
                            style = Stroke(1.1f),
                        )
                    }
                }
            }
            Column(Modifier.offset(STUB_PAD.dp, 43.dp)) {
                BasicText(date, style = StubDateStyle, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                BasicText(cabin, style = StubCabinStyle, maxLines = 1)
            }
        }

        // Dragging the stub down swings it, then tears it off. The drag is read where the stub rests,
        // so that its own movement does not feed back into the finger's travel.
        Box(
            Modifier
                .offset(0.dp, TEAR_Y.dp)
                .size(WIDTH.dp, (HEIGHT - TEAR_Y).dp)
                .pointerInput(Unit) {
                    var dragged = 0f
                    val release = {
                        if (sim.detached) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        sim.touching = false
                        sim.pull = 0f
                        sim.detached = false
                    }
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragged = 0f
                            sim.takeOver()
                            sim.touching = true
                        },
                        onDragEnd = release,
                        onDragCancel = release,
                    ) { change, amount ->
                        change.consume()
                        dragged += amount / density
                        sim.pull = max(0f, dragged)
                        if (!sim.detached && sim.pull >= TEAR_AT) {
                            sim.detached = true
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            torn?.invoke()
                        }
                    }
                },
        )
    }
}

/**
 * Adds the dots of one airport code on the board to [lit] or [dark]. [first] is the position of its
 * first letter among all the letters on the ticket, which decides when each one starts to flap.
 */
private fun addCode(lit: Path, dark: Path, code: String, first: Int, top: Float, step: Int) {
    val pitch = CELL * 5f + LETTER_GAP
    val left = (WIDTH - (pitch * code.length - LETTER_GAP)) / 2f
    code.forEachIndexed { n, letter ->
        val rows = flapGlyph(letter, first + n, step)
        for (row in 0 until 7) {
            for (column in 0 until 5) {
                val corner = Offset(
                    left + pitch * n + CELL * column + (CELL - DOT) / 2f,
                    top + CELL * row + (CELL - DOT) / 2f,
                )
                val dot = RoundRect(Rect(corner, Size(DOT, DOT)), CornerRadius(0.9f))
                if (rows[row] and (16 shr column) != 0) lit.addRoundRect(dot) else dark.addRoundRect(dot)
            }
        }
    }
}
