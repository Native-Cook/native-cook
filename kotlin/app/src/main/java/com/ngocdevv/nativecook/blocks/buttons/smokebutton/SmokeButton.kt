package com.ngocdevv.nativecook.blocks.buttons.smokebutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberBloom
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch

private val HEIGHT = 60.dp
private val PAD_X = 42.dp

// The canvas overflows the button by this much on every side to fit the halo behind it.
private val BLEED = 110.dp

// Where the smoke is frozen when the system asks for reduced motion.
private const val STILL_TIME = 4f

private val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 17.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.2).sp,
)

// Domain-warped fbm smoke that thickens towards the right, plus a few drifting dust motes.
// `boost` (0..1, driven by press) lets the smoke spill across the whole capsule.
private const val SMOKE_AGSL = """
uniform vec2 origin;
uniform vec2 size;
uniform float time;
uniform float boost;

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(
    mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
    mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
    u.y
  );
}

float fbm(vec2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    v += a * noise(p);
    p = vec2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 2.03 + 11.7;
    a *= 0.46;
  }
  return v;
}

vec4 main(vec2 xy) {
  vec2 l = xy - origin;
  vec2 n = l / size;
  vec2 p = l / size.y;
  float t = time;

  vec2 q = vec2(
    fbm(p * 1.3 + vec2(-0.10 * t, 0.04 * t)),
    fbm(p * 1.3 + vec2(4.1 + 0.07 * t, 2.7 - 0.09 * t))
  );
  float f = fbm(p * 1.7 + 2.4 * q + vec2(-0.16 * t, 0.0));
  float wisps = smoothstep(0.3, 0.8, f);
  wisps = wisps * (0.4 + 1.1 * q.x * q.x);

  float mask = smoothstep(mix(0.14, -0.6, boost), 0.96, n.x);
  float haze = 0.26 * mask * q.y;
  float smoke = wisps * mask * (0.35 + 0.65 * mask) * (1.7 + 0.5 * boost) + haze;
  smoke = min(smoke, 0.9);

  float dust = 0.0;
  for (int i = 0; i < 9; i++) {
    float fi = float(i);
    vec2 base = vec2(hash(vec2(fi, 1.3)), hash(vec2(fi, 7.9)));
    base = vec2(0.2 + 0.74 * base.x, 0.16 + 0.68 * base.y);
    vec2 drift = vec2(sin(t * 0.31 + fi * 2.1) * 0.03, cos(t * 0.23 + fi * 1.3) * 0.07);
    float d = distance(l, (base + drift) * size);
    float twinkle = 0.5 + 0.5 * sin(t * 1.1 + fi * 4.0);
    dust += (0.25 + 0.75 * twinkle) * (smoothstep(1.4, 0.5, d) + 0.22 * smoothstep(4.5, 0.0, d));
  }

  float v = clamp(smoke + dust, 0.0, 1.0);
  return vec4(v, v, v, v);
}
"""

/** The smoke fill: the AGSL shader on API 33+, a still haze below that. */
private class SmokeFill {
    private val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(SMOKE_AGSL) else null
    private val brush = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) shader?.let { ShaderBrush(it) } else null
    val clip = Path()
    private var w = Float.NaN
    private var h = Float.NaN

    fun DrawScope.draw(x: Float, y: Float, w: Float, h: Float, time: Float, boost: Float) {
        if (w != this@SmokeFill.w || h != this@SmokeFill.h) {
            this@SmokeFill.w = w
            this@SmokeFill.h = h
            clip.reset()
            clip.addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(h / 2)))
        }
        clipPath(clip) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                shader.setFloatUniform("origin", x, y)
                shader.setFloatUniform("size", w, h)
                shader.setFloatUniform("time", time)
                shader.setFloatUniform("boost", boost)
                drawRect(brush, Offset(x, y), Size(w, h))
            } else {
                val start = x + w * (0.14f - 0.74f * boost)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to rgba(255, 255, 255, 0f),
                        1f to rgba(255, 255, 255, 0.3f + 0.1f * boost),
                        startX = start,
                        endX = x + w,
                    ),
                    topLeft = Offset(x, y),
                    size = Size(w, h),
                )
            }
        }
    }
}

/**
 * "Get Started" button: a glass capsule with smoke drifting inside it, dust motes, a rim lit
 * from the right and a glowing label. Pressing it lets the smoke flood the capsule and lights
 * the whole rim.
 */
