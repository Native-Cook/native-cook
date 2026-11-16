package com.ngocdevv.nativecook.blocks.media.extrusionrecapstory

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * The RN block's swept-solid shader (`ers-shaders.ts`) as AGSL (API 33+): one solid per frame, marched through a glyph distance atlas,
 * its next silhouette growing out of the depth, lit by the distance field's normal; the field, the rays, the card and the next card's
 * peek are drawn here too. Coordinates are design points (402 wide). `p3` stays 0 on Android.
 */
internal const val ExtrusionRecapStoryShaderSource = """
uniform shader atlas;
uniform float2 size;
uniform float2 center;
uniform float2 direction;
uniform float2 nextDirection;
uniform float q;
uniform float reduced;
uniform float current;
uniform float next;
uniform float count;
uniform float3 fieldA;
uniform float3 fieldB;
uniform float3 nextA;
uniform float3 nextB;
uniform float3 afterB;
uniform float3 faceA;
uniform float3 faceB;
uniform float2 steer;
uniform float phase;
uniform float age;
uniform float steps;
uniform float loft;
uniform float p3;
uniform float px;
uniform float cardBottom;
uniform float radius;
uniform float peek;
uniform float stretch;
uniform float bloom;
uniform float bilinear;

float landingPulse(float t) { return t>=0.0 && t<0.65 ? exp(-6.0*t)*sin(sqrt(164.0)*t)/0.532647813016 : 0.0; }
float ease(float a, float b, float x) { return smoothstep(a,b,x); }
float texelValue(float2 t) {
 float hiB=float(atlas.eval(t).r), loB=float(atlas.eval(t+float2(0.0,256.0)).r);
 return hiB*(65280.0/65535.0)+loB*(255.0/65535.0);
}
// The platform's own linear filtering is used where it exists (API 34+); below, the four taps are blended here.
float fetchValue(float2 tx) {
 if(bilinear<0.5) return texelValue(tx);
 float2 f=tx-0.5; float2 i=floor(f); float2 w=f-i;
 return mix(mix(texelValue(i+float2(0.5,0.5)),texelValue(i+float2(1.5,0.5)),w.x),mix(texelValue(i+float2(0.5,1.5)),texelValue(i+float2(1.5,1.5)),w.x),w.y);
}
float readDistance(float2 uv, float index) {
 if(max(abs(uv.x-0.5),abs(uv.y-0.5))>0.5) return 8.0+length(max(abs(uv-0.5)-0.48,float2(0.0)))*256.0;
 float2 cl=clamp(uv,float2(1.5/256.0),float2(1.0-1.5/256.0));
 float2 tx=float2((index+cl.x)*256.0,cl.y*256.0);
 float v=fetchValue(tx);
 return (v-0.5)*192.0+length(max(abs(uv-0.5)-0.496,float2(0.0)))*256.0;
}
float section(float2 uv,float a,float b,float w) {
 if(w<0.001) return readDistance(uv,a);
 if(w>0.999) return readDistance(uv,b);
 return mix(readDistance(uv,a),readDistance(uv,b),w);
}
// Colour blends in (approximately) linear light: red to green passes through warm yellow, not olive.
float3 mixL(float3 a,float3 b,float t) { return sqrt(max(mix(a*a,b*b,t),float3(0.0))); }
float morphAt(float z,float front,float band) { return smoothstep(front-0.5*band,front+0.5*band,z); }
float roundedBox(float2 p,float2 b,float r) { float2 d=abs(p)-b+r; return min(max(d.x,d.y),0.0)+length(max(d,0.0))-r; }
float3 toP3(float3 c) {
 float3 lin=mix(c/12.92,pow((c+0.055)/1.055,float3(2.4)),step(float3(0.04045),c));
 float3 d=float3(dot(lin,float3(0.822593,0.177534,0.0)),dot(lin,float3(0.033200,0.966783,0.0)),dot(lin,float3(0.017085,0.072396,0.910302)));
 return mix(12.92*d,1.055*pow(max(d,float3(0.0)),float3(1.0/2.4))-0.055,step(float3(0.0031308),d));
}
float4 solid(float2 p,float2 nearC,float2 farC,float nearS,float farS,float a,float b,float front,float band,float3 nearColor,float3 farColor,float spec,float seam,float pxd,float maxSteps) {
 float2 halfNear=float2(180.0*nearS+3.0), halfFar=float2(180.0*farS+3.0);
 if(any(lessThan(p,min(nearC-halfNear,farC-halfFar))) || any(greaterThan(p,max(nearC+halfNear,farC+halfFar)))) return float4(0.0);
 float tpt=360.0/256.0;
 float zHit=0.0, z=0.0, minD=1e5, zMin=0.0;
 float2 uvHit=float2(0.0), uvMin=float2(0.0);
 float travel=length(farC-nearC)+abs(farS-nearS)*255.0+0.01;
 bool found=false;
 for(int j=0;j<72;j++) {
  if(float(j)>=maxSteps || z>1.0) break;
  float scale=mix(nearS,farS,z);
  float2 uv=(p-mix(nearC,farC,z))/(360.0*scale)+0.5;
  float d=section(uv,a,b,morphAt(z,front,band));
  float dd=(d-0.8)*tpt*scale;
  if(dd<minD) { minD=dd; zMin=z; uvMin=uv; }
  if(dd<0.0) { zHit=z; uvHit=uv; found=true; break; }
  float dz=max(0.00005,(dd+0.8*tpt*scale)*0.72/travel);
  if(abs(z-front)<band) dz=min(dz,band*0.08);
  z+=dz;
 }
 float coverage=1.0;
 if(!found) {
  float2 capUV=(p-farC)/(360.0*farS)+0.5;
  float capD=section(capUV,a,b,morphAt(1.0,front,band));
  if(capD<0.0) { found=true; zHit=1.0; uvHit=capUV; }
 }
 if(!found) {
  coverage=1.0-smoothstep(0.0,1.25*pxd,minD);
  if(coverage<=0.0) return float4(0.0);
  zHit=zMin; uvHit=uvMin;
 }
 float w=morphAt(zHit,front,band);
 float eps=0.02;
 float dx=section(uvHit+float2(eps,0.0),a,b,w)-section(uvHit-float2(eps,0.0),a,b,w);
 float dy=section(uvHit+float2(0.0,eps),a,b,w)-section(uvHit-float2(0.0,eps),a,b,w);
 float2 n=normalize(float2(dx,dy)+float2(0.00001));
 float lit=0.55+0.45*max(0.0,dot(n,normalize(float2(0.574,-0.819))));
 float3 base=mixL(nearColor,farColor,w);
 // The walls lighten a little with depth, then recede into shade, so the body's far end never reads as a pale blob.
 float3 wall=mix(mix(base,float3(1.0),0.18),mix(base,float3(1.0),0.34),ease(0.0,0.48,zHit));
 wall*=lit*mix(1.0,0.72,ease(0.3,0.9,zHit));
 float groove=0.86+0.14*cos(dot(uvHit,float2(13.0,9.0))+zHit*3.0);
 wall*=groove;
 float shine=exp(-pow((zHit-spec)/0.085,2.0))*0.46;
 // Highlights stay in the body's own hue (a white mix turned the receding base into a pale blob).
 wall+=min(base*1.6,float3(1.0))*shine*0.8*pow(max(0.0,lit),4.0);
 wall+=mix(base,float3(1.0),0.7)*ease(0.85,1.0,zHit)*0.06;
 wall+=min(base*1.5,float3(1.0))*seam*exp(-pow((zHit-front)/(0.22*band),2.0))*0.3;
 float w0=morphAt(0.0,front,band);
 float faceD=section((p-nearC)/(360.0*nearS)+0.5,a,b,w0);
 float facePt=faceD*tpt*nearS;
 float faceMask=1.0-smoothstep(-0.7*pxd,0.7*pxd,facePt);
 float bevel=exp(-abs(facePt+1.1)/1.5)*0.55*(0.3+0.7*lit);
 float3 cap=mixL(nearColor,farColor,w0)*(0.9+0.1*clamp((uvHit.x+uvHit.y)*0.5,0.0,1.0));
 cap+=mix(cap,float3(1.0),0.5)*seam*exp(-pow(front/(0.3*band),2.0))*0.35;
 float3 result=mix(wall,cap,faceMask);
 if(zHit>0.999) result=farColor*0.95+float3(0.18)*exp(-abs(facePt+1.1)/1.5);
 result+=float3(1.0,0.87,0.93)*bevel;
 return float4(result,coverage);
}
half4 main(float2 xy) {
 float2 p=xy*402.0/size.x;
 float h=size.y*402.0/size.x;
 float pxd=px>0.0 ? px : 0.34;
 float qv=clamp(q,0.0,1.0);
 float swing=sin(3.14159265*qv);
 float angle=steer.x;
 float2 d0=normalize(mix(direction,nextDirection,ease(0.12,0.92,qv))+float2(0.00001));
 float2 dir=float2(d0.x*cos(angle)-d0.y*sin(angle),d0.x*sin(angle)+d0.y*cos(angle));
 dir.y+=steer.y;
 dir=normalize(dir+float2(0.00001));
 float2 c=center+dir*16.0*swing;
 float fieldMix=ease(0.25,0.75,qv);
 float3 a=mixL(fieldA,nextA,fieldMix),b=mixL(fieldB,nextB,fieldMix);
 float field=ease(0.0,h,p.y)*0.9+0.1*ease(0.0,402.0,p.x);
 float3 color=mix(a,mix(a,b,0.25),ease(0.0,0.5,field));
 color=mix(color,b,ease(0.3,1.0,field));
 float2 vanish=c-dir*360.0;
 float2 v=p-vanish;
 float theta=atan(v.y,v.x)+0.0349066*(8.0*sin(phase*6.2831853)/6.2831853);
 float rays=pow(max(0.0,cos(theta*9.0+0.22*sin(theta*5.0))),26.0);
 color+=mix(b,float3(1.0,0.83,0.9),0.2)*rays*0.09*ease(80.0,460.0,length(v));
 float2 end=c+dir*610.0*(1.0+stretch+0.16*swing);
 // The far end is off screen: the morph front sweeps the visible depth (z 0..0.5) so the whole loft is seen.
 float band=0.3;
 float front=loft>0.5 ? 0.5+0.5*band-qv*(0.5+band) : (qv<0.5 ? 2.0 : -1.0);
 float spec=age<0.4?mix(0.0,0.6,ease(0.0,0.4,age)):0.6;
 if(qv>0.001) spec=mix(0.6,clamp(front,0.0,1.0),ease(0.0,0.12,qv));
 float4 body;
 float capScale=(1.0+0.03*max(0.0,landingPulse(age)))*(1.0-0.05*swing);
 if(reduced>0.5) {
  float4 oldBody=solid(p,center,center+direction*610.0,1.0,1.1,current,current,2.0,band,faceA,faceA,0.6,0.0,pxd,steps);
  float4 newBody=solid(p,center,center+nextDirection*610.0,1.0,1.1,next,next,2.0,band,faceB,faceB,0.6,0.0,pxd,steps);
  float3 oldField=mix(fieldA,fieldB,ease(0.15,1.0,field));
  float3 newField=mix(nextA,nextB,ease(0.15,1.0,field));
  color=mix(mix(oldField,oldBody.rgb,oldBody.a),mix(newField,newBody.rgb,newBody.a),qv);
  body=float4(0.0);
 } else {
  body=solid(p,c,end,capScale,1.1+stretch,current,next,front,band,faceA,faceB,spec,swing,pxd,steps);
 }
 if(bloom>0.5) {
  float3 glow=clamp(mix(body.rgb,float3(1.0),0.2),0.0,1.0);
  return half4(half3(mix(glow,toP3(glow),p3)*body.a),body.a);
 }
 color=mix(color,body.rgb,body.a);
 float rise=8.0*landingPulse(age);
 float card=cardBottom;
 float upper=roundedBox(p-float2(201.0,card*0.5),float2(201.0,card*0.5),radius);
 float lower=roundedBox(p-float2(201.0,(h+card+8.0-rise+radius)*0.5),float2(201.0,(h-card-8.0+rise+radius)*0.5),radius);
 float3 gap=float3(0.025,0.012,0.022);
 float aa=0.6*pxd;
 if(p.y>card-radius) color=mix(gap,color,1.0-ease(-aa,aa,upper));
 float3 peekColor=mixL(nextB,afterB,ease(0.3,0.9,qv));
 float sheen=0.06*(1.0-ease(0.0,22.0,p.y-(card+8.0-rise)));
 color=mix(color,peekColor+sheen,1.0-ease(-aa,aa,lower));
 color=clamp(color,0.0,1.0);
 return half4(half3(mix(color,toP3(color),p3)),1.0);
}
"""

