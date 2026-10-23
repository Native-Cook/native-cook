package com.ngocdevv.nativecook.blocks.modals.leveluptakeover

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val Cream = Color(0xFFFFF0B8)
private val Edge = Color(0xFFFFE08A)
private val Ink = Color(0xFF1A1A2E)
private val Mute = Color(0xFF8A7A50)
private val Orange = Color(0xFFFF6A1A)

private const val GLOW = 420f
private const val DOT = 12f
private const val COIN = 34f
private const val CTA_HEIGHT = 56f
private const val SHARE_HEIGHT = 56f
private const val MARGIN = 24f

/** The sunburst behind the badge: rays, its size, and how fast it turns (radians a second). */
private const val RAYS = 14
private const val RAY_SIZE = 460f
private const val RAY_TURN = 0.12f

/** Wedges from the centre of the sunburst, alternating with gaps. */
private val RayPath = Path().apply {
    val c = RAY_SIZE / 2f
    val half = PI.toFloat() / RAYS / 1.6f
    for (i in 0 until RAYS) {
        val a = i.toFloat() / RAYS * TAU
        moveTo(c, c)
        lineTo(c + cos(a - half) * c, c + sin(a - half) * c)
        lineTo(c + cos(a + half) * c, c + sin(a + half) * c)
        close()
    }
}

private val Coin = listOf(Color(0xFFFFE066), Color(0xFFFFB000))

/** The dot's glow: React Native's `0 0 14px 4px`, a sigma of 7 round a shape 4 dp larger. */
private val DotGlow = SoftRoundRect(Color(255, 184, 0).copy(alpha = 0.7f), 7f)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(size: Float, lineHeight: Float, weight: FontWeight, letterSpacing: Float = 0f) = TextStyle(
    fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight, letterSpacing = letterSpacing.sp, lineHeightStyle = Centered,
)

/** React Native's box shadow: a blur radius and a downward offset, in dp. */
private fun Modifier.boxShadow(shape: Shape, color: Color, blur: Float, y: Float) =
    dropShadow(shape, Shadow(radius = blur.dp, color = color, offset = DpOffset(0.dp, y.dp)))

private fun grouped(value: Int): String {
    if (value < 1000) return value.toString()
    val rest = value % 1000
    return "${value / 1000},${if (rest < 10) "00" else if (rest < 100) "0" else ""}$rest"
}

/**
 * A level-up screen: a gold badge bursts out of a dot with confetti, the heading and two counted
 * figures follow, and Continue slides up. After a pause the whole screen fades back to the dot.
 * One clock drives all of it, so a touch and the autoPlay loop run the same motion.
 *
 * @param points the figures counted up under the heading, with [xp].
 * @param subtitle the line under the heading.
 * @param shareReward the coin chip next to the share link.
 * @param autoPlay plays by itself every [LOOP] seconds and ends where it began.
 * @param onContinue called when Continue is pressed by a touch.
 */
