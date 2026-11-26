import { Skia } from '@shopify/react-native-skia';

/** Past tip positions in the trail. */
export const TRAIL = 24;

/**
 * The whole screen in one pass: the photo's clean floor, the band's light pool and its ring trail, the contact shadow,
 * and the top cut out of the photo and turned rigidly about its tip, its band glowing with hot spots racing round it. A
 * port of the designer's WebGL 2 reference (`top.frag.glsl` of #54), with these changes:
 * - `top.webp` is sampled in its own pixels and placed by `uOrigin` (x −87.5, y 0 in the design: it keeps its size and
 *   x, its top moves with the height);
 * - no foreshortening uniform (the reference keeps it at 1: the top is rigid);
 * - the floor pool and the contact shadow fade with the top (`uAlpha`), so nothing jumps at the reset (the designer's
 *   answer 1 on #54).
 * - the output gets ±0.5/255 of screen-fixed noise (interleaved gradient noise on the 3× pixel grid, `dither`): Free's
 *   canvas is 8-bit and undithered, so the smooth gradients would otherwise show 1-level steps.
 */
export const TOP = Skia.RuntimeEffect.Make(`
uniform shader uImg;
uniform float2 uTip; uniform float uA; uniform float uSpin; uniform float uW; uniform float uGlow; uniform float uAlpha;
uniform float uFall; uniform float2 uOrigin; uniform float3 uTrail[${TRAIL}];
const float K=.5547; const float2 TIP=float2(525.,1088.); const float BANDY=875.; const float CXT=531.; const float BANDW=222.;
float3 lin(float3 c){return pow(c,float3(2.2));}
float3 img(float2 q){return float3(uImg.eval(q).rgb);}
half3 dither(float3 c,float2 p){float n=fract(52.9829189*fract(dot(floor(p*3.),float2(.06711056,.00583715))));return half3(c+(n-.5)/255.);}
half4 main(float2 p){
 // clean plate: the photo's own grey, taken beside the top at the same height
 float2 pp=(p-uOrigin)/K;float3 col=lin(mix(img(float2(70.,pp.y)),img(float2(960.,pp.y)),clamp(pp.x/1024.,0.,1.)));
 // floor light: a pool under the band, and the band's footprint ring at each past position
 float3 OR=lin(float3(1.,.42,.06));
 float2 dp=p-uTip;float px=dp.x/85.,py=(dp.y-3.)/11.;col+=OR*.40*uGlow*uAlpha*exp(-px*px-py*py);
 for(int i=0;i<${TRAIL};i++){float3 tr=uTrail[i];if(tr.z>0.){float2 d=(p-tr.xy-float2(0.,2.))/float2(124.,13.);float e=(length(d)-1.)*13./1.6;col+=OR*tr.z*.16*exp(-e*e);}}
 // contact shadow: a dot under the tip, stretching along the floor toward the rim as the top goes down
 {float2 a=uTip;float2 ba=float2(-174.*uFall,0.);float2 pa=p-a;float bb=dot(ba,ba);float h=bb>1.?clamp(dot(pa,ba)/bb,0.,1.):0.;float2 q=pa-ba*h;
  float sx=q.x/(8.+6.*uFall),sy=(q.y-1.5)/(3.+2.*uFall);col*=1.-(.22+.18*uFall)*uAlpha*exp(-sx*sx-sy*sy);}
 // the top: rotated rigidly about its tip by uA
 float2 v=p-uTip;float c=cos(uA),s=sin(uA);float2 l=float2(c*v.x+s*v.y,-s*v.x+c*v.y);float2 q=TIP+l/K;
 if(q.y<TIP.y+4.&&q.x>270.&&q.x<800.&&q.y>520.){
  float3 t=img(q);float3 b=img(float2(70.,q.y));
  float a=smoothstep(.10,.20,length(t-b))*(1.-smoothstep(TIP.y-2.,TIP.y+4.,q.y));
  float lu=dot(t,float3(.3,.59,.11));a*=mix(1.,1.-smoothstep(.22,.34,lu),smoothstep(905.,935.,q.y)); // below the band only the black cone, never the floor glow
  float3 tl=lin(t);
  float orng=smoothstep(.25,.45,t.r-t.b)*smoothstep(.5,.7,t.r);
  tl=mix(tl,tl*(.25+.85*uGlow),orng);
  tl=mix(tl,tl*float3(1.,.42,.28),orng*smoothstep(.55,.2,uGlow));
  // three hot spots run round the band, blurred by speed
  float len=clamp(uW*.022*BANDW,3.,110.);
  for(int i=0;i<3;i++){float ph=uSpin+float(i)*2.0943951;float front=cos(ph);if(front>0.){float xs=CXT+BANDW*sin(ph);
   float hx=(q.x-xs)/len,hy=(q.y-BANDY)/5.;tl+=lin(float3(1.,.75,.35))*1.6*front*uGlow*exp(-hx*hx-hy*hy);}}
  col=mix(col,tl,a*uAlpha);}
 return half4(dither(pow(clamp(col,0.,1.),float3(1./2.2)),p),1.);}
`)!;
