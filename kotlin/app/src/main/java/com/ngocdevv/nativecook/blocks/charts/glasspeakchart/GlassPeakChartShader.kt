package com.ngocdevv.nativecook.blocks.charts.glasspeakchart

internal const val GlassPeakShaderSource = """
uniform float px, clock, presented;
uniform float ys[96];
uniform float3 trace;
uniform float2 beam;
uniform float4 orb;
uniform float nodeX[4], nodeY[4], nodeLit[4], nodeScale[4], drops[4], nodeBottom[4];
uniform float4 pool;
uniform float gain, stir;
uniform float splashX[8], splashY[8], splashR[8];
uniform float4 ring;
uniform float flash, glint;
uniform float2 tilt;
uniform float3 touch;
uniform float4 control;
const float3 green = float3(.12,1.,.22);
const float3 mint = float3(.84,1.,.87);

float bell(float v, float spread) { float a=v/spread; return exp(-a*a); }
float grainAt(float2 p) { return fract(sin(dot(floor(p*px),float2(127.1,311.7)))*43758.5453)-.5; }
float slab(float2 p) {
  float2 q=abs(p-float2(201,416))-float2(109,176);
  float2 a=max(q,0.);
  float n=pow(pow(a.x,2.25)+pow(a.y,2.25),1./2.25);
  float grad=length(pow(a/max(n,.001),float2(1.25)));
  return (n-64.)/max(grad,.8)+min(max(q.x,q.y),0.);
}
float2 slabNormal(float2 p) {
  float2 v=float2(slab(p+float2(.2,0))-slab(p-float2(.2,0)),slab(p+float2(0,.2))-slab(p-float2(0,.2)));
  return v/max(length(v),.0001);
}
float2 segment(float2 p,float2 a,float2 b) {
  float2 v=b-a;
  float h=clamp(dot(p-a,v)/max(dot(v,v),.0001),0.,1.);
  return float2(length(p-a-h*v),h);
}
float poolFrontY(float level,float hump,float humpX,float slope,float x) {
  float dx=x-humpX;
  return 656.-(10.+140.*level)-hump*84.*exp(-(dx*dx)/(2.*58.*58.))-slope*(x-201.)*.4;
}
float poolSurfaceY(float level,float hump,float humpX,float slope,float x) {
  return 656.-.42*(656.-poolFrontY(level,hump,humpX,slope,x));
}
float3 liquid(float2 p,float rimDepth,float bottomFacing) {
  p+=float2(tilt.y,-tilt.x)*48.;
  float f=poolFrontY(pool.x,pool.z,pool.y,pool.w,p.x);
  float depth=max(0.,p.y-f);
  float a=smoothstep(0.,max(1.,656.-f),depth);
  float bottom=(1.-smoothstep(0.,12.,rimDepth))*bottomFacing;
  float phase=6.2831853*clock/13.;
  float warpedY=p.y+7.*sin(p.x*.024+phase)+3.*sin(p.x*.055-phase*2.);
  float ribbon1=sin(warpedY*.115+1.4*sin(p.x*.015-phase));
  float ribbon2=sin(warpedY*.19+1.8*sin(p.x*.009+phase*2.));
  float ribbonGain=mix(.035+.065*stir,.14,smoothstep(586.,616.,p.y));
  float ribbons=(.65*ribbon1+.35*ribbon2)*ribbonGain;
  float energy=(.08+.92*min(pool.x,1.1))*gain;
  float3 col=mix(float3(.015,.23,.075),float3(.06,.96,.24),smoothstep(.12,.85,a))*a;
  col*=1.+ribbons*smoothstep(0.,.6,a);
  col=mix(col,mint*1.02,bottom*.86*smoothstep(0.,6.,depth));
  float surface=poolSurfaceY(pool.x,pool.z,pool.y,pool.w,p.x);
  float wet=smoothstep(surface,surface+2.,p.y)*a;
  float ripple=surface+4.+4.*stir*sin(p.x*6.2831853/60.-phase*3.);
  col+=float3(.24,1.,.39)*.25*stir*bell(p.y-ripple,1.5)*wet;
  float pour=smoothstep(.6,1.,beam.x)*smoothstep(.94,1.,beam.y);
  col+=green*.12*pour*bell(p.x-orb.x,42.)*wet;
  return col*energy;
}
float3 shaftAt(float2 p) {
  float surface=poolSurfaceY(pool.x,pool.z,pool.y,pool.w,orb.x);
  float yEnd=mix(min(surface,492.),surface,smoothstep(.3,.7,beam.x));
  float endY=mix(orb.y,yEnd,beam.y);
  float span=max(1.,endY-orb.y);
  float along=clamp((p.y-orb.y)/span,0.,1.);
  float cap=smoothstep(orb.y-1.,orb.y+4.,p.y)*(1.-smoothstep(.7,1.,along));
  float width=mix(6.,4.2,along);
  return float3(.48,1.,.64)*(bell(p.x-orb.x,width)*.45+bell(p.x-orb.x,.75)*.52)*(1.-.65*along)*cap*beam.x;
}
float3 tubeAt(float2 p,float bevelBand,float edgeFade) {
  if(p.y<234. || p.y>521.) return float3(0.);
  float unlit=1000., litDist=1000., heightV=0., nearU=0.;
  for(int i=0;i<95;i++) {
    float ax=28.+float(i)*346./95.;
    if(p.x<ax-42. || p.x>ax+46.) continue;
    float2 a=float2(ax,476.-ys[i]*182.);
    float2 b=float2(ax+346./95.,476.-ys[i+1]*182.);
    float2 hit=segment(p,a,b);
    unlit=min(unlit,hit.x);
    float endU=clamp(trace.x*95.-float(i),0.,1.);
    if(endU<=0.) continue;
    float2 litHit=segment(p,a,mix(a,b,endU));
    if(litHit.x<litDist) {
      litDist=litHit.x;
      heightV=mix(ys[i],ys[i+1],litHit.y*endU);
      nearU=(float(i)+litHit.y*endU)/95.;
    }
  }
  float edge=.35+.65*smoothstep(0.,20.,min(p.x-28.,374.-p.x));
  float energy=(.55+.45*heightV)*edge*trace.z;
  float pulse=clamp(trace.z-1.,0.,.8)/.8;
  float heat=flash*(1.-smoothstep(45.,100.,length(p-orb.xy)));
  float width=1.+.2*bevelBand;
  unlit/=width;
  litDist/=width;
  float aa=.65/max(px,1.)+.25*bevelBand;
  float3 col=float3(.10,.13,.11)*(1.-smoothstep(.8,1.3+aa,unlit));
  col+=green*(.12*bell(litDist,19.+5.*pulse)+.44*bell(litDist,5.+1.5*pulse)+.44*(1.-smoothstep(1.7,3.2,litDist)))*energy;
  col+=mint*(1.-smoothstep(.65,1.5+aa,litDist))*(.48+.16*pulse+.65*heat)*energy;
  float head=bell((nearU-trace.x)*346.,4.5)*trace.y;
  col+=mint*head*bell(litDist,4.)+green*.45*head*bell(litDist,13.);
  return col*mix(1.,.45,bevelBand)*edgeFade;
}
float3 sphere(float2 p,float2 center,float radius,float power) {
  float2 v=p-center;
  float r=max(radius,.01), radial=length(v), normalizedR=radial/r;
  float mask=1.-smoothstep(r-.45,r+.45,radial);
  float volume=sqrt(max(0.,1.-normalizedR*normalizedR));
  float highlight=bell(length(v-float2(-.32,-.37)*r),r*.19);
  float hot=clamp(power-1.,0.,1.);
  float3 surface=mix(float3(.09,.67,.19),mint,volume*.76)*min(power,1.)*(1.-.3*hot);
  surface+=mint*bell(radial,r*.34)*hot*1.1;
  surface+=mint*highlight*(.23+.45*power);
  surface+=float3(.025,.038,.029)*(1.-min(power,1.));
  return surface*mask+green*power*(.23*bell(radial,max(r*1.55,16.))+.07*bell(radial,48.));
}
half4 main(float2 p) {
  if(p.y<=150. || p.y>=820. || p.x<=-65. || p.x>=467. || presented<=0.) return half4(0,0,0,1);
  float d=slab(p), aa=.7/max(px,1.);
  float inside=1.-smoothstep(-aa,aa,d);
  float2 normal=slabNormal(p);
  float2 rimPoint=p-normal*d;
  float pipe=(.025+.975*pool.x)*gain*exp(-max(0.,656.-rimPoint.y)/150.);
  float lower=smoothstep(390.,650.,p.y);
  float3 air=green*pipe*(.20*exp(-max(d,0.)/10.)+.075*exp(-max(d,0.)/37.))*(.18+.82*lower);
  air+=float3(.065,.08,.069)*exp(-max(d,0.)/14.)*.22;
  air*=1.+.5*flash;
  air*=smoothstep(150.,175.,p.y)*(1.-smoothstep(735.,820.,p.y))*smoothstep(-65.,-25.,p.x)*(1.-smoothstep(427.,467.,p.x));
  if(d>2.) return half4(air*presented,1.);
  float g=grainAt(p);
  float lift=bell(length((p-float2(100,185))/float2(1.3,1.)),200.);
  float3 body=float3(.033,.046,.037)+float3(.065,.077,.067)*lift;
  body+=float3(.012,.03,.013)*bell(length(p-float2(300,267)),155.)+g*.024;
  float inward=max(-d,0.);
  float bevel=(1.-smoothstep(0.,10.,inward))*exp(-inward/7.);
  float face=smoothstep(9.,12.,inward);
  body*=1.-.22*exp(-inward/19.)*(1.-lower*.7);
  float diagonal=p.x*.55+p.y*.83-250.+tilt.y*120.-tilt.x*90.;
  body+=float3(.96,.99,1.)*(.056*bell(diagonal,32.)+.026*bell(diagonal-46.,9.))*(1.-smoothstep(300.,392.,p.y))*face;
  float topFacing=clamp(-normal.y,0.,1.);
  body+=float3(.94,.98,1.)*.06*bell(inward-12.,14.)*topFacing;
  float waveDelta=length(p-ring.xy)-ring.z;
  float pressure=bell(waveDelta,15.6)*ring.w;
  float2 radial=(p-ring.xy)/max(length(p-ring.xy),.001);
  float lens=1.-smoothstep(0.,10.,inward);
  float2 bent=p-normal*(5.*lens)+float2(0.,1.5*lens)-radial*3.*pressure;
  body+=liquid(bent,inward,smoothstep(.1,.7,normal.y))+tubeAt(p-radial*3.*pressure,lens,smoothstep(0.,2.,inward))+shaftAt(bent);
  body+=float3(.30,1.,.48)*.14*pressure;
  float cool=bell(length(rimPoint-float2(100.-tilt.y*120.,177.+tilt.x*120.)),110.);
  float rimDelta=length(rimPoint-ring.xy)-ring.z;
  float2 rimRadial=(rimPoint-ring.xy)/max(length(rimPoint-ring.xy),.001);
  float tangentSpeed=abs(dot(rimRadial,float2(-normal.y,normal.x)));
  float rimShock=ring.w*bell(rimDelta,max(10.,30.*tangentSpeed));
  float finger=touch.z*bell(length(p-touch.xy),70.);
  float3 rimColor=float3(.05,.062,.055)+float3(.94,.98,1.)*(.12*topFacing+.19*cool)+green*(pipe*.7+glint*.18+finger*.24)+mint*rimShock*.85;
  body+=rimColor*(bell(d+.65,.5+aa)+bevel*.85+bell(d+10.,.375+aa)*.33);
  body+=mint*.3*exp(-inward/5.5)*smoothstep(618.,656.,p.y)*(.08+.92*pool.x)*gain;
  body+=mint*.045*bell(slab(p+float2(tilt.y,-tilt.x)*60.)+5.,.7);
  for(int i=0;i<4;i++) {
    float startY=nodeY[i]+4.5*nodeScale[i];
    float endY=mix(startY,nodeBottom[i],drops[i]);
    float fade=1.-smoothstep(startY,nodeBottom[i],p.y);
    float hair=(1.-smoothstep(.2,.65+aa,abs(p.x-nodeX[i])))*smoothstep(startY,startY+2.,p.y)*(1.-smoothstep(endY-2.,endY,p.y));
    body+=mint*.22*hair*fade*drops[i];
    float r=4.5*nodeScale[i];
    float radialNode=length(p-float2(nodeX[i],nodeY[i]));
    float mask=1.-smoothstep(r-.35,r+.35,radialNode);
    body=mix(body,mix(float3(.035,.07,.042),float3(.48,1.,.48),nodeLit[i]),mask);
    body+=mint*.28*bell(length(p-float2(nodeX[i]-r*.3,nodeY[i]-r*.3)),max(.1,r*.3))+green*.18*nodeLit[i]*bell(radialNode,8.);
  }
  for(int i=0;i<8;i++) {
    float r=splashR[i];
    if(r<=0.) continue;
    float2 beadPos=float2(splashX[i],splashY[i]);
    float radialDrop=length(p-beadPos);
    float beadMask=1.-smoothstep(r-.3,r+.3,radialDrop);
    body+=green*.32*bell(radialDrop,max(.1,3.11127*r));
    body+=mix(float3(.13,.94,.30),mint,1.-smoothstep(.58*r,.9*r,radialDrop))*beadMask;
    body+=mint*.14*bell(length(p-beadPos+float2(.27,.3)*r),max(.15,r*.32));
  }
  float orbMask=1.-smoothstep(orb.z-.5,orb.z+.5,length(p-orb.xy));
  body*=1.-orbMask*.7;
  body+=sphere(p,orb.xy,orb.z,orb.w);
  float2 burst=p-orb.xy;
  float horizontal=1.-smoothstep(0.,90.,abs(burst.x));
  float vertical=1.-smoothstep(0.,40.,abs(burst.y));
  float spindle=horizontal*horizontal*bell(burst.y,.4+2.2*horizontal)+vertical*vertical*bell(burst.x,.4+1.4*vertical)*.65;
  body+=flash*(mint*.65*bell(length(burst),138.5)+mint*.46*spindle)*(1.-.75*orbMask)+green*finger*.10;
  float2 chip=(p-float2(296,232))/(1.-.03*control.x);
  float chipD=length(float2(max(abs(chip.x)-36.,0.),chip.y))-18.;
  float chipMask=1.-smoothstep(-aa,aa,chipD);
  body=mix(body,body*.82+mint*(.06-.02*control.x),chipMask);
  body+=mint*(.045+.055*smoothstep(10.,-15.,chip.y)+.06*control.x)*bell(chipD+.5,.65);
  body-=body*.11*chipMask*bell(chip.y-15.,2.);
  float2 cv=chip-float2(34,0);
  cv=float2(cos(control.z)*cv.x+sin(control.z)*cv.y,-sin(control.z)*cv.x+cos(control.z)*cv.y);
  float chev=min(segment(cv,float2(-4.5,-2),float2(0,2.5)).x,segment(cv,float2(0,2.5),float2(4.5,-2)).x);
  body+=mint*(.67*(1.-smoothstep(.45,.85+aa,chev))+.07*bell(chev,2.));
  float2 rv=(p-float2(58,232))/(1.-.03*control.y);
  float angle=atan(rv.y,rv.x)-control.w;
  float arc=step(-.8660254,cos(angle));
  float ringD=abs(length(rv)-6.);
  body+=mint*(.48+.25*control.y)*arc*((1.-smoothstep(.4,.8+aa,ringD))+.10*bell(ringD,2.));
  float2 ringHead=float2(cos(control.w+2.61799),sin(control.w+2.61799))*6.;
  body+=mint*.67*bell(length(rv-ringHead),1.1);
  body=max(body,0.);
  return half4(mix(air,body,inside)*presented,1.);
}
"""
