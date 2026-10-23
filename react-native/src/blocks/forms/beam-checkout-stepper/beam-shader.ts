import { Skia } from '@shopify/react-native-skia';

import { F, FIELDS, G } from './beam-theme';

// These exact sources are also compiled by the CanvasKit proof renderer.
const COMMON = `
uniform float f[${F.SIZE}];
uniform float clock;
uniform float still;
const float RX = ${G.railX}.0;
const float Y0 = ${G.nodeY0}.0;
const float PITCH = ${G.pitch}.0;
const float PX = ${G.panelX}.0;
const float PW = ${G.panelW}.0;
const float PT = ${G.panelTop}.0;
const float3 BLUE = float3(0.1843, 0.4824, 1.0);
const float3 ICE = float3(0.918, 0.957, 1.0);
const float3 RED = float3(1.0, 0.302, 0.369);
const float3 GOLD = float3(1.0, 0.851, 0.627);
float sat(float v) { return clamp(v, 0.0, 1.0); }
float sq(float v) { return v*v; }
float ny(float v) { return Y0 + PITCH*v; }
float aa(float d) { return 1.0-smoothstep(-0.35, 0.35, d); }
float hash(float v) { return fract(sin(v*127.1+13.7)*43758.5453); }
float rr(float2 p, float2 center, float2 halfsize, float rad) {
  float2 q=abs(p-center)-halfsize+rad;
  return length(max(q,0.0))+min(max(q.x,q.y),0.0)-rad;
}
float segdist(float2 p, float2 a, float2 b) {
  float2 v=b-a;
  return length(p-a-v*clamp(dot(p-a,v)/max(dot(v,v),0.0001),0.0,1.0));
}
float tick(float2 p,float amount) {
  float2 a=float2(-7.0,0.0), b=float2(-2.0,5.0), c=float2(8.0,-6.0);
  float first=sat(amount/0.34), second=sat((amount-0.34)/0.66);
  float d=segdist(p,a,mix(a,b,first));
  if(second>0.0) d=min(d,segdist(p,b,mix(b,c,second)));
  return aa(d-1.2)*smoothstep(0.0,0.03,amount);
}
float3 hue(float redAmount) { return mix(mix(BLUE,RED,redAmount),GOLD,f[${F.WARM}]); }
float topmask(float y) { return smoothstep(96.0,150.0,y); }
float animtime() { return clock*(1.0-still); }
float outcontent(float v) { return 1.0-sat(v/0.3); }
float incontent(float v) { float u=sat((v-0.4)/0.6); return u*u*(3.0-2.0*u); }
float visibility(float idx) {
  if(f[${F.PANEL_FROM}]==f[${F.PANEL_TO}]) return abs(idx-f[${F.PANEL_TO}])<0.1?1.0:0.0;
  return (abs(idx-f[${F.PANEL_FROM}])<0.1?outcontent(f[${F.PANEL_MIX}]):0.0)
    +(abs(idx-f[${F.PANEL_TO}])<0.1?incontent(f[${F.PANEL_MIX}]):0.0);
}
float shiftcontent(float idx) {
  return f[${F.PANEL_FROM}]!=f[${F.PANEL_TO}] && abs(idx-f[${F.PANEL_TO}])<0.1
    ?10.0*(1.0-incontent(f[${F.PANEL_MIX}])):0.0;
}
float4 over(float4 back, float4 front) { return front+back*(1.0-front.a); }
`;

