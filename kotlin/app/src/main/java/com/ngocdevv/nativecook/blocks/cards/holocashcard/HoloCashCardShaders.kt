package com.ngocdevv.nativecook.blocks.cards.holocashcard

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class HoloCashCardShaders {
    val foil=RuntimeShader("""
        uniform float2 size; uniform float2 tilt; uniform float glow;
        float3 hue(float h) {
            float3 a=float3(1.0,0.70,0.90), b=float3(0.72,0.66,1.0), c=float3(0.62,0.92,1.0);
            float3 d=float3(0.78,1.0,0.84), e=float3(1.0,0.95,0.68);
            float x=fract(h)*5.0;
            if(x<1.0)return mix(a,b,x); if(x<2.0)return mix(b,c,x-1.0);
            if(x<3.0)return mix(c,d,x-2.0); if(x<4.0)return mix(d,e,x-3.0); return mix(e,a,x-4.0);
        }
        half4 main(float2 xy) {
            float2 uv=xy/size; float y=uv.y+tilt.x*0.9;
            float chrome=0.86-0.62*exp(-pow((y-0.56)*6.5,2.0))+0.14*smoothstep(0.62,0.95,y);
            float3 foil=hue(uv.x*0.9+uv.y*0.6+tilt.y*1.4+tilt.x*0.8);
            float3 col=mix(float3(chrome),foil*(0.55+0.5*chrome),0.62);
            float band=exp(-pow((uv.x+uv.y*0.45-0.62-tilt.y*1.6)*9.0,2.0));
            col+=band*(0.5+0.2*glow); col=mix(col,foil*1.08,0.28*glow);
            return half4(half3(min(col,1.0)),1.0);
        }
    """.trimIndent())
    val rim=RuntimeShader("""
        uniform float2 size; uniform float2 tilt;
        half4 main(float2 xy) {
            float2 uv=xy/size; float h=fract(uv.x*0.7-uv.y*0.4+tilt.y*1.2+tilt.x);
            float3 c=0.5+0.5*cos(6.2831*(h+float3(0.0,0.33,0.67)));
            return half4(half3(mix(c,float3(1.0),0.45)),1.0);
        }
    """.trimIndent())
    val lines=RuntimeShader("""
        uniform float2 size; uniform float2 tilt; uniform float glow;
        half4 main(float2 xy) {
            float2 uv=xy/size; float d=xy.x*0.82+xy.y*0.57;
            float line=smoothstep(0.55,1.0,sin(d*1.15));
            float h=fract(uv.x*0.6+uv.y*0.3+tilt.y*1.3+tilt.x*0.9);
            float3 c=0.5+0.5*cos(6.2831*(h+float3(0.0,0.33,0.67)));
            float band=exp(-pow((uv.x+uv.y*0.45-0.62-tilt.y*1.6)*3.0,2.0));
            float a=line*(0.05+0.22*band)*(1.0+0.6*glow);
            return half4(half3(c*a),a);
        }
    """.trimIndent())
    fun update(w:Float,h:Float,x:Float,y:Float,glow:Float) {
        for(s in listOf(foil,rim,lines)) { s.setFloatUniform("size",w,h); s.setFloatUniform("tilt",x,y) }
        foil.setFloatUniform("glow",glow); lines.setFloatUniform("glow",glow)
    }
}
