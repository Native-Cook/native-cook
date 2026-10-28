package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

/**
 * The fragment half of the particle world (moteFragment and its helpers in
 * gpu/HabitatParticleShader.ts), as AGSL. The simulation and the vertex stage run on the CPU
 * (HabitatParticleWorld); each live particle is one quad drawn with this shader, in the quad's own
 * frame: `xy` is the position inside the unstretched quad (the reference's `local`), and the point
 * on the screen is rebuilt from the quad's centre, axis and stretch.
 *
 * Uniforms:
 *  - info:   kind, element it is in, element it was, morph
 *  - shape:  radius, life fraction, heat, seed
 *  - pose:   roll, seconds settled, depth, stretch
 *  - place:  centre x, centre y, axis x, axis y
 *  - canvas: canvas width, screen height, stage top, stage bottom
 *  - clock:  carousel position, seam phase, exchange drift, unused
 */
internal const val MOTE_AGSL = """
uniform float4 info;
uniform float4 shape;
uniform float4 pose;
uniform float4 place;
uniform float4 canvas;
uniform float4 clock;
const float LEAF_SPAN=9.0;
float wrap3(float x) { return x-3.0*floor(x/3.0); }
float seamLine(float y,float f,float phase,float arriving) {
  float bow=sin(3.14159265*f);
  float seam=1.08-1.16*f+bow*(.040*sin(y*5.5+phase)+.018*sin(y*14.0-phase*2.0+1.3));
  if(arriving>1.5) seam-=.011*abs(sin(y*46.0+phase))*bow;
  return seam;
}
// Which side of the front a world is on: 1 shown, 0 wiped.
float worldSide(float world,float2 p) {
  float q=wrap3(clock.x);
  float leaving=floor(q);
  float f=q-leaving;
  if(f<.0005) return abs(world-leaving)<.5 ? 1.0 : 0.0;
  float arriving=wrap3(leaving+1.0);
  float edge=p.x-seamLine(p.y/canvas.y,f,clock.y,arriving)*canvas.x;
  float side=smoothstep(-1.0,1.0,edge);
  if(abs(world-leaving)<.5) return 1.0-side;
  if(abs(world-arriving)<.5) return side;
  return 0.0;
}
half4 ambientMote(float e,float2 l,float r,float lf,float heat,float seed) {
  float life=smoothstep(0.0,.14,lf)*(1.0-smoothstep(.76,1.0,lf));
  if(e<.5) {
    float2 p=l/float2(r*.6,r);
    float f=max(0.0,1.0-dot(p,p)*.2);
    float glow=f*f*f*life*(.30+.24*seed)*(1.0+heat*1.6);
    return half4(half3(float3(1.0,.37+.32*(1.0-lf),.095)*glow),glow);
  }
  if(e<1.5) {
    float ring=1.0-smoothstep(.55,1.1,abs(length(l)-r));
    float2 g=l+float2(r*.35);
    float fall=max(0.0,1.0-dot(g,g)*.4);
    float bubble=(ring*.17+fall*fall*fall*.22)*life*(1.0+heat*.8);
    return half4(half3(float3(.57,.87,.94)*bubble),bubble);
  }
  float2 p=l/r;
  float f=max(0.0,1.0-dot(p,p)*.20);
  float pollen=f*f*f*life*.38*(1.0+heat*1.2);
  return half4(half3(float3(.85,.91,.47)*pollen),pollen);
}
half4 leaf(float2 l,float r,float roll,bool back) {
  float2 local=l/r;
  float face=cos(roll);
  float width=max(abs(face),.12);
  float along=local.x/17.0;
  float halfWidth=6.6*width*max(0.0,1.0-along*along)*(1.0+.16*along);
  float body=(1.0-smoothstep(halfWidth-.7,halfWidth+.3,abs(local.y)))*step(abs(along),1.0);
  float vein=(1.0-smoothstep(.1,.5,abs(local.y)/max(width,.35)))*step(abs(along),.9);
  float3 top=mix(float3(.22,.50,.16),float3(.52,.80,.30),clamp(.5+local.y/(halfWidth+.01)*.45,0.0,1.0));
  float3 under=mix(float3(.50,.68,.30),float3(.70,.84,.44),clamp(.5-local.y/(halfWidth+.01)*.45,0.0,1.0));
  float3 green=(face>=0.0 ? top : under)*(.62+.38*abs(face))+float3(.10,.14,.05)*vein;
  if(back) green*=.74;
  return half4(half3(green*body),body);
}
half4 main(float2 xy) {
  float kind=info.x;
  float r=shape.x;
  float lf=shape.y;
  float2 axis=place.zw;
  float2 p=place.xy+axis*(xy.x*pose.w)+float2(-axis.y,axis.x)*xy.y;
  half4 c=half4(0.0);
  if(kind<.5) {
    c=ambientMote(info.z,xy,r,lf,shape.z,shape.w)*(1.0-info.w)+ambientMote(info.y,xy,r,lf,shape.z,shape.w)*info.w;
    if(pose.z<0.0) c*=.75;
    // The motes live on the stage: they thin out above it and at the screen's sides.
    c*=smoothstep(canvas.z-50.0,canvas.z+40.0,p.y)*(1.0-smoothstep(canvas.w-10.0,canvas.w+20.0,p.y))
      *smoothstep(0.0,24.0,p.x)*(1.0-smoothstep(canvas.x-24.0,canvas.x,p.x));
  } else if(kind<1.5) {
    float life=1.0-lf;
    float d2=dot(xy,xy)/(r*r);
    float glow=(exp(-d2)+exp(-d2/9.0)*.25)*life*(1.0+shape.z);
    c=half4(half3(float3(1.0,.58+.3*life,.18)*glow),min(glow*.8,1.0));
  } else if(kind<2.5) {
    float d=length(xy);
    float drop=(1.0-smoothstep(r*.5,r,d))*smoothstep(0.0,.04,shape.y*3.0);
    float2 g=xy+float2(r*.35);
    float sheen=exp(-dot(g,g)/(r*r*.25))*drop;
    c=half4(half3(float3(.6,.88,.98)*drop*.8+float3(1.0)*sheen*.6),drop*.75+sheen*.3);
  } else if(kind<3.5) {
    float2 q=xy/float2(r,r*.3);
    float d=length(q);
    float x=(d-1.0)/.25;
    float ring=exp(-x*x)*(1.0-lf)*(1.0-lf);
    float flash=exp(-d*d*4.0)*(1.0-smoothstep(0.0,.3,lf));
    c=half4(half3(float3(.75,.95,1.0)*(ring*.75+flash*.5)),ring*.6+flash*.4);
  } else if(kind<4.5) {
    // Settled on the floor it lies still, then fades; a contact shadow grows under it as it settles.
    float ground=pose.y;
    float rest=ground>0.0 ? 1.0-pow(1.0-min(1.0,ground/.26),3.0) : 0.0;
    float birth=smoothstep(0.0,.06,lf*LEAF_SPAN);
    c=leaf(xy,r,pose.x,pose.z<0.0);
    float2 sq=xy/(r*float2(16.0,3.0));
    float shadow=exp(-dot(sq,sq)*1.6)*.38*rest;
    c=(c+half4(0.0,0.0,0.0,shadow)*(1.0-c.a))*birth*(1.0-smoothstep(.55,1.05,ground));
  } else {
    float d2=dot(xy,xy)/(r*r);
    float age=lf*1.45;
    float life=smoothstep(0.0,.08,age)*(1.0-smoothstep(1.05,1.45,age));
    float twinkle=.7+.3*sin(age*18.0+shape.w*40.0);
    float mote=(exp(-d2)+exp(-d2/7.0)*.25)*life*twinkle*(1.0+shape.z);
    c=half4(half3(float3(1.0,.95,.62)*mote),min(mote*.8,1.0));
  }
  if(kind>.5) {
    // Wiped by the habitat front together with the world it belongs to.
    c*=worldSide(info.y,p);
  }
  return c;
}"""
