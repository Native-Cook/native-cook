package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

// Shader equations and uniform names preserved from the React Native source.
internal const val TROPHY_AGSL = """
uniform float2 uOrigin;
uniform float uUnit;
uniform float uPixelRatio;
uniform float2 uCamera;
uniform float2 uTurn;
uniform float4 uPlanes[31];
uniform float4 uBase[18];
uniform float4 uRim[32];
uniform float4 uFire[8];
uniform float uDrop;
uniform float uLamp;
uniform float uUnder;
uniform float uEtch;
uniform float uTouch;
uniform float uFlash;
uniform float2 uFinger;
uniform float uTime;
uniform shader stage;
uniform shader engraving;
uniform shader marble;

float squared(float v) { return v*v; }
float3 safeUnit(float3 v) { return v * inversesqrt(max(dot(v,v), 0.00000001)); }
float3 toObject(float3 v) {
  float ce=cos(uCamera.y), se=sin(uCamera.y);
  float3 q=float3(v.x,v.y*ce+v.z*se,-v.y*se+v.z*ce);
  return float3(q.x*uTurn.x-q.z*uTurn.y,q.y,q.x*uTurn.y+q.z*uTurn.x);
}
float3 toView(float3 v) {
  float3 q=float3(v.x*uTurn.x+v.z*uTurn.y,v.y,-v.x*uTurn.y+v.z*uTurn.x);
  float ce=cos(uCamera.y), se=sin(uCamera.y);
  return float3(q.x,q.y*ce-q.z*se,q.y*se+q.z*ce);
}
float2 projected(float3 p) {
  float3 q=toView(p);
  return uOrigin + float2(q.x,-q.y)*uUnit*uCamera.x/max(0.2,uCamera.x-q.z);
}
float tile(float2 uv, float index) {
  return engraving.eval(float2((clamp(uv.x,0.002,0.998)+index)*512.0,clamp(uv.y,0.002,0.998)*512.0)).r;
}
float fresnel(float cosine, float eta) {
  float f=(eta-1.0)/(eta+1.0); f*=f;
  float a=1.0-clamp(cosine,0.0,1.0); float a2=a*a;
  return f+(1.0-f)*a2*a2*a;
}
float3 transmission(float3 rayDir, float3 normal, float ratio) {
  float c=-dot(normal,rayDir);
  float k=1.0-ratio*ratio*(1.0-c*c);
  if(k<=0.0) return float3(0);
  return ratio*rayDir+(ratio*c-sqrt(max(k,0.0)))*normal;
}
// Interval clipping also returns the two limiting planes, which define the silhouette's analytic gradient.
float crystalHit(float3 ro,float3 rd,out float nearT,out float farT,out float3 nearN,out float3 farN,out float face) {
  nearT=-10000.0; farT=10000.0; nearN=float3(0,0,1); farN=float3(0,0,-1); face=0.0;
  float valid=1.0;
  for(int i=0;i<31;i++) {
    float4 pl=uPlanes[i];
    if(i==30) pl.w=-uDrop;
    float den=dot(pl.xyz,rd), numer=pl.w-dot(pl.xyz,ro);
    if(abs(den)<0.000001) { if(numer<0.0) valid=0.0; }
    else {
      float t=numer/den;
      if(den<0.0 && t>nearT) { nearT=t;nearN=pl.xyz;face=float(i); }
      if(den>0.0 && t<farT) { farT=t;farN=pl.xyz; }
    }
  }
  return valid;
}
float baseHit(float3 ro,float3 rd,out float nearT,out float farT,out float3 nearN,out float3 farN) {
  nearT=-10000.0;farT=10000.0;nearN=float3(0,1,0);farN=float3(0,-1,0);float valid=1.0;
  for(int i=0;i<18;i++) {
    float4 pl=uBase[i];float den=dot(pl.xyz,rd),numer=pl.w-dot(pl.xyz,ro);
    if(abs(den)<0.000001) { if(numer<0.0) valid=0.0; }
    else { float t=numer/den;
      if(den<0.0 && t>nearT){nearT=t;nearN=pl.xyz;}
      if(den>0.0 && t<farT){farT=t;farN=pl.xyz;}
    }
  }
  return valid;
}
float coverage(float nt,float ft,float3 nn,float3 fn,float3 rd) {
  float dn=dot(nn,rd),df=dot(fn,rd);
  float3 dx=toObject(float3(1,0,0))/uUnit;
  float3 dy=toObject(float3(0,1,0))/uUnit;
  float2 grad=float2(0);
  if(abs(dn)>0.000001 && abs(df)>0.000001)
    grad=float2(-ft*dot(fn,dx)/df+nt*dot(nn,dx)/dn,-ft*dot(fn,dy)/df+nt*dot(nn,dy)/dn);
  float width=clamp(length(grad)/uPixelRatio,0.0000001,0.003/uPixelRatio);
  return clamp(0.5+(ft-nt)/width,0.0,1.0);
}
// Reflections see the house. Only outgoing transmission samples the real stage.
float3 env(float3 dir,float3 pos) {
  float3 v=safeUnit(toView(dir));
  float3 p=toView(pos-float3(0,uDrop,0));
  float alignment=dot(v,safeUnit(float3(0.16,0.9,0.4)));
  float disc=smoothstep(0.993,0.995,alignment)*9.0;
  float halo=pow(max(alignment,0.0),24.0)*0.8;
  float3 c=float3(0.002,0.004,0.014)+float3(0.045,0.065,0.13)*uLamp;
  // A large lilac softbox, 50 degrees wide, with a diagonal falloff across the slab.
  // Position in the house stays fixed as the trophy turns beneath it.
  float panel=pow(max(dot(v,safeUnit(float3(-0.40,0.56,1.0))),0.0),7.0);
  float sweep=0.12+0.88*smoothstep(0.6,3.1,p.y-p.x*1.2);
  c+=float3(15.0,14.4,15.6)*panel*sweep*uLamp*(1.0+uFlash*0.12);
  float strip=exp(-squared((v.x-0.66)*19.0))*smoothstep(-0.7,0.0,v.y);
  c+=float3(2.5,3.5,4.8)*strip*uLamp;
  for(int j=0;j<5;j++) {
    float a=float(j);
    float3 pin=safeUnit(float3(-0.78+a*0.37,0.14+0.19*sin(a*2.3),1.0));
    c+=float3(2.8,3.4,4.2)*pow(max(dot(v,pin),0.0),1400.0)*uLamp;
  }
  // A soft low reflector gives internally reflected lower facets a broad band, never a rule.
  float low=exp(-squared((v.y+v.x*0.62+0.30)*4.0));
  c+=float3(0.55,0.72,1.1)*low*uLamp;
  c+=float3(0.55,0.65,0.82)*max(-v.y,0.0)*max(-v.y,0.0)*uLamp;
  float flag=exp(-squared((v.x*0.6+v.y*0.35+p.x*0.70-p.y*0.15-0.12)*12.0));
  c=mix(c,float3(0.055,0.102,0.36)*uLamp,flag*0.88);
  return c+float3(0.86,0.94,1.0)*(disc+halo)*uLamp;
}
float3 outside(float3 dir,float3 pos) {
  float3 v=safeUnit(toView(dir));
  if(v.z < -0.15) {
    float2 offset=2.4*float2(v.x,-v.y)/max(-v.z,0.3);
    offset*=min(1.0,1.6/max(length(offset),0.0001));
    float2 canvasPoint=projected(pos-float3(0,uDrop,0))+uUnit*offset;
    float3 transmitted=stage.eval(canvasPoint).rgb*float3(0.87,0.885,0.90);
    // House flags interrupt oblique transmission; the bands move with the exiting ray.
    float3 wallRay=toView(pos-float3(0,uDrop,0));
    float band=exp(-squared((v.x*0.6+v.y*0.35+wallRay.x*0.70-wallRay.y*0.15-0.12)*12.0));
    float oblique=smoothstep(0.06,0.18,abs(v.x)+abs(v.y)*0.7);
    return mix(transmitted,float3(0.055,0.102,0.36)*uLamp,band*oblique*0.94);
  }
  return env(dir,pos);
}
float3 spectrum(float3 rd,float3 n,float3 p) {
  float3 rr=transmission(rd,n,1.508),gg=transmission(rd,n,1.52),bb=transmission(rd,n,1.534);
  float3 c=float3(0);
  if(dot(rr,rr)>0.1)c.r=outside(rr,p).r;
  if(dot(gg,gg)>0.1)c.g=outside(gg,p).g;
  if(dot(bb,bb)>0.1)c.b=outside(bb,p).b;
  return c;
}
float frost(float3 p,float face,out float3 tint) {
  tint=float3(0);
  if(p.y<1.45 && abs(face-3.0)>0.1)return 0.0;
  if(p.y>=1.45 && (face<3.5 || face>6.5))return 0.0;
  if((p.y<1.45 && uEtch<0.88) || (p.y>=1.45 && uEtch<=0.30))return 0.0;
  float2 uv=float2((p.x-0.02)/1.152+0.5,0.5-(p.y-2.125)/0.8775);
  float which=0.0;float present=1.0;
  if(p.y<1.45) {
    uv=float2(p.x/0.43+0.5,0.5-(p.y-0.98)/0.43);which=2.0;
    present=smoothstep(0.88,0.995,uEtch-length(uv-0.5)*0.05);
  }
  float inTile=step(0.0,uv.x)*step(uv.x,1.0)*step(0.0,uv.y)*step(uv.y,1.0);
  if(inTile<0.5)return 0.0;
  float sdf=tile(uv,which);
  float aa=(which<1.0 ? 8.0 : 18.0)/(uUnit*uPixelRatio);
  float mask=smoothstep(0.5-aa,0.5+aa,sdf)*inTile;
  float order=tile(uv,1.0);
  float progress=clamp((uEtch-0.30)/0.62,0.0,1.0);
  float laser=0.0;
  if(which<1.0) {
    present=smoothstep(order-0.018,order+0.018,progress)*step(0.301,uEtch);
    if(uEtch<0.925) laser=exp(-squared((order-progress)*95.0))*exp(-squared((sdf-0.53)*7.0))*inTile;
  }
  if(sdf<0.30 && laser<0.0001)return 0.0;
  float2 eps=float2(1.0/512.0,0);
  float gx=tile(uv+eps,which)-tile(uv-eps,which);
  float gy=tile(uv+eps.yx,which)-tile(uv-eps.yx,which);
  // 32 source pixels encode the signed distance; convert a 1.7pt wall to SDF units.
  float wallWidth=(which<1.0 ? 512.0/1.152 : 512.0/0.43)*1.7/(32.0*uUnit);
  float slope=clamp(-(gx+gy)*25.0,-1.0,1.0);
  float wall=exp(-squared((sdf-0.54)/max(wallWidth,0.01)));
  float grain=tile(uv*3.0-floor(uv*3.0),3.0);
  float3 body=float3(0.965,0.977,1.0)*(0.99-0.08*uv.y+0.018*grain);
  float3 wallTint=mix(float3(0.431,0.525,0.784),float3(1.5,1.6,1.75),smoothstep(-0.2,0.55,slope));
  tint=mix(body,wallTint,wall)*(0.025+uLamp+uUnder*0.65);
  float contact=exp(-squared((sdf-0.43)/0.052))*smoothstep(0.05,-0.30,slope)*(1.0-mask)*present;
  tint=mix(tint,float3(0.045,0.10,0.27)*(0.02+uLamp),contact);
  tint+=float3(0.7,1.1,1.7)*laser*3.0;
  return clamp(max(max(mask*present*0.86,contact*0.70),laser*0.8),0.0,1.0);
}
float3 glass(float3 ro,float3 rd,float enterT,float3 normal,float face) {
  float3 hit=ro+rd*max(enterT,0.0);
  float3 incident=safeUnit(rd);
  float f=fresnel(-dot(incident,normal),1.52);
  float3 result=env(reflect(incident,normal),hit)*f;
  float3 rayDir=transmission(incident,normal,1.0/1.52);
  float3 throughput=float3(1.0-f);
  // Engraving viewed from the rear is intercepted on entry and is naturally mirrored by the camera.
  if((face>3.5 && face<7.0) || abs(face-3.0)<0.1) {
    float3 ink;float m=frost(hit,face,ink);result+=throughput*ink*(face<3.5 ? float3(1.0) : float3(0.64,0.74,0.90))*m;throughput*=1.0-m;
  }
  float3 point=hit+rayDir*0.0003;
  for(int bounce=0;bounce<4;bounce++) {
    float exitT=10000.0;float3 exitN=float3(0,0,-1);float exitFace=0.0;
    for(int i=0;i<31;i++) {
      float4 pl=uPlanes[i];if(i==30)pl.w=-uDrop;
      float den=dot(pl.xyz,rayDir);
      if(den>0.000001) { float t=(pl.w-dot(pl.xyz,point))/den;
        if(t<exitT && t>=-0.0001){exitT=max(t,0.0);exitN=pl.xyz;exitFace=float(i);}
      }
    }
    if(exitT>1000.0)break;
    point+=rayDir*exitT;
    throughput*=exp(-float3(0.020,0.009,0.003)*exitT);
    if((exitFace>3.5 && exitFace<7.0) || abs(exitFace-3.0)<0.1) {
      float3 ink;float m=frost(point,exitFace,ink);
      result+=throughput*ink*m;throughput*=1.0-m;
    }
    if(exitFace>30.0-0.5) {
      result+=throughput*float3(0.65,1.05,1.8)*uUnder*2.4;
      throughput*=0.12;
    }
    float cosI=dot(rayDir,exitN);
    float3 outgoing=transmission(rayDir,-exitN,1.52);
    float split=dot(outgoing,outgoing)>0.1 ? fresnel(cosI,1.52) : 1.0;
    if(split<1.0) result+=throughput*spectrum(rayDir,-exitN,point)*(1.0-split);
    throughput*=split;
    if(max(throughput.r,max(throughput.g,throughput.b))<0.012)break;
    rayDir=reflect(rayDir,exitN);point+=rayDir*0.0003;
  }
  result+=throughput*float3(0.013,0.028,0.09)*(0.05+uLamp+uUnder);
  // Distance to a neighbouring plane in canvas points: constant-width lit seams at every yaw.
  float edge=100.0;float edgeFace=-1.0;
  float den=dot(normal,rd);
  float3 dx=toObject(float3(1,0,0))/uUnit;
  float3 dy=toObject(float3(0,1,0))/uUnit;
  float safeDen=abs(den)<0.00001 ? -0.00001 : den;
  float3 hx=enterT*(dx-rd*dot(normal,dx)/safeDen);
  float3 hy=enterT*(dy-rd*dot(normal,dy)/safeDen);
  for(int i=0;i<30;i++) {
    float4 pl=uPlanes[i];
    float grad=length(float2(dot(pl.xyz,hx),dot(pl.xyz,hy)));
    if(abs(float(i)-face)>0.1 && grad>0.00001) {
      float candidate=abs(pl.w-dot(pl.xyz,hit))/grad;
      if(candidate<edge){edge=candidate;edgeFace=float(i);}
    }
  }
  float lampFacing=smoothstep(-0.30,0.8,dot(toView(normal),safeUnit(float3(-0.5,0.8,0.4))));
  float edgeWidth=mix(0.15,0.45,lampFacing);
  float edgeLight=exp(-edge*edge/edgeWidth)+0.12*exp(-edge*edge/5.0);
  float etched=1.0;
  if(uEtch<0.38) {
    float phase=atan(hit.x,hit.y-1.34)/6.2831853;
    if(phase<0.0)phase+=1.0;
    etched=0.55+0.45*smoothstep(phase-0.02,phase+0.02,uEtch/0.38);
  }
  float edgeFacing=0.16+0.82*lampFacing;
  edgeFacing*=0.35+0.65*smoothstep(-0.6,0.65,hit.y*0.4-hit.x);
  if(abs(face-3.0)<0.1 || abs(edgeFace-3.0)<0.1)edgeFacing=max(edgeFacing,0.85);
  if((face<0.5 && abs(edgeFace-3.0)<0.1) || (abs(face-3.0)<0.1 && edgeFace<0.5))
    edgeFacing*=0.16+0.84*exp(-(hit.x+0.50)*(hit.x+0.50)*5.0);
  result+=float3(0.68,0.91,1.12)*edgeLight*(0.015+uLamp*1.20+uUnder*1.25)*etched*edgeFacing;
  float foot=max(hit.y-uDrop,0.0);
  float body=0.030+0.050*exp(-edge/10.0)+0.016*smoothstep(1.7,2.9,hit.y);
  result+=float3(0.65,0.80,1.0)*body*uLamp;
  // Light injected at the cut face spreads through the full slab during the rise.
  float pipe=uUnder*(0.35+1.4*exp(-foot*1.5));
  float fire=exp(-foot*6.0)*(0.52*uLamp+1.25*uUnder);
  result+=float3(0.55,0.91,1.25)*pipe+float3(0.40,0.85,1.25)*fire;
  result+=float3(0.11,0.025,0.015)*exp(-squared((foot-0.055)*35.0))*(uLamp+uUnder);
  float2 delta=hit.xy-float2(uFinger.x,uFinger.y+uDrop);
  float radius=length(delta);
  result+=float3(0.4,0.75,1.1)*uTouch*(exp(-radius*radius*17.0)*0.45+exp(-radius*radius*1200.0)*2.5);
  float ring=exp(-squared((radius-(1.0-uFlash)*2.8)*42.0))*sqrt(max(uFlash,0.0));
  result+=float3(0.85,1.1,1.6)*(ring*1.8+edgeLight*uFlash*3.0);
  return result;
}
float3 caustic(float3 p) {
  float3 c=float3(0);
  float3 light=toObject(safeUnit(float3(0.16,0.9,0.4)));
  for(int j=0;j<4;j++) {
    float a=float(j);float3 normal=safeUnit(float3((a-1.5)*0.31,0.23,0.8));
    float3 bend=transmission(-light,normal,0.66);
    float2 centre=float2((a-1.5)*0.2,0.0)+bend.xz/max(abs(bend.y),0.18)*0.55;
    float2 q=p.xz-centre;
    float streak=exp(-q.x*q.x*5.0-q.y*q.y*180.0);
    c+=float3(0.22,0.4,0.65)*streak;
    c+=float3(0.15,0.04,0.02)*exp(-q.x*q.x*5.0-(q.y-0.022)*(q.y-0.022)*350.0);
  }
  return c*uLamp*0.34;
}
float3 stone(float3 p,float3 n,float3 rd) {
  // Triplanar mapping blends across the chamfer; top and sides share world coordinates.
  float3 weights=abs(n);weights*=weights;weights/=max(weights.x+weights.y+weights.z,0.001);
  float3 tex=marble.eval((p.xz*0.46+0.5)*768.0).rgb*weights.y;
  tex+=marble.eval((float2(p.x,p.y+p.z)*0.46+0.5)*768.0).rgb*weights.z;
  tex+=marble.eval((float2(p.z,p.y+p.x)*0.46+0.5)*768.0).rgb*weights.x;
  float3 light=toObject(safeUnit(float3(0.16,0.9,0.4)));
  float diffuse=max(dot(n,light),0.0);
  float3 c=tex*(float3(0.013,0.022,0.045)+uLamp*(float3(0.45,0.49,0.58)+diffuse*0.48));
  float3 h=safeUnit(light-safeUnit(rd));
  c+=float3(0.5,0.65,0.85)*pow(max(dot(n,h),0.0),65.0)*uLamp*0.35;
  float chamfer=step(0.1,n.y)*step(n.y,0.9);
  c+=float3(0.24,0.29,0.36)*chamfer*uLamp;
  if(n.y>0.5) {
    float slot=exp(-p.z*p.z*220.0)*smoothstep(0.51,0.39,abs(p.x));
    c*=1.0-slot*0.65;
    c*=1.0-0.3*exp(-p.x*p.x*3.0-(p.z+0.15)*(p.z+0.15)*12.0);
    float footHalo=exp(-p.x*p.x*5.0-p.z*p.z*32.0);
    c+=caustic(p)+float3(0.16,0.32,0.48)*footHalo*(uLamp*0.55+uUnder);
    c+=float3(0.25,0.65,1.25)*uUnder*slot;
    c+=float3(0.14,0.20,0.28)*exp(-p.x*p.x*7.0-(p.z-0.26)*(p.z-0.26)*35.0)*uLamp;
  }
  if(n.z>0.95 && abs(p.z-0.94)<0.02) {
    float2 uv=float2(p.x/0.20+0.5,0.5-(p.y+0.17)/0.20);
    float markAA=32.0/(uUnit*uPixelRatio);
    float m=smoothstep(0.5-markAA,0.5+markAA,tile(uv,2.0))*step(0.0,uv.x)*step(uv.x,1.0)*step(0.0,uv.y)*step(uv.y,1.0);
    float3 steel=float3(0.12,0.20,0.30)*(0.12+uLamp)+float3(0.25,0.38,0.50)*exp(-squared((uv.y-0.35)*10.0))*uLamp+float3(0.4,0.55,0.72)*uLamp*pow(max(dot(n,h),0.0),12.0);
    c=mix(c,steel,m);
  }
  // Preserve the white marble's veins through the lamp's 25% overshoot.
  if(uLamp>1.0) {
    float3 baseline=c/uLamp;
    float3 headroom=max(float3(0.01),float3(0.995)-baseline);
    c=baseline+headroom*(1.0-exp(-(c-baseline)/headroom));
  }
  return c;
}
float star(float2 d,float size) {
  float2 a=abs(d)/max(size,0.001);
  float core=exp(-dot(a,a)*8.0);
  float rays=exp(-min(a.x,a.y)*28.0)*exp(-max(a.x,a.y)*2.7);
  return core+rays*0.6;
}
float flashStar(float2 d,float size) {
  float2 a=abs(d)/max(size,0.001);
  return exp(-dot(a,a)*200.0)+exp(-min(a.x,a.y)*70.0-max(a.x,a.y)*2.7)*0.75;
}
float3 glints(float2 xy) {
  float3 c=float3(0);
  for(int j=0;j<8;j++) {
    float4 pin=uFire[j];
    if(pin.z*(uLamp+uUnder*0.7)>0.0001) {
      float2 d=xy-pin.xy;
      if(dot(d,d)<pin.w*pin.w*9.0) {
        float spread=j==0 ? 0.7 : 0.95;
        float3 sparkle=float3(star(d+float2(spread,0),pin.w),star(d,pin.w),star(d-float2(spread,0),pin.w));
        if(j==0 && uFlash>0.0) {
          float3 burst=float3(flashStar(d+float2(spread,0),pin.w),flashStar(d,pin.w),flashStar(d-float2(spread,0),pin.w));
          sparkle=mix(sparkle,burst,uFlash);
        }
        c+=sparkle*float3(0.86,0.94,1.0)*pin.z*(uLamp+uUnder*0.7)*(1.0+0.05*sin(uTime*0.83775804));
      }
    }
  }
  if(uEtch>0.0 && uEtch<0.38) {
    float phase=uEtch/0.38*7.0;
    float2 a=float2(0.05,2.90),b=float2(0.76,2.46);
    if(phase>=1.0){a=b;b=float2(0.86,1.36);}
    if(phase>=2.0){a=b;b=float2(0.40,0.0);}
    if(phase>=3.0){a=b;b=float2(-0.40,0.0);}
    if(phase>=4.0){a=b;b=float2(-0.86,1.34);}
    if(phase>=5.0){a=b;b=float2(-0.74,2.52);}
    if(phase>=6.0){a=b;b=float2(0.05,2.90);}
    float2 pt=mix(a,b,fract(phase));float z=min(0.39-0.075*pt.y,0.015+0.26*pt.y-0.22*abs(pt.x));
    if(pt.y>=uDrop-0.005)c+=float3(0.6,1.0,1.5)*star(xy-projected(float3(pt.x,pt.y-uDrop,z)),10.0*uUnit/100.0)*2.0;
  }
  if(uFlash>0.0001) {
    float2 fd=xy-projected(float3(uFinger.x,uFinger.y,0.28));
    if(dot(fd,fd)<uUnit*uUnit*0.5){
      float size=45.0*uUnit/108.0;
      c+=float3(flashStar(fd+float2(0.8,0),size),flashStar(fd,size),flashStar(fd-float2(0.8,0),size))*float3(0.9,1.0,1.2)*uFlash*2.5;
    }
  }
  return c;
}
half4 main(float2 xy) {
  float2 q=(xy-uOrigin)/uUnit;
  float3 ro=toObject(float3(0,0,uCamera.x));
  float3 rd=toObject(float3(q.x,-q.y,-uCamera.x));
  float3 rgb=float3(0);float alpha=0.0;
  // The floor is transparent except for a tight contact shadow and refracted spill.
  if(q.y > -0.36 && rd.y < -0.0001) {
    float ft=(-0.305-ro.y)/rd.y;float3 floorP=ro+rd*ft;
    float rad=length(floorP.xz/float2(1.15,1.08));
    float shadow=0.55*exp(-pow(rad/0.95,4.0));
    float3 spill=float3(0);
    if(rad<1.9)spill=caustic(floorP)*exp(-pow(rad/1.2,4.0))*0.4;
    float ca=max(spill.r,max(spill.g,spill.b));
    alpha=clamp(shadow+ca,0.0,0.8);rgb=spill;
  }
  float nt,ft;float3 nn,fn;
  if(q.y > -0.36 && q.y < 0.60 && abs(q.x)<1.15) {
    float valid=baseHit(ro,rd,nt,ft,nn,fn);
    float cov=coverage(nt,ft,nn,fn,rd)*valid;
    if(cov>0.0 && ft>0.0) {
      float3 p=ro+rd*nt;float3 c=stone(p,nn,rd);
      rgb=mix(rgb,c,cov);alpha=mix(alpha,1.0,cov);
    }
  }
  // Conservative all-yaw screen bounds avoid the 31-plane crystal test over the floor and margins.
  float crystalTop=max(0.0,2.94-uDrop);
  if(abs(q.x)<1.30 && q.y<0.15 && q.y>-crystalTop*1.13-0.13 && crystalTop>0.0) {
    float face;
    float3 cro=ro+float3(0,uDrop,0);
    float cv=crystalHit(cro,rd,nt,ft,nn,fn,face);
    float cc=coverage(nt,ft,nn,fn,rd)*cv;
    // Bloom uses the exact projected silhouette, including the moving cut at the marble.
    // This remains well-defined at grazing rays, edge-on views and rise overshoot.
    if(cc<1.0) {
      float outsidePx=10000.0;
      for(int i=0;i<32;i++) {
        float4 segment=uRim[i];
        float2 v=segment.zw-segment.xy;
        float t=clamp(dot(xy-segment.xy,v)/max(dot(v,v),0.0001),0.0,1.0);
        outsidePx=min(outsidePx,length(xy-segment.xy-t*v));
      }
      float air=(0.10*exp(-outsidePx*outsidePx/100.0)*smoothstep(0.8,1.8,-q.y)
        +0.22*exp(-outsidePx*outsidePx/5.0))*(uLamp+uUnder)*(1.0-cc);
      air*=smoothstep(0.12,-0.02,q.y);
      rgb+=float3(0.45,0.73,1.0)*air;alpha=max(alpha,air);
    }
    if(cc>0.0 && ft>0.0) {
      float3 col=glass(cro,rd,nt,nn,face);
      // Gentle highlight shoulder retains detail above nominal lamp intensity.
      col=col/(1.0+col*0.12);
      rgb=mix(rgb,col,cc);alpha=mix(alpha,1.0,cc);
    }
  }
  float3 flare=glints(xy);float fa=clamp(max(flare.r,max(flare.g,flare.b)),0.0,1.0);
  rgb+=flare;alpha=max(alpha,fa);
  return half4(min(rgb,float3(alpha)),alpha);
}
"""
