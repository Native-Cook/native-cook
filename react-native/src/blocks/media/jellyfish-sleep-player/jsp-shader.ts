import { Skia } from '@shopify/react-native-skia';

/**
 * The whole screen in one pass: the jellyfish photo pulsing (bob, bell squeeze, tentacle wave), its light breathing,
 * recoloured per track with a gradient map, a faint sea tint and drifting motes. A port of the designer's WebGL 2
 * reference (`jellyfish.frag.glsl` of #53), with these changes:
 * - `jellyfish.webp` is sampled in its own pixels and placed by `uOrigin` (centred across, its top at 34 × height /
 *   852); the motes work in the design's points (`uOff`), so the warps and the motes fall where the reference has them;
 * - the clocks are split: `uT` drives the tentacle wave (it slows to a stop on pause), `uM` the motes;
 * - the motes drift 6.75 pt/s instead of 6, so their 18 pt cycle (8/3 s) closes the 8 s loop;
 * - `uWave` scales the tentacle wave (0 under Reduce Motion) and `uLit` multiplies the lit animal (the pause dims it
 *   15 %, Reduce Motion breathes it ± 8 %);
 * - the colour stays the image's sRGB: no linear step and no colour matrix (the canvas asks for `colorSpace="srgb"`,
 *   so Skia converts neither the image nor the output).
 */
export const JELLY = Skia.RuntimeEffect.Make(`
uniform shader uImg;
uniform float uT; uniform float uM; uniform float uC; uniform float uH1; uniform float uH2; uniform float uK;
uniform float uWave; uniform float uLit; uniform float2 uOrigin; uniform float2 uOff;
const float K=568./1024.;
float3 tex(float2 q){return float3(uImg.eval(q).rgb);}
float luma(float3 c){return dot(c,float3(.299,.587,.114));}
float3 grad(float l,float3 a,float3 b,float3 c){l=pow(clamp(l*1.15,0.,1.),.8);return l<.5?mix(a,b,l*2.):mix(b,c,l*2.-1.);}
float3 track(float3 col,float i){float l=luma(col);
 if(i<.5)return col;
 if(i<1.5)return mix(col,grad(l,float3(.0,.05,.12),float3(.08,.62,.92),float3(.80,1.,1.)),.88);
 return mix(col,grad(l,float3(.10,.03,0.),float3(1.,.52,.10),float3(1.,.94,.72)),.88);}
float hash(float2 p){p=fract(p*float2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}
half4 main(float2 xy){float2 px=(xy-uOrigin)/K;float2 p=xy-uOff;
 float c=uC;
 float2 q=px;q.y+=14.*c;
 // bell: narrows and lengthens about its top (353, 450)
 float e=length((q-float2(353.,610.))/float2(430.,240.));float wb=1.-smoothstep(.75,1.15,e);
 float2 o=float2(353.,450.);float2 qb=o+(q-o)/float2(1.-.075*c,1.+.045*c);q=mix(q,qb,wb);
 // tentacles: the root follows the bell's squeeze, a wave runs down them
 float below=smoothstep(690.,780.,q.y);
 q.x=mix(q.x,353.+(q.x-353.)/(1.-.075*c*(1.-smoothstep(760.,1000.,q.y))),below);
 q.x-=uWave*below*(4.+16.*smoothstep(760.,1500.,q.y))*sin(.011*q.y-6.2831853*uT/2.+.6);
 float3 col=tex(q);
 // the glow breathes with the pulse
 float lm=luma(col);col*=1.+.28*c*smoothstep(.15,.6,lm);
 // track colour: two gradient maps crossfaded, on the lit animal only; the sea gets a faint tint
 float w=smoothstep(.05,.18,lm);
 float3 hc=mix(track(col,uH1),track(col,uH2),uK);col=mix(col,hc,w);
 col*=1.+(uLit-1.)*w;
 float3 T0=float3(.9,.3,.8),T1=float3(.2,.75,1.),T2=float3(1.,.6,.15);
 float3 ta=uH1<.5?T0:(uH1<1.5?T1:T2),tb=uH2<.5?T0:(uH2<1.5?T1:T2);float3 tint=mix(ta,tb,uK);
 float2 sd=(px-float2(353.,700.))/float2(600.,700.);
 col+=tint*.05*exp(-dot(sd,sd))*(1.-w);
 // drifting motes
 float2 g=floor(p/18.);float h=hash(g);float2 cp=(g+.5+.35*float2(hash(g+3.),hash(g+9.))-.17)*18.;cp.y-=mod(uM*6.75+h*18.,18.)-9.;
 float dd=length(p-cp);col+=tint*.5*step(.93,h)*exp(-dd*dd/1.2)*(.5+.5*sin(uM*3.1415927+h*40.));
 return half4(half3(clamp(col,0.,1.)),1.);}
`)!;
