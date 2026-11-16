#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Direct translation of the approved gcu-shaders.ts + gcu-studio.ts, including polish.
// Keep the reference's encoded Display P3 result; adapt it to SwiftUI's sRGB host below.
namespace GelCubeUnboxShader {
struct GelCubeUnboxScene {

float4 uFrame;
float4 uShape;
float4 uState;
float4 uOptics;
float3 uTint;
float3 uPage;
float3 uFloor;
float3 uEdge;
float3 uCore;
float3 uFan;
float3 uUnder;
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

float3 studioTint(float3 delta,float3 tint) {
  float3 base=float3(47.0,99.0,216.0)/255.0;
  if(length(tint-base)<0.00001) return delta;
  float3 neutral=normalize(float3(1.0));
  float3 oldAxis=base-neutral*dot(base,neutral);
  float oldChroma=max(0.00001,length(oldAxis));
  oldAxis/=oldChroma;
  float3 newAxis=tint-neutral*dot(tint,neutral);
  float newChroma=length(newAxis);
  newAxis=newChroma>0.00001 ? newAxis/newChroma : oldAxis;
  float scale=clamp(newChroma/oldChroma,0.0,1.4);
  return neutral*dot(delta,neutral)+scale*(newAxis*dot(delta,oldAxis)+cross(neutral,newAxis)*dot(delta,cross(neutral,oldAxis)));
}

float3 studioPage(float2 p,float3 page,float3 floorColor,float3 tint) {
  float x=(p.x-201.0)/201.0,y=(p.y-300.0)/300.0;
  float x2=x*x,x3=x2*x,x4=x3*x,y2=y*y,y3=y2*y,y4=y3*y;
  float c0=(121.8820975)*1.0 + (9.6421052)*y + (-304.4327713)*y2 + (-5.9829222)*y3 + (191.6102040)*y4 + (-21.5562409)*x + (-12.7486659)*x*y + (17.6019079)*x*y2 + (8.3686411)*x*y3 + (-199.2838889)*x2 + (-15.6869266)*x2*y + (163.8982806)*x2*y2 + (19.8128677)*x3 + (4.5537877)*x3*y + (95.6992651)*x4;
  float c1=(-41.0229208)*1.0 + (-3.3636841)*y + (102.6678371)*y2 + (2.2357038)*y3 + (-64.7124885)*y4 + (7.3101623)*x + (4.1040536)*x*y + (-5.9851316)*x*y2 + (-2.6847236)*x*y3 + (67.1543870)*x2 + (5.3151688)*x2*y + (-55.2636632)*x2*y2 + (-6.6400867)*x3 + (-1.4675604)*x3*y + (-32.2506152)*x4;
  float c2=(-30.6638063)*1.0 + (-2.3688341)*y + (76.6308477)*y2 + (1.3924405)*y3 + (-48.2262297)*y4 + (5.4523713)*x + (3.3152840)*x*y + (-4.4328587)*x*y2 + (-2.2076868)*x*y3 + (50.2033662)*x2 + (3.9377387)*x2*y + (-41.3135762)*x2*y2 + (-5.0355677)*x3 + (-1.1857514)*x3*y + (-24.1192551)*x4;
  float3 delta=(float3(60.0,81.0,128.0)/255.0)*c0+(float3(143.0,166.0,218.0)/255.0)*c1+(float3(47.0,99.0,216.0)/255.0)*c2;
  return studioTint(delta,tint);
}
float3 studioTop(float2 p,float3 page,float3 floorColor,float3 tint) {
  float x=(p.x-196.0)/110.0,y=(p.y-153.0)/30.0;
  float x2=x*x,x3=x2*x,x4=x3*x,y2=y*y,y3=y2*y,y4=y3*y;
  float c0=(2.8355152)*1.0 + (108.2827841)*y + (53.3345372)*y2 + (-99.4722462)*y3 + (-29.4256126)*y4 + (16.3822849)*x + (-52.3031534)*x*y + (-57.9289452)*x*y2 + (3.6010449)*x*y3 + (-169.2856960)*x2 + (-350.4651595)*x2*y + (10.2555080)*x2*y2 + (-76.9109187)*x3 + (5.7768080)*x3*y + (82.9793374)*x4;
  float c1=(-1.0301145)*1.0 + (-36.2479028)*y + (-17.8718350)*y2 + (33.6512101)*y3 + (10.0198971)*y4 + (-5.5518507)*x + (17.4653621)*x*y + (18.9285909)*x*y2 + (-1.3361027)*x*y3 + (56.7886835)*x2 + (117.8598308)*x2*y + (-3.3040197)*x2*y2 + (25.9112281)*x3 + (-1.6806942)*x3*y + (-27.8894629)*x4;
  float c2=(-0.5867601)*1.0 + (-27.5108529)*y + (-13.6185504)*y2 + (24.9439072)*y3 + (7.3272040)*y4 + (-4.0750668)*x + (13.3546446)*x*y + (15.0973800)*x*y2 + (-0.7835769)*x*y3 + (42.8038502)*x2 + (88.4825849)*x2*y + (-2.6531361)*x2*y2 + (19.2603480)*x3 + (-1.6391237)*x3*y + (-21.0453746)*x4;
  float3 delta=(float3(60.0,81.0,128.0)/255.0)*c0+(float3(143.0,166.0,218.0)/255.0)*c1+(float3(47.0,99.0,216.0)/255.0)*c2;
  return studioTint(delta,tint);
}
float3 studioLeft(float2 p,float3 page,float3 floorColor,float3 tint) {
  float x=(p.x-150.0)/64.0,y=(p.y-245.0)/90.0;
  float x2=x*x,x3=x2*x,x4=x3*x,y2=y*y,y3=y2*y,y4=y3*y;
  float c0=(151.6151172)*1.0 + (12.3521329)*y + (-255.4911307)*y2 + (-107.9885587)*y3 + (-89.5878860)*y4 + (142.9802454)*x + (-72.6648056)*x*y + (-39.9556520)*x*y2 + (-172.2115134)*x*y3 + (-172.3641915)*x2 + (290.9978480)*x2*y + (602.3461110)*x2*y2 + (-23.9957328)*x3 + (119.3288201)*x3*y + (31.0381890)*x4;
  float c1=(-51.3003604)*1.0 + (-3.8696650)*y + (85.8592906)*y2 + (35.8971051)*y3 + (30.2768066)*y4 + (-48.0050352)*x + (24.8276965)*x*y + (12.9993926)*x*y2 + (57.8149110)*x*y3 + (58.8803007)*x2 + (-97.4632284)*x2*y + (-203.1079875)*x2*y2 + (8.1848463)*x3 + (-39.9817246)*x3*y + (-11.2573926)*x4;
  float c2=(-38.1324879)*1.0 + (-3.4810445)*y + (64.7752508)*y2 + (27.7763781)*y3 + (22.4753765)*y4 + (-36.2956529)*x + (18.0080778)*x*y + (10.6324335)*x*y2 + (43.5888950)*x*y3 + (42.8115810)*x2 + (-73.8072517)*x2*y + (-151.8364130)*x2*y2 + (6.0068573)*x3 + (-30.3173721)*x3*y + (-7.1573297)*x4;
  float3 delta=(float3(60.0,81.0,128.0)/255.0)*c0+(float3(143.0,166.0,218.0)/255.0)*c1+(float3(47.0,99.0,216.0)/255.0)*c2;
  return studioTint(delta,tint);
}
float3 studioRight(float2 p,float3 page,float3 floorColor,float3 tint) {
  float x=(p.x-259.0)/49.0,y=(p.y-244.0)/91.0;
  float x2=x*x,x3=x2*x,x4=x3*x,y2=y*y,y3=y2*y,y4=y3*y;
  float c0=(88.0425900)*1.0 + (37.6426490)*y + (-258.7306305)*y2 + (-33.0003463)*y3 + (30.6107624)*y4 + (-178.3193786)*x + (309.5446637)*x*y + (330.8832915)*x*y2 + (77.1749925)*x*y3 + (27.9423378)*x2 + (148.9136980)*x2*y + (244.1303615)*x2*y2 + (46.7415201)*x3 + (-275.6923692)*x3*y + (-40.0889591)*x4;
  float c1=(-29.7454281)*1.0 + (-12.5707846)*y + (87.4572302)*y2 + (11.1974285)*y3 + (-10.6427251)*y4 + (60.2018079)*x + (-103.6652879)*x*y + (-110.5031293)*x*y2 + (-25.5851460)*x*y3 + (-9.3235031)*x2 + (-49.5347599)*x2*y + (-81.6661236)*x2*y2 + (-16.2027793)*x3 + (92.1084299)*x3*y + (13.0946533)*x4;
  float c2=(-22.1047641)*1.0 + (-9.7041260)*y + (65.0195490)*y2 + (8.4886080)*y3 + (-7.3471734)*y4 + (44.9895569)*x + (-78.3371422)*x*y + (-84.3931607)*x*y2 + (-19.8149962)*x*y3 + (-7.2840128)*x2 + (-38.0295120)*x2*y + (-62.0036312)*x2*y2 + (-11.4354906)*x3 + (70.0212874)*x3*y + (10.6239954)*x4;
  float3 delta=(float3(60.0,81.0,128.0)/255.0)*c0+(float3(143.0,166.0,218.0)/255.0)*c1+(float3(47.0,99.0,216.0)/255.0)*c2;
  return studioTint(delta,tint);
}

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
half4 render(float2 frag) {
  float2 p=(frag-uFrame.xy)/uFrame.z;
  float progress=sat(uState.x), core=uState.y;
  float showClose=fmod(uFrame.w,2.0),showDetails=step(1.5,uFrame.w);
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
  float closeDisk=sdRound(p-float2(362.0,66.0),float2(22.0),22.0);
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
  float crossRim=(1.0-smoothstep(0.1,0.85,abs(closeDisk)))*(0.15+0.85*pow(abs(sin(atan2(p.y-66.0,p.x-362.0)-0.65)),8.0));
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
  return half4(half3(col),1.0h);
}

};

inline half4 GelCubeUnboxDisplaySRGB(half4 color) {
    float3 c = float3(color.rgb);
    c = select(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), c > 0.04045);
    c = float3(dot(c, float3(1.22474527, -0.22490437, 0.0)),
               dot(c, float3(-0.04205797, 1.04208100, 0.0)),
               dot(c, float3(-0.01964227, -0.07865492, 1.09853719)));
    c = max(c, 0.0);
    c = select(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, c > 0.0031308);
    return half4(half3(c), color.a);
}
} // namespace GelCubeUnboxShader

[[ stitchable ]] half4 gelCubeUnbox(float2 position, half4 color,
                                  device const float *u, int count) {
    if (count < 37) return color;
    GelCubeUnboxShader::GelCubeUnboxScene s;
    s.uFrame = float4(u[0],u[1],u[2],u[3]);
    s.uShape = float4(u[4],u[5],u[6],u[7]);
    s.uState = float4(u[8],u[9],u[10],u[11]);
    s.uOptics = float4(u[12],u[13],u[14],u[15]);
    s.uTint = float3(u[16],u[17],u[18]);
    s.uPage = float3(u[19],u[20],u[21]);
    s.uFloor = float3(u[22],u[23],u[24]);
    s.uEdge = float3(u[25],u[26],u[27]);
    s.uCore = float3(u[28],u[29],u[30]);
    s.uFan = float3(u[31],u[32],u[33]);
    s.uUnder = float3(u[34],u[35],u[36]);
    return GelCubeUnboxShader::GelCubeUnboxDisplaySRGB(s.render(position));
}
