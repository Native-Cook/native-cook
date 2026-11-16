package com.ngocdevv.nativecook.blocks.charts.thermalglasstiles

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * The RN block's thermal field (`tgt-shaders.ts`, FIELD_SKSL) as AGSL (API 33+), unchanged apart from a renamed helper: a runner's
 * heat atlas (6 x 4 frames of the stride) and a palette lookup, with a trail, grain and a touch warmth. `outputP3` stays 0.
 */
internal const val ThermalGlassTilesFieldSource = """
uniform shader image;
uniform shader paletteImage;
uniform float2 size;
uniform float2 offset;
uniform float scale;
uniform float phase;
uniform float effort;
uniform float streak;
uniform float time;
uniform float3 touch;
uniform float peak;
uniform float grain;
uniform float wake;
uniform float direction;
uniform float sourceMode;
uniform float2 sourceSize;
uniform float2 atlas;
uniform float frame;
uniform float paletteMode;
uniform float paletteRows;
uniform float outputP3;
uniform float3 figure;
float hash21(float2 p) { return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453); }
float nz(float2 p) {
 float2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
 return mix(mix(hash21(i),hash21(i+float2(1.0,0.0)),f.x),mix(hash21(i+float2(0.0,1.0)),hash21(i+float2(1.0)),f.x),f.y);
}
float3 linearize(float3 c) { return mix(c/12.92,pow((c+.055)/1.055,float3(2.4)),step(float3(.04045),c)); }
float3 encode(float3 c) { return mix(c*12.92,1.055*pow(max(c,float3(0.0)),float3(1.0/2.4))-.055,step(float3(.0031308),c)); }
float2 atlasUV(float2 uv,float f,float2 grid,float2 texSize) {
 float idx=mod(floor(f),grid.x*grid.y); if(idx<0.0) idx+=grid.x*grid.y;
 float2 cell=float2(mod(idx,grid.x),floor(idx/grid.x));
 float2 pad=0.5*grid/texSize;
 return (cell+clamp(uv,pad,1.0-pad))/grid*texSize;
}
float4 imageHeat(float2 p,float f) {
 // figure = (anchor x, anchor y, scale): the runner is drawn larger about the anchor, the field around it is not.
 float2 uv=(figure.xy+(p-figure.xy)/max(figure.z,0.01))/float2(402.0,874.0);
 return image.eval(atlasUV(uv,f,atlas,sourceSize));
}
half4 main(float2 xy) {
 float2 p=(xy-offset)/scale;
 float loopAngle=time*0.78539816;
 float2 flow=float2(cos(loopAngle),sin(loopAngle))*1.2;
 float anisotropy=mix(1.0,6.0,streak);
 float2 np=float2(p.x/anisotropy,p.y)*.012;
 float n=nz(np+flow)*.7+nz(np*2.1+flow*.6)*.3;
 float haze=peak*(1.0-smoothstep(270.0,355.0,p.y));
 p.x+=haze*(nz(p*.04+float2(0.0,-time*1.6))-.5)*5.0;
 float dy=(p.y-572.0)/92.0, ground=(p.y-763.0)/123.0;
 float base=.70+.055*n+.07*sin(p.y*.009+1.7);
 base-=.30*exp(-dy*dy); base+=.15*exp(-ground*ground);
 float f0=floor(frame), fraction=fract(frame);
 float4 body=mix(imageHeat(p,f0),imageHeat(p,f0+1.0),fraction);
 float ramp=smoothstep(.50,.90,effort);
 float temp=mix(.018+.008*n,dot(body.rgb,float3(.2126,.7152,.0722)),ramp);
 float heat=mix(base,temp,body.a);
 float trail=0.0;
 for(int k=1;k<=3;k++) {
   float4 past=imageHeat(p-float2(wake*float(k)*direction/3.0,0.0),frame-2.0*float(k)*direction);
   trail=max(trail,past.a*pow(.6,float(k)));
 }
 heat=mix(heat,.27+.52*effort,trail*.35*(1.0-body.a)*step(.001,wake));
 heat=mix(heat,.28+.49*effort,.4*body.a*(1.0-body.a));
 heat+=.12*exp(-dot(p-touch.xy,p-touch.xy)/(46.0*46.0))*exp(-max(0.0,touch.z)/.5)*step(0.0,touch.z);
 if(sourceMode<.5) heat=base;
 float row=paletteMode<.5 ? clamp(effort,0.0,1.0)*63.0 : 63.0+paletteMode;
 float3 c=paletteImage.eval(float2(clamp(heat,0.0,1.0)*255.0+.5,row+.5)).rgb;
 if(paletteMode>3.5) c=paletteImage.eval(float2(clamp(heat,0.0,1.0)*255.0+.5,effort*max(0.0,paletteRows-1.0)+.5)).rgb;
 float film=hash21(floor(xy)+floor(time*12.0)*float2(71.0,113.0))-.5;
 c+=film*grain;
 if(outputP3>.5) {
  float3 lin=linearize(clamp(c,0.0,1.0));
  c=encode(float3(dot(lin,float3(.82259287,.17753395,0.0)),dot(lin,float3(.03319951,.9667835,0.0)),dot(lin,float3(.01708535,.07239572,.91030148))));
 }
 return half4(half3(clamp(c,0.0,1.0)),1.0);
}
"""