export const RAYS_SKSL = COMMON + `
half4 main(float2 p) {
  if(p.y<=96.0) return half4(0.0,0.0,0.0,1.0);
  float2 q=p-float2(RX,ny(f[${F.HEAD}]));
  float radial=length(q), tm=animtime();
  float flare=f[${F.BURST}], reach=0.9+0.16*flare;
  float3 col=float3(0.0);
  // Three incommensurate angular spectra form irregular lens streaks without a ray loop.
  float angle=atan(q.y,q.x)+0.022*sin(tm*0.78);
  float warp=angle+0.075*sin(angle*7.0)+0.035*sin(angle*13.0);
  float a1=0.5+0.5*sin(warp*13.0+sin(angle*4.0)*1.4);
  float a2=0.5+0.5*sin(warp*37.0+sin(angle*9.0)*0.8);
  float a3=0.5+0.5*sin(warp*67.0+1.8);
  float spread=exp(-(1.0-a1)*5.0)*0.95+exp(-(1.0-a2)*12.0)*0.24+exp(-(1.0-a3)*28.0)*0.055;
  float extent=(170.0+180.0*(0.5+0.5*sin(angle*5.0+0.8)))*reach;
  float fall=exp(-sq(radial/extent)*2.2)*smoothstep(18.0,52.0,radial);
  float gaps=0.40+0.60*sq(0.5+0.5*sin(angle*3.0+0.9));
  float dashes=0.50+0.50*sq(sin(radial*0.013+angle*9.0+tm*0.3));
  float violet=sat(-sin(angle))*0.98;
  float3 tint=mix(BLUE,float3(0.478,0.302,1.0),violet);
  tint=mix(tint,RED,f[${F.TONE}]);
  float goldSector=0.5+0.5*sin(angle*4.0+0.7);
  float3 warmTint=mix(float3(1.0,0.32,0.055),float3(1.0,0.72,0.30),goldSector);
  warmTint=mix(warmTint,BLUE,0.85*exp(-sq((angle-0.62)/0.12)));
  tint=mix(tint,warmTint,smoothstep(0.0,0.3,f[${F.WARM}]));
  col=tint*spread*fall*gaps*dashes*0.82;
  float fan=exp(-sq((angle-0.55)/0.22))*0.22+exp(-sq((angle+0.52)/0.26))*0.12;
  col+=tint*fan*exp(-sq(radial/260.0))*smoothstep(24.0,70.0,radial);
  // Broad amber lens ghosts live only in the two reference sectors.
  float amber=exp(-sq((angle+0.68)/0.10))+exp(-sq((angle-2.49)/0.12));
  float amberFall=exp(-sq((radial-125.0)/185.0))*smoothstep(26.0,95.0,radial);
  col+=float3(1.0,0.37,0.10)*amber*amberFall*(0.25+0.18*f[${F.WARM}]);
  col+=hue(f[${F.TONE}])*exp(-radial/67.0)*0.16;
  col*=flare*topmask(p.y);
  // Secondary fan follows the actual pill centre, behind the panel glass.
  float split=f[${F.CTA_SPLIT}], lens=f[${F.CTA_LENS}];
  float cLeft=mix(16.0+64.0*split,149.0,lens);
  float cWidth=mix(322.0-64.0*split,56.0,lens);
  float2 cq=p-float2(PX+cLeft+cWidth*0.5,PT+f[${F.PANEL_H}]-44.0);
  float cr=length(cq), ca=atan(cq.y,cq.x);
  float caWarp=ca+0.10*sin(ca*5.0);
  float shards=exp(-(1.0-sin(caWarp*11.0+0.8))*3.5)
    +0.24*exp(-(1.0-sin(caWarp*23.0))*6.0);
  float envelope=exp(-sq(cr/145.0))*smoothstep(18.0,38.0,cr)*(1.0-smoothstep(155.0,220.0,cr));
  float lower=smoothstep(-45.0,28.0,cq.y)*0.78+0.22;
  float3 secondary=mix(mix(BLUE,float3(0.34,0.18,0.85),sat(-sin(ca*2.0))),float3(1.0,0.58,0.20),smoothstep(0.0,0.4,f[${F.CTA_DONE}]));
  col+=secondary*shards*envelope*lower*(0.30+0.12*f[${F.CTA_PRESS}]+0.12*f[${F.CTA_DONE}]);
  col*=1.0-smoothstep(810.0,870.0,p.y);
  // Smooth filmic shoulder preserves colour in overlapping ignition streaks.
  col=1.0-exp(-col*1.45);
  return half4(col*f[${F.PRESENT}],1.0);
}
`;

