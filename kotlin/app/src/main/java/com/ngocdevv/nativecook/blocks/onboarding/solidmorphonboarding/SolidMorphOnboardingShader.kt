package com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding

/**
 * The RN block's one shader (`smo-shaders.ts`) as AGSL (API 33+), verbatim: the page UI atlas and the glyph field as child shaders, a ray-marched
 * ceramic solid (a dish and a plate, an optional wedge, a smooth morph between pages, an engraved mark), its floor shadow, the iris that opens the next
 * page's ground and the copy that leaves and arrives. World radius 1 = 128 pt; all uniforms are dp (the canvas is scaled by the density); the
 * Display-P3 output is off (`render.y = 0`).
 */
internal const val SolidMorphOnboardingSource = """
uniform shader pages;
uniform shader glyphs;
uniform float2 size;
uniform float2 centre;
uniform float radius;
uniform float progress;
uniform float count;
uniform float2 sides;
uniform float2 rounds;
uniform float2 splits;
uniform float2 bases;
uniform float2 themes;
uniform float spin;
uniform float speed;
uniform float hover;
uniform float bob;
uniform float baseTilt;
uniform float press;
uniform float2 lean;
uniform float4 render;
uniform float footerStack;
uniform float4 ink;
uniform float4 paper;
uniform float4 clay;
uniform float4 clayShade;
uniform float4 glaze;
uniform float2 buttonPress;
uniform float4 controls;
uniform float arrival;
uniform float reduced;
uniform float2 reaction;
uniform float2 copyBand;
const float PI = 3.14159265359;
float sm(float a, float b, float x) { return smoothstep(a,b,x); }
float2 rot(float2 p, float a) { float c=cos(a); float s=sin(a); return float2(c*p.x-s*p.y,s*p.x+c*p.y); }
float3 rx(float3 p,float a) { p.yz=rot(p.yz,a); return p; }
float poly(float2 p,float n,float r,float roundness) {
  if(n<3.0 || n>32.0 || roundness>0.999) return length(p)-r;
  float a=PI/n;
  float rounding=clamp(roundness,0.0,1.0)*r;
  float inner=max(0.00001,r-rounding);
  float angle=atan(p.y,p.x)+PI/2.0+PI/n;
  float sector=mod(angle+a,2.0*a)-a;
  float2 v=float2(length(p)*sin(sector),length(p)*cos(sector));
  float h=inner*sin(a);
  float2 edge=float2(clamp(v.x,-h,h),inner*cos(a));
  return length(v-edge)*sign(v.y-edge.y)-rounding;
}
float extrude(float d,float z,float h,float bevel) {
  float2 q=float2(d+bevel,abs(z)-h+bevel);
  return min(max(q.x,q.y),0.0)+length(max(q,0.0))-bevel;
}
float minSmooth(float a,float b,float k) { float h=clamp(0.5+0.5*(b-a)/k,0.0,1.0);return mix(b,a,h)-k*h*(1.0-h); }
float2 interval(float p,float n) { float v=clamp(p,0.0,max(0.0,n-1.0));float f=min(floor(v),max(0.0,n-2.0));return float2(f,v-f); }
float area(float n,float rounded) {
  if(n<3.0||n>32.0) return PI;
  float r=clamp(rounded,0.0,1.0);float inner=1.0-r;
  return 0.5*n*sin(2.0*PI/n)*inner*inner+2.0*n*sin(PI/n)*inner*r+PI*r*r;
}
float outline(float2 p,float r,float phase) {
  float mc=sm(0.15,0.85,phase);
  float blur=sm(0.5,3.0,abs(speed));
  float r0=mix(rounds.x,1.0,blur);float r1=mix(rounds.y,1.0,blur);
  if(mc<0.0001) return poly(p,sides.x,r,r0);
  if(mc>0.9999) return poly(p,sides.y,r,r1);
  return mix(poly(p,sides.x,r,r0),poly(p,sides.y,r,r1),mc);
}
float3 objectPoint(float3 p,float2 touch,float base) {
  p=rx(p,0.419+touch.y);
  p.xy=rot(p.xy,touch.x+base);
  return p;
}
float polygonAt(float phase) { return mix(step(3.0,sides.x),step(3.0,sides.y),sm(0.15,0.85,phase)); }
float3 plateCentre(float phase,float gap) {
  float polygon=polygonAt(phase);
  return float3(mix(0.18,-0.10,polygon),gap+0.18*polygon,-0.03);
}
float3 baseCentre(float phase,float split) {
  float polygon=polygonAt(phase);
  return float3(-0.18+0.18*max(polygon,split),-0.15-0.08*polygon,0.12);
}
float2 field(float3 pos,float phase,float split,float thick,float gap,float angle) {
  float3 q=objectPoint(pos,lean,0.0);
  float3 a=q-plateCentre(phase,gap);
  a.xz=rot(a.xz,angle);
  // Gated fallback: the wedge is carved from the blank while the cylinder rises
  // from inside the dish. No distance-field crossfade or detached crescent mid-way.
  float3 cutPoint=a;
  cutPoint.xz=rot(a.xz,-PI*split);
  float3 w=rx(cutPoint-float3(0.28,0.08,0.15)*split,-0.22*split);
  float crown=0.09*clamp(dot(w.xz,w.xz),0.0,1.0)*(1.0-split);
  float plateOutline=mix(outline(w.xz,1.0,phase),length(w.xz)-0.77,split);
  float facet=sin(PI*phase)*sin(PI*phase)*0.20*(1.0-split);
  plateOutline=mix(plateOutline,poly(w.xz,7.0,1.0,0.35),facet);
  float cut=-w.x*0.6-w.z*0.8+mix(-1.5,0.03,split);
  float top=extrude(max(plateOutline,cut),w.y-crown,mix(thick,0.072,split),0.035);
  if(split>0.0001) {
    float3 c=cutPoint-float3(-0.37,mix(-0.65,0.20,split),-0.34);
    float cyl=extrude(length(c.xz)-0.40,c.y,0.065,0.035);
    // The dish occludes the emerging cylinder; no geometry can leak below its lip.
    cyl=max(cyl,-0.04-q.y);
    top=min(top,cyl);
  }
  float3 b=objectPoint(pos,lean*0.2,baseTilt*PI/180.0)-baseCentre(phase,split);
  float bottom=extrude(outline(b.xz,1.0,phase),b.y,0.065,0.027);
  float bowl=length(b-float3(0.0,2.8,0.0))-2.82;
  if(mix(bases.x,bases.y,phase)<1.5) bottom=-minSmooth(-bottom,bowl,0.065);
  return top<bottom?float2(top,1.0):float2(bottom,2.0);
}
// Signed distance of the engraved mark, in 1/128 of the glyph cell (negative inside). 256² texels per page.
float glyph(float2 uv,float page) { return (0.50196-glyphs.eval(float2(clamp(uv.x,0.0,1.0)*256.0,clamp(uv.y,0.0,1.0)*256.0+page*256.0)).r)*31.875; }
float glyphMix(float2 uv,float page,float other,float k) { return mix(glyph(uv,page),glyph(uv,other),k); }
float3 toP3(float3 c) {
  float3 l=mix(c/12.92,pow((c+0.055)/1.055,float3(2.4)),step(float3(0.04045),c));
  float3 p=float3(dot(l,float3(0.8225929,0.1775340,0.0)),dot(l,float3(0.0331995,0.9667835,0.0)),dot(l,float3(0.0170854,0.0723957,0.9103015)));
  return mix(p*12.92,1.055*pow(max(p,0.0),float3(1.0/2.4))-0.055,step(float3(0.0031308),p));
}
half4 main(float2 xy) {
  float volume=render.x;float p3=render.y;float objectOnly=render.z;float pixel=render.w;
  float2 it=interval(progress,count); float page=it.x;float f=it.y;
  float shapePhase=mix(f,step(0.5,f),reduced);
  float reducedFade=reduced*(1.0-sm(0.0,0.12,abs(f-0.5)));
  float morph=sm(0.15,0.85,shapePhase);
  // The new page's ground opens as an iris from the solid's centre while it turns, and covers the screen by the held half-way beat.
  float iris=mix(-64.0,length(max(centre,size-centre))+64.0,sm(0.16,0.5,f));
  float wipe=mix(1.0-sm(iris-56.0,iris+56.0,length(xy-centre)),f,reduced);
  if(f<0.001) wipe=0.0;
  if(f>0.999) wipe=1.0;
  float light=mix(themes.x,themes.y,wipe);
  float3 ground=mix(ink.rgb,paper.rgb,light);
  // Press deformation samples the same atlas: no native tint or duplicate pill layer.
  float2 uiPoint=xy;
  if(xy.y>controls.y && xy.y<controls.y+controls.z*(1.0+footerStack)+footerStack*8.0*controls.w) {
    float single=step(buttonPress.y,-0.5);
    float which=mix(step(size.x*0.5,xy.x),step(controls.y+controls.z+4.0*controls.w,xy.y),footerStack)*(1.0-single);
    float scale=1.0-0.03*mix(buttonPress.x,buttonPress.y,which);
    float2 anchor=float2(mix(mix(size.x*0.255,size.x*0.745,which),size.x*0.5,max(single,footerStack)),controls.y+controls.z*0.5+which*footerStack*(controls.z+8.0*controls.w));
    uiPoint=anchor+(xy-anchor)/scale;
  }
  // Copy leaves upward and arrives from below, each line a beat after the one above; the chrome cross-fades with the ground.
  float inCopy=step(copyBand.x,xy.y)*step(xy.y,copyBand.y);
  float row=clamp((xy.y-copyBand.x)/max(1.0,copyBand.y-copyBand.x),0.0,1.0);
  float outT=sm(0.04+0.06*row,0.30+0.06*row,f);
  float inT=sm(0.56+0.06*row,0.86+0.06*row,f);
  outT=mix(outT,f,reduced);inT=mix(inT,f,reduced);
  float slide=10.0*controls.w*(1.0-reduced)*inCopy;
  float4 oldUI=pages.eval(float2(uiPoint.x,uiPoint.y+slide*outT+page*size.y));
  float4 newUI=pages.eval(float2(uiPoint.x,uiPoint.y-slide*(1.0-inT)+min(page+1.0,count-1.0)*size.y));
  float oldW=mix(1.0-wipe,1.0-outT,inCopy);
  float newW=mix(wipe,inT,inCopy);
  if(f<0.001) {oldW=1.0;newW=0.0;}
  if(f>0.999) {oldW=0.0;newW=1.0;}
  float4 ui=oldUI*oldW+newUI*newW;
  float3 color=ui.rgb+ground*(1.0-ui.a);
  // 100 ms brightness pass on arrival; the atlas keeps the original segmented progress.
  if(abs(xy.y-controls.x)<4.0*controls.w && xy.x<size.x*0.74) color+=arrival*0.12;
  if(objectOnly>0.5) color=ground;
  float2 screen=(xy-centre)/max(1.0,radius);
  if(radius<1.0) return half4(mix(color,toP3(color),p3),1.0);
  float lampPhase=mix(themes.x,themes.y,clamp((f-0.2)/0.6,0.0,1.0));
  float az=mix(-40.0,30.0,lampPhase)*PI/180.0;
  // Flat blurred floor shadow, 1.4× the plate width, no raymarch outside the bounds.
  float shadow=exp(-dot((screen-float2(-0.03-sin(az)*0.12,0.64))/float2(1.25,0.24),(screen-float2(-0.03-sin(az)*0.12,0.64))/float2(1.25,0.24))*2.0)*0.22*light;
  color*=1.0-shadow;
  // Only the distant field is skipped; the floor remains full-bleed without a quad edge.
  if(abs(screen.x)>1.65 || abs(screen.y)>1.45) return half4(mix(color,toP3(color),p3),1.0);
  float cornerContrast=1.0-sm(0.5,3.0,abs(speed));
  float n=mix(sides.x,sides.y,morph);
  // Blend fields of the endpoints rather than interpolate integer sector counts (no topology pop).
  float rd=mix(rounds.x,rounds.y,morph);
  float split=mix(splits.x,splits.y,sm(0.3,0.8,shapePhase));
  float targetArea=mix(area(sides.x,rounds.x),area(sides.y,rounds.y),morph);
  float thick=0.058*mix(1.0,PI/max(2.3,targetArea),volume);
  float gap=(22.0-16.0*sm(0.0,0.3,shapePhase)+16.0*sm(0.7,1.0,shapePhase)+(hover*3.0+bob)*(1.0-clamp(press,0.0,1.0))-press*18.0)/128.0;
  gap=max(0.03125,gap);
  // The last twenty degrees settle onto the authored pose; the physical rate still drives blur.
  float angle=spin*2.0*PI;

  float3 ro=float3(0.0,6.24,3.70);
  float3 forward=normalize(-ro);float3 right=float3(1.0,0.0,0.0);float3 up=cross(right,forward);
  ro+=screen.x*right-screen.y*up;
  float3 ray=forward;
  float dist=5.45;float best=1.0;float mat=0.0;float3 pos=ro;
  float silhouette=0.0;
  // Ray-bounds early-out. 44 is the ceiling; rays stop on a hit or when they leave the scene.
  if(dot(screen,screen)<2.8) {
    for(int i=0;i<44;i++) {
      pos=ro+ray*dist;
      float2 hit=field(pos,shapePhase,split,thick,gap,angle);
      float d=hit.x;
      best=min(best,d);mat=hit.y;
      if(d<pixel*0.35 || dist>9.0) break;
      dist+=max(d*0.78,0.0007);
    }
  }
  if(best<pixel*1.5 && dist<9.0) {
    float e=max(0.0012,pixel*0.7);float3 ex=float3(e,0.0,0.0);float3 ey=float3(0.0,e,0.0);float3 ez=float3(0.0,0.0,e);
    float dx=field(pos+ex,shapePhase,split,thick,gap,angle).x-field(pos-ex,shapePhase,split,thick,gap,angle).x;
    float dy=field(pos+ey,shapePhase,split,thick,gap,angle).x-field(pos-ey,shapePhase,split,thick,gap,angle).x;
    float dz=field(pos+ez,shapePhase,split,thick,gap,angle).x-field(pos-ez,shapePhase,split,thick,gap,angle).x;
    float3 normal=normalize(float3(dx,dy,dz)+float3(0.00001));
    float3 key=normalize(float3(sin(az)-0.3,1.3,0.85));
    float diffuse=clamp((dot(normal,key)+0.45)/1.45,0.0,1.0);
    float ao=1.0;
    for(int j=1;j<=4;j++) {
      float h=0.035*float(j);
      float3 ap=pos+normal*h;
      float d=field(ap,shapePhase,split,thick,gap,angle).x;
      ao-=max(0.0,h-d)*(0.8/float(j));
    }
    float3 q=objectPoint(pos,lean,0.0)-plateCentre(shapePhase,gap);q.xz=rot(q.xz,angle-PI*(page+step(0.5,f)));
    float3 wedgePoint=rx(q-float3(0.28,0.08,0.15),-0.22);
    float glyphPage=min(page+step(0.5,f),count-1.0);
    float span=mix(1.15,0.78,step(1.5,glyphPage));
    float2 uv=mix(q.xz/span+0.5,(wedgePoint.xz-float2(0.27,0.28))/0.54+0.5,split);
    // Both fields blend while flush; no symbol swap can pop during a reversal.
    float nextPage=min(page+1.0,count-1.0);float swapK=sm(0.4,0.6,shapePhase);
    float unit=mix(span,0.54,split)/128.0;
    float sd=glyphMix(uv,page,nextPage,swapK)*unit;
    float relief=(1.0-sm(0.1,0.45,shapePhase))+sm(0.55,0.9,shapePhase);
    float inkRelief=relief*(1.0-sm(0.4,0.6,abs(speed)));
    float aa=pixel*0.85;
    float mark=(1.0-sm(-aa,aa,sd))*inkRelief;
    if(mat>1.5 || mix(q.y,wedgePoint.y,split)<0.025) mark=0.0;
    // Groove walls: the SDF gradient gives the wall direction; the key, carried into the solid's frame, lights one wall and shades the other.
    float2 wallDir=float2(0.0);
    if(mark>0.0) {
      float du=2.5/256.0;
      wallDir=float2(glyphMix(uv+float2(du,0.0),page,nextPage,swapK)-glyphMix(uv-float2(du,0.0),page,nextPage,swapK),
                     glyphMix(uv+float2(0.0,du),page,nextPage,swapK)-glyphMix(uv-float2(0.0,du),page,nextPage,swapK));
      wallDir/=max(0.0001,length(wallDir));
    }
    float grain=(fract(sin(dot(pos.xz,float2(127.1,311.7)))*43758.5453)-0.5)*0.018;
    float shade=0.30+0.68*diffuse;
    float3 ceramic=mix(clayShade.rgb,clay.rgb,shade)*clamp(ao,0.45,1.0)+grain;
    // Softbox reflection in the shallow ceramic basin, locked to the physical surface.
    float basin=exp(-dot((q.xz-float2(-0.22,0.05))/0.85,(q.xz-float2(-0.22,0.05))/0.85)*1.4);
    float topFace=sm(0.35,0.8,dot(normal,normalize(float3(0.0,0.92,-0.4))));
    if(mat<1.5) ceramic+=basin*0.22*topFace;
    if(mat>1.5) {
      float3 b=objectPoint(pos,lean*0.2,baseTilt*PI/180.0)-baseCentre(shapePhase,split);
      float contactShade=(1.0-sm(0.6,1.18,length(b.xz-float2(0.25,-0.02))))*0.52;
      contactShade*=1.0+0.20*reaction.x+0.15*reaction.y;
      ceramic=ceramic*(1.0-contactShade)+clayShade.rgb*0.025;
    }
    float contactDistance=(q.y+gap+0.12)/0.16;
    float contact=exp(-contactDistance*contactDistance)*step(1.45,mat)*0.25;
    ceramic*=1.0-contact*(1.0+press*0.2);
    float3 halfVector=normalize(key-ray);
    float spec=pow(max(0.0,dot(normal,halfVector)),9.0)*0.12;
    // Broad ceramic lobe and the lathe's speed-stretched rim share the one key.
    float rim=pow(1.0-clamp(dot(normal,-ray),0.0,1.0),3.0);
    float arc=pow(max(0.0,cos(atan(q.z,q.x)-az)),mix(12.0,1.5,sm(0.5,3.0,abs(speed))));
    ceramic+=spec+rim*arc*0.12;
    float glint=pow(max(0.0,dot(normal,halfVector)),72.0)*0.45;
    float3 gl=glaze.rgb+glint;
    float3 kq=objectPoint(key,lean,0.0);kq.xz=rot(kq.xz,angle-PI*(page+step(0.5,f)));
    float2 light2=kq.xz/max(0.0001,length(kq.xz));
    float wallW=0.014*inkRelief+0.0001;
    float wall=1.0-sm(wallW*0.55,wallW,-sd);
    float facing=dot(-wallDir,light2);
    float3 floorTone=mix(ceramic*0.46,gl,0.28);
    float3 wallTone=ceramic*clamp(0.62+0.5*facing,0.18,1.12);
    float3 groove=mix(floorTone,wallTone,wall);
    // A hairline catch-light on the lip of the wall that faces away from the key.
    float lip=(1.0-sm(0.0,aa*2.0,abs(sd)))*max(0.0,dot(wallDir,light2))*0.22*inkRelief;
    float3 lit=mix(ceramic,groove,mark)+lip*step(mat,1.5);
    silhouette=1.0-sm(pixel*0.35,pixel*1.5,best);
    color=mix(color,lit,silhouette*(1.0-reducedFade));
  }
  return half4(mix(color,toP3(clamp(color,0.0,1.0)),p3),1.0);
}
"""