@Composable
fun LevelUpTakeover(
    modifier: Modifier = Modifier,
    level: Int = 3,
    points: Int = 596,
    xp: Int = 1750,
    title: String = "LEVEL UP!",
    subtitle: String = "You reached level 3. Keep the streak going!",
    pointsLabel: String = "Coins earned",
    xpLabel: String = "XP earned",
    continueLabel: String = "Continue",
    shareLabel: String = "Share the News",
    shareReward: String = "+10",
    autoPlay: Boolean = false,
    onContinue: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val clock = rememberLoopClock(autoPlay)
    val t = { clock.seconds.value }
    val insetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
    val insetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Edge)
            .semantics { contentDescription = title }
            .clickable(interactionSource = null, indication = null) { clock.touch(TAP_AT, false) },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val badgeY = max(insetTop + 150f, height * 0.32f)
        val titleTop = badgeY + BADGE_HEIGHT / 2f + 26f
        val statsTop = titleTop + 100f
        val linkBottom = insetBottom + 12f
        val ctaBottom = linkBottom + SHARE_HEIGHT + 12f

        // The haze behind everything.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(Cream, Edge),
                    center = Offset(width / 2f * density, badgeY * density),
                    radius = max(width, height) * 0.75f * density,
                ),
            )
        }

        // The dot the badge bursts out of.
        Canvas(Modifier.fillMaxSize()) {
            val now = t()
            val gone = seg(now, BURST_AT, BURST_AT + DOT_OUT_IN)
            val back = seg(now, DOT_BACK_AT, DOT_BACK_AT + DOT_BACK_IN)
            val alpha = if (now < DOT_BACK_AT) 1f - gone else back
            val pulse = 1f + 0.15f * sin(now * 4f) * (1f - gone)
            if (alpha <= 0f) return@Canvas
            scale(density, density, pivot = Offset.Zero) {
                scale(pulse, pivot = Offset(width / 2f, badgeY)) {
                    drawIntoCanvas {
                        DotGlow.draw(it.nativeCanvas, width / 2f - DOT / 2f - 4f, badgeY - DOT / 2f - 4f, DOT + 8f, DOT + 8f, DOT / 2f + 4f, alpha)
                    }
                    drawCircle(Color(0xFFFFD84A), DOT / 2f, Offset(width / 2f, badgeY), alpha = alpha)
                }
            }
        }

        // Everything but the dot fades and shrinks a little at the end.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val out = seg(t(), OUT_AT, OUT_AT + OUT_IN)
                    alpha = 1f - out
                    scaleX = if (reduced) 1f else 1f - (1f - OUT_SCALE) * out
                    scaleY = scaleX
                },
        ) {
            // A resting haze, and a pulse that spreads out of it once.
            Canvas(
                Modifier
                    .offset(x = (width / 2f - GLOW / 2f).dp, y = (badgeY - GLOW / 2f).dp)
                    .size(GLOW.dp)
                    .graphicsLayer {
                        val p = seg(t(), GLOW_AT, GLOW_AT + GLOW_FOR)
                        alpha = if (reduced) 0.4f else (0.4f + 0.6f * sin(PI.toFloat() * p) * (1f - p) * 2f).coerceIn(0f, 1f)
                        scaleX = if (reduced) 1f else 1f + 0.3f * easeOut(p)
                        scaleY = scaleX
                    },
            ) {
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)), center = center, radius = size.width / 2f),
                    size.width / 2f,
                )
            }

            // The sunburst: it turns slowly while it shows and holds still otherwise.
            Canvas(
                Modifier
                    .offset(x = (width / 2f - RAY_SIZE / 2f).dp, y = (badgeY - RAY_SIZE / 2f).dp)
                    .size(RAY_SIZE.dp)
                    .graphicsLayer {
                        val now = t()
                        val p = if (reduced) seg(now, BURST_AT, BURST_AT + REDUCED_FADE) else easeOut(seg(now, BURST_AT, BURST_AT + 0.6f))
                        alpha = p
                        scaleX = if (reduced) 1f else 0.6f + 0.4f * p
                        scaleY = scaleX
                    },
            ) {
                val now = min(max(t(), BURST_AT), OUT_AT + OUT_IN)
                val c = RAY_SIZE / 2f
                scale(density, density, pivot = Offset.Zero) {
                    rotate(if (reduced) 0f else (now - BURST_AT) * RAY_TURN * 180f / PI.toFloat(), Offset(c, c)) {
                        drawPath(
                            RayPath,
                            Brush.radialGradient(
                                0f to Color.White.copy(alpha = 0.85f), 0.45f to Color.White.copy(alpha = 0.35f), 1f to Color.White.copy(alpha = 0f),
                                center = Offset(c, c), radius = c,
                            ),
                        )
                    }
                }
            }

            Badge(
                level,
                sweep = { seg(t(), SHINE_AT, SHINE_AT + SHINE_FOR) },
                // The sparkles blink on after the burst and twice more while the screen holds.
                twinkle = {
                    val now = t()
                    if (now < BURST_AT + 0.3f) 0f else 0.35f + 0.65f * abs(sin((now - BURST_AT - 0.3f) * 1.6f))
                },
                modifier = Modifier
                    .offset(x = ((width - BADGE_BOX_WIDTH) / 2f).dp, y = (badgeY - BADGE_BOX_HEIGHT / 2f).dp)
                    .graphicsLayer {
                        val now = t()
                        if (reduced) {
                            alpha = seg(now, BURST_AT, BURST_AT + REDUCED_FADE)
                        } else {
                            val pop = if (now < BURST_AT) 0f else BADGE_FROM + (1f - BADGE_FROM) * spring(now - BURST_AT, BADGE_STIFFNESS, BADGE_DAMPING)
                            alpha = if (now < BURST_AT) 0f else 1f
                            scaleX = pop
                            scaleY = pop
                        }
                    },
            )

            Text(
                title,
                Modifier
                    .offset(y = titleTop.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = easeOut(seg(t(), TITLE_AT, TITLE_AT + TITLE_IN))
                        alpha = p
                        translationY = (if (reduced) 0f else (1f - p) * SLIDE) * density
                    },
                color = Ink, style = style(36f, 42f, FontWeight.Black, 0.5f), textAlign = TextAlign.Center,
            )
            Text(
                subtitle,
                Modifier
                    .offset(y = (titleTop + 50f).dp)
                    .padding(horizontal = 40.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = easeOut(seg(t(), TITLE_AT + 0.08f, TITLE_AT + 0.08f + TITLE_IN))
                        alpha = p
                        translationY = (if (reduced) 0f else (1f - p) * SLIDE) * density
                    },
                color = Color(0xFF6F6140), style = style(16f, 22f, FontWeight.Medium), textAlign = TextAlign.Center,
            )

            // The two figures on a frosted card.
            Row(
                Modifier
                    .offset(y = statsTop.dp)
                    .padding(horizontal = MARGIN.dp)
                    .fillMaxWidth()
                    .height(128.dp)
                    .graphicsLayer {
                        val p = easeOut(seg(t(), STATS_AT - 0.1f, STATS_AT + 0.3f))
                        alpha = p
                        translationY = (if (reduced) 0f else (1f - p) * SLIDE) * density
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .boxShadow(RoundedCornerShape(24.dp), Color(196, 140, 20).copy(alpha = 0.16f), 30f, 12f)
                    .boxShadow(RoundedCornerShape(24.dp), Color(150, 100, 0).copy(alpha = 0.1f), 3f, 1f)
                    .background(Color.White.copy(alpha = 0.62f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(24.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stat(points, STATS_AT, pointsLabel, false, t, reduced, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(72.dp).background(Color(138, 122, 80).copy(alpha = 0.2f)))
                Stat(xp, STATS_AT + STATS_STAGGER, xpLabel, true, t, reduced, Modifier.weight(1f))
            }

            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = MARGIN.dp, end = MARGIN.dp, bottom = ctaBottom.dp)
                    .fillMaxWidth()
                    .height(CTA_HEIGHT.dp)
                    .graphicsLayer {
                        val now = t()
                        val into = if (reduced) seg(now, CTA_AT, CTA_AT + REDUCED_FADE) else min(1f, spring(now - CTA_AT, CTA_STIFFNESS, CTA_DAMPING))
                        val press = seg(now, PRESS_AT, PRESS_AT + PRESS_DOWN) - seg(now, PRESS_AT + PRESS_DOWN, PRESS_AT + PRESS_DOWN + PRESS_UP)
                        alpha = min(1f, into * 3f)
                        translationY = (if (reduced) 0f else (1f - into) * CTA_SLIDE) * density
                        scaleX = 1f - (1f - PRESS_SCALE) * press
                        scaleY = scaleX
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .boxShadow(CircleShape, Color(255, 106, 26).copy(alpha = 0.38f), 24f, 12f)
                    .boxShadow(CircleShape, Color(200, 70, 0).copy(alpha = 0.25f), 4f, 2f)
                    .background(Brush.verticalGradient(0f to Color(0xFFFF8A3D), 0.55f to Orange, 1f to Color(0xFFF45400)), CircleShape)
                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        // A press on Continue goes straight to the end of the screen.
                        clock.touch(PRESS_AT, true)
                        onContinue?.invoke()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(continueLabel, color = Color.White, style = style(18f, 22f, FontWeight.ExtraBold, -0.2f))
            }

            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = MARGIN.dp, end = MARGIN.dp, bottom = linkBottom.dp)
                    .fillMaxWidth()
                    .height(SHARE_HEIGHT.dp)
                    .graphicsLayer {
                        val p = easeOut(seg(t(), LINK_AT, LINK_AT + LINK_IN))
                        alpha = p
                        translationY = (if (reduced) 0f else (1f - p) * 12f) * density
                    }
                    .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(18.dp))
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { onShare?.invoke() }
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.IosShare, null, Modifier.size(20.dp), tint = Ink)
                Text(shareLabel, Modifier.weight(1f), color = Ink, style = style(16f, 20f, FontWeight.Bold))
                Row(
                    Modifier
                        .height(30.dp)
                        .background(Color(0xFFFFF7D6), CircleShape)
                        .border(1.dp, Color(0xFFFFE28A), CircleShape)
                        .padding(start = 5.dp, end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(20.dp).background(Brush.verticalGradient(Coin), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Star, null, Modifier.size(11.dp), tint = Color.White)
                    }
                    Text(shareReward, color = Ink, style = style(14f, 18f, FontWeight.ExtraBold))
                }
            }
        }

        if (!reduced) {
            val origin = Offset(width / 2f, badgeY)
            Confetti(clock, BURST_AT, origin, Modifier.fillMaxSize())
            Confetti(clock, SECOND_BURST_AT, origin, Modifier.fillMaxSize(), SECOND_BURST_COUNT)
        }
    }
}