export const GLASS_SKSL = COMMON + `
// kind: row=0, panel=1, recessed field=2, raised thumb=3, CTA=4.
float4 glass(float2 p,float4 rect,float rad,float kind,float active,float done,float sweep,float err) {
  float2 center=rect.xy+rect.zw*0.5;
  float d=rr(p,center,rect.zw*0.5,rad);
  if(d>34.0) return float4(0.0);
  float inside=aa(d), rim=exp(-sq(d/0.65));
  float2 uv=(p-rect.xy)/rect.zw;
  float2 lamp=float2(RX,ny(f[${F.HEAD}]));
  float2 delta=p-lamp;
  float nearLight=exp(-length(delta)/170.0);
  float2 normal=normalize(p-clamp(p,rect.xy+rad,rect.xy+rect.zw-rad)+float2(0.001));
  float facing=0.18+0.82*sat(dot(normal,normalize(lamp-p+float2(0.001))));
  float exposure=nearLight*facing*0.65+exp(-sq(length(delta)/85.0))*facing*f[${F.HEAD_GLOW}]*3.2;
  float poured=1.0-smoothstep(sweep-0.025,sweep+0.07,sat(uv.x));
  float head=sweep<0.99?exp(-sq((uv.x-sweep)/0.045)):0.0;
  float upper=1.0-sat(uv.y);
  float3 lightcol=hue(max(err,f[${F.TONE}]*sat(f[${F.HEAD_GLOW}])));
  float3 tint=float3(0.055,0.068,0.105);
  float alpha=0.56;
  float3 body=tint*(0.64+upper*0.85);
  float edge=0.12+0.07*upper+exposure*0.35;
  float lit=active*poured;
  float glow=lit*0.22+exposure*f[${F.HEAD_GLOW}]*0.10;
  if(kind<0.5) {
    alpha=0.57+done*0.05;
    body+=float3(0.015,0.028,0.058)*done*(1.0-uv.x);
    body+=lightcol*active*(0.035+0.09*(1.0-uv.x));
    body+=lightcol*active*exp(-sq((uv.y-0.82)/0.18))*0.045;
    edge+=lit*(0.7+0.35*(1.0-uv.x))+head*active*0.8;
  } else if(kind<1.5) {
    alpha=0.82;
    body=float3(0.029,0.043,0.081)*(0.8+0.45*upper);
    body+=lightcol*exposure*0.018;
    edge=0.10+0.12*upper+exposure*0.45;
  } else if(kind<2.5) {
    alpha=0.45;
    body=float3(0.07,0.09,0.14)*(0.65+upper*0.35);
    body*=1.0-0.32*exp(-sq((p.y-rect.y-2.0)/2.0));
    body+=lightcol*err*0.08;
    edge=0.10+0.06*upper+exposure*0.18+err*0.72;
    glow=err*0.11;
  } else if(kind<3.5) {
    alpha=0.75;
    body=float3(0.05,0.12,0.23)+BLUE*0.12*upper;
    edge=0.18+0.28*upper+exposure*0.3;
    glow=0.055;
  } else {
    alpha=1.0;
    body=mix(float3(0.08,0.29,0.83),float3(0.20,0.68,1.0),upper);
    body+=float3(0.05,0.15,0.19)*exp(-sq((uv.y-0.89)/0.1));
    body+=ICE*exp(-sq((uv.y-0.16)/0.09))*exp(-sq((uv.x-0.42)/0.50))*0.055;
    body*=1.0-0.15*f[${F.CTA_PRESS}];
    float3 paid=mix(float3(0.953,0.906,0.816),float3(1.0),upper);
    paid+=float3(0.035)*exp(-sq((uv.y-0.16)/0.065));
    body=mix(body,paid,f[${F.CTA_DONE}]);
    edge=0.62+0.28*upper;
    glow=0.26+0.12*f[${F.CTA_DONE}];
    lightcol=mix(BLUE,GOLD,f[${F.CTA_DONE}]);

  }
  float3 edgecol=mix(float3(0.40,0.48,0.64),lightcol,sat(lit+err+exposure));
  if(kind>3.5) edgecol=mix(float3(0.50,0.86,1.0),ICE,f[${F.CTA_DONE}]);
  float3 rgb=body*alpha*inside;
  rgb+=edgecol*edge*rim;
  float inner=exp(-sq((d+1.4)/0.7))*inside;
  rgb+=ICE*inner*(0.035+0.06*upper)*(0.3+0.7*upper);
  float outside=exp(-max(d,0.0)/6.0)*(1.0-smoothstep(22.0,34.0,d));
  if(kind<0.5) outside*=1.0-smoothstep(7.0,12.0,d);
  if(kind>1.5 && kind<2.5) outside*=inside;
  rgb+=lightcol*glow*outside*(kind>3.5?1.0-inside:1.0);
  float a=sat(alpha*inside+rim*edge*0.45+outside*glow*0.35);
  return float4(min(rgb,float3(a)),a);
}
half4 main(float2 p) {
  float4 col=float4(0.0);
  int i=int(clamp(floor((p.y-Y0)/PITCH+0.5),0.0,4.0));
  float yy=ny(float(i));
  if(abs(p.y-yy)<58.0) {
    float scale=(0.965+0.035*(i==0?f[${F.ROW_RISE}]:i==1?f[${F.ROW_RISE+1}]:i==2?f[${F.ROW_RISE+2}]:i==3?f[${F.ROW_RISE+3}]:f[${F.ROW_RISE+4}]))*(1.0-0.03*(i==0?f[${F.ROW_PRESS}]:i==1?f[${F.ROW_PRESS+1}]:i==2?f[${F.ROW_PRESS+2}]:i==3?f[${F.ROW_PRESS+3}]:f[${F.ROW_PRESS+4}]));
    float2 q=(p-float2(RX,yy))/scale+float2(RX,yy);
    col=over(col,glass(q,float4(${G.rowX}.0,yy-22.0,${G.rowW}.0,44.0),22.0,0.0,
      (i==0?f[${F.ROW_ACTIVE}]:i==1?f[${F.ROW_ACTIVE+1}]:i==2?f[${F.ROW_ACTIVE+2}]:i==3?f[${F.ROW_ACTIVE+3}]:f[${F.ROW_ACTIVE+4}]),(i==0?f[${F.ROW_DONE}]:i==1?f[${F.ROW_DONE+1}]:i==2?f[${F.ROW_DONE+2}]:i==3?f[${F.ROW_DONE+3}]:f[${F.ROW_DONE+4}]),(i==0?f[${F.ROW_SWEEP}]:i==1?f[${F.ROW_SWEEP+1}]:i==2?f[${F.ROW_SWEEP+2}]:i==3?f[${F.ROW_SWEEP+3}]:f[${F.ROW_SWEEP+4}]),f[${F.TONE}]*(i==0?f[${F.ROW_ACTIVE}]:i==1?f[${F.ROW_ACTIVE+1}]:i==2?f[${F.ROW_ACTIVE+2}]:i==3?f[${F.ROW_ACTIVE+3}]:f[${F.ROW_ACTIVE+4}])));
  }
  float py=PT;
  if(p.y>py-34.0) {
    col=over(col,glass(p,float4(PX,py,PW,f[${F.PANEL_H}]),28.0,1.0,0.0,0.0,0.0,0.0));
    float current=f[${F.PANEL_TO}];
    if(f[${F.PANEL_FROM}]!=current && f[${F.PANEL_MIX}]<0.4) current=f[${F.PANEL_FROM}];
    float contentAlpha=visibility(current), contentDy=shiftcontent(current);
    float4 fieldRect=float4(PX+16.0,py+52.0+contentDy,322.0,56.0);
    int fieldIndex=current<0.5?0:current<1.5?1:4;
    if(current>0.5 && p.y>py+112.0+contentDy) {
      fieldRect.y=py+116.0+contentDy;
      if(current<1.5) {
        fieldRect.x=PX+(p.x<PX+218.0?16.0:222.0);
        fieldRect.z=p.x<PX+218.0?198.0:116.0;
        fieldIndex=p.x<PX+218.0?2:3;
      } else {
        fieldRect.x=PX+(p.x<PX+177.0?16.0:181.0);
        fieldRect.z=157.0; fieldIndex=p.x<PX+177.0?5:6;
      }
    }
    if(current<2.5 && contentAlpha>0.001)
      col=over(col,glass(p,fieldRect,16.0,2.0,0.0,0.0,0.0,(fieldIndex==0?f[${F.FIELD_ERR+0}]:fieldIndex==1?f[${F.FIELD_ERR+1}]:fieldIndex==2?f[${F.FIELD_ERR+2}]:fieldIndex==3?f[${F.FIELD_ERR+3}]:fieldIndex==4?f[${F.FIELD_ERR+4}]:fieldIndex==5?f[${F.FIELD_ERR+5}]:f[${F.FIELD_ERR+6}]))*contentAlpha);
    float delivery=visibility(1.0), sy=py+${G.segY}.0+shiftcontent(1.0);
    if(delivery>0.001) {
      col=over(col,glass(p,float4(PX+16.0,sy,322.0,48.0),24.0,2.0,0.0,0.0,0.0,0.0)*delivery);
      float tx=PX+20.0+161.0*min(f[${F.SEG_LEAD}],f[${F.SEG_TRAIL}]);
      float tw=157.0+161.0*abs(f[${F.SEG_LEAD}]-f[${F.SEG_TRAIL}]);
      float2 tc=float2(tx+tw*0.5,sy+24.0);
      float2 tp=(p-tc)/(1.0-0.03*f[${F.SEG_PRESS}])+tc;
      col=over(col,glass(tp,float4(tx,sy+4.0,tw,40.0),20.0,3.0,0.0,0.0,0.0,0.0)*delivery);
    }
    float rev=visibility(3.0);
    for(int j=1;j<4;j++) {
      float ly=py+${G.summaryY}.0+32.0*float(j)+shiftcontent(3.0);
      float ln=(1.0-smoothstep(0.2,0.6,abs(p.y-ly)))*smoothstep(PX+16.0,PX+17.0,p.x)*(1.0-smoothstep(PX+337.0,PX+338.0,p.x))*0.07*rev;
      col=over(col,float4(float3(ln),ln));
    }
    if(f[${F.FOCUS_A}]>0.001) {
      float4 focus=float4(PX+f[${F.FOCUS_X}],py+f[${F.FOCUS_Y}],f[${F.FOCUS_W}],f[${F.FOCUS_H}]);
      float ferr=0.0, nearest=1e10;
      ${FIELDS.map((v, i) => `if(abs(f[${F.PANEL_TO}]-${v.step}.0)<0.1) {
        float dd=length(float2(f[${F.FOCUS_X}]+f[${F.FOCUS_W}]*0.5,f[${F.FOCUS_Y}]+f[${F.FOCUS_H}]*0.5)-float2(${v.x + v.w / 2},${v.y + G.fieldH / 2}.0));
        if(dd<nearest) { nearest=dd; ferr=f[${F.FIELD_ERR + i}]; }
      }`).join('\n')}
      float fd=rr(p,focus.xy+focus.zw*0.5,focus.zw*0.5,16.0);
      float rim=exp(-sq(fd/0.85));
      float bloom=exp(-abs(fd)/4.5)*(1.0-smoothstep(15.0,24.0,abs(fd)))*0.14;
      float inner=exp(-sq((p.y-focus.y-focus.w+4.0)/8.0))*aa(fd)*0.035;
      float hot=exp(-length(p-focus.xy-float2(25.0,0.0))/65.0);
      float3 fc=mix(hue(ferr),ICE,0.25+0.45*hot);
      float fa=(rim*0.9+bloom+inner)*f[${F.FOCUS_A}];
      col=over(col,float4(fc*sat(fa),sat(fa)));
    }
    float cy=PT+f[${F.PANEL_H}]-72.0;
    float cx=PX+mix(16.0+64.0*f[${F.CTA_SPLIT}],149.0,f[${F.CTA_LENS}]);
    float backAlpha=f[${F.CTA_SPLIT}]*(1.0-f[${F.CTA_LENS}])*(1.0-smoothstep(cx-1.0,cx,p.x));
    float2 bc=float2(PX+44.0,cy+28.0);
    float2 bp=(p-bc)/(1.0-0.06*f[${F.BACK_PRESS}])+bc;
    col=over(col,glass(bp,float4(PX+16.0,cy,56.0,56.0),28.0,0.0,0.0,1.0,0.0,0.0)*backAlpha);
    float cw=mix(322.0-64.0*f[${F.CTA_SPLIT}],56.0,f[${F.CTA_LENS}]);
    float2 cc=float2(cx+cw*0.5,cy+28.0);
    float2 cp=(p-cc)/(1.0-0.03*f[${F.CTA_PRESS}])+cc;
    col=over(col,glass(cp,float4(cx,cy,cw,56.0),28.0,4.0,1.0,0.0,1.0,0.0)*(1.0-smoothstep(0.65,1.0,f[${F.CTA_LENS}])));
    // The contracted CTA becomes the same machined object as a node.
    float2 cq=cp-cc;
    float lensMix=smoothstep(0.65,1.0,f[${F.CTA_LENS}]);
    if(lensMix>0.0) {
      float rad=length(cq), face=sat(0.5-(cq.x+cq.y)/76.0);
      float litDone=smoothstep(0.0,0.5,f[${F.CTA_DONE}]);
      float3 lc=mix(BLUE,GOLD,litDone);
      float outer=aa(rad-28.0), dome=aa(rad-21.0);
      float3 metal=float3(0.042,0.055,0.084)*(0.6+face*1.5);
      metal+=float3(0.20,0.27,0.38)*exp(-sq((rad-27.0)/0.65))*face;
      metal*=1.0-0.65*exp(-sq((rad-22.5)/0.7));
      float height=sqrt(max(0.0,1.0-sq(rad/21.0)));
      float lighting=sat(0.14+0.76*height-(cq.x+cq.y)*0.014);
      float3 dc=mix(float3(0.025,0.10,0.36),float3(0.27,0.67,1.0),lighting);
      dc+=ICE*exp(-sq((cq.x+6.0)/11.0)-sq((cq.y+12.0)/3.1))*0.48;
      dc+=ICE*exp(-sq((rad-20.0)/0.65))*(0.15+face*0.32);
      dc=mix(dc,mix(float3(0.96,0.86,0.66),float3(1.0),lighting),litDone);
      float3 object=mix(metal,dc,dome);
      col=over(col,float4(object*outer,outer)*lensMix);
      float angle=atan(cq.y,cq.x), spin=f[${F.CTA_SPIN}];
      float arc=smoothstep(0.68,0.76,cos(angle-spin+0.64));
      arc=mix(arc,1.0,litDone);
      float band=exp(-sq((rad-25.4)/1.15));
      float2 orbitHead=25.4*float2(cos(spin),sin(spin));
      float headDist=length(cq-orbitHead);
      float orbitGlow=(band*arc+exp(-sq(headDist/2.4))*1.8)*lensMix;
      col.rgb+=mix(ICE,GOLD,litDone)*orbitGlow;
      col.rgb+=lc*(exp(-max(rad-27.0,0.0)/10.0)*(1.0-smoothstep(48.0,62.0,rad))*(1.0-outer)*0.45
        +exp(-sq(headDist/7.0))*0.34)*lensMix;
      col.a=max(col.a,sat(max(col.r,max(col.g,col.b))));
    }
    float check=f[${F.CTA_DONE}]<0.03?0.0:tick(cp-float2(cx+28.0,cy+28.0),f[${F.CTA_DONE}]);
    col=over(col,float4(float3(0.024,0.086,0.227)*check,check));
  }
  return half4(col*f[${F.PRESENT}]*smoothstep(96.0,104.0,p.y));
}
`;

