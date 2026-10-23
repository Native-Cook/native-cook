package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.sqrt

/** API 33 AGSL lens with a material-preserving Canvas fallback for older devices. */
internal class GlassDiscOnboardingShader {
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val transparentTexture = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var runtimeShader: RuntimeShader? = null

    fun draw(
        canvas: Canvas,
        frame: GlassDiscOnboardingFrame,
        texture: Bitmap?,
        lightX: Float,
        lightY: Float,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            drawAgsl(canvas, frame, texture, lightX, lightY)
            return
        }
        drawFallback(canvas, frame, texture)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun drawAgsl(
        canvas: Canvas,
        frame: GlassDiscOnboardingFrame,
        texture: Bitmap?,
        lightX: Float,
        lightY: Float,
    ) {
        val shader = runtimeShader ?: RuntimeShader(LensAgsl).also { runtimeShader = it }
        val image = texture ?: transparentTexture
        val imageShader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val radius = max(0.5f, frame.discRadius)
        val pointScale = frame.density
        val lens = GlassDiscOnboardingTheme.LAYOUT.lens
        // Works for the reference's capped 2x texture and for a full physical-pixel texture.
        val textureScale = image.width.toFloat() / max(1f, canvas.width.toFloat())
        val textureOriginX = if (frame.textureMode < 0.5f) {
            canvas.width / 2f - lens.tx * radius
        } else {
            frame.discX
        }
        val textureOriginY = if (frame.textureMode < 0.5f) {
            canvas.height * 1.5f - lens.ty * radius
        } else {
            frame.discY
        }
        shader.setInputShader("tex", imageShader)
        shader.setFloatUniform("size", canvas.width.toFloat(), canvas.height.toFloat())
        shader.setFloatUniform("texScale", textureScale)
        shader.setFloatUniform("center", frame.discX, frame.discY)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("stretch", frame.stretch)
        shader.setFloatUniform("glint", frame.glint)
        shader.setFloatUniform("glintAng", frame.glintAngle)
        shader.setFloatUniform("light", lightX.coerceIn(-1f, 1f), lightY.coerceIn(-1f, 1f))
        shader.setFloatUniform("glow", GlassDiscOnboardingMotion.UPGRADE.glow)
        shader.setFloatUniform("spreadBoost", frame.spreadBoost)
        shader.setFloatUniform("pointScale", pointScale)
        shader.setFloatUniform("alpha", 1f)
        shader.setFloatUniform("texOrigin", textureOriginX, textureOriginY)
        shader.setFloatUniform("reveal", frame.reveal)
        shader.setFloatUniform("fold", lens.fold)
        shader.setFloatUniform("foldBack", lens.foldBack)
        shader.setFloatUniform("foldShape", lens.foldShape)
        shader.setFloatUniform("kY", lens.kY)
        shader.setFloatUniform("kX", lens.kX)
        shader.setFloatUniform("squeezeX", lens.squeezeX)
        shader.setFloatUniform("spread", lens.spread * pointScale)
        shader.setFloatUniform("fringeFrom", lens.fringeFrom)
        shader.setFloatUniform("fade", lens.fade)
        shader.setFloatUniform("mirrorFloor", lens.mirrorFloor)
        shader.setFloatUniform("ring", frame.ring)
        shader.setFloatUniform("ringFrom", GlassDiscOnboardingTheme.LAYOUT.ringFrom)
        shader.setFloatUniform("ringTo", GlassDiscOnboardingTheme.LAYOUT.ringTo)
        shader.setFloatUniform("rimLight", GlassDiscOnboardingTheme.LAYOUT.rimLight)
        shader.setFloatUniform("rimWidth", GlassDiscOnboardingTheme.LAYOUT.rimWidth * pointScale)
        shader.setFloatUniform("shadowAt", GlassDiscOnboardingTheme.LAYOUT.shadowAt * pointScale)
        shader.setFloatUniform("shadowWidth", GlassDiscOnboardingTheme.LAYOUT.shadowWidth * pointScale)
        shader.setFloatUniform("shadowAlpha", GlassDiscOnboardingTheme.LAYOUT.shadowAlpha)
        shader.setFloatUniform("beadFrom", GlassDiscOnboardingTheme.LAYOUT.bead.from * pointScale)
        shader.setFloatUniform("beadSpan", GlassDiscOnboardingTheme.LAYOUT.bead.span * pointScale)
        shader.setFloatUniform("beadFrost", GlassDiscOnboardingTheme.LAYOUT.bead.frost)
        shader.setFloatUniform("domeFrost", GlassDiscOnboardingTheme.LAYOUT.domeFrost * frame.introAlpha)
        shader.setColorUniform("bgTL", BackgroundTopLeft)
        shader.setColorUniform("bgTR", BackgroundTopRight)
        shader.setColorUniform("bgBL", BackgroundBottomLeft)
        shader.setColorUniform("bgBR", BackgroundBottomRight)
        shader.setColorUniform("shadowColor", ShadowColor)
        shader.setColorUniform("ringInner", RingInner)
        shader.setColorUniform("ringCore", RingCore)
        shader.setColorUniform("ringOuter", RingOuter)
        shader.setColorUniform("ringBlush", RingBlush)
        fallbackPaint.shader = shader
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), fallbackPaint)
        fallbackPaint.shader = null
    }

    /** API 26-32 keeps the same silhouette, frost, ring, shadow, texture and glint without refraction. */
    private fun drawFallback(canvas: Canvas, frame: GlassDiscOnboardingFrame, texture: Bitmap?) {
        val radius = max(0.5f, frame.discRadius)
        val pointScale = frame.density
        val stretchRoot = sqrt(1f + frame.stretch)
        val horizontalRadius = radius / stretchRoot
        val verticalRadius = radius * stretchRoot
        val bounds = RectF(
            frame.discX - horizontalRadius,
            frame.discY - verticalRadius,
            frame.discX + horizontalRadius,
            frame.discY + verticalRadius,
        )
        fallbackPaint.style = Paint.Style.FILL
        fallbackPaint.color = ShadowColor
        fallbackPaint.alpha = (GlassDiscOnboardingTheme.LAYOUT.shadowAlpha * 255f * 1.6f).toInt()
        canvas.drawOval(
            RectF(
                bounds.left - GlassDiscOnboardingTheme.LAYOUT.shadowWidth * pointScale,
                bounds.top + GlassDiscOnboardingTheme.LAYOUT.shadowAt * pointScale,
                bounds.right + GlassDiscOnboardingTheme.LAYOUT.shadowWidth * pointScale,
                bounds.bottom + (GlassDiscOnboardingTheme.LAYOUT.shadowAt + GlassDiscOnboardingTheme.LAYOUT.shadowWidth) * pointScale,
            ),
            fallbackPaint,
        )
        val saveCount = canvas.save()
        val clipPath = Path().apply { addOval(bounds, Path.Direction.CW) }
        canvas.clipPath(clipPath)
        if (texture != null && frame.reveal > 0.001f) {
            fallbackPaint.alpha = (frame.reveal * 255f).toInt()
            val source = Rect(0, 0, texture.width, texture.height)
            val destination = RectF(0f, 0f, canvas.width.toFloat(), canvas.height * 2f)
            canvas.drawBitmap(texture, source, destination, fallbackPaint)
        }
        fallbackPaint.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            intArrayOf(0x44FFFFFF, 0x16FFFFFF, 0x30FFFFFF),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
        fallbackPaint.alpha = 255
        canvas.drawOval(bounds, fallbackPaint)
        fallbackPaint.shader = null
        if (frame.ring > 0.001f) {
            fallbackPaint.style = Paint.Style.STROKE
            fallbackPaint.strokeWidth = radius * 0.22f
            fallbackPaint.alpha = (frame.ring * 230f).toInt()
            fallbackPaint.shader = LinearGradient(
                bounds.left,
                frame.discY,
                bounds.right,
                bounds.bottom,
                intArrayOf(RingInner, RingCore, RingOuter, RingBlush),
                null,
                Shader.TileMode.CLAMP,
            )
            val inset = radius * 0.18f
            canvas.drawArc(
                RectF(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset),
                8f,
                164f,
                false,
                fallbackPaint,
            )
            fallbackPaint.shader = null
        }
        canvas.restoreToCount(saveCount)
        fallbackPaint.style = Paint.Style.STROKE
        fallbackPaint.strokeWidth = GlassDiscOnboardingTheme.LAYOUT.rimWidth * pointScale
        fallbackPaint.color = 0x55FFFFFF
        fallbackPaint.alpha = 255
        canvas.drawOval(bounds, fallbackPaint)
        if (frame.glint > 0.001f) {
            fallbackPaint.strokeWidth = GlassDiscOnboardingTheme.LAYOUT.rimWidth * pointScale * 0.7f
            fallbackPaint.color = 0xFFFFFFFF.toInt()
            fallbackPaint.alpha = (frame.glint * 210f).toInt()
            canvas.drawArc(bounds, Math.toDegrees(frame.glintAngle.toDouble()).toFloat() - 8f, 16f, false, fallbackPaint)
        }
        fallbackPaint.style = Paint.Style.FILL
        fallbackPaint.alpha = 255
    }

    companion object {
        private val BackgroundTopLeft = GlassDiscOnboardingTheme.COLOR.bgTL.toArgb()
        private val BackgroundTopRight = GlassDiscOnboardingTheme.COLOR.bgTR.toArgb()
        private val BackgroundBottomLeft = GlassDiscOnboardingTheme.COLOR.bgBL.toArgb()
        private val BackgroundBottomRight = GlassDiscOnboardingTheme.COLOR.bgBR.toArgb()
        private val ShadowColor = GlassDiscOnboardingTheme.COLOR.shadow.toArgb()
        private val RingInner = GlassDiscOnboardingTheme.COLOR.ringInner.toArgb()
        private val RingCore = GlassDiscOnboardingTheme.COLOR.ringCore.toArgb()
        private val RingOuter = GlassDiscOnboardingTheme.COLOR.ringOuter.toArgb()
        private val RingBlush = GlassDiscOnboardingTheme.COLOR.ringBlush.toArgb()

        private const val LensAgsl = """
            uniform shader tex;
            uniform float2 size;
            uniform float texScale;
            uniform float2 center;
            uniform float radius;
            uniform float stretch;
            uniform float glint;
            uniform float glintAng;
            uniform float2 light;
            uniform float glow;
            uniform float spreadBoost;
            uniform float pointScale;
            uniform float alpha;
            uniform float2 texOrigin;
            uniform float reveal;
            uniform float fold;
            uniform float foldBack;
            uniform float foldShape;
            uniform float kY;
            uniform float kX;
            uniform float squeezeX;
            uniform float spread;
            uniform float fringeFrom;
            uniform float fade;
            uniform float mirrorFloor;
            uniform float ring;
            uniform float ringFrom;
            uniform float ringTo;
            uniform float rimLight;
            uniform float rimWidth;
            uniform float shadowAt;
            uniform float shadowWidth;
            uniform float shadowAlpha;
            uniform float beadFrom;
            uniform float beadSpan;
            uniform float beadFrost;
            uniform float domeFrost;
            layout(color) uniform half4 bgTL;
            layout(color) uniform half4 bgTR;
            layout(color) uniform half4 bgBL;
            layout(color) uniform half4 bgBR;
            layout(color) uniform half4 shadowColor;
            layout(color) uniform half4 ringInner;
            layout(color) uniform half4 ringCore;
            layout(color) uniform half4 ringOuter;
            layout(color) uniform half4 ringBlush;

            float3 backdrop(float2 p) {
                float2 u = clamp(p / size, 0.0, 1.0);
                return mix(mix(float3(bgTL.rgb), float3(bgTR.rgb), u.x), mix(float3(bgBL.rgb), float3(bgBR.rgb), u.x), u.y);
            }

            float reach(float r) {
                float peak = fold * foldShape / (foldShape + 1.0);
                if (r < fold) {
                    return r * (1.0 - pow(r / fold, foldShape) / (foldShape + 1.0));
                }
                float b = r - fold;
                return peak - foldBack * b * b;
            }

            half4 taken(float2 q) {
                return tex.eval(q * texScale);
            }

            half4 main(float2 p) {
                if (alpha <= 0.001 || radius <= 0.5) {
                    return half4(0.0);
                }
                float small = clamp((beadFrom - radius) / beadSpan, 0.0, 1.0);
                float k = sqrt(1.0 + stretch);
                float2 d = float2((p.x - center.x) * k, (p.y - center.y) / k) / radius;
                float r = length(d);
                float lengthPx = r * radius;
                if (r >= 1.0) {
                    float shadowT = (lengthPx - radius - shadowAt) / shadowWidth;
                    float shadowAmount = shadowAlpha * (1.0 + small) * exp(-shadowT * shadowT) * alpha * (0.5 + smoothstep(-0.4, 1.0, d.y));
                    float glowAmount = glow * ring * exp(-pow((r - 1.0) / 0.22, 2.0)) * smoothstep(-0.2, 0.8, d.y) * alpha;
                    float3 glowColor = mix(float3(ringCore.rgb), float3(ringOuter.rgb), 0.5 + 0.5 * d.x);
                    return half4(half3(float3(shadowColor.rgb) * shadowAmount + glowColor * glowAmount), half(shadowAmount + glowAmount));
                }
                float3 background = backdrop(p);
                float2 unit = r > 0.0001 ? d / r : float2(0.0);
                float mapped = reach(length(float2(d.x * squeezeX, d.y)));
                float2 offset = float2(unit.x * mapped * kX, unit.y * mapped * kY) * radius;
                float fringe = clamp((r - fringeFrom) / max(0.001, fold - fringeFrom), 0.0, 1.0);
                float2 dispersion = unit * spread * (1.0 + spreadBoost) * fringe;
                float squeezedRadius = length(float2(d.x * squeezeX, d.y));
                float visible = reveal * (squeezedRadius < fold ? 1.0 : clamp(1.0 - (squeezedRadius - fold) * fade, mirrorFloor, 1.0));
                half4 redSample = taken(texOrigin + offset + dispersion);
                half4 greenSample = taken(texOrigin + offset);
                half4 blueSample = taken(texOrigin + offset - dispersion);
                float3 color = float3(
                    background.r * (1.0 - redSample.a * visible) + redSample.r * visible,
                    background.g * (1.0 - greenSample.a * visible) + greenSample.g * visible,
                    background.b * (1.0 - blueSample.a * visible) + blueSample.b * visible
                );
                float below = smoothstep(-0.12, 0.42, d.y);
                float across = clamp((r - ringFrom) / (ringTo - ringFrom), 0.0, 1.0);
                float band = smoothstep(0.0, 0.18, across) * (1.0 - smoothstep(0.72, 1.0, across)) * below;
                float3 iridescence = mix(float3(ringInner.rgb), float3(ringCore.rgb), smoothstep(0.08, 0.42, across));
                iridescence = mix(iridescence, float3(ringOuter.rgb), smoothstep(0.5, 0.85, across));
                iridescence = mix(iridescence, float3(ringBlush.rgb), smoothstep(0.82, 1.0, across) * 0.6);
                color = mix(color, float3(1.0), ring * 0.22 * smoothstep(ringTo - 0.05, 1.0, r) * below);
                color = mix(color, iridescence, clamp(band * ring, 0.0, 1.0));
                color = mix(color, float3(1.0), domeFrost * (0.55 + 0.45 * smoothstep(0.2, -0.9, d.y)));
                color = mix(color, float3(1.0), beadFrost * small * (0.6 + 0.4 * smoothstep(0.3, -0.8, d.y)));
                float rimBand = smoothstep(1.0 - rimWidth / radius, 1.0 - 1.5 * pointScale / radius, r);
                color += rimLight * rimBand;
                float angle = atan(d.y, d.x);
                float deltaAngle = angle - glintAng;
                deltaAngle -= 6.2831853 * floor((deltaAngle + 3.1415927) / 6.2831853);
                color += glint * 0.32 * exp(-deltaAngle * deltaAngle / 0.05) * smoothstep(0.82, 0.99, r);
                float2 highlightCenter = float2(-0.36, -0.46) + light * 0.3;
                color += 0.035 * exp(-dot(d - highlightCenter, d - highlightCenter) / 0.06) * (1.0 - small * 0.5);
                float pixel = 1.0 / radius;
                float edge = 1.0 - smoothstep(1.0 - 1.5 * pointScale * pixel, 1.0, r);
                float shadowT = (lengthPx - radius - shadowAt) / shadowWidth;
                float shadowAmount = shadowAlpha * (1.0 + small) * exp(-shadowT * shadowT);
                float outputAlpha = alpha * (edge + (1.0 - edge) * shadowAmount);
                float3 outputColor = color * edge * alpha + float3(shadowColor.rgb) * shadowAmount * (1.0 - edge) * alpha;
                return half4(half3(outputColor), half(outputAlpha));
            }
        """
    }
}
