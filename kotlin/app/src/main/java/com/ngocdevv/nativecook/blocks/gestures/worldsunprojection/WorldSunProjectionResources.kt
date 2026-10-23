package com.ngocdevv.nativecook.blocks.gestures.worldsunprojection

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi

internal class WorldSunProjectionResources(private val context:Context){
    private fun load(name:String)=context.assets.open("world_sun_projection/$name").use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inScaled=false;inPreferredConfig=Bitmap.Config.HARDWARE})!!}.apply{prepareToDraw()}
    val scene:Bitmap?=if(Build.VERSION.SDK_INT>=33)load("scene.png")else null
    val views:List<Bitmap> = if(Build.VERSION.SDK_INT<33)(0..48).map{load("view-%02d.png".format(java.util.Locale.US,it))}else emptyList()
    val shader:Shader?=if(Build.VERSION.SDK_INT>=33)makeShader(scene!!)else null
    @RequiresApi(33) private fun makeShader(bitmap:Bitmap):Shader{
        val shader=RuntimeShader("""
            uniform shader scene;
            uniform float yaw; uniform float pitch;
            half4 main(float2 p){
                float y=radians(yaw),t=radians(pitch);
                float3 f=float3(sin(y)*cos(t),sin(t),cos(y)*cos(t));
                float3 r=float3(cos(y),0,-sin(y));
                float3 u=float3(-sin(y)*sin(t),cos(t),-cos(y)*sin(t));
                float3 ray=normalize(f+r*((p.x-196.5)/510.0)-u*((p.y-426.0)/510.0));
                float2 uv=float2(fract(.5+atan(ray.x,ray.z)/6.28318530718),clamp((1.57079632679-asin(clamp(ray.y,-1.0,1.0)))/2.61799387799,0.0,1.0));
                return scene.eval(uv*float2(2048,1024));
            }
        """.trimIndent())
        shader.setInputShader("scene",BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply{setFilterMode(BitmapShader.FILTER_MODE_LINEAR)})
        return shader
    }
    fun pose(p:WorldSunProjectionPose){if(Build.VERSION.SDK_INT>=33){(shader as? RuntimeShader)?.setFloatUniform("yaw",p.yaw.toFloat());(shader as? RuntimeShader)?.setFloatUniform("pitch",p.pitch.toFloat())}}
}