export const HERO_SKSL = COMMON + `
half4 main(float2 p) {
  float tm=animtime(), hy=ny(f[${F.HEAD}]);
  float dx=abs(p.x-RX), lineY=smoothstep(Y0-1.0,Y0+1.0,p.y)*(1.0-smoothstep(ny(4.0)-1.0,ny(4.0)+1.0,p.y));
  float3 rgb=float3(0.0);
  float alpha=0.0;
  float tube=aa(dx-3.0)*lineY;
  rgb+=float3(0.035,0.048,0.073)*tube;
  rgb+=float3(0.19,0.24,0.32)*exp(-sq((dx-2.4)/0.42))*lineY*0.45;
  alpha=tube*0.9;
  float lit=(1.0-smoothstep(hy-1.0,hy+3.0,p.y))*lineY;
  float plasma=0.82+0.18*sin(p.y*0.09-tm*2.8);
  float localRed=f[${F.TONE}]*exp(-sq((p.y-hy)/68.0));
  float3 railcol=hue(localRed);
  rgb+=railcol*exp(-sq(dx/9.0))*lit*0.48;
  rgb+=railcol*exp(-sq(dx/1.9))*lit*0.6;
  rgb+=mix(mix(ICE,float3(1.0,0.22,0.24),localRed),GOLD,f[${F.WARM}])*exp(-sq(dx/0.85))*lit*plasma*0.85;
  for(int i=0;i<5;i++) {
    float yy=ny(float(i));
    float2 q=(p-float2(RX+f[${F.NODE_SHAKE}+i],yy))/(1.0-0.05*f[${F.NODE_PRESS}+i]);
    float rad=length(q), on=f[${F.NODE_ON}+i], active=f[${F.NODE_ACTIVE}+i];
    float done=f[${F.NODE_DONE}+i], flash=f[${F.NODE_FLASH}+i];
    if(rad<96.0) {
      float3 nc=hue(f[${F.TONE}]*active);
      float halo=exp(-max(rad-20.0,0.0)/(7.0+active*5.0))*(1.0-smoothstep(75.0,96.0,rad));
      rgb+=nc*halo*(on*0.16+active*0.38);
      float bezel=aa(rad-24.0);
      float face=sat(0.5-(q.x+q.y)/68.0);
      float3 metal=float3(0.064,0.078,0.110)*(0.60+face*1.45);
      metal+=float3(0.13,0.17,0.23)*exp(-sq((rad-23.3)/0.52))*face;
      metal+=nc*exp(-sq((rad-20.8)/0.55))*on*0.14;
      metal*=1.0-0.65*exp(-sq((rad-19.8)/0.65));
      rgb=mix(rgb,metal,bezel); alpha=max(alpha,bezel);
      float orbit=0.65+0.35*cos(atan(q.y,q.x)-tm*1.047);
      rgb+=nc*exp(-sq((rad-23.0)/0.95))*active*(0.8+0.3*orbit);
      rgb+=ICE*exp(-sq((rad-23.0)/0.32))*active*sq(orbit)*0.30;
      float dome=aa(rad-18.0);
      float height=sqrt(max(0.0,1.0-sq(rad/18.0)));
      float lighting=sat(0.35+0.60*height-(q.x+q.y)*0.013);
      float3 smoked=float3(0.056,0.072,0.106)*(0.7+lighting*0.8);
      float3 litDome=mix(nc*0.43,nc*0.90+ICE*0.22,lighting);
      litDome+=ICE*exp(-length(q-float2(-5.0,-7.0))/12.0)*0.18;
      litDome=mix(litDome,nc*0.72+ICE*0.16,done*0.45);
      litDome=mix(litDome,nc*(0.36+0.16*lighting)+ICE*0.06,active*0.68);
      float3 dc=mix(smoked,litDome,sat(on));
      dc+=nc*max(on-1.0,0.0)*0.7;
      dc=mix(dc,ICE,flash*0.8);
      float fresnel=exp(-sq((rad-17.1)/0.55));
      dc+=mix(float3(0.12,0.15,0.21),ICE*0.50,sat(on))*fresnel*(0.25+face*0.65);
      float window=exp(-sq((q.x+6.0)/10.0)-sq((q.y+11.0)/2.6));
      dc+=ICE*window*(0.06+on*0.17);
      rgb=mix(rgb,dc,dome); alpha=max(alpha,dome);
      float check=tick(q,done);
      rgb=mix(rgb,float3(0.024,0.086,0.227),check);
      float progress=1.0-sat(flash), ringR=24.0+progress*48.0;
      rgb+=nc*exp(-sq((rad-ringR)/4.2))*sat(flash)*1.05;
      rgb+=mix(nc,ICE,0.25)*exp(-sq(rad/38.0))*sq(sat(flash))*0.60;
      if(flash>0.001) {
        // Twelve trajectories, one angular candidate per pixel. Compact support stays
        // inside its sector; differing radii/lengths avoid a circular tick-mark layout.
        float ang=atan(q.y,q.x);
        float seed=floor((ang+3.141593)/0.523599);
        float rayAngle=-3.141593+(seed+0.5)*0.523599+0.065*sin(seed*7.3);
        float2 axis=float2(cos(rayAngle),sin(rayAngle));
        float travel=24.0+hash(seed+5.0)*24.0+progress*(20.0+hash(seed+3.0)*36.0);
        float streak=7.0+hash(seed+11.0)*16.0+progress*5.0;
        float along=dot(q,axis), across=abs(q.x*axis.y-q.y*axis.x);
        float t=sat((along-travel)/streak);
        float span=smoothstep(travel-2.0,travel+2.0,along)*(1.0-smoothstep(travel+streak-2.0,travel+streak+1.0,along));
        float width=0.35+0.95*t;
        float sparkLight=(exp(-sq(across/width))+0.24*exp(-sq(across/3.0)))*(1.0-smoothstep(2.5,4.0,across));
        rgb+=mix(nc,ICE,0.40)*sparkLight*span*sat(flash)*(0.4+0.6*t);
      }
    }
  }
  // Moving emissions sit over the objects: nearby bezels must never erase the hero.
  float2 hq=p-float2(RX,hy);
  float hd=length(hq), held=f[${F.DRAG}], vel=f[${F.HEAD_VEL}];
  float tailLength=80.0+40.0*sat(abs(vel)/4.0);
  float behind=-hq.y*(vel>=0.0?1.0:-1.0);
  float trail=sat(1.0-behind/tailLength);
  float tail=smoothstep(-2.0,4.0,behind)*sq(trail)*exp(-sq(hq.x/(0.6+3.4*trail)))*(1.0-held);
  float bloomR=mix(40.0,50.0,held);
  float bloom=(1.0/(1.0+sq(hd/12.0)))*(1.0-smoothstep(bloomR,bloomR*1.65,hd));
  float bead=exp(-sq(hd/5.0));
  float flare=exp(-sq(hq.y/1.0))*sq(sat(1.0-abs(hq.x)/90.0));
  float3 hc=mix(hue(f[${F.TONE}]),float3(1.0,0.045,0.07),f[${F.TONE}]);
  float3 hot=mix(mix(ICE,float3(1.0,0.035,0.05),f[${F.TONE}]),GOLD,f[${F.WARM}]);
  rgb+=f[${F.HEAD_GLOW}]*(hc*(bloom*1.35+tail*1.0)+hot*(bead*2.6+flare*0.9+tail*0.35));
  float surge=f[${F.SURGE}];
  float2 sqp=p-float2(RX,ny(4.0-4.0*surge));
  float sd=length(sqp), st=sat(1.0-sqp.y/105.0);
  float surgeTail=smoothstep(0.0,4.0,sqp.y)*sq(st)*exp(-sq(sqp.x/(0.6+3.4*st)));
  float surgeGate=smoothstep(0.0,0.03,surge)*(1.0-smoothstep(0.97,1.0,surge));
  rgb+=surgeGate*(GOLD*1.35/(1.0+sq(sd/12.0))*(1.0-smoothstep(40.0,66.0,sd))+surgeTail*GOLD
    +float3(1.0,0.97,0.84)*(exp(-sq(sd/5.0))*2.6+exp(-sq(sqp.y))*sq(sat(1.0-abs(sqp.x)/90.0))*0.9));
  // Premultiplied emission: a modest alpha only where there is actual surface or light.
  alpha=max(alpha,sat(max(rgb.r,max(rgb.g,rgb.b))));
  float mask=smoothstep(96.0,104.0,p.y)*(1.0-smoothstep(488.0,520.0,p.y))*f[${F.PRESENT}];
  return half4(min(rgb,float3(1.0))*mask,alpha*mask);
}
`;

/** Compile a named material and fail explicitly rather than silently drawing a blank canvas. */
export function compileBeamShader(name: string, source: string) {
  try {
    const effect = Skia.RuntimeEffect.Make(source);
    if (!effect) throw new Error('SkSL compiler returned null');
    return effect;
  } catch (error) {
    throw new Error(`Beam Checkout ${name} shader failed: ${String(error)}`);
  }
}

export const BEAM_EFFECTS = {
  rays: compileBeamShader('rays', RAYS_SKSL),
  glass: compileBeamShader('glass', GLASS_SKSL),
  hero: compileBeamShader('rail and nodes', HERO_SKSL),
};
