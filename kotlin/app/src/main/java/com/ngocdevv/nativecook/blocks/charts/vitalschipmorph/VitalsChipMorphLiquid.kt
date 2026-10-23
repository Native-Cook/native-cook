package com.ngocdevv.nativecook.blocks.charts.vitalschipmorph

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.charts.vitalschipmorph.VitalsChipMorphTheme as T

/** API 33 liquid pass, compiled only when the material first becomes visible. */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class VitalsChipMorphLiquid {
    private val sourceNode = RenderNode("VitalsChipMorphLiquidSource")
    private val filteredNode = RenderNode("VitalsChipMorphLiquidFiltered")
    private val shader = RuntimeShader(SOURCE)

    init {
        sourceNode.setPosition(0, 0, WIDTH, HEIGHT)
        filteredNode.setPosition(0, 0, WIDTH, HEIGHT)
        val sigma = T.liquidLook.softSigma.toFloat()
        // Android takes a blur radius; RN's softSigma is a Skia sigma.
        val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)
        val blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
        val liquid = RenderEffect.createRuntimeShaderEffect(shader, "image")
        filteredNode.setRenderEffect(RenderEffect.createChainEffect(liquid, blur))
    }

    /** Starts recording the exact flat hero into the filtered offscreen layer. */
    @SuppressLint("NewApi")
    fun begin(
        clock: Double,
        liquid: Double,
        wobble: Double,
        rippleAge: Double,
        rippleAlpha: Double,
    ): Canvas {
        shader.setFloatUniform("clock", clock.toFloat())
        shader.setFloatUniform("liquid", liquid.toFloat())
        shader.setFloatUniform("wobble", wobble.toFloat())
        shader.setFloatUniform("goo", liquid.toFloat())
        shader.setFloatUniform("gooC", T.liquidLook.gooCentre[0].toFloat(), T.liquidLook.gooCentre[1].toFloat())
        shader.setFloatUniform("heartC", T.layout.heart.cx.toFloat(), T.layout.heart.cy.toFloat())
        shader.setFloatUniform("ripT", rippleAge.toFloat())
        shader.setFloatUniform("ripA", rippleAlpha.toFloat())
        return sourceNode.beginRecording(WIDTH, HEIGHT)
    }

    fun end(canvas: Canvas) {
        sourceNode.endRecording()
        val filtered = filteredNode.beginRecording(WIDTH, HEIGHT)
        filtered.drawRenderNode(sourceNode)
        filteredNode.endRecording()
        // Keep thin glyphs and the sharp core under the wet pass; the blurred shader supplies the
        // displaced rim, melt bridge, refraction, glints, and heartbeat wave.
        canvas.drawRenderNode(sourceNode)
        canvas.drawRenderNode(filteredNode)
    }

    private companion object {
        const val WIDTH = 1206
        const val HEIGHT = 1420

        /*
         * Android's RuntimeShader works in the destination colour space, so the studio constants
         * stay sRGB here. RN alone converts literals to Display P3 because Skia marks its runtime
         * effect output as P3 on iOS.
         */
        const val SOURCE = """
uniform shader image;

uniform float clock;
uniform float liquid;
uniform float wobble;
uniform float goo;
uniform float2 gooC;
uniform float2 heartC;
uniform float ripT;
uniform float ripA;

const float3 SKY_TOP = float3(1.0, 1.0, 1.0);
const float3 SKY_LOW = float3(0.8471, 0.8627, 0.9098);
const float3 HORIZON = float3(0.4627, 0.4784, 0.5490);
const float3 FLOOR_NEAR = float3(0.7765, 0.7882, 0.8392);
const float3 FLOOR_FAR = float3(0.9412, 0.9451, 0.9647);
const float3 KEY = float3(-0.47, -0.74, 0.48);

float sat1(float value) {
    return clamp(value, 0.0, 1.0);
}

float2 flowAt(float2 point) {
    float a = sin(point.y * 0.021 + clock * 5.3 + sin(point.x * 0.013 - clock * 3.1) * 1.7);
    float b = sin(point.x * 0.019 - clock * 4.7 + sin(point.y * 0.017 + clock * 2.3) * 1.5);
    return float2(a, b) * 7.0 * wobble;
}

float3 studio(float3 reflected) {
    float vertical = reflected.y;
    float3 sky = mix(SKY_TOP, SKY_LOW, smoothstep(-0.95, 0.0, vertical));
    float3 floorColor = mix(FLOOR_NEAR, FLOOR_FAR, smoothstep(0.0, 0.85, vertical));
    float3 color = mix(sky, floorColor, step(0.0, vertical));
    color = mix(color, HORIZON, exp(-vertical * vertical / 0.006) * 0.75);
    color += float3(0.35) * smoothstep(0.09, 0.02, abs(reflected.x + 0.38)) * smoothstep(-0.15, -0.55, vertical);
    return color;
}

float3 straight(half4 color) {
    return color.a > 0.0005 ? float3(color.rgb) / float(color.a) : float3(0.0);
}

half4 main(float2 point) {
    float2 radial = point - heartC;
    float radius = max(sqrt(dot(radial, radial)), 1.0);
    float2 direction = radial / radius;
    float rippleEnvelope = 0.0;
    float rippleSlope = 0.0;
    if (ripA > 0.0) {
        float wave = radius - ripT * 820.0;
        rippleEnvelope = exp(-wave * wave / 9800.0) * exp(-ripT / 0.5) * ripA;
        rippleEnvelope *= sat1(radius / 60.0);
        rippleSlope = rippleEnvelope * cos(wave * 0.0982);
    }

    float material = max(max(liquid, wobble), rippleEnvelope);
    float2 samplePoint = point + flowAt(point) + direction * rippleSlope * 3.5;
    half4 blurred = image.eval(samplePoint);
    // The source is already the soft silhouette on Android. Below this threshold the local goo
    // cannot reach the pixel, so the four slope samples would produce the same transparent result.
    if (float(blurred.a) < 0.2 || material <= 0.0005) {
        return half4(0.0);
    }

    float edge = 3.0;
    float alphaX = float(image.eval(samplePoint + float2(edge, 0.0)).a - image.eval(samplePoint - float2(edge, 0.0)).a);
    float alphaY = float(image.eval(samplePoint + float2(0.0, edge)).a - image.eval(samplePoint - float2(0.0, edge)).a);
    float2 gradient = float2(alphaX, alphaY) / (2.0 * edge);
    float antiAlias = clamp(sqrt(dot(gradient, gradient)) * 1.4, 0.015, 0.3);

    // The sharp threshold restores the source edge after BlurEffect. The wider threshold is local
    // to the ring bottom / heart tip, so nearby pieces melt without sealing every ring gap.
    float sharpAlpha = smoothstep(0.5 - antiAlias * 0.25, 0.5 + antiAlias * 0.25, float(blurred.a));
    float2 gooDelta = (samplePoint - gooC) / 260.0;
    float gooHere = goo * (1.0 - smoothstep(0.55, 1.0, sqrt(dot(gooDelta, gooDelta))));
    float gooAlpha = smoothstep(0.5 - antiAlias, 0.5 + antiAlias, float(blurred.a)) * gooHere;
    float alpha = max(sharpAlpha, gooAlpha);
    if (alpha <= 0.001) {
        return half4(0.0);
    }

    float3 color = straight(blurred);
    float light = max(liquid, rippleEnvelope * 1.0);
    float2 surfaceSlope = -gradient * 70.0 * liquid - direction * rippleSlope * 0.95 * alpha;
    float3 normal = normalize(float3(surfaceSlope, 1.0));
    float tilt = 1.0 - normal.z;

    half4 bent = image.eval(samplePoint + surfaceSlope * -12.0);
    if (bent.a > 0.2) {
        color = mix(color, straight(bent), sat1(tilt * 3.0));
    }

    float3 lightDirection = normalize(KEY);
    float diffuse = dot(normal, lightDirection);
    float3 halfway = normalize(lightDirection + float3(0.0, 0.0, 1.0));
    float highlight = max(dot(normal, halfway), 0.0);
    float specular = pow(highlight, 90.0) * 1.15 + pow(highlight, 14.0) * 0.10;
    float fresnel = 0.05 + 0.95 * pow(tilt, 2.2);
    float3 reflected = float3(2.0 * normal.z * normal.x, 2.0 * normal.z * normal.y, 2.0 * normal.z * normal.z - 1.0);
    float2 outward = sqrt(dot(normal.xy, normal.xy)) > 0.0001 ? normalize(normal.xy) : float2(0.0);
    float caustic = max(dot(outward, -normalize(lightDirection.xy)), 0.0) * smoothstep(0.02, 0.35, tilt);

    float3 body = color * (0.86 + 0.22 * diffuse) * (1.0 - 0.22 * smoothstep(0.25, 0.85, tilt));
    body += color * caustic * 0.55;
    float3 shaded = mix(body, studio(reflected), sat1(fresnel * 0.9)) + float3(specular);
    float3 outputColor = mix(color, shaded, light);
    float wetMix = sat1(material * 4.0);
    float outputAlpha = alpha * wetMix;
    return half4(half3(outputColor * outputAlpha), half(outputAlpha));
}
"""
    }
}
