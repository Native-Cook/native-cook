import { Skia } from '@shopify/react-native-skia';

/**
 * The whole screen in one pass: the honey photo, its dipper lifted, its stream widened and flowing, and its mound
 * grown and winding. A port of the designer's WebGL 2 reference (`honey.frag.glsl` of #52), with these changes:
 * - `honey.webp` is sampled in its own pixels; the image keeps its size and x and its top moves to 10 × height / 852
 *   (`uOrigin`), so every landmark is the reference's; outside it the image shader clamps to the edge;
 * - `uStill` (Reduce Motion): the original photograph at every amount, with no lift, widening, flow, growth or coils
 *   (as the SwiftUI and Compose shaders);
 * - the colour stays the image's sRGB: no linear step and no colour matrix (the canvas asks for `colorSpace="srgb"`,
 *   so Skia converts neither the image nor the output).
 */
export const HONEY = Skia.RuntimeEffect.Make(`
uniform shader uImg;
uniform float uAmt; uniform float uT; uniform float uStill; uniform float2 uOrigin;
const float K=520./1024.;
half3 tex(float2 px){return uImg.eval(px).rgb;}
float honeyMask(half3 c){return smoothstep(.70,.80,c.r)*smoothstep(.40,.50,c.g/max(c.r,.01))*(1.-smoothstep(.35,.5,c.b));}
float hash(float2 p){p=fract(p*float2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}
float noise(float2 p){float2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+float2(1.,0.)),f.x),mix(hash(i+float2(0.,1.)),hash(i+float2(1.,1.)),f.x),f.y);}
half4 main(float2 xy){float2 px=(xy-uOrigin)/K;
 if(uStill>.5) return half4(tex(px),1.);
 float A=uAmt-2.;
 // 1. dipper lift: the dipper and the top of the stream rise by 23 px per tsp; the stream stretches
 float2 q=px;q.y+=A*23.*(1.-smoothstep(560.,1040.,px.y));
 // 2. stream width: up to 1.36x at 5 tsp, faded in below the dipper and out above the mound
 float band=smoothstep(590.,660.,q.y)*(1.-smoothstep(990.,1045.,q.y));
 q.x=500.+(q.x-500.)/(1.+.12*A*band);
 // 3. flow down the stream: two cross-faded phases (period 1.5 s), honey only
 float ph0=fract(uT/1.5),ph1=fract(uT/1.5+.5),w0=1.-abs(2.*ph0-1.);
 float sm=band*honeyMask(tex(q));
 float2 n0=float2(noise(q*float2(.05,.012)+ph0*3.)-.5,0.)*6.,n1=float2(noise(q*float2(.05,.012)+ph1*3.+5.)-.5,0.)*6.;
 half3 a=tex(q-float2(0.,ph0*46.)*sm+n0*sm),b=tex(q-float2(0.,ph1*46.)*sm+n1*sm);
 half3 col=mix(b,a,w0);
 // 4. mound: grows about (500, 1230), taller more than wider, so it stays inside the saucer
 float2 m=float2(1.+.04*A,1.+.12*A);float2 mc=float2(500.,1230.);float2 mq=mc+(px-mc)/m;if(px.y>mc.y)mq.y=px.y;
 float region=1.-smoothstep(.9,1.,length((px-mc)/float2(330.,260.)));
 // coils winding: a sideways flow over the mound (period 1.5 s)
 half3 base=tex(mq);float hb=honeyMask(base);
 float mm=hb*region*smoothstep(1000.,1080.,mq.y);
 half3 ma=tex(mq-float2(ph0*22.,0.)*mm),mb=tex(mq-float2(ph1*22.,0.)*mm);half3 mound=mix(mb,ma,w0);
 float hm=hb*region*(1.-smoothstep(1195.,1225.,mq.y));
 col=mix(col,mound,hm*step(1040.,px.y+(m.y-1.)*220.));
 return half4(col,1.);}
`)!;