/** The glass lens (GLASS_SKSL) over any backdrop shader: refraction, dispersion, frost, a feathered label well and one rim of light. */
internal const val ThermalGlassTilesGlassSource = """
uniform shader backdrop;
uniform float4 rect;
uniform float radius;
uniform float circle;
uniform float frost;
uniform float refraction;
uniform float dispersion;
uniform float lens;
uniform float peak;
uniform float time;
uniform float grain;
uniform float inkDark;
uniform float4 labelRect;
uniform float materialScale;
float hashGlass(float2 p) { return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453); }
float boxSDF(float2 p,float2 b,float r) { float2 q=abs(p)-b+r; return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r; }
float sdf(float2 p,float4 rr,float r,float dome) {
 float2 ctr=rr.xy+rr.zw*.5;
 if(dome>.5) return length(p-ctr)-rr.z*.5;
 return boxSDF(p-ctr,rr.zw*.5,r);
}
half4 main(float2 p) {
 float2 ctr=rect.xy+rect.zw*.5;
 float d=sdf(p,rect,radius,circle);
 float2 grad=normalize(float2(sdf(p+float2(.5,0.0),rect,radius,circle)-sdf(p-float2(.5,0.0),rect,radius,circle),sdf(p+float2(0.0,.5),rect,radius,circle)-sdf(p-float2(0.0,.5),rect,radius,circle))+float2(.00001));
 float ms=materialScale;
 float edge=1.0-smoothstep(0.0,18.0*ms,-d);
 float profile=pow(clamp(1.0+d/max(radius,1.0),0.0,1.0),3.0);
 float2 displacement=-grad*refraction*profile;
 if(circle>.5) {
  float2 q=(p-ctr)/(rect.z*.5);
  // A smooth dome: strongest at the edge but without the steep 1/sqrt wall that squeezed the field into a dark second ring.
  displacement=-q*22.0*ms*(.45+.55*dot(q,q));
 }
 float2 uv=ctr+(p-ctr)/(1.0+lens)+displacement;
 float3 c=backdrop.eval(uv).rgb;
 c.r=backdrop.eval(uv+grad*dispersion*edge).r;
 c.b=backdrop.eval(uv-grad*dispersion*edge).b;
 // Neutral frost; label well is feathered by 12pt, not a visible extra panel.
 float labelD=boxSDF(p-(labelRect.xy+labelRect.zw*.5),labelRect.zw*.5,12.0*ms);
 float label=1.0-smoothstep(-3.0*ms,12.0*ms,labelD);
 float f=mix(frost,.46,label);
 c=mix(c,float3(dot(c,float3(.2126,.7152,.0722))),f*.38);
 c=mix(c,float3(.74,.75,.74),f*.48);
 float luminance=dot(c,float3(.2126,.7152,.0722));
 // Compress label luminance safely out of the ambiguous band; white ink stays >=4.5:1.
 float inkChoice=inkDark;
 if(inkChoice<0.0) inkChoice=step(.55,dot(backdrop.eval(labelRect.xy+labelRect.zw*.5).rgb,float3(.2126,.7152,.0722)));
 float target=inkChoice>.5 ? max(.87,luminance) : min(.39,luminance);
 float3 readable=inkChoice>.5 ? mix(c,float3(.96,.93,.86),.86) : c*target/max(luminance,.001);
 c=mix(c,readable,label);
 // One rim: a single hairline of light just inside the edge, brightest toward the key light (top left) and never gone.
 // No outside colour is gathered into the edge (that read as a halo) and the inner shade is broad, not a second line.
 float key=clamp(dot(grad,normalize(float2(-1.0,-1.0))),0.0,1.0);
 float inner=1.0-smoothstep(0.0,9.0*ms,-d);
 c=mix(c,c*.94,(1.0-key)*inner*.8);
 c+=float3(.94,.95,.92)*exp(-abs(d+.55*ms)/(.5*ms))*(.14+.34*key);
 c+=float3(.24,.12,.035)*peak*inner*.5;
 c+=(hashGlass(floor(p)+floor(time*12.0)*71.0)-.5)*grain;
 return half4(half3(clamp(c,0.0,1.0)),1.0);
}
"""

