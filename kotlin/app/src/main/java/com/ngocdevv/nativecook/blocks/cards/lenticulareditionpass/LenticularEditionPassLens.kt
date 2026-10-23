package com.ngocdevv.nativecook.blocks.cards.lenticulareditionpass

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi

/** API33 exact inverse-ray lens, using the frozen source atlas as normal managed color. */
@RequiresApi(33)
internal class LenticularEditionPassLens(resources: LenticularEditionPassResources) {
    private val program=RuntimeShader(SOURCE)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    init {
        val atlas=BitmapShader(resources.atlas,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
        atlas.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        program.setInputShader("atlas",atlas)
        paint.shader=program
    }
    fun draw(canvas: Canvas,frame: LenticularEditionPassFrame) {
        val r=LenticularEditionPassGeometry.rotation(frame.pose)
        program.setFloatUniform("row0",r[0].toFloat(),r[1].toFloat(),r[2].toFloat())
        program.setFloatUniform("row1",r[3].toFloat(),r[4].toFloat(),r[5].toFloat())
        program.setFloatUniform("row2",r[6].toFloat(),r[7].toFloat(),r[8].toFloat())
        program.setFloatUniform("actualYaw",frame.pose.yaw.toFloat())
        program.setFloatUniform("explicitViewB",(frame.explicitViewB?:-1.0).toFloat())
        canvas.drawRect(21.5f,242.5f,371.5f,507.5f,paint)
    }
    companion object {
        const val SOURCE="""
uniform shader atlas;
uniform float3 row0;
uniform float3 row1;
uniform float3 row2;
uniform float actualYaw;
uniform float explicitViewB;
float3 inverseRotation(float3 v) {
    return float3(dot(float3(row0.x,row1.x,row2.x),v),dot(float3(row0.y,row1.y,row2.y),v),dot(float3(row0.z,row1.z,row2.z),v));
}
float bow(float2 p) {
    return 3.5*(1.0-4.0*p.x*p.x/(333.0*333.0))*(1.0-4.0*p.y*p.y/(211.0*211.0));
}
float2 bowGradient(float2 p) {
    return float2(-28.0*p.x/(333.0*333.0)*(1.0-4.0*p.y*p.y/(211.0*211.0)),
                  -28.0*p.y/(211.0*211.0)*(1.0-4.0*p.x*p.x/(333.0*333.0)));
}
float roundedDistance(float2 p) {
    float2 q=abs(p)-float2(160.5,99.5);
    return length(max(q,0.0))+min(max(q.x,q.y),0.0)-6.0;
}
float3 intersection(float3 c,float3 d,float offset) {
    if(abs(d.z)<0.1) return float3(10000.0);
    float plane=-c.z/d.z;
    float t=plane;
    for(int i=0;i<6;++i) {
        float3 p=c+t*d;
        float2 gradient=bowGradient(p.xy);
        float residual=p.z-bow(p.xy)-offset;
        float derivative=d.z-dot(gradient,d.xy);
        if(abs(derivative)>0.1) t=clamp(t-residual/derivative,plane-12.0,plane+12.0);
    }
    return c+t*d;
}
half4 main(float2 position) {
    float2 screen=position-float2(196.5,366.5);
    float3 c=inverseRotation(float3(0.0,0.0,800.0));
    float3 d=inverseRotation(float3(screen/800.0,-1.0));
    float3 front=intersection(c,d,0.0);
    float distance=roundedDistance(front.xy);
    float residual=abs(front.z-bow(front.xy));
    float coverage=residual<=0.02 ? 1.0-smoothstep(-0.32,0.32,distance):0.0;
    float3 back=intersection(c,d,-1.4);
    float backDistance=roundedDistance(back.xy);
    float backResidual=abs(back.z-bow(back.xy)+1.4);
    float backCoverage=backResidual<=0.02 ? 1.0-smoothstep(-0.32,0.32,backDistance):0.0;
    if(coverage<=0.0&&backCoverage<=0.0) return half4(0.0);
    float edgeLight=1.0-smoothstep(-30.0,30.0,back.y);
    half3 edge=mix(half3(0.39,0.48,0.55),half3(0.80,0.85,0.83),half(edgeLight));
    half4 laminate=half4(edge*half(backCoverage),half(backCoverage));
    if(coverage<=0.0) return laminate;
    float ridge=floor((front.x+166.5)/1.45);
    float ridgeCenter=(ridge+0.5)*1.45-166.5;
    float beta=actualYaw+0.035*ridgeCenter+0.8*sin(6.283185307179586*ridgeCenter/333.0);
    float viewB=explicitViewB>=0.0 ? clamp(explicitViewB,0.0,1.0):smoothstep(-0.7,0.7,beta);
    float phase=fract((front.x+166.5)/1.45);
    float displacement=0.29267578125*sin(6.283185307179586*phase);
    float x=front.x+175.0+displacement;
    half4 a=atlas.eval(float2(x,front.y+106.5)*3.0);
    half4 b=atlas.eval(float2(x,front.y+319.5)*3.0);
    half4 sampleColor=mix(a,b,half(viewB));
    float shade=1.0+0.055*cos(6.283185307179586*phase)+0.025*cos(12.566370614359172*phase+0.7);
    float trough=0.045*smoothstep(0.9,1.0,phase);
    sampleColor.rgb*=half(shade*(1.0-trough));
    float reflectedCenter=22.5*clamp(actualYaw/14.0,-1.0,1.0);
    float reflection=(0.06+0.08*clamp(abs(actualYaw)/14.0,0.0,1.0))*exp(-0.5*pow((front.x-reflectedCenter)/33.972872,2.0));
    sampleColor.rgb+=(sampleColor.a-sampleColor.rgb)*half(reflection);
    float border=1.0-smoothstep(-1.2,-0.8,distance);
    float borderWeight=(1.0-border)*0.35;
    sampleColor.rgb=mix(sampleColor.rgb,half3(0.718,0.776,0.808)*sampleColor.a,half(borderWeight));
    half4 face=sampleColor*half(coverage);
    return face+laminate*(1.0-face.a);
}
"""
    }
}
