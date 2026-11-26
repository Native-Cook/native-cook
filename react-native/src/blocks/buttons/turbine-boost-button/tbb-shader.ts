import { Skia } from '@shopify/react-native-skia';

/** Size of `fan.webp` in pixels. */
export const FAN_PX = 1024;

/**
 * The whole screen in one pass: the fan, its rotor turning with an arc blur, the specular rings, the blue field, the
 * core flare and the rim light. A port of the designer's WebGL 2 reference (`turbine.frag.glsl` of #50), with these
 * changes:
 * - the clocks come in as uniforms (`uA` rotor angle, `uArc` blur arc, `uB` boost 0…1), so touch can drive them;
 * - the fan is placed by `uOff`: the 393 × 852 design centred across, the fan square's top at 252.1 × height / 852; the
 *   shader works in the design's points, so every radius is the reference's;
 * - `fan.webp` is sampled in image pixels; Skia hands it premultiplied, and it is opaque, so its colour is used as is;
 * - `n` samples along the arc instead of a fixed 40 (the frame-rate gate picks the count, see `SAMPLES`);
 * - `uP3` moves the linear colour to Display P3 (off: the canvas is tagged sRGB, see `P3` in the component).
 * - the output gets ±0.5/255 of screen-fixed noise (interleaved gradient noise on the 3× pixel grid, `dither`): Free's
 *   canvas is 8-bit and undithered, so the smooth gradients would otherwise show 1-level steps.
 */
export function turbineEffect(n: number) {
  return Skia.RuntimeEffect.Make(`
uniform shader uFan;
uniform float uA; uniform float uArc; uniform float uB; uniform float2 uOff; uniform float uP3;
const float2 C=float2(194.2,448.6); const float2 SQ=float2(0.,252.1); const float SIDE=393.; const float RR=168.; const float RN=192.;
const float3 BG0=float3(0.,.008,.067);
const int N=${n};
float3 lin(float3 c){return pow(c,float3(2.2));}
float3 img(float2 p){float2 uv=(p-SQ)/SIDE;float2 e=min(uv,1.-uv);float m=smoothstep(0.,.04,min(e.x,e.y));
 return mix(lin(BG0),lin(uFan.eval(clamp(uv,0.,1.)*${FAN_PX}.).rgb),m);}
float2 rot(float2 v,float a){float c=cos(a),s=sin(a);return float2(c*v.x-s*v.y,s*v.x+c*v.y);}
float luma(float3 c){return dot(c,float3(.2126,.7152,.0722));}
float3 shade(float2 p){float2 d=p-C;float r=length(d);
 float inR=1.-smoothstep(RR-4.,RR+3.,r);
 float3 col=img(p);
 if(inR>0.){
  // rotor: turned by uA, averaged along the blur arc
  float3 acc=float3(0.);
  for(int i=0;i<N;i++){float f=(float(i)+.5)/float(N)-.5;acc+=img(C+rot(d,-(uA+f*uArc)));}
  acc/=float(N);
  float bl=smoothstep(.05,.6,uArc);
  // specular rings where the blades shine, brighter toward the key light (upper left)
  float ang=atan(d.y,d.x);float k=max(0.,cos(ang+2.3));float key=.45+.55*k*k;
  float a1=(r-113.6)/2.2,a2=(r-155.)/1.6,a3=(r-113.6)/9.;
  float rings=exp(-a1*a1)*.9+exp(-a2*a2)*.6+exp(-a3*a3)*.18;
  col=mix(col,acc+lin(float3(.85,.92,1.))*rings*key*bl,inR);
 }
 float b=uB;float3 blue=lin(float3(.05,.28,1.));
 // outside the nacelle the field turns electric blue, with only a soft contact shadow
 float outside=smoothstep(RN,RN+6.,r);
 float g=(r-RR-40.)/260.;float h=(r-RN-4.)/14.;
 float3 field=lin(float3(.02,.06,.22))+blue*.55*exp(-g*g);
 field*=1.-.35*exp(-h*h);
 col=mix(col,field,b*outside);
 float q=r/115.;col+=blue*b*1.3*exp(-q*q)*inR*(1.-luma(col)*1.5);
 float q2=r/38.;col+=lin(float3(.6,.8,1.))*b*.9*exp(-q2*q2);
 float q3=(r-RR-14.)/9.;col+=blue*b*.9*exp(-q3*q3);
 return col;}
float3 toP3(float3 c){return float3(dot(c,float3(.8225,.1774,0.)),dot(c,float3(.0332,.9669,0.)),dot(c,float3(.0171,.0724,.9108)));}
half3 dither(float3 c,float2 p){float n=fract(52.9829189*fract(dot(floor(p*3.),float2(.06711056,.00583715))));return half3(c+(n-.5)/255.);}
half4 main(float2 xy){float3 c=clamp(shade(xy-uOff),0.,1.);c=mix(c,toP3(c),uP3);return half4(dither(pow(c,float3(1./2.2)),xy),1.);}
`)!;
}
