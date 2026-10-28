package com.ngocdevv.nativecook.blocks.cards.trophysavingsmilestone

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.PathParser

/** Code-native reference glyph, avoiding platform emoji size/perspective drift. */
internal object TrophySavingsMilestoneTargetArt {
    val picture=Picture().apply {
        val c=beginRecording(32,26); val p=Paint(Paint.ANTI_ALIAS_FLAG)
        c.save(); c.rotate(-43f,11.1f,14.5f)
        p.shader=LinearGradient(2.2f,3.4f,20.7f,26.7f,intArrayOf(0xFFF05C60.toInt(),0xFFC72E38.toInt(),0xFF86232A.toInt()),floatArrayOf(0f,0.48f,1f),Shader.TileMode.CLAMP)
        c.drawOval(2.2f,3.4f,20.7f,26.7f,p)
        p.shader=LinearGradient(1.95f,3.15f,19.75f,25.25f,intArrayOf(0xFFE54D52.toInt(),0xFFD1353E.toInt(),0xFFBB2732.toInt()),floatArrayOf(0f,0.55f,1f),Shader.TileMode.CLAMP)
        c.drawOval(1.95f,3.15f,19.75f,25.25f,p)
        p.shader=null; p.color=0xFFFFF0E4.toInt(); c.drawOval(3.7f,5f,17.6f,22.9f,p)
        p.color=0xFFC52D36.toInt(); c.drawOval(5.5f,7.05f,15.8f,20.85f,p)
        p.color=0xFFFFF0E3.toInt(); c.drawOval(7.25f,9.1f,13.75f,18.5f,p)
        p.color=0xFFB6232D.toInt(); c.drawOval(8.75f,11.15f,12.25f,16.45f,p)
        c.restore()
        fun path(data: String,color: Int) { p.shader=null; p.style=Paint.Style.FILL; p.color=color; c.drawPath(requireNotNull(PathParser.createPathFromPathData(data)),p) }
        path("M11.25 14.85L12.8 10.7L15.05 12.5Z",0xFFA3672C.toInt())
        p.shader=LinearGradient(13.25f,13.6f,25.55f,2.7f,intArrayOf(0xFF2A7380.toInt(),0xFF54ADB5.toInt(),0xFF87D1D2.toInt()),floatArrayOf(0f,0.5f,1f),Shader.TileMode.CLAMP)
        p.color=Color.WHITE; c.drawPath(requireNotNull(PathParser.createPathFromPathData("M13.25 12.25L23.9 2.7L25.55 4.5L14.5 13.6Z")),p)
        p.shader=null; p.style=Paint.Style.STROKE; p.color=0xFFA0DDDA.toInt(); p.strokeWidth=0.65f
        c.drawPath(requireNotNull(PathParser.createPathFromPathData("M14.25 11.8L24.25 2.9")),p)
        path("M22.15 3.85L23.55 0.55L27.65 0.25L26.55 3.75L24.55 5.6Z",0xFF6EBEC1.toInt())
        path("M22.15 3.85L24.55 5.6L26.55 3.75L24.55 2.6Z",0xFF3E939D.toInt())
        path("M24.3 4.55L27.7 3.9L31.65 5.6L31.6 7.25L27.65 7.1L24.1 5.85Z",0xFF2F737D.toInt())
        path("M25.1 4.8L28.15 4.65L31.65 5.6L27.7 5.8Z",0xFF58A4AC.toInt())
        p.color=0xFF276E78.toInt(); p.style=Paint.Style.STROKE; p.strokeWidth=0.7f; p.strokeCap=Paint.Cap.ROUND
        c.drawPath(requireNotNull(PathParser.createPathFromPathData("M24.15 4.9L27.1 2.2")),p)
        endRecording()
    }
}

@Composable
internal fun TrophySavingsMilestoneTargetGlyph(modifier: Modifier) {
    Canvas(modifier) { drawIntoCanvas { canvas ->
        canvas.nativeCanvas.save(); canvas.nativeCanvas.scale(density,density)
        canvas.nativeCanvas.drawPicture(TrophySavingsMilestoneTargetArt.picture); canvas.nativeCanvas.restore()
    } }
}
