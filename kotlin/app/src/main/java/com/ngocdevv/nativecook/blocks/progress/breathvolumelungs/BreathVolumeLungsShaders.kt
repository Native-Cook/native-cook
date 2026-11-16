package com.ngocdevv.nativecook.blocks.progress.breathvolumelungs

/**
 * The RN block's SkSL, verbatim, as AGSL (API 33+). Atlas is the CPU data bitmap (nearest texels sampled at cell centres).
 * Coordinates are dp: the canvas is scaled by the screen density before drawing.
 */
internal object BreathVolumeLungsShaders {
    const val MAIN = """
uniform shader atlas;
uniform float2 resolution;
uniform float4 lattice; // pitchX, pitchY, heroTop, cols
uniform float4 atlasInfo; // cols, rows, width, dotRadius
uniform float4 readout; // cx, cy, dx, dy
uniform float4 panel; // x,y,w,h
uniform float4 state; // p, flow, time, breath
uniform float4 feedback; // packAge, pressed, rebound, reduced
uniform float4 knobInfo; // x0, x1, y, radius
uniform float4 glyphRows[8]; // two vec4 per slot, 7 row masks
uniform float4 glyphX;
uniform float4 glyphKind;
uniform float glyphMix;
uniform float3 ground;
uniform float3 gridInk;
uniform float3 dotInk;
uniform float3 heatInk;
uniform float3 lineInk;
uniform float3 knobInk;
uniform float p3;
uniform float lastAir;

float ss(float a, float b, float x) { return smoothstep(a,b,x); }
float hash21(float2 p) { return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453); }
float disc(float2 d, float radius, float aa) { return 1.0-ss(max(0.0,radius-aa),radius+aa,length(d)); }
float boxDistance(float2 q, float2 extent, float r) {
  float2 z=abs(q)-extent+r;
  return length(max(z,0.0))+min(max(z.x,z.y),0.0)-r;
}
float3 lin(float3 c) { return mix(c/12.92,pow((c+0.055)/1.055,float3(2.4)),step(float3(0.04045),c)); }
float3 gam(float3 c) { c=max(c,0.0); return mix(c*12.92,1.055*pow(c,float3(1.0/2.4))-0.055,step(float3(0.0031308),c)); }
float3 toLab(float3 c) {
  c=lin(c);
  float3 lms=float3(dot(c,float3(0.41222147,0.53633254,0.05144599)),dot(c,float3(0.21190350,0.68069955,0.10739696)),dot(c,float3(0.08830246,0.28171884,0.62997870)));
  lms=pow(max(lms,0.0),float3(1.0/3.0));
  return float3(dot(lms,float3(0.21045426,0.79361779,-0.00407205)),dot(lms,float3(1.97799850,-2.42859221,0.45059371)),dot(lms,float3(0.02590404,0.78277177,-0.80867577)));
}
float3 fromLab(float3 c) {
  float3 lms=float3(c.x+0.39633778*c.y+0.21580376*c.z,c.x-0.10556135*c.y-0.06385417*c.z,c.x-0.08948418*c.y-1.29148555*c.z);
  lms=lms*lms*lms;
  return gam(float3(dot(lms,float3(4.07674166,-3.30771159,0.23096993)),dot(lms,float3(-1.268438,2.60975740,-0.34131940)),dot(lms,float3(-0.00419609,-0.70341861,1.70761470))));
}
float3 lchMix(float3 a, float3 b, float f) {
  float3 u=toLab(a), v=toLab(b);
  float ha=atan(u.z,u.y), hb=atan(v.z,v.y);
  float dh=mod(hb-ha+3.14159265,6.2831853)-3.14159265;
  float h=ha+dh*f, chroma=mix(length(u.yz),length(v.yz),f);
  return clamp(fromLab(float3(mix(u.x,v.x,f),cos(h)*chroma,sin(h)*chroma)),0.0,1.0);
}
float3 colorOut(float3 c, float convert) {
  if(convert<0.5) return c;
  float3 a=lin(clamp(c,0.0,1.0));
  float3 wide=float3(dot(a,float3(0.82259287,0.17753395,0.0)),dot(a,float3(0.03319951,0.96678350,0.0)),dot(a,float3(0.01708535,0.07239572,0.91030148)));
  return mix(c,gam(wide),convert);
}
float4 cellAt(float2 ij, float4 info) {
  if(ij.x<0.0||ij.x>=info.x||ij.y<0.0||ij.y>=info.y) return float4(0.0);
  return atlas.eval(floor(ij)+0.5);
}
float pileThreshold(float p, float rows) {
  float f=pow(clamp(p,0.0,1.0),0.8)*63.0;
  float4 a=atlas.eval(float2(floor(f)+0.5,rows+0.5));
  float4 b=atlas.eval(float2(min(63.0,floor(f)+1.0)+0.5,rows+0.5));
  float ta=(a.r*65280.0+a.g*255.0)/65535.0;
  float tb=(b.r*65280.0+b.g*255.0)/65535.0;
  return mix(ta,tb,fract(f))*1.22-0.22;
}
float rowCode(int slot, int row) {
  float4 a=slot==0?glyphRows[0]:(slot==1?glyphRows[2]:(slot==2?glyphRows[4]:glyphRows[6]));
  float4 b=slot==0?glyphRows[1]:(slot==1?glyphRows[3]:(slot==2?glyphRows[5]:glyphRows[7]));
  float4 v=row>3?b:a;
  int k=row>3?row-4:row;
  return k==0?v.x:(k==1?v.y:(k==2?v.z:v.w));
}
float bitAt(float mask, int col) {
  // pow(2,n) is approximate on SkVM/Metal; at exact integer quotients it can invent/drop an LED.
  float divisor=col==0?1.0:(col==1?2.0:(col==2?4.0:(col==3?8.0:(col==4?16.0:(col==5?32.0:(col==6?64.0:(col==7?128.0:256.0)))))));
  return mod(floor(mask/divisor+0.0001),2.0);
}
float glyphDot(float2 pos, float4 rd, float4 xs, float r, float mixValue) {
  float light=0.0;
  for(int s=0;s<4;s++) {
    float sx=s==0?xs.x:(s==1?xs.y:(s==2?xs.z:xs.w));
    float2 q=float2((pos.x-rd.x)/rd.z-sx,(pos.y-rd.y)/rd.w+3.0);
    int row=int(floor(q.y+0.5));
    int col=int(floor(q.x*2.0+4.5));
    if(row>=0&&row<7&&col>=-1&&col<=9) {
      float code=rowCode(s,row);
      float a=floor(code/512.0), b=mod(code,512.0);
      for(int offset=-1;offset<=1;offset++) {
        int cc=col+offset;
        if(cc>=0&&cc<9) {
          float bitA=bitAt(a,cc);
          float bitB=bitAt(b,cc);
          float gate=mix(bitA,bitB,ss(0.0,1.0,mixValue));
          float2 delta=float2((q.x-(float(cc)-4.0)*0.5)*rd.z,(q.y-float(row))*rd.w);
          light=max(light,disc(delta,r*sqrt(gate),0.3)*gate);
        }
      }
    }
  }
  return light;
}
float pileLight(float2 pos, float2 cell, float4 info, float4 gridBox, float th, float p, float age, float reduced) {
  if(cell.x<0.0||cell.x>=info.x||cell.y<0.0||cell.y>=info.y) return 0.0;
  float4 sampleData=cellAt(cell,info);
  float baseRadius=atlas.eval(cell+float2(info.x+0.5,0.5)).b;
  // Only mound cells take part: below zero the threshold would otherwise light the whole lattice.
  float radiusFactor=max(ss(0.0,0.22,sampleData.b-th)*step(0.002,sampleData.b),baseRadius);
  float sourceRow=cell.y*32.0/info.x;
  float rowDelay=clamp((36.0-sourceRow)*0.02,0.0,0.14);
  float rowAge=age-rowDelay;
  // Stagger by 20 ms, but every row is back on its grid cell at 160 ms.
  float shift=(age>=0.0&&age<0.16&&rowAge>=0.0)?sin(rowAge*3.14159265/(0.16-rowDelay))*gridBox.y:0.0;
  float topRow=7.0*info.x/32.0;
  float2 center=float2((cell.x+0.5)*gridBox.x,gridBox.z+(cell.y-topRow+0.5)*gridBox.y+shift*(1.0-reduced));
  return disc(pos-center,info.w*radiusFactor,0.35)*radiusFactor*mix(1.0,max(p,baseRadius),reduced);
}
half4 main(float2 pos) {
  float scale=resolution.x/402.0;
  float p=clamp(state.x,0.0,1.0), flow=state.y, time=state.z;
  float moving=1.0-feedback.w;
  float3 heat=heatInk;
  float topRow=7.0*atlasInfo.x/32.0;
  float2 ij=float2(floor(pos.x/lattice.x),floor((pos.y-lattice.z)/lattice.y+topRow));
  float2 center=float2((ij.x+0.5)*lattice.x,lattice.z+(ij.y-topRow+0.5)*lattice.y);
  float2 delta=pos-center;
  float4 data=cellAt(ij,atlasInfo);
  float th=0.04+0.9*pow(p,0.8)+0.03*state.w*(1.0-ss(0.0,0.125,p));
  float pressure=ss(0.0,0.22,data.r-th);
  float reducedAlpha=mix(1.0,1.0-p,feedback.w);
  pressure=mix(pressure,1.0,feedback.w);
  float radius=atlasInfo.w*data.g*pressure;
  float twinkle=1.0+0.06*moving*(sin(time*12.5663706/8.5+hash21(ij)*6.2831853)-sin(hash21(ij)*6.2831853));
  float lit=disc(delta,radius*(1.0+feedback.z),0.35)*pressure*data.g*reducedAlpha;
  float nearField=0.0, residualId=0.0, sparse=0.0;
  if(ij.x>=0.0&&ij.x<atlasInfo.x&&ij.y>=0.0&&ij.y<atlasInfo.y) {
    float4 sideData=atlas.eval(ij+float2(atlasInfo.x+0.5,0.5)); nearField=sideData.r; residualId=sideData.g; sparse=sideData.b;
  }
  float finalCells=step(0.001,residualId)*lastAir*moving*(0.65+0.35*cos(time*83.0+residualId*37.0));
  lit=max(lit,disc(delta,atlasInfo.w*finalCells,0.35)*finalCells);
  float gridDot=disc(delta,0.95*scale,0.25);
  float halo=nearField*(1.0-p)*(1.0-data.g);
  float age=feedback.x;
  float packPulse=age>=0.0&&age<0.24?sin(age*3.14159265/0.24):0.0;
  float jy=ij.y*32.0/atlasInfo.x;
  float pileTh=pileThreshold(mix(p,1.0,feedback.w),atlasInfo.y);
  float pileDot=pileLight(pos,ij,atlasInfo,lattice,pileTh,p,age,feedback.w);
  if(age>=0.0&&age<0.16&&moving>0.5) {
    // Neighbour cells receive travelling discs; no SDF is clipped by a tile boundary.
    pileDot=max(pileDot,pileLight(pos,ij-float2(0.0,1.0),atlasInfo,lattice,pileTh,p,age,feedback.w));
    pileDot=max(pileDot,pileLight(pos,ij+float2(0.0,1.0),atlasInfo,lattice,pileTh,p,age,feedback.w));
  }
  float3 pileInk=mix(dotInk,heat,0.22*ss(33.0,35.0,jy));
  float3 c=ground+gridInk*gridDot;
  c=mix(c,dotInk*0.28,gridDot*halo);
  c=mix(c,dotInk*clamp(twinkle,0.88,1.06),clamp(lit,0.0,1.0));
  c=mix(c,pileInk,pileDot);
  // Two outlets, one under each lobe (columns 11 and 20, from row 17 down to the pile's shoulder). They run
  // outside the readout and its label, so nothing that moves ever crosses the number.
  float outletCol=floor(11.0*atlasInfo.x/32.0+0.5);
  float leftX=(outletCol+0.5)*lattice.x, rightX=(atlasInfo.x-outletCol-0.5)*lattice.x;
  float travel=fract((pos.y/lattice.y)-time*sign(flow)*11.0);
  float streamX=abs(pos.x-leftX)<abs(pos.x-rightX)?leftX:rightX;
  float transit=disc(float2(pos.x-streamX,(travel-0.5)*lattice.y),atlasInfo.w*0.85,0.3);
  float streamTop=lattice.z+(17.5-7.0)*lattice.y, streamEnd=lattice.z+(29.0-7.0)*lattice.y;
  float corridor=ss(streamTop,streamTop+lattice.y,pos.y)*(1.0-ss(streamEnd-1.5*lattice.y,streamEnd,pos.y));
  transit*=corridor*min(1.0,abs(flow)*4.0)*moving;
  c=mix(c,dotInk,transit*0.75);
  float digits=0.0;
  if(abs(pos.x-readout.x)<70.0*scale&&abs(pos.y-readout.y)<5.0*readout.w) digits=glyphDot(pos,readout,glyphX,2.15*scale,glyphMix);
  c=mix(c,dotInk,digits);
  // Thick inset glass, one emitting surface. All light terminates smoothly at the rounded edge.
  float2 panelCenter=panel.xy+panel.zw*0.5;
  float sd=boxDistance(pos-panelCenter,panel.zw*0.5,50.0*scale);
  float cardMask=1.0-ss(-1.25*scale,1.25*scale,sd);
  if(cardMask>0.0) {
    float2 uv=(pos-panel.xy)/panel.zw;
    float side=exp(-min(uv.x,1.0-uv.x)*9.0);
    float topLight=exp(-uv.y*11.0);
    float lower=ss(1.0-(0.40+0.35*p),1.0,uv.y);
    // Four source-sampled studio bands, rather than a generic purple edge gradient.
    float3 capLight=float3(0.42,0.40,0.64);
    float3 shoulder=float3(0.294,0.282,0.545);
    float3 blueBody=float3(0.0745,0.106,0.361);
    float3 deepBody=float3(0.008,0.010,0.066);
    float3 studio=uv.y<0.05?mix(capLight,shoulder,ss(0.0,0.05,uv.y)):(uv.y<0.15?mix(shoulder,blueBody,ss(0.05,0.15,uv.y)):mix(blueBody,deepBody,ss(0.15,0.40,uv.y)));
    float3 sideTint=mix(float3(0.44,0.36,0.39),float3(0.17,0.22,0.65),ss(0.15,0.55,uv.y));
    sideTint=mix(sideTint,heat,p*0.5);
    float3 glass=studio+0.85*side*sideTint+heat*((0.24+0.45*p)*lower*(1.0+0.15*packPulse));
    float inner=exp(-abs(sd+1.1*scale)/(0.65*scale));
    glass+=float3(0.486,0.424,0.941)*inner*(0.08+0.10*topLight);
    // The grid is displaced 3 pt in the top 14 pt of the glass.
    float2 refractDelta=float2(mod(pos.x+3.0*scale,lattice.x)-lattice.x*0.5,mod(pos.y-lattice.z+3.0*scale,lattice.y)-lattice.y*0.5);
    glass+=heat*disc(refractDelta,0.85*scale,0.3)*0.12*(1.0-ss(10.0*scale,14.0*scale,pos.y-panel.y));
    glass+=(hash21(floor(pos/scale))-0.5)*0.008;
    c=mix(c,glass,cardMask);
  }
  // The source's track, yellow breath line and directional half-lit knob.
  float ky=knobInfo.z, kx=mix(knobInfo.x,knobInfo.y,state.x*moving);
  float track=disc(float2(mod(pos.x-knobInfo.x,6.6*scale)-3.3*scale,pos.y-ky),0.55*scale,0.25);
  track*=step(panel.x+32.0*scale,pos.x)*(1.0-step(panel.x+panel.z-20.0*scale,pos.x));
  c=mix(c,float3(0.35,0.36,0.55),track*0.6);
  float lineEnd=min(kx+93.0*scale,panel.x+panel.z-24.0*scale);
  float lineMask=(1.0-ss(0.4*scale,1.0*scale,abs(pos.y-ky)))*step(kx-42.0*scale,pos.x)*(1.0-step(lineEnd,pos.x));
  c=mix(c,lineInk,lineMask);
  float tip=disc(pos-float2(lineEnd,ky),2.6*scale,0.3);
  c=mix(c,lineInk,tip);
  float2 kd=(pos-float2(kx,ky))/(1.0-0.06*feedback.y);
  float kr=knobInfo.w;
  float ring=abs(length(kd)-(kr+3.5*scale));
  float angle=atan(kd.y,kd.x);
  float stipple=ss(0.35,0.75,cos(angle*22.0)); // ~1.3 px soft dash ends at 3x, no stair-stepped caps
  c=mix(c,float3(0.73,0.74,0.83), (1.0-ss(0.3*scale,0.75*scale,ring))*stipple*0.8);
  float ball=disc(kd,kr,0.4);
  float nx=kd.x/max(kr,0.01), ny=kd.y/max(kr,0.01);
  float z=sqrt(max(0.0,1.0-nx*nx-ny*ny));
  float key=max(0.0,-nx*0.65-ny*0.22+z*0.5);
  // Source dial: a silver face and a dark hemisphere with a crisp vertical terminator.
  float litFace=1.0-ss(-0.025,0.025,nx);
  float3 ballInk=mix(float3(0.045,0.05,0.085)+key*0.06,knobInk*(0.72+0.20*z),litFace);
  ballInk+=pow(key,14.0)*0.12;
  c=mix(c,ballInk,ball);
  c=mix(c,lineInk,lineMask*0.85);
  return half4(colorOut(clamp(c,0.0,1.0),p3),1.0);
}
"""
}