/** Uniforms of the field shader; one object mutated every frame. */
internal class ThermalGlassTilesFieldUniforms {
    var width = 0f
    var height = 0f
    var ox = 0f
    var oy = 0f
    var scale = 1f
    var phase = 0.72f
    var effort = 0.39655f
    var streak = 0f
    var time = 0f
    val touch = floatArrayOf(0f, 0f, -1f)
    var peak = 0f
    var grain = 0.04f
    var wake = 20f
    var direction = 1f
    var sourceW = 1f
    var sourceH = 1f
    var frame = 0f
    var paletteRows = 1f
}

@RequiresApi(33)
internal class ThermalGlassTilesFieldShader(atlas: Bitmap, palette: Bitmap) {
    val shader = RuntimeShader(ThermalGlassTilesFieldSource)
    val paint = Paint().apply { shader = this@ThermalGlassTilesFieldShader.shader }
    private val sourceSize = floatArrayOf(atlas.width.toFloat(), atlas.height.toFloat())

    init {
        shader.setInputShader("image", linear(BitmapShader(atlas, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)))
        shader.setInputShader("paletteImage", linear(BitmapShader(palette, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)))
        shader.setFloatUniform("sourceMode", 1f)
        shader.setFloatUniform("sourceSize", sourceSize)
        shader.setFloatUniform("atlas", 6f, 4f)
        shader.setFloatUniform("paletteMode", 0f)
        shader.setFloatUniform("outputP3", 0f)
        shader.setFloatUniform("figure", 201f, 250f, 1.173f)
    }

    private fun linear(s: BitmapShader): BitmapShader {
        if (Build.VERSION.SDK_INT >= 34) s.filterMode = BitmapShader.FILTER_MODE_LINEAR
        return s
    }

    fun apply(u: ThermalGlassTilesFieldUniforms) {
        shader.setFloatUniform("size", u.width, u.height)
        shader.setFloatUniform("offset", u.ox, u.oy)
        shader.setFloatUniform("scale", u.scale)
        shader.setFloatUniform("phase", u.phase)
        shader.setFloatUniform("effort", u.effort)
        shader.setFloatUniform("streak", u.streak)
        shader.setFloatUniform("time", u.time)
        shader.setFloatUniform("touch", u.touch)
        shader.setFloatUniform("peak", u.peak)
        shader.setFloatUniform("grain", u.grain)
        shader.setFloatUniform("wake", u.wake)
        shader.setFloatUniform("direction", u.direction)
        shader.setFloatUniform("frame", u.frame)
        shader.setFloatUniform("paletteRows", u.paletteRows)
    }
}

@RequiresApi(33)
internal class ThermalGlassTilesGlassShader(backdrop: Shader) {
    val shader = RuntimeShader(ThermalGlassTilesGlassSource)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@ThermalGlassTilesGlassShader.shader }

    init {
        shader.setInputShader("backdrop", backdrop)
    }

    fun apply(
        x: Float, y: Float, w: Float, h: Float, radius: Float, circle: Boolean, peak: Float, time: Float, inkDark: Float,
        label: FloatArray, materialScale: Float,
    ) {
        shader.setFloatUniform("rect", x, y, w, h)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("circle", if (circle) 1f else 0f)
        shader.setFloatUniform("frost", 0.3f)
        shader.setFloatUniform("refraction", 14f * materialScale)
        shader.setFloatUniform("dispersion", 1.5f * materialScale)
        shader.setFloatUniform("lens", 0.06f)
        shader.setFloatUniform("peak", peak)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("grain", 0.04f)
        shader.setFloatUniform("inkDark", inkDark)
        shader.setFloatUniform("labelRect", label)
        shader.setFloatUniform("materialScale", materialScale)
    }
}

