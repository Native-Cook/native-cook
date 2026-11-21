package com.ngocdevv.nativecook.blocks.animations.hourscrubsky
import android.graphics.*
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class HourScrubSkyBackdrop {
 private val node=RenderNode("Hour Scrub Sky glass")
 fun draw(canvas:Canvas,sky:HourScrubSkyShader,f:HourScrubSkyFrame,c:HourScrubSkyColors,ch:Double,e:HourScrubSkyEngine) {
  node.setPosition(0,0,402,kotlin.math.ceil(ch).toInt());node.setRenderEffect(RenderEffect.createBlurEffect(24f,24f,Shader.TileMode.CLAMP))
  val target=node.beginRecording(402,kotlin.math.ceil(ch).toInt());sky.draw(target,f,c,ch,e.reduced,e.hold,e.fingerX,e.fingerY);node.endRecording();canvas.drawRenderNode(node)
 }
}
