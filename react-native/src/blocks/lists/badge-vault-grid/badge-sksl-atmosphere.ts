/** Both effects are single passes, with no offscreen layers or blur allocations. */
export const FLOOD_SKSL = `
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
`;

export const PILL_SKSL = `
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
`;
