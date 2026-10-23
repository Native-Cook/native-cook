package com.ngocdevv.nativecook.blocks.lists.badgevaultgrid

internal object BadgeVaultAgsl {
    // The React Native SkSL programs are valid AGSL; inputs and equations are preserved verbatim.
    const val Badge = """
uniform shader uAtlas;
uniform float2 uCentre;
uniform float uR;
uniform float3x3 uRot;
uniform float3 uA;
uniform float3 uB;
uniform float3 uC;
uniform float uTile;
uniform float2 uEnv;
uniform float uSheen;
uniform float uFill;
uniform float uWave;
uniform float uTime;
uniform float uLift;
uniform float uPresent;
uniform float uSocket;

float3 safeUnit(float3 v) { return v / max(length(v), 0.00001); }
float3 objectPoint(float3 v) { return float3(dot(uRot[0],v),dot(uRot[1],v),dot(uRot[2],v)); }
float hexagon(float2 p) {
  float2 q = abs(p.yx);
  float2 k = float2(-0.866025404, 0.5);
  q -= 2.0 * min(dot(k,q),0.0) * k;
  q -= float2(clamp(q.x,-0.45033321,0.45033321),0.78);
  return length(q) * sign(q.y) - 0.22;
}
float slab(float3 p) {
  float2 q = float2(hexagon(p.xy)+0.03,abs(p.z)-0.04);
  return min(max(q.x,q.y),0.0)+length(max(q,float2(0.0)))-0.03;
}
float2 edgeGradient(float2 p) {
  return float2(hexagon(p+float2(.001,0))-hexagon(p-float2(.001,0)),
                hexagon(p+float2(0,.001))-hexagon(p-float2(0,.001))) / .002;
}
float atlasValue(float2 p, float tile, float channel) {
  float2 uv = clamp((float2(p.x,-p.y)/2.4+.5)*384.0,float2(1.0),float2(383.0));
  uv += float2(mod(tile,5.0)*768.0+channel*384.0,floor(tile/5.0)*384.0);
  return uAtlas.eval(uv).r;
}
float wireDistance(float2 p, float tile) { return atlasValue(p,tile,0.0)*.16; }
float studioBox(float2 p, float2 box, float softness) {
  float2 q=abs(p)-box;
  float d=length(max(q,float2(0)))+min(max(q.x,q.y),0.0);
  return 1.0-smoothstep(-softness,softness,d);
}
float3 studio(float3 ray, float roughness) {
  float ax=uEnv.x*.43633; float ay=uEnv.y*.43633;
  float3 v=float3(cos(ax)*ray.x+sin(ax)*ray.z,ray.y,-sin(ax)*ray.x+cos(ax)*ray.z);
  v=float3(v.x,cos(ay)*v.y-sin(ay)*v.z,sin(ay)*v.y+cos(ay)*v.z);
  float front=smoothstep(-.35,.15,v.z);
  float key=studioBox(v.xy-float2(-.35,.46),float2(.33,.28),.045+roughness*.22)*front;
  float strip=studioBox(v.xy-float2(.67,.05),float2(.065,.67),.018+roughness*.16)*front;
  float horizon=exp(-abs(v.y+.10)*14.0)*.13;
  float bounce=exp(-length(v.xy-float2(-.1,-.85))*4.0)*.19;
  return float3(.028,.035,.045)+key*float3(1.0,1.01,1.025)
       +strip*float3(.89,.95,1.0)+horizon*float3(.40,.49,.64)+bounce*float3(.62,.40,.24);
}
float sweep(float2 p) {
  if (uSheen<0.0 || uSheen>1.0) return 0.0;
  float along=(p.x-p.y)*.70710678;
  float delta=(along-mix(-1.62,1.62,uSheen))/.175;
  return exp(-delta*delta)*.55;
}
float liquid(float2 p) {
  if (uFill>=1.0) return 1.0;
  if (uFill<=0.0) return 0.0;
  float wav=uWave*(.035*sin(6.5*p.x+5.0*uTime)+.02*sin(11.0*p.x-7.3*uTime));
  float level=mix(-1.15,1.15,uFill)+wav;
  return 1.0-smoothstep(-.009,.009,p.y-level);
}
float3 silver(float3 normal, float3 viewRay, float2 p, float roughness) {
  float3 reflected=reflect(-viewRay,normal);
  float3 env=studio(reflected,roughness);
  float facing=max(dot(normal,viewRay),0.0);
  float edge=pow(max(1.0-facing,0.0),3.0);
  return env*(.86+.14*edge)+float3(.085,.09,.10)+sweep(p);
}
half4 tray(float2 p) {
  if (uSocket<=0.0) return half4(0.0);
  float hd=hexagon(p/1.07)*1.07;
  float aa=1.0/(3.0*max(uR,1.0));
  float mask=1.0-smoothstep(-aa,aa,hd);
  float2 grad=edgeGradient(p/1.07);
  float lip=max(dot(grad,float2(.60,-.80)),0.0);
  float darkLip=max(dot(grad,float2(-.60,.80)),0.0);
  float inner=exp(-abs(hd+.012)*95.0);
  float3 colour=mix(float3(.024,.024,.028),float3(.048,.048,.060),clamp(.5-p.y*.32,0.0,1.0));
  colour*=1.0-.82*darkLip*exp(-abs(hd+.045)*25.0);
  colour+=float3(.07)*inner*lip;
  colour+=uA*inner*.12*(1.0-uPresent)*uFill;
  float settle=(1.0-uLift)*uPresent;
  float2 below=p-float2(0,-.16);
  float pool=exp(-dot(below,below)/(0.62+uLift*.34));
  // Compact support leaves the narrow cell margins exactly transparent.
  float fade=(1.0-smoothstep(1.04,1.17,hexagon(p)+1.0))
    *(1.0-smoothstep(.92,1.0,max(abs(p.x)/1.19,abs(p.y)/1.25)));
  float glow=.22*pool*fade*uFill*settle;
  float contact=exp(-abs(hexagon(p-float2(0,-.045)))*32.0)*settle*.65;
  colour*=1.0-contact;
  float alpha=max(mask,glow)*uSocket;
  float3 premul=colour*mask*uSocket + uA*glow*uSocket*(1.0-mask*.65);
  return half4(premul,alpha);
}
half4 main(float2 xy) {
  float2 screen=(xy-uCentre)/max(uR,1.0)*float2(1,-1);
  half4 under=tray(screen);
  if (uPresent<=0.0) return under;
  if (dot(screen,screen)>1.75) return under;
  float3 origin=objectPoint(float3(0,0,5.57));
  float3 ray=objectPoint(safeUnit(float3(screen,-5.5)));
  float aa=1.0/(3.0*max(uR,1.0));
  float faceSign=origin.z>=0.0 ? 1.0 : -1.0;
  float denom=abs(ray.z)>.00001 ? ray.z : -.00001*faceSign;
  float travel=(faceSign*.07-origin.z)/denom;
  float3 hit=origin+ray*travel;
  bool face=travel>0.0 && abs(ray.z)>.025 && hexagon(hit.xy)<-.03;
  float coverage=1.0;
  if (!face) {
    // A local bounding box also gives a finite interval for edge-on views.
    float3 inv=float3(1.0/(abs(ray.x)>.00001?ray.x:.00001),
                      1.0/(abs(ray.y)>.00001?ray.y:.00001),
                      1.0/(abs(ray.z)>.00001?ray.z:.00001));
    float3 lo=(float3(-1.003,-1.124,-.073)-origin)*inv;
    float3 hi=(float3(1.003,1.124,.073)-origin)*inv;
    float3 nearV=min(lo,hi); float3 farV=max(lo,hi);
    float start=max(max(nearV.x,nearV.y),nearV.z);
    float finish=min(min(farV.x,farV.y),farV.z);
    if (finish<start || finish<0.0) return under;
    travel=max(start,0.0);
    float gap=1.0;
    for (int i=0;i<20;i++) {
      hit=origin+ray*travel;
      gap=slab(hit);
      if (gap<aa*.35 || travel>finish) break;
      travel+=max(gap,aa*.2);
    }
    if (travel>finish+aa || gap>aa*1.5) return under;
    coverage=1.0-smoothstep(aa*.15,aa*1.5,gap);
  }
  float edgeDist=hexagon(hit.xy);
  float2 edgeGrad=edgeGradient(hit.xy);
  float3 nLocal;
  float3 pigment=float3(1.0);
  float metal=1.0;
  float rough=.03;
  float formed=liquid(hit.xy);
  float regionId=0.0;
  float poolEdge=0.0;
  float poolLight=0.0;
  float wireCore=0.0;
  float pressedDetail=0.0;
  bool front=hit.z>0.0;
  bool side=abs(hit.z)<.039 || edgeDist>-.024;
  if (side) {
    float sideWeight=clamp((edgeDist+.03)/.03,0.0,1.0);
    nLocal=safeUnit(float3(edgeGrad*sideWeight,sign(hit.z)*sqrt(max(1.0-sideWeight*sideWeight,0.0))));
  } else if (!front) {
    float ring=exp(-abs(edgeDist+.16)*230.0);
    float mark=wireDistance(float2(-hit.x,hit.y),18.0);
    float mx=wireDistance(float2(-hit.x+.004,hit.y),18.0)-wireDistance(float2(-hit.x-.004,hit.y),18.0);
    float my=wireDistance(float2(-hit.x,hit.y+.004),18.0)-wireDistance(float2(-hit.x,hit.y-.004),18.0);
    float groove=exp(-mark*mark/.00018);
    float brush=sin(length(hit.xy)*1100.0)*.007;
    nLocal=safeUnit(float3(edgeGrad*ring*.23+float2(mx,-my)*groove*24.0+hit.xy*brush,-1.0));
    float ringSide=dot(edgeGrad,float2(-.60,.80));
    float markSide=dot(float2(-mx,my),float2(-.60,.80))/.008;
    pressedDetail=-ring*.20-groove*.23+ring*ringSide*.20+groove*markSide*.23;
    pigment=float3(1.0);
    rough=.48;
  } else if (edgeDist>-.114) {
    // The rolled shoulder catches the studio without blackening the whole rim.
    float rimT=clamp((edgeDist+.059)/.055,-.98,.98);
    nLocal=safeUnit(float3(edgeGrad*rimT,sqrt(max(1.0-rimT*rimT,.001))));
  } else if (edgeDist>-.127) {
    nLocal=float3(0,0,1);
    pigment=float3(.36);
    rough=.2;
  } else {
    float wire=wireDistance(hit.xy,uTile);
    float dx=(wireDistance(hit.xy+float2(.0125,0),uTile)-wireDistance(hit.xy-float2(.0125,0),uTile))/.025;
    float dy=(wireDistance(hit.xy+float2(0,.0125),uTile)-wireDistance(hit.xy-float2(0,.0125),uTile))/.025;
    regionId=floor(clamp((atlasValue(hit.xy,uTile,1.0)*255.0-32.0)/64.0+.5,0.0,3.0));
    pigment=regionId<.5?uA:(regionId<1.5?uB:uC);
    if (uTile>16.5 && uTile<17.5 && regionId>1.5 && regionId<2.5 && abs(hit.x)>.148+.13*clamp((hit.y-.296)/.40,0.0,1.0)) pigment=mix(uA,uC,.34);
    metal=1.0-smoothstep(.019-aa*.6,.025+aa*.6,wire);
    float shoulder=sin(clamp((wire-.010)/.017,0.0,1.0)*3.14159)*.73;
    float2 slope=float2(dx,dy)*shoulder;
    float meniscus=exp(-max(wire-.025,0.0)*55.0)*.16;
    float2 poolSlope=hit.xy*.105-float2(dx,dy)*meniscus;
    float peel=.0015*sin(hit.x*109.0+sin(hit.y*83.0))*sin(hit.y*131.0);
    poolSlope+=float2(peel,.0015*cos(hit.y*97.0+hit.x*51.0)*sin(hit.x*127.0));
    slope=mix(poolSlope,slope,metal);
    if (regionId>2.5) {
      float groovePhase=(hit.x+.038*sin(hit.y*13.0))*179.52;
      float engraved=sin(groovePhase);
      slope+=float2(engraved*.105,engraved*.035*cos(hit.y*13.0));
      metal=1.0; pigment=float3(.85); rough=.34;
    }
    nLocal=safeUnit(float3(slope,1));
    float depth=smoothstep(.025,.155,wire);
    float pale=smoothstep(.64,.90,min(pigment.r,min(pigment.g,pigment.b)));
    pigment*=mix(mix(.70,1.035,depth),mix(.92,1.0,depth),pale);
    poolEdge=exp(-max(wire-.026,0.0)*80.0)*(1.0-metal);
    poolLight=dot(float2(dx,dy),float2(.60,-.80));
    wireCore=1.0-smoothstep(.002,.013,wire);
  }
  float3 normal=safeUnit(uRot*nLocal);
  float3 viewRay=safeUnit(float3(0,0,5.57)-uRot*hit);
  float facing=clamp(dot(normal,viewRay),0.0,1.0);
  float3 reflected=studio(reflect(-viewRay,normal),.09);
  float fresnel=.055+.945*pow(max(1.0-facing,0.0),5.0);
  float illumination=.86+.14*max(dot(normal,safeUnit(float3(-.5,.8,1.4))),0.0);
  // One studio window spans every pool; shallow local menisci bend only its edge.
  float3 coatNormal=safeUnit(uRot*float3(hit.xy*.16,1.0));
  float3 coatRay=reflect(-viewRay,coatNormal);
  float2 windowAt=hit.xy*.48+coatRay.xy*.72+uEnv*float2(.34,-.34);
  float windowAcross=windowAt.x+.20*windowAt.y;
  float windowHeight=windowAt.y-.18*windowAt.x;
  float window=studioBox(float2(windowAcross+.30,windowHeight-.43),float2(.57,.31),.055);
  window*=smoothstep(-.1,.40,coatRay.z);
  float coat=.265*window;
  float3 glass=mix(pigment*illumination,float3(1.0,.995,.985),coat);
  glass+=reflected*(.035+fresnel*.35)+sweep(hit.xy)*.65;
  glass+=poolEdge*(max(poolLight,0.0)*.19-max(-poolLight,0.0)*.13);
  float3 polished=silver(normal,viewRay,hit.xy,rough);
  float keyFacing=dot(edgeGrad,float2(-.60,.80));
  if (side) {
    float streak=pow(max(.5+.5*sin(hit.z*42.0+keyFacing*2.8+uEnv.x),0.0),4.0);
    polished=float3(.48,.50,.53)+polished*.28+streak*.32;
  } else if (front && edgeDist>-.114) {
    float rollLight=.5+.5*keyFacing;
    polished=float3(.58,.60,.63)+polished*.25+rollLight*.13;
    polished+=exp(-abs(edgeDist+.036)*170.0)*max(keyFacing,0.0)*.22;
    polished-=exp(-abs(edgeDist+.106)*260.0)*.24;
  } else if (front && edgeDist<-.127 && rough<.25) {
    polished=float3(.60,.63,.65)+polished*.25+wireCore*.18+window*.08;
  }
  if (rough>.25 && front) polished=mix(polished,float3(.64,.68,.72),.60);
  if (!front && !side) {
    // Circular brushing stretches the reflection into opposing conic fans.
    float azimuth=atan(hit.y,hit.x+.00001);
    float3 backLamp=objectPoint(safeUnit(float3(-.55+uEnv.x*.4,.75+uEnv.y*.4,1.0)));
    float lampAngle=atan(backLamp.y,backLamp.x+.00001);
    float fan=pow(max(.5+.5*cos(2.0*(azimuth-lampAngle)),0.0),5.0);
    float brushed=sin(length(hit.xy)*1100.0+sin(azimuth*7.0)*.6)*.012;
    polished=float3(.48,.51,.55)+fan*.36+brushed+pressedDetail+studio(reflect(-viewRay,normal),.5)*.09;
  }
  if (front && edgeDist>-.127 && edgeDist<=-.114) polished*=pigment;
  float3 unlocked=mix(glass,polished,metal);
  float graphite=.060+regionId*.027;
  float3 blank=mix(float3(graphite)*( .78+.22*facing)+reflected*.025,
                    float3(.115,.13,.145)+polished*.18,metal);
  float3 colour=mix(blank,unlocked,formed);
  if (uFill>0.0 && uFill<1.0 && front) {
    float wav=uWave*(.035*sin(6.5*hit.x+5.0*uTime)+.02*sin(11.0*hit.x-7.3*uTime));
    float below=mix(-1.15,1.15,uFill)+wav-hit.y;
    float core=exp(-(below*below)/.001225);
    float glow=exp(-(below*below)/.0144);
    float cooling=(1.0-smoothstep(0.0,.35,below))*smoothstep(-.01,.04,below);
    colour+=(float3(1.0,.97,.90)*core*.90+uA*glow*.38+uA*cooling*.22)*uWave;
  }
  float alpha=coverage*clamp(uPresent,0.0,1.0);
  colour=clamp(colour,float3(0),float3(1));
  return half4(colour*alpha+under.rgb*(1.0-alpha),alpha+under.a*(1.0-alpha));
}
"""
    const val Flood = """
uniform float2 uSize;
uniform float2 uOrigin;
uniform float2 uCentre;
uniform float uR;
uniform float3 uColour;
uniform float uSpread;
uniform float uPower;
uniform float uRays;
uniform float uSince;
uniform float uTime;
float hash(float2 p) { return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453); }
half4 main(float2 xy) {
  if (uSpread<=0.0 || uPower<=0.0) return half4(0);
  float reach=length(max(uOrigin,uSize-uOrigin))+84.0;
  float radial=length(xy-uOrigin);
  float front=uSpread*reach;
  float cover=1.0-smoothstep(front-40.0,front+40.0,radial);
  float ring=exp(-abs(radial-front)/11.0)*(1.0-smoothstep(.70,1.0,uSpread));
  float2 p=(xy-uCentre)/max(uR,1.0);
  float rr=dot(p,p);
  float bloom=exp(-rr/5.12);
  float theta=atan(p.y,p.x+.00001);
  float fan=pow(max(.5+.5*sin(theta*9.0-uTime*.94248+sin(theta*3.0)*1.5),0.0),12.0);
  float beams=fan*exp(-rr/10.0)*smoothstep(.30,1.2,sqrt(rr))*uRays*.12;
  float2 centered=xy/uSize-.5;
  float vignette=1.0-.65*smoothstep(.20,.74,length(centered));
  float top=smoothstep(110.0,205.0,xy.y);
  float haloDelta=(sqrt(rr)-1.04)/.30;
  float halo=exp(-haloDelta*haloDelta)*.085;
  float2 shadowAt=p-float2(0,.22);
  float shadowMask=exp(-dot(shadowAt,shadowAt)/1.25)*.35;
  float body=(.165+.34*bloom+beams+halo)*vignette*top*(1.0-shadowMask);
  float landing=0.0;
  if (uSince>=0.0 && uSince<.6) {
    float phase=uSince/.6;
    float ringDelta=(sqrt(rr)-mix(1.0,4.0,phase))/.10;
    landing=exp(-ringDelta*ringDelta)*sin(phase*3.14159)*(1.0-phase)*.22;
  }
  float particles=0.0;
  for (int i=0;i<16;i++) {
    float fi=float(i);
    float seed=hash(float2(fi,19.3));
    float phase=fract(uTime*.13+seed);
    float age=uSince;
    float life=0.0;
    float2 at;
    if (i<12) {
      float angle=fi*2.399963;
      life=smoothstep(0.0,.15,age)*(1.0-smoothstep(.65,1.8,age));
      float travel=max(age,0.0);
      at=uCentre+float2(cos(angle)*uR*(.55+travel*.65),sin(angle)*uR*.48-travel*uR*(.55+seed));
    } else {
      life=sin(phase*3.14159)*.48;
      at=uCentre+float2((seed-.5)*uR*3.7,(.8-phase*3.0)*uR);
    }
    if (life>0.0) {
      float2 delta=(xy-at)/(1.5+seed*1.65);
      float glow=exp(-dot(delta,delta)/6.0);
      float core=glow*glow*glow;
      particles+=(core*core+.20*glow)*life;
    }
  }
  float grain=(hash(floor(xy*3.0))-.5)/255.0;
  // The object keeps a trace of its vault; the details need a quiet background.
  float detailQuiet=smoothstep(uCentre.y+uR,uCentre.y+uR*1.8,xy.y);
  float alpha=mix(.96,.992,detailQuiet)*uPower*cover;
  alpha=mix(alpha,uPower*cover,1.0-top);
  float3 colour=uColour*(body+(ring*.37+landing)*top)+uColour*particles*uRays*.70*top+grain;
  return half4(clamp(colour,float3(0),float3(1))*alpha,alpha);
}
"""
    const val Pill = """
uniform float2 uSize;
uniform float uPress;
uniform float uSheen;
uniform float uDark;
uniform float3 uTint;
float pillSdf(float2 p) {
  float rad=uSize.y*.5-.5;
  float2 q=abs(p-uSize*.5)-float2(max(uSize.x*.5-uSize.y*.5,0.0),0.0);
  return length(float2(max(q.x,0.0),q.y))-rad;
}
half4 main(float2 xy) {
  float sd=pillSdf(xy);
  float alpha=1.0-smoothstep(-.25,.25,sd);
  if (alpha<=0.0) return half4(0);
  float y=xy.y/uSize.y;
  float rimWidth=mix(2.0,2.5,smoothstep(48.0,56.0,uSize.y));
  float depth=-sd-rimWidth;
  float pool=smoothstep(-.25,.25,depth);
  float2 edgeGrad=float2(pillSdf(xy+float2(.2,0))-pillSdf(xy-float2(.2,0)),
    pillSdf(xy+float2(0,.2))-pillSdf(xy-float2(0,.2)))/.4;
  float lightSide=dot(edgeGrad,float2(-.60,-.80));
  float rollLight=.5+.5*lightSide;
  float crestDelta=(-sd-.8)/.45;
  float crest=exp(-crestDelta*crestDelta);
  float3 rim=float3(.59,.62,.65)+rollLight*.22+crest*.20;
  rim+=uPress*max(-lightSide,0.0)*.15;
  float grooveDelta=(depth+.35)/.4;
  rim-=exp(-grooveDelta*grooveDelta)*.20;
  // Keep the label's middle band even; put depth and the window above it.
  float pale=smoothstep(.64,.90,min(uTint.r,min(uTint.g,uTint.b)));
  float shade=1.0-.17*smoothstep(.55,1.0,y)-.18*exp(-max(depth,0.0)/2.2);
  shade=mix(shade,1.0-(1.0-shade)*.36,pale);
  float3 enamel=uTint*shade*(1.0-.10*uPress);
  float reflectedY=(xy.y-uPress*1.5)/uSize.y;
  float window=(1.0-smoothstep(.32,.37,reflectedY))*smoothstep(.025,.14,reflectedY);
  window*=.82+.18*(1.0-smoothstep(.1,.85,xy.x/uSize.x));
  enamel=mix(enamel,float3(1.0,.997,.985),window*.26);
  float meniscusDelta=(depth-.65)/.50;
  float meniscus=exp(-meniscusDelta*meniscusDelta);
  enamel+=meniscus*(max(lightSide,0.0)*.24-max(-lightSide,0.0)*.10);
  float sheenBand=0.0;
  if (uSheen>=0.0 && uSheen<=1.0) {
    float diag=(xy.x/uSize.x+y*.28)/1.28;
    float delta=(diag-mix(-.17,1.17,uSheen))/.135;
    sheenBand=exp(-delta*delta)*.35;
  }
  float3 colour=mix(rim,enamel,pool)+sheenBand;
  float3 graphite=mix(float3(.15,.17,.19)+rim*.12,float3(.070,.075,.083)*(1.0-uPress*.10)+window*.022,pool);
  colour=mix(colour,graphite+sheenBand*.12,uDark);
  return half4(clamp(colour,float3(0),float3(1))*alpha,alpha);
}
"""
}
