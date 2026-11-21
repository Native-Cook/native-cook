import { Skia } from '@shopify/react-native-skia';

// The relief is analytic: no offscreen is redrawn during a ripple or a drag. Only the typeset ink atlas is cached.
const COLOR = `
uniform float uP3;
float lin(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
half3 toOut(float3 c) {
  if (uP3 < 0.5) return half3(c);
  float3 l = float3(lin(c.r), lin(c.g), lin(c.b));
  return half3(enc(0.8225*l.r+0.1774*l.g),enc(0.0332*l.r+0.9669*l.g),enc(0.0171*l.r+0.0724*l.g+0.9108*l.b));
}
float hash(float2 p) { return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453); }
float2 rotate(float2 p,float a) { float c=cos(a); float s=sin(a); return float2(c*p.x-s*p.y,s*p.x+c*p.y); }
`;
const MATERIAL = `
uniform shader uArt;
uniform float uLight;
uniform float uElevation;
float art(float2 q,float r,float angle) {
  float2 uv=rotate(q,-angle*0.0174532925)/(r*2.0)+0.5;
  if (max(abs(uv.x-0.5),abs(uv.y-0.5))>0.5) return 0.0;
  return uArt.eval(uv*56.0).a;
}
float3 lightDirection() { float a=uLight*0.0174532925; float e=uElevation*0.0174532925; return float3(sin(a)*cos(e),-cos(a)*cos(e),sin(e)); }
float3 sealColor(float2 q,float r,float angle,float gold,float flash,float index,float wet) {
  float d=length(q); float bevel=smoothstep(r-1.6,r,d);
  float2 edge=q/max(d,0.01)*bevel*1.9;
  // A 0.3 pt engraving shares the same normal and key light as the substrate.
  float ink=art(q,r,angle);
  float2 inkN=float2(art(q+float2(0.35,0),r,angle)-art(q-float2(0.35,0),r,angle),art(q+float2(0,0.35),r,angle)-art(q-float2(0,0.35),r,angle))*0.22;
  float3 n=normalize(float3(edge-inkN,1.0)); float3 L=lightDirection();
  float diffuse=max(dot(n,L),0.0);
  float noise=hash(floor(rotate(q,-angle)*3.0)+index*17.3);
  float crescent=smoothstep(-15.0,28.0,q.x+q.y*0.45+index*1.3);
  float coverage=1.0-0.22*noise*crescent;
  float3 ivory=float3(0.9373,0.9020,0.8235)*(0.79+0.29*diffuse);
  float band=exp(-pow((q.x*0.83+q.y*0.36+sin(uLight*0.03)*6.0-r*0.13)/7.0,2.0));
  float darkBand=exp(-pow((q.x*0.83+q.y*0.36-r*0.43)/7.5,2.0));
  float3 foil=mix(float3(0.8510,0.6431,0.2549),float3(1.0,0.9137,0.6588),band*0.85);
  foil=mix(foil,float3(0.4196,0.2902,0.0784),darkBand*0.62);
  foil*=0.70+0.4*diffuse;
  float3 base=mix(ivory,foil,gold);
  float3 inkColor=mix(float3(0.1804,0.1216,0.0549),float3(0.28,0.16,0.035),gold);
  base=mix(base,inkColor,ink*coverage*(0.89+index*0.013));
  float roughness=mix(0.35,0.15,wet);
  float spec=pow(max(dot(n,normalize(L+float3(0,0,1))),0.0),2.0/(roughness*roughness)-2.0+gold*38.0);
  base+=float3(1.0,0.95,0.84)*(spec*(0.04+gold*0.18)*mix(1.0,2.5,wet)+flash*bevel*0.5);
  // Fresh ink: a soft liquid gloss toward the key light that dries with the wetness.
  float2 gl=q-normalize(L.xy+float2(0.0001))*r*0.42;
  base+=float3(1.0,0.97,0.9)*wet*0.26*exp(-dot(gl,gl)/(r*r*0.12));
  return clamp(base,0.0,1.0);
}
`;

