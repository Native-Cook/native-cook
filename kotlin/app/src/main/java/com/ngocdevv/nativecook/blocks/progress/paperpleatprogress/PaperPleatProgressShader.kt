package com.ngocdevv.nativecook.blocks.progress.paperpleatprogress

import android.graphics.*
import androidx.annotation.RequiresApi

internal const val PAPER_PLEAT_PROGRESS_AGSL = """

uniform shader uPhoto; uniform shader uPlate; uniform shader uMask; uniform shader uPhoto4; uniform shader uPlate5;
uniform shader uPlate6; uniform shader uMask5;
uniform float uP; uniform float uSw; uniform float uT; uniform float uBoost; uniform float uFade; uniform float uLift;
uniform float2 uOrigin;
const float K=393./1024.; const float2 CEN=float2(749.,903.); const float TH0=1.75; const float WEDGE=.08; const float TAU=6.2831853;
float3 lin(float3 c){return pow(c,float3(2.2));}
float3 photoAt(float2 uv){return float3(uPhoto.eval(clamp(uv,0.,1.)*float2(1024.,1536.)).rgb);}
float3 plateAt(float2 uv){return float3(uPlate.eval(clamp(uv,0.,1.)*float2(1024.,1536.)).rgb);}
float maskAt(float2 uv){return float(uMask.eval(clamp(uv,0.,1.)*float2(512.,768.)).r);}
float3 photo4At(float2 uv){return float3(uPhoto4.eval(clamp(uv,0.,1.)*float2(64.,96.)).rgb);}
float3 plate5At(float2 uv){return float3(uPlate5.eval(clamp(uv,0.,1.)*float2(32.,48.)).rgb);}
float3 plate6At(float2 uv){return float3(uPlate6.eval(clamp(uv,0.,1.)*float2(16.,24.)).rgb);}
float mask5At(float2 uv){return float(uMask5.eval(clamp(uv,0.,1.)*float2(16.,24.)).r);}
float luma(float3 c){return dot(c,float3(.2126,.7152,.0722));}
half4 main(float2 p){float2 px=(p-uOrigin)/K;float2 uv=px/float2(1024.,1536.);
 float3 plate=lin(plateAt(uv));
 // where the sculpture was painted out, soften the plate to a smooth tone so no ghost edge shows
 float gw=smoothstep(.02,.35,mask5At(uv));
 float3 soft=lin(mix(plate5At(uv),plate6At(uv),.5));
 plate=mix(plate,soft,gw);
 float2 d=px-CEN;float r=length(d);float th=atan(d.y,d.x);
 float u=fract((TH0-th)/TAU);
 float3 col=plate;
 if(u<uP+WEDGE){
  float wz=(u-uP)/WEDGE;float us=u<uP?u:uP+wz*min(.16,1.-uP);   // the next pleats bunched into a closed stack at the front
  float ts=TH0-us*TAU;float2 src=CEN+r*float2(cos(ts),sin(ts));float2 suv=src/float2(1024.,1536.);
  float m=maskAt(suv);
  float3 s=lin(photoAt(suv));
  // pleat faces: the photo's fine light minus its blurred light
  float hp=luma(s)-luma(lin(photo4At(suv)));
  float coral=smoothstep(.10,.30,s.r-s.b)*step(.15,s.r);
  float3 CO=lin(float3(1.,.42,.28));
  if(u>=uP){s*=.92-.22*wz;}
  // the money moment: freshly opened pleats catch the light on their coral edges
  float g=(uP-u)/.05;float k=u<uP?exp(-g*g)*uBoost:0.;
  // the finishing raking light: soft tail, crisp bright leading edge
  float xs=mix(150.,1100.,uT);float k2=uSw*(smoothstep(xs-150.,xs-20.,src.x)*(1.-smoothstep(xs-2.,xs+4.,src.x)));
  float e=(src.x-xs+6.)/5.;float edge=uSw*exp(-e*e);
  float kk=max(k,k2);
  s+=CO*coral*kk*1.1+float3(1.,.96,.9)*max(hp,0.)*kk*3.;s+=float3(1.,.97,.92)*edge*.22;
  s*=uLift;
  col=mix(plate,s,m*uFade);}
 // fade the picture's top and bottom into the page colour
 float f=smoothstep(0.,.12,uv.y)*(1.-smoothstep(.88,1.,uv.y));col=mix(lin(float3(.898,.851,.812)),col,f);
 return half4(half3(pow(clamp(col,0.,1.),float3(1./2.2))),1.);}
"""

@RequiresApi(33)
internal class PaperPleatProgressShader(images: List<Bitmap>) {
    private val shader=RuntimeShader(PAPER_PLEAT_PROGRESS_AGSL)
    val paint=Paint().apply { this.shader=this@PaperPleatProgressShader.shader }
    init {
        listOf("uPhoto","uPlate","uMask","uPhoto4","uPlate5","uPlate6","uMask5").zip(images).forEach { (name,image) ->
            val input=BitmapShader(image,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
            input.setFilterMode(BitmapShader.FILTER_MODE_LINEAR); shader.setInputBuffer(name,input)
        }
    }
    fun update(f: PaperPleatProgressMotion.Frame,sy: Float) {
        shader.setFloatUniform("uP",f.p.toFloat()); shader.setFloatUniform("uSw",f.sw.toFloat()); shader.setFloatUniform("uT",f.t.toFloat())
        shader.setFloatUniform("uBoost",f.boost.toFloat()); shader.setFloatUniform("uFade",f.fade.toFloat()); shader.setFloatUniform("uLift",f.lift.toFloat())
        shader.setFloatUniform("uOrigin",0f,150*sy)
    }
}
