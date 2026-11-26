package com.ngocdevv.nativecook.blocks.inputs.honeydrizzlestepper

import android.graphics.*
import androidx.annotation.RequiresApi

/** Six linear, clamped, non-mipmapped image samples. Bytes and output remain in the intended sRGB space. */
internal const val HONEY_DRIZZLE_STEPPER_AGSL = """
uniform shader uImg;
uniform float uAmt; uniform float uT; uniform float uStill; uniform float2 uOrigin;
const float K=520./1024.;
half3 tex(float2 px){return uImg.eval(px).rgb;}
float honeyMask(half3 c){return smoothstep(.70,.80,c.r)*smoothstep(.40,.50,c.g/max(c.r,.01))*(1.-smoothstep(.35,.5,c.b));}
float hash(float2 p){p=fract(p*float2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}
float noise(float2 p){float2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+float2(1.,0.)),f.x),mix(hash(i+float2(0.,1.)),hash(i+float2(1.,1.)),f.x),f.y);}
half4 main(float2 xy){float2 px=(xy-uOrigin)/K;
 if(uStill>.5) return half4(tex(px),1.);
 float A=uAmt-2.;
 float2 q=px;q.y+=A*23.*(1.-smoothstep(560.,1040.,px.y));
 float band=smoothstep(590.,660.,q.y)*(1.-smoothstep(990.,1045.,q.y));
 q.x=500.+(q.x-500.)/(1.+.12*A*band);
 float ph0=fract(uT/1.5),ph1=fract(uT/1.5+.5),w0=1.-abs(2.*ph0-1.);
 float sm=band*honeyMask(tex(q));
 float2 n0=float2(noise(q*float2(.05,.012)+ph0*3.)-.5,0.)*6.,n1=float2(noise(q*float2(.05,.012)+ph1*3.+5.)-.5,0.)*6.;
 half3 a=tex(q-float2(0.,ph0*46.)*sm+n0*sm),b=tex(q-float2(0.,ph1*46.)*sm+n1*sm);
 half3 col=mix(b,a,w0);
 float2 m=float2(1.+.04*A,1.+.12*A);float2 mc=float2(500.,1230.);float2 mq=mc+(px-mc)/m;if(px.y>mc.y)mq.y=px.y;
 float region=1.-smoothstep(.9,1.,length((px-mc)/float2(330.,260.)));
 half3 base=tex(mq);float hb=honeyMask(base);
 float mm=hb*region*smoothstep(1000.,1080.,mq.y);
 half3 ma=tex(mq-float2(ph0*22.,0.)*mm),mb=tex(mq-float2(ph1*22.,0.)*mm);half3 mound=mix(mb,ma,w0);
 float hm=hb*region*(1.-smoothstep(1195.,1225.,mq.y));
 col=mix(col,mound,hm*step(1040.,px.y+(m.y-1.)*220.));
 return half4(col,1.);}
"""

@RequiresApi(33)
internal class HoneyDrizzleStepperShader(photo: Bitmap) {
    private val shader = RuntimeShader(HONEY_DRIZZLE_STEPPER_AGSL)
    val paint = Paint()
    init {
        val texture = BitmapShader(photo,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
        texture.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        shader.setInputBuffer("uImg",texture)
        paint.shader = shader
    }
    fun update(f: HoneyDrizzleStepperMotion.Frame, sy: Float) {
        shader.setFloatUniform("uAmt",f.amount.toFloat()); shader.setFloatUniform("uT",f.flow.toFloat())
        shader.setFloatUniform("uStill",if(f.still) 1f else 0f); shader.setFloatUniform("uOrigin",-12f,10*sy)
    }
}
