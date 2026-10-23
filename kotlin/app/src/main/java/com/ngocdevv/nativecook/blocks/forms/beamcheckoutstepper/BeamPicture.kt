package com.ngocdevv.nativecook.blocks.forms.beamcheckoutstepper

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

@RequiresApi(33)
private class BeamShaderPicture {
    private val rays=RuntimeShader(BeamAgsl.rays)
    private val glass=RuntimeShader(BeamAgsl.glass)
    private val hero=RuntimeShader(BeamAgsl.hero)
    private val rayPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { shader=rays }
    private val glassPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { shader=glass }
    private val heroPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { shader=hero }
    fun draw(canvas:android.graphics.Canvas,f:FloatArray,clock:Float,still:Boolean){
        for(shader in arrayOf(rays,glass,hero)){
            shader.setFloatUniform("f",f)
            shader.setFloatUniform("clock",clock)
            shader.setFloatUniform("still",if(still)1f else 0f)
        }
        canvas.drawRect(0f,0f,402f,874f,rayPaint)
        canvas.drawRect(0f,96f,402f,874f,glassPaint)
        canvas.drawRect(0f,96f,402f,520f,heroPaint)
    }
}

/** Runs the three source analytic materials at design coordinates; older APIs retain a static glass scene. */
@Composable
internal fun BeamPicture(controller:BeamController,k:Float){
    val shaders=remember { if(Build.VERSION.SDK_INT>=33) BeamShaderPicture() else null }
    Canvas(Modifier.fillMaxSize()){
        controller.drawTick
        if(Build.VERSION.SDK_INT >= 33 && shaders!=null){
            drawIntoCanvas { composeCanvas ->
                val c=composeCanvas.nativeCanvas
                c.save()
                c.scale(k,k)
                shaders.draw(c,controller.core.frame,controller.core.time.toFloat(),controller.core.still)
                c.restore()
            }
        } else {
            val f=controller.core.frame
            val lit=if(f[BF.WARM]>.5f)Color(0xFFFFD9A0) else Color(0xFF65B7FF)
            val head=128f+68f*f[BF.HEAD]
            drawLine(lit.copy(alpha=.55f),Offset(56*k,128*k),Offset(56*k,head*k),6*k)
            for(i in 0..4){
                val y=(128+68*i)*k
                drawRoundRect(Color(0xB91A2436),Offset(32*k,(106+68*i)*k),Size(338*k,44*k),
                    androidx.compose.ui.geometry.CornerRadius(22*k))
                drawCircle(lit.copy(alpha=if(i==controller.form.step).9f else .25f),20*k,Offset(56*k,y))
            }
            drawRoundRect(Color(0xE90B1426),Offset(24*k,446*k),
                Size(354*k,f[BF.PANEL_H]*k),androidx.compose.ui.geometry.CornerRadius(28*k))
            drawRoundRect(lit,Offset((24+16+64*f[BF.CTA_SPLIT])*k,(446+f[BF.PANEL_H]-72)*k),
                Size((322-64*f[BF.CTA_SPLIT])*k,56*k),androidx.compose.ui.geometry.CornerRadius(28*k))
        }
    }
}
