package com.ngocdevv.nativecook.blocks.navigation.raceglobecalendar
import android.graphics.*
import androidx.annotation.RequiresApi
import kotlin.math.*
@RequiresApi(33)
internal class RaceGlobeCalendarBackdrop {
 private val nodes=mutableListOf<RenderNode>();private var cursor=0
 fun begin() { cursor=0 }
 fun draw(c:Canvas,shader:RaceGlobeCalendarShader,f:RaceGlobeCalendarFrame,ch:Double,px:Double,rect:RectF,blur:Float) {
  if(cursor==nodes.size)nodes.add(RenderNode("Race globe glass $cursor"));val node=nodes[cursor++]
  val left=floor(rect.left-blur*3).toInt();val top=floor(rect.top-blur*3).toInt();val right=ceil(rect.right+blur*3).toInt();val bottom=ceil(rect.bottom+blur*3).toInt()
  node.setPosition(left,top,right,bottom);node.setRenderEffect(RenderEffect.createBlurEffect(blur,blur,Shader.TileMode.CLAMP))
  val canvas=node.beginRecording(right-left,bottom-top);canvas.translate(-left.toFloat(),-top.toFloat());shader.draw(canvas,f,ch,px);node.endRecording();c.drawRenderNode(node)
 }
}