internal class ExtrusionRecapStoryUniforms {
    var width = 1f
    var height = 1f
    var centerX = 0f
    var centerY = 0f
    var direction = floatArrayOf(0f, 0f)
    var nextDirection = floatArrayOf(0f, 0f)
    var q = 0f
    var reduced = 0f
    var current = 0f
    var next = 0f
    var count = 1f
    var fieldA = floatArrayOf(0f, 0f, 0f)
    var fieldB = floatArrayOf(0f, 0f, 0f)
    var nextA = floatArrayOf(0f, 0f, 0f)
    var nextB = floatArrayOf(0f, 0f, 0f)
    var afterB = floatArrayOf(0f, 0f, 0f)
    var faceA = floatArrayOf(0f, 0f, 0f)
    var faceB = floatArrayOf(0f, 0f, 0f)
    var steerX = 0f
    var steerY = 0f
    var phase = 0f
    var age = 10f
    var steps = 64f
    var loft = 1f
    var px = 0.34f
    var cardBottom = 0f
    var radius = 28f
    var peek = 48f
    var stretch = 0f
    var bloom = 0f
}

@RequiresApi(33)
internal class ExtrusionRecapStoryShader(atlas: Bitmap) {
    val shader = RuntimeShader(ExtrusionRecapStoryShaderSource)
    val paint = Paint().apply { shader = this@ExtrusionRecapStoryShader.shader }

