#include <metal_stdlib>
using namespace metal;
struct JellyfishSleepPlayerUniforms { float4 clock; float4 tracks; float4 layout; float4 viewport; };
namespace JellyfishSleepPlayerShader {
constexpr sampler jspSampler(coord::normalized,address::clamp_to_edge,filter::linear);
float jspMod(float a,float b){return a-b*floor(a/b);}
constant float K=568./1024.;
float3 tex(float2 q, texture2d<float> photo){return photo.sample(jspSampler,q/float2(708.,1536.)).rgb;}
float luma(float3 c){return dot(c,float3(.299,.587,.114));}
float3 grad(float l,float3 a,float3 b,float3 c){l=pow(clamp(l*1.15,0.,1.),.8);return l<.5?mix(a,b,l*2.):mix(b,c,l*2.-1.);}
float3 track(float3 col,float i){float l=luma(col);
 if(i<.5)return col;
 if(i<1.5)return mix(col,grad(l,float3(.0,.05,.12),float3(.08,.62,.92),float3(.80,1.,1.)),.88);
 return mix(col,grad(l,float3(.10,.03,0.),float3(1.,.52,.10),float3(1.,.94,.72)),.88);}
float hash(float2 p){p=fract(p*float2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}
half4 shade(float2 xy, constant JellyfishSleepPlayerUniforms &u, texture2d<float> photo){float2 px=(xy-u.layout.xy)/K;float2 p=xy-u.layout.zw;
 float c=u.clock.z;
 float2 q=px;q.y+=14.*c;
 // bell: narrows and lengthens about its top (353, 450)
 float e=length((q-float2(353.,610.))/float2(430.,240.));float wb=1.-smoothstep(.75,1.15,e);
 float2 o=float2(353.,450.);float2 qb=o+(q-o)/float2(1.-.075*c,1.+.045*c);q=mix(q,qb,wb);
 // tentacles: the root follows the bell's squeeze, a wave runs down them
 float below=smoothstep(690.,780.,q.y);
 q.x=mix(q.x,353.+(q.x-353.)/(1.-.075*c*(1.-smoothstep(760.,1000.,q.y))),below);
 q.x-=u.clock.w*below*(4.+16.*smoothstep(760.,1500.,q.y))*sin(.011*q.y-6.2831853*u.clock.x/2.+.6);
 float3 col=tex(q,photo);
 // the glow breathes with the pulse
 float lm=luma(col);col*=1.+.28*c*smoothstep(.15,.6,lm);
 // track colour: two gradient maps crossfaded, on the lit animal only; the sea gets a faint tint
 float w=smoothstep(.05,.18,lm);
 float3 hc=mix(track(col,u.tracks.x),track(col,u.tracks.y),u.tracks.z);col=mix(col,hc,w);
 col*=1.+(u.tracks.w-1.)*w;
 float3 T0=float3(.9,.3,.8),T1=float3(.2,.75,1.),T2=float3(1.,.6,.15);
 float3 ta=u.tracks.x<.5?T0:(u.tracks.x<1.5?T1:T2),tb=u.tracks.y<.5?T0:(u.tracks.y<1.5?T1:T2);float3 tint=mix(ta,tb,u.tracks.z);
 float2 sd=(px-float2(353.,700.))/float2(600.,700.);
 col+=tint*.05*exp(-dot(sd,sd))*(1.-w);
 // drifting motes
 float2 g=floor(p/18.);float h=hash(g);float2 cp=(g+.5+.35*float2(hash(g+3.),hash(g+9.))-.17)*18.;cp.y-=jspMod(u.clock.y*6.75+h*18.,18.)-9.;
 float dd=length(p-cp);col+=tint*.5*step(.93,h)*exp(-dd*dd/1.2)*(.5+.5*sin(u.clock.y*3.1415927+h*40.));
 return half4(half3(clamp(col,0.,1.)),1.);}

}
vertex float4 JellyfishSleepPlayerVertex(uint id [[vertex_id]]){return float4(id==1?3.:-1.,id==2?3.:-1.,0.,1.);}
fragment half4 JellyfishSleepPlayerFragment(float4 position [[position]],constant JellyfishSleepPlayerUniforms &u [[buffer(0)]],texture2d<float> photo [[texture(0)]]){return JellyfishSleepPlayerShader::shade(position.xy/u.viewport.x,u,photo);}
