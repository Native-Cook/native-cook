package com.ngocdevv.nativecook.blocks.cards.prismaticgameprofile

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import kotlin.math.*

/** Frozen RN card/Gold/Enamel SkSL and the artwork-facet Diamond WGSL response in cached AGSL. */
internal class PrismaticGameProfileMaterials(private val artwork: PrismaticGameProfileArtwork) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect=RectF()
    private val card: Shader?=if(Build.VERSION.SDK_INT>=33)runCatching { RuntimeShader(CARD) }.getOrNull()else null
    private val effects: List<Shader?> = listOf(GOLD,ENAMEL,PRISM).map { source ->
        if(Build.VERSION.SDK_INT>=33)runCatching { RuntimeShader(source) }.getOrNull()else null
    }
    private val caustic: Shader?=if(Build.VERSION.SDK_INT>=33)runCatching { RuntimeShader(CAUSTIC) }.getOrNull()else null
    private val names=listOf("crown","shield","diamond")
    private val inputShaders=names.map { name ->
        val bitmap=artwork.bitmap(name); val scale=min(114f/bitmap.width,126f/bitmap.height)
        val matrix=Matrix().apply { setValues(floatArrayOf(scale,0f,(114-bitmap.width*scale)/2,0f,scale,(126-bitmap.height*scale)/2,0f,0f,1f)) }
        BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
    }
    val hasRuntimeMaterial get() = card!=null
    fun drawCard(canvas: Canvas, frame: PrismaticGameProfileMotion.Frame, origin: PrismaticGameProfileMotion.Bounds, source: PrismaticGameProfileMotion.Source, identity: PrismaticGameProfileMotion.Identity) {
        val p=frame.progress;if(p<=0)return
        val b=PrismaticGameProfileMotion.surface(p,origin,frame.reduced)
        paint.color=Color.WHITE;paint.alpha=255;paint.style=Paint.Style.FILL
        if(Build.VERSION.SDK_INT>=33 && card is RuntimeShader) {
            val avoid=PrismaticGameProfileMotion.avoidance(p,frame.reduced,source,identity)
            val lx=if(frame.reduced)0f else (frame.tiltX/6).coerceIn(-1f,1f)
            val ly=if(frame.reduced)0f else (frame.tiltY/6).coerceIn(-1f,1f)
            val angle=(frame.tiltX*b.height*.072f+frame.tiltY*b.width*.09f)/max(1f,b.height*.072f+b.width*.09f)
            val engagement=PrismaticGameProfileMotion.smooth((angle-.5f)/2)
            val distance=(angle-4.5f)/1.35f
            val response=engagement*exp(-.5f*distance*distance)
            val optical=if(frame.reduced)0f else max(frame.catchStrength,.75f*response)
            card.setFloatUniform("bounds",b.x,b.y,b.width,b.height)
            card.setFloatUniform("shape",b.radius,348f/590,p,if(frame.reduced)1f else 0f)
            card.setFloatUniform("light",lx,ly)
            card.setFloatUniform("catchStrength",if(frame.reduced)0f else frame.catchStrength)
            card.setFloatUniform("opticalStrength",optical)
            card.setFloatUniform("peelCurl",PrismaticGameProfileMotion.formation(p,frame.reduced).curl)
            card.setFloatUniform("peelAvoidance",avoid.x,avoid.y,avoid.width,avoid.height)
            paint.shader=card
            canvas.save();canvas.translate(0f,40f);canvas.drawRect(0f,0f,393f,690f,paint);canvas.restore()
        } else {
            // API26–32/unsupported shader: coherent pearl/gold geometry, no curl/foil.
            val opacity=if(frame.reduced)p else PrismaticGameProfileMotion.step(p,.015f,.17f)
            paint.shader=null;paint.color=Color.argb((34*opacity).roundToInt(),14,17,23)
            rect.set(b.x+1.5f,b.y+4,b.right+1.5f,b.bottom+4);canvas.drawRoundRect(rect,b.radius,b.radius,paint)
            paint.color=Color.argb((255*opacity).roundToInt(),255,251,243)
            rect.set(b.x,b.y,b.right,b.bottom);canvas.drawRoundRect(rect,b.radius,b.radius,paint)
            paint.color=Color.argb((255*opacity).roundToInt(),234,205,138);paint.style=Paint.Style.STROKE;paint.strokeWidth=3f
            canvas.drawRoundRect(rect,b.radius,b.radius,paint);paint.style=Paint.Style.FILL
        }
        paint.shader=null;paint.alpha=255
    }
    fun drawBadge(canvas: Canvas,index: Int,frame: PrismaticGameProfileMotion.Frame) {
        val effect=effects[index];val power=if(!frame.reduced&&frame.material==index)frame.power else 0f
        paint.color=Color.WHITE;paint.alpha=255;paint.style=Paint.Style.FILL
        if(Build.VERSION.SDK_INT>=33&&effect is RuntimeShader) {
            effect.setInputShader("artwork",inputShaders[index]);effect.setFloatUniform("phase",if(frame.material==index)frame.phase else 0f);effect.setFloatUniform("power",power)
            paint.shader=effect;canvas.drawRect(0f,0f,114f,126f,paint);paint.shader=null
        } else {
            val bitmap=artwork.bitmap(names[index]);val scale=min(114f/bitmap.width,126f/bitmap.height)
            rect.set((114-bitmap.width*scale)/2,(126-bitmap.height*scale)/2,(114+bitmap.width*scale)/2,(126+bitmap.height*scale)/2)
            canvas.drawBitmap(bitmap,null,rect,paint)
        }
    }
    fun drawCaustic(canvas: Canvas,index: Int,frame: PrismaticGameProfileMotion.Frame) {
        if(index!=0||frame.reduced||frame.material!=index||frame.power<=0)return
        if(Build.VERSION.SDK_INT>=33&&caustic is RuntimeShader) {
            caustic.setFloatUniform("phase",frame.phase);caustic.setFloatUniform("power",frame.power);caustic.setFloatUniform("kind",if(index==2)1f else 0f)
            paint.color=Color.WHITE;paint.alpha=255;paint.shader=caustic;canvas.drawRect(0f,0f,158f,158f,paint);paint.shader=null
        }
    }
    companion object {
        private const val CARD = """

uniform float4 bounds;
uniform float4 shape;
uniform float2 light;
uniform float catchStrength;
uniform float opticalStrength;
uniform float peelCurl;
uniform float4 peelAvoidance;

float roundedDistance(float2 p, float2 halfSize, float radius) {
  float2 q=abs(p)-halfSize+radius;
  return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
}
float coverage(float distance) { return 1.0-smoothstep(-0.5,0.5,distance); }
// The only deforming region is a 20 pt blank lip along the top material edge.
// Its projected silhouette recedes as the warm underside rolls toward the face.
float topContour(float x, float width, float radius) {
  float corner=max(0.0,radius-min(x,width-x));
  return radius-sqrt(max(0.0,radius*radius-corner*corner));
}
float curlEnvelope(float x, float width) {
  float u=clamp(x/width,0.0,1.0);
  float envelope=smoothstep(0.0,0.18,u)*(1.0-smoothstep(0.82,1.0,u));
  if (peelAvoidance.z<=0.0 || peelAvoidance.w<=0.0) return envelope;
  float4 avoid=float4(peelAvoidance.xy-bounds.xy,peelAvoidance.zw);
  float top=topContour(x,width,shape.x);
  // The 10 pt guard includes the backing/cast's 4 pt translation, so every
  // additional channel clears rigid actors by at least 6 pt; a 24 pt taper
  // lets the rolled edge flatten gradually without forming a vertical notch.
  float horizontal=smoothstep(avoid.x-34.0,avoid.x-10.0,x)*
    (1.0-smoothstep(avoid.x+avoid.z+10.0,avoid.x+avoid.z+34.0,x));
  float vertical=smoothstep(avoid.y-34.0,avoid.y-10.0,top+26.0)*
    (1.0-smoothstep(avoid.y+avoid.w+10.0,avoid.y+avoid.w+34.0,top));
  return envelope*(1.0-horizontal*vertical);
}
float curlTrim(float x, float width) {
  return 7.0*peelCurl*curlEnvelope(x,width)*(0.72+0.28*clamp(x/width,0.0,1.0));
}
float peeledDistance(float2 p, float2 size, float radius) {
  float intact=roundedDistance(p-size*0.5,size*0.5,radius);
  if (p.y>radius+20.0) return intact;
  float trim=curlTrim(p.x,size.x);
  if (trim<=0.0) return intact;
  return roundedDistance(p-float2(0.0,trim)-size*0.5,size*0.5,radius);
}
// Ray/rounded-rectangle intersection keeps the source continuously on its real edge.
float2 perimeterSource(float2 halfSize, float radius, float angle) {
  float2 direction=float2(cos(angle),sin(angle));
  float2 absolute=abs(direction);
  float extent=min(halfSize.x/max(absolute.x,0.0001),halfSize.y/max(absolute.y,0.0001));
  float2 point=absolute*extent;
  float2 corner=halfSize-radius;
  if (point.x>corner.x && point.y>corner.y) {
    float projection=dot(absolute,corner);
    extent=projection+sqrt(max(0.0,projection*projection-dot(corner,corner)+radius*radius));
  }
  return direction*extent;
}
float sourceWindow(float2 delta, float2 normal, float tangentWidth, float normalWidth) {
  float t=dot(delta,float2(-normal.y,normal.x))/tangentWidth;
  float n=dot(delta,normal)/normalWidth;
  return exp(-0.5*(t*t+n*n));
}
half4 main(float2 pixel) {
  if (shape.z<=0.0) return half4(0.0);
  float2 stage=pixel+float2(0.0,40.0);
  float2 size=bounds.zw;
  float radius=shape.x;
  float2 p=stage-bounds.xy;
  float2 centre=size*0.5;
  float2 halfSize=size*0.5;
  float2 uv=p/size;
  if (p.x < -28.0 || p.x > size.x+28.0 || p.y < -28.0 || p.y > size.y+28.0) return half4(0.0);
  float formation=shape.w>0.5 ? 0.0 : smoothstep(0.80,0.98,shape.z);
  float eclipse=shape.w>0.5 ? 0.0 : smoothstep(0.72,0.98,shape.z);
  float optical=clamp(max(catchStrength,opticalStrength),0.0,1.0)*eclipse;
  float shift=light.y*size.x*0.09+light.x*size.y*0.072;
  float drive=clamp(shift/max(1.0,size.x*0.09+size.y*0.072),-1.0,1.0);
  // A shallow tilt stays on the corner arc; larger tilt explores the raised edge.
  float sourceDrive=drive*abs(drive);
  float frontDistance=roundedDistance(p-centre,halfSize,radius);
  if (peelCurl>0.0) frontDistance=peeledDistance(p,size,radius);
  float frontAlpha=coverage(frontDistance);
  float backDistance=roundedDistance(p-centre-float2(2.3,3.3),halfSize,radius);
  if (peelCurl>0.0) backDistance=peeledDistance(p-float2(2.3,3.3),size,radius);
  float backAlpha=coverage(backDistance);
  float shadowDistance=max(0.0,roundedDistance(p-centre-float2(1.5,4.0),halfSize,radius));
  if (peelCurl>0.0) shadowDistance=max(0.0,peeledDistance(p-float2(1.5,4.0),size,radius));
  float canvasEdge=min(min(pixel.x,393.0-pixel.x),min(pixel.y,690.0-pixel.y));
  float edgeFade=smoothstep(0.0,6.0,canvasEdge);
  float3 outputColor=float3(0.0);
  float outputAlpha=0.0;
  // Emission is behind the opaque card, never a bloom layer over face or text.
  if (frontAlpha<1.0 && eclipse>0.0) {
    float2 source=perimeterSource(halfSize,radius,-2.074+3.14159265*sourceDrive);
    float2 cornerDelta=max(abs(source)-(halfSize-radius),0.0)*sign(source);
    float2 normal=cornerDelta/max(length(cornerDelta),0.0001);
    float2 delta=p-centre-source;
    float outside=max(0.0,frontDistance);
    float aureole=exp(-0.5*outside*outside/81.0)*sourceWindow(delta,normal,58.0,36.0);
    float leak=exp(-outside/1.25)*sourceWindow(delta,normal,70.0,50.0);
    // Elongation follows the edge; the intact front clips it into a crescent.
    float bloom=sourceWindow(delta,normal,22.0,7.0);
    float warmAlpha=clamp(eclipse*(0.13+0.18*optical)*aureole+eclipse*(0.24+0.22*optical)*leak+0.58*optical*bloom,0.0,0.88)*edgeFade;
    float3 warmColor=mix(float3(0.94,0.87,0.99),float3(1.0,0.94,0.73),0.42+0.44*optical);
    float2 oppositeDelta=p-centre+source;
    float reflected=exp(-0.5*outside*outside/36.0)*sourceWindow(oppositeDelta,-normal,30.0,22.0);
    float reflectedAlpha=eclipse*(0.055+0.045*optical)*reflected*edgeFade;
    float3 reflectedColor=mix(float3(0.62,0.91,1.0),float3(0.79,0.72,1.0),0.5+0.5*drive);
    outputColor=reflectedColor*reflectedAlpha+warmColor*warmAlpha*(1.0-reflectedAlpha);
    outputAlpha=reflectedAlpha+warmAlpha*(1.0-reflectedAlpha);
  }
  float shadowAlpha=0.17*exp(-shadowDistance*shadowDistance/18.0)*edgeFade;
  outputColor=float3(0.055,0.065,0.09)*shadowAlpha+outputColor*(1.0-shadowAlpha);
  outputAlpha=shadowAlpha+outputAlpha*(1.0-shadowAlpha);
  float3 backColor=mix(float3(0.52,0.31,0.10),float3(0.86,0.64,0.28),clamp(uv.y,0.0,1.0));
  outputColor=backColor*backAlpha+outputColor*(1.0-backAlpha);
  outputAlpha=backAlpha+outputAlpha*(1.0-backAlpha);
  float opacity=shape.w>0.5 ? shape.z : smoothstep(0.015,0.17,shape.z);
  if (frontAlpha<=0.0) return half4(outputColor*opacity,outputAlpha*opacity);

  float innerDistance=roundedDistance(p-centre,halfSize-3.0,max(1.0,radius-3.0));
  if (peelCurl>0.0 && p.y<radius+20.0) {
    float trim=curlTrim(p.x,size.x);
    if (trim>0.0) innerDistance=roundedDistance(p-float2(0.0,trim)-centre,
      halfSize-3.0,max(1.0,radius-3.0));
  }
  float pearlAlpha=coverage(innerDistance);
  float goldValue=clamp(0.58+0.16*(1.0-uv.y)+0.14*(1.0-uv.x)+0.045*(light.x-light.y),0.0,1.0);
  float3 gold=mix(float3(0.74,0.49,0.17),float3(1.0,0.89,0.63),goldValue);
  float ridge=exp(-pow((frontDistance+1.05)/0.48,2.0));
  gold=mix(gold,float3(1.0,0.98,0.84),0.72*ridge);
  float3 pearl=mix(float3(1.0,0.995,0.979),float3(0.974,0.951,0.908),clamp(uv.y,0.0,1.0));
  pearl+=0.012*(light.x*(uv.x-0.5)-light.y*(uv.y-0.5));
  float lip=exp(-max(0.0,-innerDistance)/1.4);
  pearl*=1.0-0.026*lip;

  // The same tilted light plane crosses the lower pearl and the gold rim.
  float distance=(0.55*p.x+p.y-size.y*0.87-shift)/1.141271;
  float core=exp(-0.5*distance*distance/49.0);
  float shoulder=exp(-0.5*distance*distance/324.0);
  float filmPhase=distance*0.11+0.60*light.y-0.35*light.x;
  float3 spectrum=0.5+0.5*cos(float3(0.0,2.1,4.2)+filmPhase);
  float3 film=0.58+0.42*spectrum;
  float foil=formation*(0.50*core+0.22*shoulder);
  float lower=smoothstep(size.y*shape.y+1.0,size.y*shape.y+17.0,p.y);
  pearl=mix(pearl,film,foil*lower);
  pearl+=float3(0.08+0.035*optical)*formation*core*lower;
  gold=mix(gold,mix(film,float3(1.0),0.45),formation*(0.82*core+0.18*shoulder));
  float3 frontColor=clamp(mix(gold,pearl,pearlAlpha),0.0,1.0);
  if (peelCurl>0.0 && p.y<radius+26.0) {
    float envelope=curlEnvelope(p.x,size.x);
    float depth=20.0*peelCurl*envelope;
    float top=p.y-topContour(p.x,size.x,radius);
    float trim=curlTrim(p.x,size.x);
    float turn=clamp((top-trim)/max(0.5,depth-trim),0.0,1.0);
    // A convex warm reverse face, a narrow reflected rim and an occluded return.
    // Upper-left illumination casts onto the face below/right of the hinge.
    float reverseFace=smoothstep(trim+1.1,trim+2.4,top)*(1.0-smoothstep(depth-1.0,depth+0.5,top));
    float belly=exp(-pow((turn-0.28)/0.28,2.0));
    float returnShade=smoothstep(0.52,1.0,turn);
    float3 underside=mix(float3(0.88,0.79,0.62),float3(1.0,0.96,0.85),belly);
    underside*=1.0-0.19*returnShade;
    underside+=0.018*(1.0-uv.x);
    frontColor=mix(frontColor,underside,reverseFace*peelCurl);
    float reflectedRim=exp(-pow((top-trim-1.15)/0.52,2.0))*peelCurl*envelope;
    frontColor=mix(frontColor,float3(1.0,0.965,0.82),0.72*reflectedRim);
    float castShadow=smoothstep(depth-0.5,depth+0.6,top)*exp(-max(0.0,top-depth)/2.0);
    float contact=exp(-pow((top-depth-0.25)/0.65,2.0));
    float shadowSupport=1.0-smoothstep(depth+2.0,depth+3.0,top);
    float localShadow=(0.15*castShadow+0.13*contact)*peelCurl*envelope*shadowSupport;
    frontColor*=1.0-localShadow;
  }
  return half4((frontColor*frontAlpha+outputColor*(1.0-frontAlpha))*opacity,
    (frontAlpha+outputAlpha*(1.0-frontAlpha))*opacity);
}
"""
        private const val GOLD = """
uniform shader artwork; uniform float phase; uniform float power;
  half4 main(float2 p) { half4 src = artwork.eval(p); float a = src.a;
  float3 base = a > 0.001 ? src.rgb / a : float3(0.0); float2 uv = p / float2(114.0,126.0);
  float metal = smoothstep(0.05,0.24,base.r-base.b) * smoothstep(0.02,0.18,base.g-base.b);
  float travel=clamp((phase-0.10)/0.55,0.0,1.0);
  float x=0.20+0.65*(1.0-pow(1.0-travel,3.0));
  float y=0.48-0.05*sin(travel*3.141593);
  float2 d=uv-float2(x,y);
  float core=exp(-dot(d/float2(0.034,0.034),d/float2(0.034,0.034))*1.4);
  float strike=exp(-pow((d.x+0.62*d.y)/0.014,2.0))*exp(-pow(d.y/0.12,2.0));
  float cross=exp(-pow((d.x-1.15*d.y)/0.012,2.0))*exp(-pow(d.x/0.095,2.0));
  float shoulder=exp(-dot(d/float2(0.13,0.16),d/float2(0.13,0.16))*1.8);
  float baseCatch=exp(-pow((uv.x-(0.22+0.62*phase))/0.14,2.0)-pow((uv.y-0.745)/0.024,2.0));
  float specular=power*metal*(0.98*core+0.92*strike+0.58*cross);
  float3 gold=mix(base,float3(1.0,0.99,0.80),clamp(specular,0.0,0.98));
  gold+=power*metal*(baseCatch*float3(0.30,0.23,0.08)+shoulder*float3(0.16,0.12,0.035));
  return half4(clamp(gold,0.0,1.0)*a,a); }
"""
        private const val ENAMEL = """
uniform shader artwork; uniform float phase; uniform float power;
  half4 main(float2 p) { half4 src = artwork.eval(p); float a = src.a;
  float3 base = a > 0.001 ? src.rgb / a : float3(0.0); float2 uv = p / float2(114.0,126.0);
  half4 horizontal=artwork.eval(p+float2(2.3,0.0));
  half4 vertical=artwork.eval(p+float2(0.0,2.3));
  float3 hx=horizontal.rgb/max(horizontal.a,0.001); float3 vy=vertical.rgb/max(vertical.a,0.001);
  float boundary=max(length(base-hx),length(base-vy));
  float bevel=smoothstep(0.025,0.19,boundary);
  float angle = (atan(uv.y-0.49,uv.x-0.5)+3.141593)/6.283186;
  float delta = abs(angle-phase); delta=min(delta,1.0-delta);
  float tracing = exp(-pow(delta/0.15,2.0));
  float teal = smoothstep(0.04,0.22,base.b-base.r) * smoothstep(0.0,0.08,base.g-base.b*0.72);
  float inset = exp(-pow((length((uv-float2(0.50,0.48))*float2(1.0,0.78))-(0.43-0.32*phase))/0.065,2.0));
  float enamel = power*teal*(0.98*tracing*(0.78+0.22*bevel)+0.32*inset);
  float3 color = mix(base,float3(0.84,1.0,0.99),clamp(enamel,0.0,0.96));
  return half4(color*a,a); }
"""
        private const val PRISM = """
uniform shader artwork;
uniform float phase;
uniform float power;
float4 picture(float2 uv) {
  if(uv.x<0.0 || uv.y<0.0 || uv.x>1.0 || uv.y>1.0) return float4(0.0);
  float4 colorSample=artwork.eval(float2(uv.x*114.0,uv.y*90.84375+17.578125));
  return float4(colorSample.a>0.001?colorSample.rgb/colorSample.a:float3(0.0),colorSample.a);
}
float cross2(float2 a,float2 b) { return a.x*b.y-a.y*b.x; }
float triangleMask(float2 p,float2 a,float2 b,float2 c) {
  float2 ab=b-a;float2 bc=c-b;float2 ca=a-c;
  float distance=min(min(cross2(ab,p-a)/length(ab),cross2(bc,p-b)/length(bc)),cross2(ca,p-c)/length(ca));
  return smoothstep(-0.002,0.002,distance);
}
half4 main(float2 p) {
  float2 point=p-float2(0.0,6.0);
  if(point.y<0.0 || point.y>114.0) return half4(0.0);
  float2 inUV=point/114.0;
  float2 uv=(inUV-float2(0.5))*float2(1.0,512.0/408.0)+float2(0.5);
  float4 base=picture(uv);
  float2 sourceSize=float2(512.0,408.0);
  float2 leftTip=float2(80.0,169.0)/sourceSize;
  float2 leftCrown=float2(145.0,105.0)/sourceSize;
  float2 tableLeft=float2(197.0,120.0)/sourceSize;
  float2 tableRight=float2(316.0,120.0)/sourceSize;
  float2 rightCrown=float2(366.0,103.0)/sourceSize;
  float2 rightTip=float2(431.0,169.0)/sourceSize;
  float2 girdleLeft=float2(174.0,189.0)/sourceSize;
  float2 girdleCentre=float2(256.0,189.0)/sourceSize;
  float2 girdleRight=float2(338.0,189.0)/sourceSize;
  float2 tip=float2(256.0,341.0)/sourceSize;
  float upperLeft=min(1.0,triangleMask(uv,leftTip,leftCrown,tableLeft)+triangleMask(uv,leftTip,tableLeft,girdleLeft));
  float upperCentre=min(1.0,triangleMask(uv,tableLeft,tableRight,girdleRight)+triangleMask(uv,tableLeft,girdleRight,girdleLeft));
  float upperRight=min(1.0,triangleMask(uv,tableRight,rightCrown,rightTip)+triangleMask(uv,tableRight,rightTip,girdleRight));
  float lowerLeft=triangleMask(uv,leftTip,girdleLeft,tip);
  float centreLeft=triangleMask(uv,girdleLeft,girdleCentre,tip);
  float centreRight=triangleMask(uv,girdleCentre,girdleRight,tip);
  float lowerRight=triangleMask(uv,girdleRight,rightTip,tip);
  float sum=upperLeft+upperCentre+upperRight+lowerLeft+centreLeft+centreRight+lowerRight;
  float table=max(0.0,1.0-sum);float total=max(0.001,table+sum);
  float facet=(upperLeft+2.0*upperRight+3.0*upperCentre+4.0*(lowerLeft+centreLeft)+5.0*(centreRight+lowerRight))/total;
  float3 plane=normalize((table*float3(0.0,-0.8,0.6)+upperLeft*float3(-0.65,-0.35,0.67)+upperCentre*float3(0.0,-0.4,0.91)+upperRight*float3(0.72,-0.3,0.62)+lowerLeft*float3(-0.72,0.48,0.5)+centreLeft*float3(-0.35,0.65,0.67)+centreRight*float3(0.36,0.65,0.67)+lowerRight*float3(0.72,0.48,0.5))/total);
  float lightX=-1.0+2.0*phase;float lightY=-0.35+0.45*phase;
  float3 light=normalize(float3(lightX*0.65+0.25,lightY*0.65-0.25,1.0));
  float facing=0.5+0.5*cos(facet*1.72-phase*6.2831853);
  float response=smoothstep(0.24,0.92,facing)*power*(0.75+0.25*max(0.0,dot(plane,light)));
  float2 shift=float2(1.5,-0.45)*response/114.0*float2(1.0,512.0/408.0);
  float4 red=picture(uv+shift);float4 blue=picture(uv-shift);
  float3 dispersed=float3(red.a>0.001?red.r:base.r,base.g,blue.a>0.001?blue.b:base.b);
  float3 spectrum=0.5+0.5*cos(float3(0.0,2.1,4.2)+facet*1.13-phase*4.2);
  float3 tint=float3(0.15,0.76,1.0)+spectrum*float3(0.52,0.18,0.43);
  float3 rgb=mix(base.rgb,dispersed,0.7*response);rgb=mix(rgb,tint,0.46*response);rgb+=response*float3(0.08,0.15,0.18);rgb=clamp(rgb,0.0,1.0);
  float dx=point.x-57.0;float arcY=93.5+0.0075*dx*dx;
  float normal=(point.y-arcY)/sqrt(1.0+0.000225*dx*dx);
  float q=dx/30.0;float taper=exp(-q*q*q*q*1.6);
  float band=exp(-0.5*normal*normal/(1.4*1.4));float shoulder=exp(-0.5*normal*normal/(3.2*3.2));
  float3 spectrumFan=exp(-0.5*pow((float3(normal)-float3(1.0,0.0,-1.0))/1.25,float3(2.0)));
  float3 fanColour=mix(float3(0.30,0.85,1.0),spectrumFan/max(max(spectrumFan.r,spectrumFan.g),max(spectrumFan.b,0.001)),0.8);
  float fanAlpha=power*taper*(0.52*band+0.07*shoulder)*(1.0-base.a);
  return half4(rgb*base.a+fanColour*fanAlpha,base.a+fanAlpha);
}
"""
        private const val CAUSTIC = """
uniform float phase; uniform float power; uniform float kind;
  half4 main(float2 p) {
    float dx=p.x-79.0;
    float arcY=mix(122.0,115.5,kind)+0.0075*dx*dx;
    float normal=(p.y-arcY)/sqrt(1.0+0.000225*dx*dx);
    float taper=exp(-pow(dx/30.0,4.0)*1.6);
    float band=exp(-0.5*pow(normal/1.4,2.0));
    float shoulder=exp(-0.5*pow(normal/3.2,2.0));
    float3 spectrum=float3(exp(-0.5*pow((normal-1.0)/1.25,2.0)),
      exp(-0.5*pow(normal/1.25,2.0)),exp(-0.5*pow((normal+1.0)/1.25,2.0)));
    spectrum/=max(max(spectrum.r,spectrum.g),max(spectrum.b,0.001));
    float3 color=mix(float3(1.0,0.81,0.30),spectrum,kind);
    float a=power*taper*(0.48*band+0.07*shoulder);
    return half4(color*a,a);
  }
"""
    }
}