/** A figure counted up from 0 under a coin or an XP mark, with a caption under it. */
@Composable
private fun Stat(to: Int, at: Float, caption: String, xp: Boolean, t: () -> Float, reduced: Boolean, modifier: Modifier) {
    // Recomposes only when the shown integer changes.
    val shown by remember(to, at, reduced) {
        derivedStateOf {
            val now = t()
            (to * (if (reduced) seg(now, at, at + REDUCED_FADE) else easeOut(seg(now, at, at + STATS_COUNT)))).roundToInt()
        }
    }
    val icon: ImageVector = if (xp) Icons.Filled.Bolt else Icons.Filled.Star
    Column(
        modifier.graphicsLayer {
            val p = easeOut(seg(t(), at, at + 0.35f))
            alpha = p
            translationY = (if (reduced) 0f else (1f - p) * 10f) * density
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(bottom = 8.dp)
                .size(COIN.dp)
                .boxShadow(CircleShape, if (xp) Color(40, 100, 255).copy(alpha = 0.3f) else Color(230, 150, 0).copy(alpha = 0.35f), 8f, 3f)
                .background(Brush.verticalGradient(if (xp) listOf(Color(0xFF5AA2FF), Color(0xFF2A62FF)) else Coin), CircleShape)
                .border(2.dp, if (xp) Color(0xFF7AB4FF) else Color(0xFFFFD23A), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(if (xp) 19.dp else 18.dp), tint = Color.White)
        }
        Text(
            "+${grouped(shown)}",
            color = Ink, style = style(26f, 30f, FontWeight.ExtraBold, -0.4f).copy(fontFeatureSettings = "tnum"),
        )
        Text(caption, Modifier.padding(top = 2.dp), color = Mute, style = style(13f, 16f, FontWeight.SemiBold))
    }
}
