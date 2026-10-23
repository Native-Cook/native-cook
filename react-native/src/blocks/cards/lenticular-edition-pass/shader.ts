/** Ordinary atlas colors stay color-managed; only fixed sRGB edge colors need conversion. */
export const lensSource = `
uniform shader atlas;
uniform float3 row0;
uniform float3 row1;
uniform float3 row2;
uniform float actualYaw;
uniform float explicitViewB;
float3 inverseRotation(float3 v) {
  return float3(dot(float3(row0.x,row1.x,row2.x),v),dot(float3(row0.y,row1.y,row2.y),v),dot(float3(row0.z,row1.z,row2.z),v));
}
float bow(float2 p) { return 3.5*(1.0-4.0*p.x*p.x/(333.0*333.0))*(1.0-4.0*p.y*p.y/(211.0*211.0)); }
float2 bowGradient(float2 p) {
  return float2(-28.0*p.x/(333.0*333.0)*(1.0-4.0*p.y*p.y/(211.0*211.0)),-28.0*p.y/(211.0*211.0)*(1.0-4.0*p.x*p.x/(333.0*333.0)));
}
float distanceToCard(float2 p) { float2 q=abs(p)-float2(160.5,99.5);return length(max(q,0.0))+min(max(q.x,q.y),0.0)-6.0; }
float3 intersection(float3 c,float3 d,float offset) {
  if(abs(d.z)<0.1)return float3(10000.0);
  float plane=-c.z/d.z,t=plane;
  for(int i=0;i<6;++i){float3 p=c+t*d;float residual=p.z-bow(p.xy)-offset;float derivative=d.z-dot(bowGradient(p.xy),d.xy);if(abs(derivative)>0.1)t=clamp(t-residual/derivative,plane-12.0,plane+12.0);}
  return c+t*d;
}
float3 standardColor(float3 color) {
  float3 linear=mix(color/12.92,pow((color+.055)/1.055,float3(2.4)),step(float3(.04045),color));
  return fromLinearSrgb(linear);
}
half4 main(float2 position) {
  float2 screen=position-float2(196.5,366.5);
  float3 c=inverseRotation(float3(0.0,0.0,800.0));
  float3 d=inverseRotation(float3(screen/800.0,-1.0));
  float3 front=intersection(c,d,0.0);
  float distance=distanceToCard(front.xy),residual=abs(front.z-bow(front.xy));
  float coverage=residual<=.02?1.0-smoothstep(-.32,.32,distance):0.0;
  float3 back=intersection(c,d,-1.4);
  float backDistance=distanceToCard(back.xy),backResidual=abs(back.z-bow(back.xy)+1.4);
  float backCoverage=backResidual<=.02?1.0-smoothstep(-.32,.32,backDistance):0.0;
  if(coverage<=0.0&&backCoverage<=0.0)return half4(0.0);
  float edgeLight=1.0-smoothstep(-30.0,30.0,back.y);
  half3 edge=mix(half3(standardColor(float3(.39,.48,.55))),half3(standardColor(float3(.80,.85,.83))),half(edgeLight));
  half4 laminate=half4(edge*half(backCoverage),half(backCoverage));
  if(coverage<=0.0)return laminate;
  float ridge=floor((front.x+166.5)/1.45),ridgeCenter=(ridge+.5)*1.45-166.5;
  float beta=actualYaw+.035*ridgeCenter+.8*sin(6.283185307179586*ridgeCenter/333.0);
  float viewB=explicitViewB>=0.0?clamp(explicitViewB,0.0,1.0):smoothstep(-.7,.7,beta);
  float phase=fract((front.x+166.5)/1.45),displacement=.29267578125*sin(6.283185307179586*phase);
  float x=front.x+175.0+displacement;
  half4 a=atlas.eval(float2(x,front.y+106.5)*3.0),b=atlas.eval(float2(x,front.y+319.5)*3.0);
  half4 print=mix(a,b,half(viewB));
  float shade=1.0+.055*cos(6.283185307179586*phase)+.025*cos(12.56637061435917*phase+.7);
  float trough=.045*smoothstep(.9,1.0,phase);print.rgb*=half(shade*(1.0-trough));
  float reflectedCenter=22.5*clamp(actualYaw/14.0,-1.0,1.0),sigma=33.972872;
  float reflection=(.06+.08*clamp(abs(actualYaw)/14.0,0.0,1.0))*exp(-.5*pow((front.x-reflectedCenter)/sigma,2.0));
  print.rgb+=(print.a-print.rgb)*half(reflection);
  float border=1.0-smoothstep(-1.2,-.8,distance),borderWeight=(1.0-border)*.35;
  print.rgb=mix(print.rgb,half3(standardColor(float3(.718,.776,.808)))*print.a,half(borderWeight));
  half4 face=print*half(coverage);return face+laminate*(1.0-face.a);
}
`;