@Composable
fun SmokeButton(
    label: String = "Get Started",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }

    val time = remember { mutableFloatStateOf(STILL_TIME) }
    val boost = remember { Animatable(0f) }

    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    launch { boost.animateTo(1f, tween(380, easing = EaseOutStrong)) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressed = false
                    launch { boost.animateTo(0f, tween(1100, easing = EaseOutStrong)) }
                }
            }
        }
    }

    // Integrating time (rather than reading a clock) lets a press speed the smoke up without a jump.
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - last) / 1e6f, 64f) / 1000f
            time.floatValue += dt * (1f + 2.4f * boost.value)
            last = now
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    val smoke = remember { SmokeFill() }
    val rimBloom = rememberBloom(4.dp)
    val glowRadius = with(LocalDensity.current) { 9.dp.toPx() }
    val glowStyle = remember(glowRadius) {
        LabelStyle.copy(shadow = Shadow(color = rgba(255, 255, 255, 0.95f), blurRadius = glowRadius))
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    val r = h / 2
                    val cy = y + h / 2
                    val t = time.floatValue
                    val b = boost.value

                    val haloCenter = Offset(x + w * 0.6f, cy)
                    val haloRadius = w * 0.92f
                    scale(1f, (h * 1.7f) / haloRadius, pivot = haloCenter) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to rgba(255, 255, 255, 0.2f),
                                0.45f to rgba(255, 255, 255, 0.07f),
                                1f to rgba(255, 255, 255, 0f),
                                center = haloCenter,
                                radius = haloRadius,
                            ),
                            radius = haloRadius,
                            center = haloCenter,
                            alpha = (0.78f + 0.22f * sin(t * 0.8f) + 0.6f * b).coerceIn(0f, 1f),
                        )
                    }

                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF060606), Color(0xFF141414)),
                            startX = x,
                            endX = x + w,
                        ),
                        topLeft = Offset(x, y),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(r),
                    )
                    with(smoke) { draw(x, y, w, h, t, b) }
                    drawRoundRect(
                        color = rgba(255, 255, 255, 0.05f),
                        topLeft = Offset(x + 0.5f, y + 0.5f),
                        size = Size(w - 1, h - 1),
                        cornerRadius = CornerRadius(r - 0.5f),
                        style = Stroke(1f),
                    )
                }
            }
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    renderEffect = rimBloom
                    alpha = if (rimBloom == null) 0f else 0.7f
                },
            ) {
                surface(BLEED) { x, y, w, h ->
                    drawRim(x, y, w, h, inset = 0f, strokeWidth = 4f, alpha = 1f, time.floatValue, boost.value)
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    drawRim(x, y, w, h, inset = 0.9f, strokeWidth = 1.8f, alpha = 1f, time.floatValue, boost.value)
                    drawRim(x, y, w, h, inset = 4f, strokeWidth = 1f, alpha = 0.3f, time.floatValue, boost.value)
                }
            }
        }
        Box(Modifier.height(HEIGHT).padding(horizontal = PAD_X).wrapContentHeight()) {
            BasicText(text = label, style = LabelStyle, maxLines = 1)
            BasicText(
                text = label,
                style = glowStyle,
                maxLines = 1,
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .graphicsLayer {
                        // Modulating instead of compositing offscreen keeps the shadow from being
                        // clipped to the text box.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = min(1f, 0.6f + 0.2f * sin(time.floatValue * 0.8f) + 0.4f * boost.value)
                    },
            )
        }
    }
}

// The lit stretch of the rim sways a little, as if the light source were drifting; a press
// pushes its start past the left cap so the whole rim lights up.
private fun DrawScope.drawRim(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    inset: Float,
    strokeWidth: Float,
    alpha: Float,
    time: Float,
    boost: Float,
) {
    val start = x + w * (0.2f + 0.09f * sin(time * 0.55f) - 1.1f * boost)
    drawRoundRect(
        brush = Brush.horizontalGradient(
            0f to rgba(255, 255, 255, 0f),
            0.5f to rgba(255, 255, 255, 0.5f),
            1f to Color.White,
            startX = start,
            endX = x + w * 0.8f,
        ),
        topLeft = Offset(x + inset, y + inset),
        size = Size(w - inset * 2, h - inset * 2),
        cornerRadius = CornerRadius(h / 2 - inset),
        style = Stroke(strokeWidth),
        alpha = alpha,
    )
}
