package com.ngocdevv.nativecook.blocks.onboarding.flowfieldwelcome

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val Mute = Color(0xFF8E8E93)
private val Surface = Color(0xFF2C2C2E)

// The finger's pull comes on fast and lets go slowly, both on a strong ease-out.
private const val PULL_IN = 150
private const val PULL_OUT = 450

// A finger that moves less than this has tapped.
private const val SLOP = 10f

private const val MARK = 24f
private const val MARK_TOP = 7f
private const val SIDE = 24f
private const val TITLE_LINE = 56f
private const val SUBTITLE_GAP = 10f
private const val SUBTITLE_LINE = 20f
private const val BUTTONS_GAP = 43f
private const val BUTTON = 56f
private const val BUTTON_GAP = 10f
private const val LEGAL_GAP = 28f
private const val LEGAL_LINE = 16f

// The foot's height, from the title's top to the last line of the legal note.
private const val FOOT = TITLE_LINE + SUBTITLE_GAP + SUBTITLE_LINE + BUTTONS_GAP + BUTTON * 2f + BUTTON_GAP + LEGAL_GAP + LEGAL_LINE * 2f

// The field's centre sits a little above the middle of the room between the mark and the title,
// and the field shrinks when that room is less than this.
private const val FIELD_AT = 0.435f
private const val FIELD_ROOM = 310f
private const val PRESS_SCALE = 0.97f
private const val PRESS_MS = 130

