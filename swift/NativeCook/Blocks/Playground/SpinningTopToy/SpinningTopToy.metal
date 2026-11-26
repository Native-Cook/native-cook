#include <metal_stdlib>
using namespace metal;
namespace SpinningTopToyShader {
struct Varying { float4 position [[position]]; };
struct Uniforms { float2 size; float2 pixels; float2 tip; float angle; float spin; float speed; float glow; float alpha; float fall; float4 trail[24]; };
vertex Varying vertexMain(uint id [[vertex_id]]) { float2 p=float2((id<<1)&2,id&2); return {float4(p*2.-1.,0,1)}; }
constant float K=.5547; constant float2 TIP=float2(525.,1088.); constant float BANDY=875.; constant float CXT=531.; constant float BANDW=222.;
float3 lin(float3 c){return pow(c,float3(2.2));}
// Match the reference's screen-fixed 3x design grid after gamma encoding.
float3 dither(float3 c,float2 p){float n=fract(52.9829189*fract(dot(floor(p*3.),float2(.06711056,.00583715))));return c+(n-.5)/255.;}

fragment float4 fragmentMain(Varying in [[stage_in]], constant Uniforms &u [[buffer(0)]], texture2d<float> photo [[texture(0)]]) {
 float2 p=in.position.xy/u.pixels*u.size;
 constexpr sampler smp(coord::normalized,address::clamp_to_edge,filter::linear);
 // clean plate: the photo's own grey, taken beside the top at the same height
 float2 pp=(p-float2(-87.5,0.))/K;float3 col=lin(mix(photo.sample(smp,(float2(70.,pp.y))/float2(1024.,1536.)).rgb,photo.sample(smp,(float2(960.,pp.y))/float2(1024.,1536.)).rgb,clamp(pp.x/1024.,0.,1.)));
 // floor light: a pool under the band, and the band's footprint ring at each past position
 float3 OR=lin(float3(1.,.42,.06));
 float2 dp=p-u.tip;float px=dp.x/85.,py=(dp.y-3.)/11.;col+=OR*.40*u.glow*u.alpha*exp(-px*px-py*py);
 for(int i=0;i<24;i++){float3 tr=u.trail[i].xyz;if(tr.z>0.){float2 d=(p-tr.xy-float2(0.,2.))/float2(124.,13.);float e=(length(d)-1.)*13./1.6;col+=OR*tr.z*.16*exp(-e*e);}}
 // contact shadow: a dot under the tip, stretching along the floor toward the rim as the top goes down
 {float2 a=u.tip;float2 ba=float2(-174.*u.fall,0.);float2 pa=p-a;float bb=dot(ba,ba);float h=bb>1.?clamp(dot(pa,ba)/bb,0.,1.):0.;float2 q=pa-ba*h;
  float sx=q.x/(8.+6.*u.fall),sy=(q.y-1.5)/(3.+2.*u.fall);col*=1.-(.22+.18*u.fall)*u.alpha*exp(-sx*sx-sy*sy);}
 // the top: rotated rigidly about its tip by u.angle
 float2 v=p-u.tip;float c=cos(u.angle),s=sin(u.angle);float2 l=float2(c*v.x+s*v.y,-s*v.x+c*v.y);float2 q=TIP+l/K;
 if(q.y<TIP.y+4.&&q.x>270.&&q.x<800.&&q.y>520.){
  float3 t=photo.sample(smp,(q)/float2(1024.,1536.)).rgb;float3 b=photo.sample(smp,(float2(70.,q.y))/float2(1024.,1536.)).rgb;
  float a=smoothstep(.10,.20,length(t-b))*(1.-smoothstep(TIP.y-2.,TIP.y+4.,q.y));
  float lu=dot(t,float3(.3,.59,.11));a*=mix(1.,1.-smoothstep(.22,.34,lu),smoothstep(905.,935.,q.y)); // below the band only the black cone, never the floor glow
  float3 tl=lin(t);
  float orng=smoothstep(.25,.45,t.r-t.b)*smoothstep(.5,.7,t.r);
  tl=mix(tl,tl*(.25+.85*u.glow),orng);
  tl=mix(tl,tl*float3(1.,.42,.28),orng*(1.-smoothstep(.2,.55,u.glow)));
  // three hot spots run round the band, blurred by speed
  float len=clamp(u.speed*.022*BANDW,3.,110.);
  for(int i=0;i<3;i++){float ph=u.spin+float(i)*2.0943951;float front=cos(ph);if(front>0.){float xs=CXT+BANDW*sin(ph);
   float hx=(q.x-xs)/len,hy=(q.y-BANDY)/5.;tl+=lin(float3(1.,.75,.35))*1.6*front*u.glow*exp(-hx*hx-hy*hy);}}
  col=mix(col,tl,a*u.alpha);}
 return float4(dither(pow(clamp(col,0.,1.),float3(1./2.2)),p),1.);}

}
