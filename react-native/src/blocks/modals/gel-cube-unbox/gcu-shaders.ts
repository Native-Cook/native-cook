import { STUDIO_SKSL } from './gcu-studio';

/**
 * One analytic material pass. No textures, WebGPU, random clock or per-ray sample loop.
 * uFrame=(originX,originY,scale,controls: bit0 close / bit1 default sheet copy), uShape=(cubeScale,glassFill,glassStroke,sheetRadius).
 * uState=(progress,coreBrightness,fanProgress,causticBrightness); uOptics=(lidRadians,flash,P3Output,grain).
 * All palettes are sRGB; Graphite/P3 output conversion is explicit and independently switchable for CanvasKit.
 */
export const GEL_SKSL = `
uniform float4 uFrame;
uniform float4 uShape;
uniform float4 uState;
uniform float4 uOptics;
uniform float3 uTint;
uniform float3 uPage;
uniform float3 uFloor;
uniform float3 uEdge;
uniform float3 uCore;
uniform float3 uFan;
uniform float3 uUnder;
float segmentDistance(float2 p,float2 a,float2 b) { float2 v=b-a; return length(p-a-v*clamp(dot(p-a,v)/max(0.001,dot(v,v)),0.0,1.0)); }
float sq(float x) { return x*x; }
float sat(float v) { return clamp(v, 0.0, 1.0); }
float hash21(float2 p) { return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453123); }
float sdRound(float2 p, float2 b, float r) { float2 q=abs(p)-b+r; return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r; }
float sdBox3(float3 p,float3 b,float r) { float3 q=abs(p)-b+r; return length(max(q,0.0))+min(max(q.x,max(q.y,q.z)),0.0)-r; }
float3 normalBox(float3 q,float3 b,float r) {
  float3 outside=max(abs(q)-b+r,0.0);
  if(dot(outside,outside)>0.0000001) return normalize(outside*sign(q));
  float3 d=abs(q)-b;
  if(d.x>d.y && d.x>d.z) return float3(sign(q.x),0.0,0.0);
  if(d.y>d.z) return float3(0.0,sign(q.y),0.0);
  return float3(0.0,0.0,sign(q.z));
}
float2 hitBox(float3 ro,float3 rd,float3 b,float r) {
  float3 inv=1.0/(sign(rd)*max(abs(rd),float3(0.00001)));
  float3 a=(-b-ro)*inv, c=(b-ro)*inv;
  float3 mn=min(a,c),mx=max(a,c);
  float nearT=max(mn.x,max(mn.y,mn.z));
  float farT=min(mx.x,min(mx.y,mx.z));
  if(farT<max(nearT,0.0)) return float2(-1.0);
  for(int i=0;i<5;i++) nearT+=max(0.0,sdBox3(ro+rd*nearT,b,r));
  for(int j=0;j<5;j++) farT-=max(0.0,sdBox3(ro+rd*farT,b,r));
  if(farT<nearT || sdBox3(ro+rd*nearT,b,r)>0.001) return float2(-1.0);
  return float2(nearT,farT);
}
float integral(float3 ro,float3 rd,float a,float b,float3 center) {
  float3 v=ro-center;
  float along=dot(v,rd), k=38.0;
  float h=max(1.0,1.0+k*(dot(v,v)-along*along));
  float q=sqrt(k/h);
  return max(0.0,(atan((b+along)*q)-atan((a+along)*q))/sqrt(k*h));
}
${STUDIO_SKSL}
float3 floorAt(float2 p,float3 page,float3 floorColor,float core,float fan,float flash) {
  float v=exp(-sq((p.y-289.0)/165.0));
  float3 col=page*(0.94+0.06*sat(p.y/650.0))+float3(0.023,0.029,0.048)*sat(p.y/874.0);
  col=mix(col,floorColor,0.29*v);
  float haze=exp(-dot((p-float2(105.0,282.0))/float2(245.0,73.0),(p-float2(105.0,282.0))/float2(245.0,73.0)));
  col=mix(col,floorColor,0.82*haze);
  float sh=exp(-dot((p-float2(330.0,315.0))/float2(118.0,23.0),(p-float2(330.0,315.0))/float2(118.0,23.0)));
  col*=1.0-0.42*sh;
  float contact=exp(-dot((p-float2(211.0,330.0))/float2(83.0,6.0),(p-float2(211.0,330.0))/float2(83.0,6.0)));
  col*=1.0-0.16*contact;
  float2 c=(p-float2(240.0,316.0))/float2(74.0+34.0*fan,16.0+9.0*fan);
  float caustic=exp(-dot(c,c))*0.25*core*(1.0+0.5*fan);
  col=mix(col,floorColor,caustic);
  float reflection=exp(-sq((p.x-190.0)/42.0))*exp(-sq((p.y-355.0)/49.0));
  col=mix(col,floorColor,0.32*reflection*core);
  col+=studioPage(float2(clamp(p.x,0.0,402.0),clamp(p.y,0.0,590.0)),page,floorColor,uTint)*(1.0-smoothstep(550.0,670.0,p.y));
  float p1=sat(fan);
  float front=340.0+520.0*pow(p1,0.8);
  float along=smoothstep(276.0,355.0,p.y)*(1.0-smoothstep(front-30.0,front+30.0,p.y));
  float width=35.0+0.42*max(0.0,p.y-290.0);
  float cone=exp(-pow(abs(p.x-199.0)/max(1.0,width),4.0));
  float flux=smoothstep(0.0,0.07,p1)*along*cone*0.55;
  // Grazing light is attenuated above the floor: retain contrast of the source's headline and subtitle.
  float typeZone=smoothstep(358.0,378.0,p.y)*(1.0-smoothstep(474.0,494.0,p.y));
  flux*=1.0-0.84*typeZone;
  col=mix(col,uFan,min(0.55,flux*(1.0+flash)));
  return col;
}
float3 gel(float2 p,float3 ro,float3 rd,float2 hit,float3 halfSize,float3 center,float core,float opening,float isLid,float3 base) {
  float3 pos=ro+rd*hit.x;
  float3 n=normalBox(pos,halfSize,0.0185);
  float path=max(0.0,hit.y-hit.x);
  float3 world=pos+center;
  float fresnel=0.04+0.96*pow(1.0-abs(dot(n,-rd)),5.0);
  float lower=(1.0-smoothstep(0.1,0.36,world.y))*(1.0-isLid);
  float3 absorb=(float3(1.0)-uTint)*float3(2.5,1.7,0.65)+float3(0.22,0.16,0.08);
  float3 trans=exp(-absorb*path*(1.0+1.3*lower));
  float3 refracted=floorAt(p+float2(n.x-n.z,n.y)*6.0,uPage,uFloor,core,opening,0.0);
  float3 pigment=uTint*float3(0.94,1.15,0.92)*(0.82+0.12*max(n.y,0.0)+0.12*n.z);
  float3 col=mix(pigment,refracted,trans*0.52);
  float glow=integral(ro+center,rd,hit.x,hit.y,float3(0.45,0.35,0.41));
  glow=1.0-exp(-glow*5.8);
  col=mix(col,mix(uTint,uCore,smoothstep(0.10,0.49,glow)),glow*0.95*core*(1.0-0.18*opening));
  float2 inner=hitBox(ro+center-float3(0.5,0.10,0.375),rd,float3(0.31,0.10,0.25),0.035);
  if(inner.x>hit.x && inner.x<hit.y && isLid<0.5) {
    float depth=max(0.0,inner.y-inner.x);
    float pedestal=(1.0-exp(-depth*7.0))*smoothstep(0.0,0.18,world.y);
    col=mix(col,uTint*float3(0.42,0.53,0.75),pedestal*0.78);
    float3 ip=ro+center+rd*inner.x;
    float crown=exp(-sq((ip.y-0.20)/0.065));
    col+=uCore*crown*0.06*core;
  }
  float3 q=abs(pos)-halfSize+0.0185;
  float second=q.x+q.y+q.z-max(q.x,max(q.y,q.z))-min(q.x,min(q.y,q.z));
  float edge=pow(sat(second/0.0185),2.0);
  float3 key=normalize(float3(-0.8,0.42,0.32));
  float diffuse=0.5+0.5*dot(n,key);
  col=mix(col,uEdge,edge*(0.12+0.27*diffuse));
  float spec=pow(max(0.0,dot(reflect(rd,n),key)),28.0);
  col+=uCore*spec*0.12;
  col=mix(col,uEdge,0.19*fresnel);
  if(isLid>0.5) {
    col=mix(uTint*0.78,col,0.8);
    float2 lq=(world.xz-float2(0.5,0.375))/float2(0.6,0.45);
    col=mix(col,uEdge,0.20*exp(-dot(lq,lq))*max(n.y,0.0)*core);
    // The underside is the same gel, lit by the core through the open top: brightest over the opening near the hinge,
    // falling off toward the far edge, deep tint where the slab's thickness is seen at its border, the bevel catching
    // the light (it was one flat colour, which read as a card floating above the cube).
    float under=(1.0-smoothstep(-0.8,0.1,n.y))*opening;
    float2 uq=(world.xz-float2(0.5,0.2))/float2(0.46,0.42);
    float lit=exp(-dot(uq,uq))*core;
    float inset=smoothstep(0.0,0.1,min(min(world.x,1.0-world.x),min(world.z,0.75-world.z)));
    float3 underCol=mix(uTint*0.66,uUnder,0.3+0.45*inset);
    underCol=mix(underCol,uCore,0.42*lit*inset);
    underCol=mix(underCol,uEdge,0.5*(1.0-inset)*(0.4+0.6*lit));
    col=mix(col,underCol,under*0.92);
    col=mix(col,uEdge,edge*0.3);
  }
  float fog=1.0-exp(-path*95.0);
  return mix(base,col,fog);
}
float3 p3(float3 c) {
  float3 lo=c/12.92,hi=pow(max((c+0.055)/1.055,float3(0.0)),float3(2.4));
  float3 lin=float3(c.r<=0.04045?lo.r:hi.r,c.g<=0.04045?lo.g:hi.g,c.b<=0.04045?lo.b:hi.b);
  lin=float3(dot(lin,float3(0.82259287,0.17753395,0.0)),dot(lin,float3(0.03319951,0.96678350,0.0)),dot(lin,float3(0.01708535,0.07239572,0.91030148)));
  float3 l=lin*12.92,h=1.055*pow(max(lin,float3(0.0)),float3(1.0/2.4))-0.055;
  return float3(lin.r<=0.0031308?l.r:h.r,lin.g<=0.0031308?l.g:h.g,lin.b<=0.0031308?l.b:h.b);
}
half4 main(float2 frag) {
  float2 p=(frag-uFrame.xy)/uFrame.z;
  float progress=sat(uState.x), core=uState.y;
  float showClose=mod(uFrame.w,2.0),showDetails=step(1.5,uFrame.w);
  float3 col=floorAt(p,uPage,uFloor,uState.w,uState.z,uOptics.y);
  float2 cp=(p-float2(196.0,235.0))/uShape.x+float2(196.0,235.0);
  // Small calibrated perspective correction; preserves the source's slightly non-parallel face edges.
  cp.x-=5.0*exp(-sq((cp.y-129.0)/25.0)-sq((cp.x-185.0)/100.0));
  cp.y+=1.0*exp(-sq((cp.x-305.0)/35.0)-sq((cp.y-152.0)/20.0));
  cp.y+=2.0*smoothstep(135.0,154.0,cp.y)*(1.0-smoothstep(180.0,202.0,cp.y));
  cp.y+=12.0*abs(cp.x-211.0)/125.0*smoothstep(160.0,300.0,cp.y);
  float bodyHeight=1.0-0.14*smoothstep(0.0,0.04,progress);
  if(cp.x>38.0 && cp.x<330.0 && cp.y>5.0 && cp.y<346.0) {
    float3 rd=normalize(float3(-0.752,-0.267,-0.75));
    float z=3.0;
    float x=(cp.x-180.0+125.333333*z)/125.0;
    float y=(291.0+23.0*x+34.666667*z-cp.y)/162.0;
    float3 ro=float3(x,y,z);
    float3 center=float3(0.5,bodyHeight*0.5,0.375), halfSize=float3(0.5,bodyHeight*0.5,0.375);
    float2 body=hitBox(ro-center,rd,halfSize,0.0185);
    if(body.x>0.0) {
      col=gel(cp,ro-center,rd,body,halfSize,center,core,progress,0.0,col);
      float3 n=normalBox(ro-center+rd*body.x,halfSize,0.0185);
      float2 originalP=(p-float2(196.0,235.0))/uShape.x+float2(196.0,235.0);
      float3 calibrated= n.y>0.8 ? studioTop(originalP,uPage,uFloor,uTint) : n.z>n.x ? studioLeft(originalP,uPage,uFloor,uTint) : studioRight(originalP,uPage,uFloor,uTint);
      col+=clamp(calibrated,float3(-0.18),float3(0.18))*(1.0-0.18*progress);
      // Refracted rear edges of the cube's floor: the dense core occludes their middle.
      float floorEdge=min(segmentDistance(originalP,float2(86.0,308.0),float2(185.0,274.0)),segmentDistance(originalP,float2(185.0,274.0),float2(305.0,306.0)));
      float unoccluded=1.0-smoothstep(125.0,132.0,originalP.x)+smoothstep(259.0,267.0,originalP.x);
      float floorRim=(1.0-smoothstep(0.1,0.7,floorEdge))*sat(unoccluded);
      col=mix(col,uTint*0.48,floorRim*0.4);
      float densityEdge=min(segmentDistance(originalP,float2(128.0,283.0),float2(128.0,314.0)),segmentDistance(originalP,float2(264.0,283.0),float2(264.0,314.0)));
      col=mix(col,uEdge,(1.0-smoothstep(0.1,0.7,densityEdge))*0.25*core);
    }
    float a=uOptics.x,cs=cos(a),sn=sin(a);
    // the hinge is the joint itself: the lid's lower back edge, which stays on the body's upper back edge as it swings
    float3 pivot=float3(0.0,0.86,0.0);
    float3 rr=ro-pivot;
    float3 lr=float3(rr.x,cs*rr.y-sn*rr.z,sn*rr.y+cs*rr.z)+pivot;
    float3 ld=float3(rd.x,cs*rd.y-sn*rd.z,sn*rd.y+cs*rd.z);
    float3 lc=float3(0.5,0.93,0.375), lb=float3(0.5,0.07,0.375);
    float2 lid=hitBox(lr-lc,ld,lb,0.0185);
    if(progress>0.001 && lid.x>0.0 && (body.x<0.0 || lid.x<body.x)) col=gel(cp,lr-lc,ld,lid,lb,lc,core,progress,1.0,col);
  }
  // the front vertical edge starts at the body's front top corner (cp.y 178 closed): with the lid off it is lower, and a
  // fixed start drew the line on into the open top
  float cornerTop=340.1-162.0*bodyHeight;
  float corner=(1.0-smoothstep(0.1,0.7,abs(cp.x-211.0)))*smoothstep(cornerTop,cornerTop+3.0,cp.y)*(1.0-smoothstep(338.0,341.0,cp.y));
  col=mix(col,uTint*0.6,corner*0.35);
  // The narrow seal is a light leak, not an outline painted around the object.
  float2 seamP=float2((cp.x-148.5)/62.5,(cp.y-(154.0+0.184*(cp.x-86.0))));
  float seam=(1.0-smoothstep(0.2,0.7,abs(seamP.y)))*step(abs(seamP.x),1.0)*(1.0-smoothstep(0.0,0.025,progress));
  col=mix(col,uEdge,0.12*seam*(1.0+uOptics.y));
  // Capsule and bottom sheet use the exact same incident light as the floor.
  float lightP=smoothstep(0.24,0.32,progress),lightS=smoothstep(0.37,0.45,progress);
  float pill=sdRound(p-float2(201.0,526.0),float2(185.0,29.0),29.0);
  float sheet=sdRound(p-float2(201.0,900.0),float2(201.0,304.0),uShape.w);
  float chip=sdRound(p-float2(201.0,646.0),float2(52.0,17.0),17.0);
  float cross=sdRound(p-float2(362.0,66.0),float2(22.0),22.0);
  float glassMask=1.0-smoothstep(-0.5,0.5,pill);
  col=mix(col,uCore,glassMask*(uShape.y+0.12*lightP));
  float rim=(1.0-smoothstep(0.15,0.8,abs(pill)))*(0.25+0.75*sat((526.0-p.y+10.0)/39.0));
  col=mix(col,uCore,rim*(uShape.z+0.35*lightP));
  float sm=1.0-smoothstep(-0.6,0.6,sheet);
  col=mix(col,uUnder,sm*0.05);
  float sheetRim=(1.0-smoothstep(0.15,0.9,abs(sheet)))*sat((640.0-p.y)/44.0);
  col=mix(col,uCore,sheetRim*(uShape.z+0.4*lightS));
  float catchLight=exp(-max(0.0,p.y-596.0)/40.0)*sm*lightS;
  col=mix(col,uCore,catchLight*0.16);
  float chipRim=(1.0-smoothstep(0.1,0.8,abs(chip)))*(0.3+0.7*sat((654.0-p.y)/25.0));
  col=mix(col,uCore,chipRim*0.65*showDetails);
  col=mix(col,uCore,(1.0-smoothstep(-0.5,0.5,chip))*0.025*showDetails);
  float crossRim=(1.0-smoothstep(0.1,0.85,abs(cross)))*(0.15+0.85*pow(abs(sin(atan(p.y-66.0,p.x-362.0)-0.65)),8.0));
  col=mix(col,uCore,crossRim*0.85*showClose);
  float2 xx=p-float2(362.0,66.0);
  float crossLine=min(sdRound(float2((xx.x+xx.y)*0.707107,(xx.y-xx.x)*0.707107),float2(10.5,0.85),0.7),sdRound(float2((xx.x-xx.y)*0.707107,(xx.y+xx.x)*0.707107),float2(10.5,0.85),0.7));
  col=mix(col,uCore,(1.0-smoothstep(-0.2,0.4,crossLine))*showClose);
  float2 clock=p-float2(174.0,646.0);
  float dial=abs(length(clock)-6.4)-0.55;
  float hand=min(sdRound(clock-float2(0.0,-2.0),float2(0.5,2.0),0.4),sdRound(clock-float2(1.6,0.0),float2(1.6,0.5),0.4));
  col=mix(col,mix(uCore,uPage*0.58,lightS),(1.0-smoothstep(-0.15,0.45,min(dial,hand)))*showDetails);
  float grain=(hash21(floor(p*3.0))-0.5)*uOptics.w*0.5;
  col=clamp(col+grain,float3(0.0),float3(0.99));
  if(uOptics.z>0.5) col=p3(col);
  return half4(col,1.0);
}
`;