    init {
        val bitmapShader = BitmapShader(atlas, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val native = Build.VERSION.SDK_INT >= 34
        if (native) bitmapShader.filterMode = BitmapShader.FILTER_MODE_LINEAR
        shader.setInputShader("atlas", bitmapShader)
        shader.setFloatUniform("bilinear", if (native) 0f else 1f)
        shader.setFloatUniform("p3", 0f)
    }

    fun apply(u: ExtrusionRecapStoryUniforms) {
        shader.setFloatUniform("size", u.width, u.height)
        shader.setFloatUniform("center", u.centerX, u.centerY)
        shader.setFloatUniform("direction", u.direction)
        shader.setFloatUniform("nextDirection", u.nextDirection)
        shader.setFloatUniform("q", u.q)
        shader.setFloatUniform("reduced", u.reduced)
        shader.setFloatUniform("current", u.current)
        shader.setFloatUniform("next", u.next)
        shader.setFloatUniform("count", u.count)
        shader.setFloatUniform("fieldA", u.fieldA)
        shader.setFloatUniform("fieldB", u.fieldB)
        shader.setFloatUniform("nextA", u.nextA)
        shader.setFloatUniform("nextB", u.nextB)
        shader.setFloatUniform("afterB", u.afterB)
        shader.setFloatUniform("faceA", u.faceA)
        shader.setFloatUniform("faceB", u.faceB)
        shader.setFloatUniform("steer", u.steerX, u.steerY)
        shader.setFloatUniform("phase", u.phase)
        shader.setFloatUniform("age", u.age)
        shader.setFloatUniform("steps", u.steps)
        shader.setFloatUniform("loft", u.loft)
        shader.setFloatUniform("px", u.px)
        shader.setFloatUniform("cardBottom", u.cardBottom)
        shader.setFloatUniform("radius", u.radius)
        shader.setFloatUniform("peek", u.peek)
        shader.setFloatUniform("stretch", u.stretch)
        shader.setFloatUniform("bloom", u.bloom)
    }
}