const RELIEF = `${COLOR}${MATERIAL}
uniform shader uGrain;
uniform float uDent;
uniform float uSweep;
uniform float uPhase;
uniform float uNext;
uniform float uFlipFlash;
uniform float4 uSeals[8]; // local centre x/y, radius, opacity
uniform float4 uDetail[8]; // rotation degrees, gold, highlight flash, wetness
uniform float4 uRipple; // centre x/y, radius, amplitude in points
uniform float uGold;
uniform float uMagnet;
// RuntimeEffect uniform arrays require constant indices on Graphite's iOS backend.
float4 sealAt(int i) {
  if (i==0) return uSeals[0]; if (i==1) return uSeals[1]; if (i==2) return uSeals[2]; if (i==3) return uSeals[3];
  if (i==4) return uSeals[4]; if (i==5) return uSeals[5]; if (i==6) return uSeals[6]; return uSeals[7];
}
float4 detailAt(int i) {
  if (i==0) return uDetail[0]; if (i==1) return uDetail[1]; if (i==2) return uDetail[2]; if (i==3) return uDetail[3];
  if (i==4) return uDetail[4]; if (i==5) return uDetail[5]; if (i==6) return uDetail[6]; return uDetail[7];
}
float2 well(int i) { return float2(60.0+77.0*float(i-4*(i/4)),114.0+94.0*float(i/4)); }
float reliefHeight(float2 p,float2 c) {
  float d=length(p-c);
  float h=-1.2*(1.0-smoothstep(34.5,36.0,d));
  float rd=length(p-uRipple.xy)-uRipple.z;
  float dent=-uDent*exp(-dot(p-uRipple.xy,p-uRipple.xy)/(2.0*46.0*46.0));
  return h+dent+uRipple.w*exp(-rd*rd/49.0)*sin(rd*0.23);
}
half4 main(float2 p) {
  float2 q=abs(p-float2(181,172))-float2(157,148);
  float box=length(max(q,0.0))+min(max(q.x,q.y),0.0)-24.0;
  float mask=1.0-smoothstep(-0.4,0.4,box);
  float k=clamp((p.x+p.y)/500.0,0.0,1.0);
  float3 base=k<0.4?mix(float3(0.2275,0.1490,0.0627),float3(0.1922,0.1255,0.0471),k/0.4):mix(float3(0.1922,0.1255,0.0471),float3(0.1216,0.0824,0.0314),(k-0.4)/0.6);
  int col=int(clamp(floor((p.x-21.5)/77.0),0.0,3.0));
  int row=p.y<161.0?0:1; int i=row*4+col;
  float2 c=well(i); float d=length(p-c);
  float2 grad=float2(reliefHeight(p+float2(0.4,0),c)-reliefHeight(p-float2(0.4,0),c),reliefHeight(p+float2(0,0.4),c)-reliefHeight(p-float2(0,0.4),c))/0.8;
  float grain=uGrain.eval(p).r;
  float2 grainN=float2(uGrain.eval(p+float2(0.3,0)).r-grain,uGrain.eval(p+float2(0,0.3)).r-grain)*0.55;
  float3 n=normalize(float3(-grad-grainN,1.0)); float3 L=lightDirection();
  base*=0.88+0.20*max(dot(n,L),0.0)+(grain-0.5)*1.7;
  float floorMask=1.0-smoothstep(34.5,36.0,d);
  float innerShadow=0.85*exp(-pow((d-34.0)/6.0,2.0))*clamp(0.6-dot(normalize(p-c+float2(0.01)),L.xy),0.0,1.0);
  base=mix(base,float3(0.08235,0.05098,0.01569)*(1.0-innerShadow),floorMask);
  float lip=1.0-smoothstep(0.25,1.0,abs(d-35.3));
  base=mix(base,float3(0.4784,0.3412,0.1882),lip*0.7);
  float arc=atan(p.y-c.y,p.x-c.x)-uPhase*2.513274;
  float glint=step(cos(0.610865),cos(arc))*lip*0.4;
  if (abs(float(i)-uNext)<0.1) base=mix(base,float3(0.7804,0.6039,0.3569),glint);
  if (abs(float(i)-uMagnet)<0.1) base+=float3(0.18,0.12,0.045)*lip;
  float4 seal=sealAt(i); float4 detail=detailAt(i);
  float2 sp=p-seal.xy; float sd=length(sp);
  float shadow=(1.0-smoothstep(seal.z-1.0,seal.z+4.0,length(sp-float2(1,1.5))))*seal.w*0.55;
  base*=1.0-shadow;
  float sa=(1.0-smoothstep(seal.z-0.35,seal.z+0.35,sd))*seal.w;
  if (sa>0.0) base=mix(base,sealColor(sp,seal.z,detail.x,detail.y,detail.z,float(i),detail.w),sa);
  // Wet squeeze lip: uneven ink coverage, 1.5 pt beyond the fresh impression.
  float squeeze=(1.0-smoothstep(0.0,1.5,abs(sd-seal.z)))*detail.w*seal.w;
  base=mix(base,float3(1.0,0.9922,0.9647),squeeze*0.6*(0.65+0.35*hash(floor(sp*3.0))));
  // One 56 pt reflected band crosses the entire card; only embossed seals catch it.
  float diagonal=(p.x-p.y)*0.707107;
  float band=exp(-pow((diagonal-mix(290.0,-290.0,uSweep))/28.0,2.0))*step(0.0,uSweep)*step(uSweep,1.0);
  base+=float3(1.0,0.9137,0.6588)*band*0.35*sa*detail.y;
  base+=float3(1.0,0.9137,0.6588)*uFlipFlash*0.5*sa;
  // The continuous perimeter catches the same key light as the wells.
  base+=float3(0.08,0.055,0.023)*exp(-pow((box+0.9)/0.55,2.0))*clamp(1.0-(p.x+p.y)/600.0,0.0,1.0);
  return half4(toOut(base)*mask,mask);
}`;
const PAPER = `${COLOR}
uniform shader uPaper;
half4 main(float2 p) {
  float g=uPaper.eval(p).r-0.5;
  return half4(toOut(float3(0.95686,0.92941,0.87843)+g*0.35),1);
}`;
const compile = (source: string) => {
  const fx = Skia.RuntimeEffect.Make(source);
  if (!fx) throw new Error('Pressed Stamp Card: material shader failed to compile');
  return fx;
};
export const RELIEF_FX = compile(RELIEF);
export const PAPER_FX = compile(PAPER);
export const BUTTON_FX = compile(`${COLOR}
uniform float uGold;
uniform float uPhase;
uniform float uLight;
half4 main(float2 p) {
  float band=exp(-pow((p.x+p.y*0.6-fract(uPhase*1.4+(uLight-315.0)/90.0)*510.0+60.0)/44.0,2.0));
  float3 foil=mix(float3(0.4196,0.2902,0.0784),float3(0.8510,0.6431,0.2549),clamp(p.x/250.0,0.0,1.0));
  foil=mix(foil,float3(1.0,0.9137,0.6588),band);
  return half4(toOut(mix(float3(0.1647,0.1059,0.0471),foil,uGold)),1);
}`);
