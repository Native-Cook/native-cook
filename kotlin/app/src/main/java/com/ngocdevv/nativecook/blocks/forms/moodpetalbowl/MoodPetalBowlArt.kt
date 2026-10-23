package com.ngocdevv.nativecook.blocks.forms.moodpetalbowl

import android.graphics.Canvas as NativeCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

/** Filtering happens after the seven opaque stamps have been depth-composited. */
@Composable
internal fun MoodPetalBowlArt(painter: MoodPetalBowlPainter,clock: MutableDoubleState,driver: MoodPetalBowlDriver,
    autoPlay: Boolean,reduced: Boolean,preview: Double?,scale: Float,pixels: Float,height: Float,bottom: Float) {
    fun scene(): MoodPetalBowlScene = driver.scene(clock.doubleValue,autoPlay,reduced,preview)
    Canvas(Modifier.fillMaxSize()) {drawIntoCanvas { val c=it.nativeCanvas;c.save();c.scale(pixels,pixels);painter.background(c,height);c.restore() }}
    val mask=remember(painter,pixels) {MoodPetalBowlMask(painter,pixels)}
    Box(Modifier.offset((35*scale).dp,(137*scale).dp).size((323*scale).dp,(293*scale).dp).clipToBounds()) {
        if(Build.VERSION.SDK_INT>=33) {
            val effect=remember(pixels) {moodPetalBowlEffect(pixels)}
            Box(Modifier.offset((-26*scale).dp,(-26*scale).dp).wrapContentSize(Alignment.TopStart,unbounded=true).requiredSize((375*scale).dp,(345*scale).dp)
                .graphicsLayer {compositingStrategy=CompositingStrategy.Offscreen}) {
                Canvas(Modifier.fillMaxSize().drawWithContent {mask.apply(this,false) {drawContent()}}) {
                    val s=scene();drawIntoCanvas {val c=it.nativeCanvas;c.save();c.scale(pixels,pixels);painter.pile(c,s);c.restore()}
                }
                Box(Modifier.fillMaxSize().graphicsLayer {
                    compositingStrategy=CompositingStrategy.Offscreen;blendMode=BlendMode.Plus;alpha=.86f
                }.drawWithContent {mask.apply(this,true) {drawContent()}}) {
                    Canvas(Modifier.fillMaxSize().graphicsLayer {renderEffect=effect}) {
                        val s=scene();drawIntoCanvas {val c=it.nativeCanvas;c.save();c.scale(pixels,pixels);painter.pile(c,s);c.restore()}
                    }
                }
            }
        } else {
            val fallback=remember(painter) {MoodPetalBowlFallback(painter)}
            Canvas(Modifier.offset((-26*scale).dp,(-26*scale).dp).wrapContentSize(Alignment.TopStart,unbounded=true).requiredSize((375*scale).dp,(345*scale).dp)) {
                val s=scene();drawIntoCanvas {val c=it.nativeCanvas;c.save();c.scale(pixels,pixels);fallback.draw(c,s);c.restore()}
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val s=scene();driver.rendered=s
        drawIntoCanvas {val c=it.nativeCanvas;c.save();c.scale(pixels,pixels);painter.foreground(c,s,bottom);c.restore()}
    }
}

private class MoodPetalBowlMask(val painter: MoodPetalBowlPainter,pixels: Float) {
    private val destination=RectF(0f,0f,375*pixels,345*pixels)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN)}
    private val transform=Matrix().apply {setScale(pixels,pixels)}
    private val disk=Path(painter.disk).apply {transform(transform)}
    private val sharp=Path(painter.sharpVisibility).apply {transform(transform)}
    fun apply(scope: androidx.compose.ui.graphics.drawscope.ContentDrawScope,soft: Boolean,content: () -> Unit) {
        val c=scope.drawContext.canvas.nativeCanvas
        c.save();c.clipPath(if(soft) disk else sharp)
        val saved=c.saveLayer(destination,null)
        content()
        c.drawBitmap(if(soft) painter.resources.softStrip else painter.resources.sharpStrip,null,destination,paint)
        c.restoreToCount(saved);c.restore()
    }
}

@RequiresApi(33)
private fun moodPetalBowlEffect(pixels: Float): androidx.compose.ui.graphics.RenderEffect {
    // Android RenderEffect converts its radius to sigma (.57735*r + .5 device px).
    // Swift's authored 8.5-point Gaussian therefore needs the inverse conversion.
    val radius=(8.5f*pixels-.5f)/.57735f
    val blur=RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.DECAL)
    val saturation=RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(ColorMatrix().apply {setSaturation(.95f)}),blur)
    val shader=RuntimeShader("""
        uniform shader image;
        uniform float pixels;
        half4 main(float2 p) {
            float globalY = p.y / pixels + 111.0;
            float delta = 1.6 * clamp((globalY - 273.0) / 153.0, 0.0, 1.0) * pixels;
            return image.eval(p - float2(delta, 0.0));
        }
    """.trimIndent()).apply {setFloatUniform("pixels",pixels)}
    return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader,"image"),saturation).asComposeRenderEffect()
}