// The envelope's flap, in a 20 × 20 box.
private val Flap = Path().apply {
    moveTo(3.2f, 6.6f); lineTo(10f, 11.4f); lineTo(16.8f, 6.6f)
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun welcomeText(size: Float, lineHeight: Float, weight: FontWeight, spacing: Float = 0f) = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    textAlign = TextAlign.Center,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = welcomeText(46f, TITLE_LINE, FontWeight.SemiBold, -1f)
private val SubtitleStyle = welcomeText(15f, SUBTITLE_LINE, FontWeight.Normal)
private val ButtonStyle = welcomeText(16f, 21f, FontWeight.SemiBold)
private val LegalStyle = welcomeText(11f, LEGAL_LINE, FontWeight.Normal)

/**
 * A black welcome screen whose hero is a field of 324 short white strokes. Packed, they are a
 * small shimmering square; opening, the square is torn into a pinwheel and settles as a vortex
 * of rings round a wandering eye, and closing runs the same way back. A tap on the field opens
 * or closes it, and a finger on it pushes the strokes away and turns them like filings.
 *
 * @param legal The legal note reads: [legal], [termsLabel], [legalAnd], [privacyLabel].
 * @param ink The strokes, the title and the first button.
 * @param mute The subtitle and the legal note.
 * @param surface The second button.
 * @param autoPlay Opens and closes the field by itself, twice every [LOOP] seconds.
 * @param onToggle Called when the field starts to open (true) or to close (false).
 */
@Composable
fun FlowFieldWelcome(
    title: String = "Welcome",
    subtitle: String = "Your journey starts from here",
    primaryLabel: String = "Continue with Phone",
    secondaryLabel: String = "Continue with Email",
    legal: String = "By pressing on “Continue with...” you agree\nto our ",
    termsLabel: String = "Terms of Service",
    legalAnd: String = " and ",
    privacyLabel: String = "Privacy Policy",
    background: Color = Color.Black,
    ink: Color = Color.White,
    mute: Color = Mute,
    surface: Color = Surface,
    autoPlay: Boolean = false,
    onToggle: ((open: Boolean) -> Unit)? = null,
    onPrimary: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null,
    onTerms: (() -> Unit)? = null,
    onPrivacy: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // The loop and the gesture outlive a recomposition: they read what the caller passes through these.
    val still by rememberUpdatedState(rememberReducedMotion())
    val toggled by rememberUpdatedState(onToggle)
    val terms by rememberUpdatedState(onTerms)
    val privacy by rememberUpdatedState(onPrivacy)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var open by remember { mutableStateOf(false) }
    // How far the field has opened, at a steady pace; the field eases it per stroke. Read while drawing only.
    val progress = remember { Animatable(0f) }
    // Seconds since the screen appeared. Read while drawing only.
    val clock = remember { mutableFloatStateOf(0f) }
    // The eye circles from `eyeSince` on (NaN: it stands still), starting at the angle `eyeHeld`.
    var eyeSince by remember { mutableFloatStateOf(Float.NaN) }
    var eyeHeld by remember { mutableFloatStateOf(EYE_FROM) }
    // The touch, from the field's centre in the field's own dp, and how strongly it pulls.
    var finger by remember { mutableStateOf(Offset.Zero) }
    val pull = remember { Animatable(0f) }
    // A finger is on the field: the loop waits.
    var holding by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    fun eyeAt(now: Float) = eyeHeld + if (eyeSince.isNaN()) 0f else EYE_SPEED * (now - eyeSince)

    /** Opens the field into the vortex, or packs it again, from wherever it is. */
    fun morph(next: Boolean) {
        val now = clock.floatValue
        val at = progress.value
        if (next) {
            // From the packed field the eye starts where it always starts.
            if (at == 0f) eyeHeld = EYE_FROM
            eyeSince = now
        } else {
            eyeHeld = eyeAt(now)
            eyeSince = Float.NaN
        }
        open = next
        toggled?.invoke(next)
        val to = if (next) 1f else 0f
        val seconds = if (still) FADE else MORPH * abs(to - at)
        scope.launch { progress.animateTo(to, tween((seconds * 1000f).toInt(), easing = LinearEasing)) }
    }

    LaunchedEffect(still) {
        if (still) {
            clock.floatValue = 0f
            return@LaunchedEffect
        }
        val origin = withFrameNanos { it }
        while (true) clock.floatValue = (withFrameNanos { it } - origin) / 1e9f
    }

    LaunchedEffect(autoPlay, touches, holding) {
        if (!autoPlay || holding) return@LaunchedEffect
        if (touches > 0) {
            // A touch takes over; a while later the field packs again and the loop starts over.
            delay((USER_HOLD * 1000).toLong())
            if (open) {
                morph(false)
                delay((MORPH * 1000).toLong())
            }
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            until(OPEN_AT)
            morph(true)
            until(CLOSE_AT)
            morph(false)
            until(CYCLE)
            origin += (CYCLE * 1e9f).toLong()
        }
    }

    // The legal note with its two document names as underlined links.
    val note = remember(legal, termsLabel, legalAnd, privacyLabel, ink) {
        val linked = TextLinkStyles(SpanStyle(color = ink, textDecoration = TextDecoration.Underline))
        buildAnnotatedString {
            append(legal)
            withLink(LinkAnnotation.Clickable("terms", linked) { terms?.invoke() }) { append(termsLabel) }
            append(legalAnd)
            withLink(LinkAnnotation.Clickable("privacy", linked) { privacy?.invoke() }) { append(privacyLabel) }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(background)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val markBottom = top + MARK_TOP + MARK
        val bottom = max(insets.calculateBottomPadding().value, 20f)
        val room = maxHeight.value - bottom - FOOT - markBottom
        val fieldScale = min(1f, room / FIELD_ROOM)
        val fieldSize = FIELD * fieldScale

        // The round mark at the top: a disc with a head and shoulders cut out of it.
        Canvas(Modifier.align(Alignment.TopCenter).offset(y = (top + MARK_TOP).dp).size(MARK.dp)) {
            scale(size.width / MARK, pivot = Offset.Zero) {
                drawCircle(ink, 12f, Offset(12f, 12f))
                drawCircle(background, 3.1f, Offset(12f, 9.4f))
                drawOval(background, Offset(5.8f, 15.2f), Size(12.4f, 9f))
            }
        }

        Canvas(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (markBottom + room * FIELD_AT - fieldSize / 2f).dp)
                .size(fieldSize.dp)
                .pointerInput(Unit) {
                    // The touch in the field's own dp, from its centre.
                    fun toField(at: Offset) = (at - Offset(size.width / 2f, size.height / 2f)) * (FIELD / size.width)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var moved = 0f
                        try {
                            holding = true
                            finger = toField(down.position)
                            scope.launch { pull.animateTo(1f, tween(PULL_IN, easing = EaseOutStrong)) }
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                finger = toField(change.position)
                                moved = max(moved, (change.position - down.position).getDistance())
                                change.consume()
                            }
                            // A touch that did not move is a tap.
                            if (moved < SLOP.dp.toPx()) {
                                tap()
                                morph(!open)
                            }
                        } finally {
                            holding = false
                            touches++
                            scope.launch { pull.animateTo(0f, tween(PULL_OUT, easing = EaseOutStrong)) }
                        }
                    }
                }
                .clearAndSetSemantics {
                    contentDescription = "Pattern"
                    stateDescription = if (open) "Open" else "Packed"
                    role = Role.Button
                    onClick {
                        morph(!open)
                        true
                    }
                },
        ) {
            scale(size.width / FIELD, pivot = Offset.Zero) {
                if (still) {
                    // Reduced motion: the packed and the open figure cross-fade, and nothing twitches.
                    drawField(0f, true, 0f, EYE_FROM, 0f, 0f, 0f, ink, alpha = 1f - progress.value)
                    drawField(1f, true, 0f, EYE_FROM, 0f, 0f, 0f, ink, alpha = progress.value)
                } else {
                    drawField(progress.value, open, clock.floatValue, eyeAt(clock.floatValue), finger.x, finger.y, pull.value, ink)
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = SIDE.dp, end = SIDE.dp, bottom = bottom.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(title, style = TitleStyle.copy(color = ink), maxLines = 1, softWrap = false, modifier = Modifier.height(TITLE_LINE.dp))
            Spacer(Modifier.height(SUBTITLE_GAP.dp))
            BasicText(subtitle, style = SubtitleStyle.copy(color = mute), maxLines = 1)
            Spacer(Modifier.height(BUTTONS_GAP.dp))
            Column(verticalArrangement = Arrangement.spacedBy(BUTTON_GAP.dp)) {
                Pill(primaryLabel, color = ink, ink = background, envelope = false) {
                    tap()
                    onPrimary?.invoke()
                }
                Pill(secondaryLabel, color = surface, ink = ink, envelope = true) {
                    tap()
                    onSecondary?.invoke()
                }
            }
            Spacer(Modifier.height(LEGAL_GAP.dp))
            BasicText(note, style = LegalStyle.copy(color = mute), modifier = Modifier.heightIn(min = (LEGAL_LINE * 2f).dp))
        }
    }
}

@Composable
private fun Pill(label: String, color: Color, ink: Color, envelope: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) PRESS_SCALE else 1f, tween(PRESS_MS), label = "press")
    Row(
        Modifier
            .fillMaxWidth()
            .height(BUTTON.dp)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .background(color, CircleShape)
            .clip(CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (envelope) {
            Canvas(Modifier.size(20.dp)) {
                scale(size.width / 20f, pivot = Offset.Zero) {
                    drawRoundRect(ink, Offset(2f, 4.5f), Size(16f, 11f), CornerRadius(2.5f), style = Stroke(1.6f))
                    drawPath(Flap, ink, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        BasicText(label, style = ButtonStyle.copy(color = ink), maxLines = 1)
    }
}
