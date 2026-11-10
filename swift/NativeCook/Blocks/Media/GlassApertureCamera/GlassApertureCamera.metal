#include <metal_stdlib>
using namespace metal;

// Matches FrameU: four vec4f, 64 bytes. Textures contain encoded sRGB, as in WebGPU rgba8unorm.
struct GlassApertureCameraUniforms { float4 view; float4 cam; float4 iris; float4 ring; };
struct GlassApertureCameraVertex { float4 pos [[position]]; float2 uv; };
constexpr sampler glassApertureCameraSampler(coord::normalized, address::clamp_to_edge, filter::linear);
constant float glassApertureCameraTAU = 6.2831853;
static float glassApertureCameraSq(float v) { return v*v; }
static float3 glassApertureCameraLin(float3 c) { return pow(c,float3(2.2)); }
static float2 glassApertureCameraUv(float2 s, constant GlassApertureCameraUniforms& F) {
    return float2(s.x/F.view.x,s.y/F.cam.y);
}
static float3 glassApertureCameraPhoto(float2 s, texture2d<float> photo, constant GlassApertureCameraUniforms& F) {
    return glassApertureCameraLin(photo.sample(glassApertureCameraSampler,clamp(glassApertureCameraUv(s,F),float2(0.001),float2(0.999)),level(0)).rgb);
}
static float glassApertureCameraDepth(float2 s, constant GlassApertureCameraUniforms& F) {
    float2 uv=glassApertureCameraUv(s,F);
    float dd=1.0-clamp(length((uv-float2(0.36,0.64))*float2(1.5,1.0))*1.9,0.0,1.0)*0.8;
    float sky=smoothstep(0.2,0.26,uv.x)*(1.0-smoothstep(0.56,0.62,uv.x+(0.5-uv.y)*0.5))*(1.0-smoothstep(0.46,0.52,uv.y));
    dd=max(dd,sky*0.97);
    float right=smoothstep(0.52,0.6,uv.x)*(1.0-smoothstep(0.55,0.6,uv.y));
    dd=mix(dd,0.55+0.35*(1.0-uv.x)+0.15*(0.55-uv.y),right*0.9);
    float van=smoothstep(0.48,0.52,uv.x)*smoothstep(0.565,0.59,uv.y)*(1.0-smoothstep(0.89,0.92,uv.y));
    dd=mix(dd,0.2,van);
    dd=mix(dd,0.04,smoothstep(0.82,0.98,uv.y)*(1.0-van));
    return clamp(dd,0.0,1.0);
}
static float glassApertureCameraCoc(float dd, constant GlassApertureCameraUniforms& F) {
    return min(abs(dd-F.cam.w)*26.0*(1.4/F.cam.z),22.0);
}
static float glassApertureCameraNonagon(float th,float r) {
    float k=glassApertureCameraTAU/9.0,a=th-r,m=a-k*floor(a/k)-k*0.5;
    return cos(k*0.5)/cos(m);
}
static float3 glassApertureCameraDof(float2 s,texture2d<float> photo,constant GlassApertureCameraUniforms& F) {
    float c0=glassApertureCameraCoc(glassApertureCameraDepth(s,F),F);
    if(c0<0.35) return glassApertureCameraPhoto(s,photo,F);
    float3 acc=0.0; float wsum=0.0;
    float jit=fract(sin(dot(floor(s*F.view.z),float2(12.9898,78.233)))*43758.5453)*glassApertureCameraTAU;
    for(int i=0;i<64;i++) {
        float fi=float(i)+0.5,rr=sqrt(fi/64.0),th=fi*2.39996323+jit;
        float2 off=float2(cos(th),sin(th))*rr*glassApertureCameraNonagon(th,F.ring.y)*c0;
        float2 q=s+off;
        float3 col=glassApertureCameraPhoto(q,photo,F);
        float cq=glassApertureCameraCoc(glassApertureCameraDepth(q,F),F);
        float reach=smoothstep(length(off)-1.0,length(off)+1.0,max(cq,c0*0.5));
        float lum=dot(col,float3(0.3,0.55,0.15));
        float w=reach*(1.0+14.0*max(lum-0.42,0.0));
        acc+=col*w; wsum+=w;
    }
    return acc/max(wsum,1e-4);
}
static float glassApertureCameraStudio(float2 n) { return 0.55+0.45*sin(atan2(n.y,n.x)*2.0+0.6); }
vertex GlassApertureCameraVertex glassApertureCameraVertex(uint vi [[vertex_id]]) {
    float2 uv=float2(float((vi<<1u)&2u),float(vi&2u));
    return {float4(uv*float2(2.0,-2.0)+float2(-1.0,1.0),0.0,1.0),uv};
}
fragment float4 glassApertureCameraFragment(GlassApertureCameraVertex v [[stage_in]],texture2d<float> photo [[texture(0)]],constant GlassApertureCameraUniforms& F [[buffer(0)]]) {
    float2 s=v.pos.xy/F.view.z;
    if(s.y>F.cam.x) return float4(0.0);
    float3 col=glassApertureCameraDof(s,photo,F);
    float show=F.iris.x,snap=F.iris.y;
    if(show>0.001 || snap>0.001) {
        float2 p=s-F.iris.zw;
        float R=F.ring.x,r=length(p),th=atan2(p.y,p.x);
        float u=log(F.cam.z/1.4)/log(16.0/1.4);
        float open=R*mix(0.86,0.2,u)*(1.0-snap);
        float turn=F.ring.y+u*0.9+snap*0.5;
        float edge=open*glassApertureCameraNonagon(th,turn),a=max(show,snap);
        if(r<R+9.0) {
            if(r>edge && r<R) {
                float k=glassApertureCameraTAU/9.0,bent=th-turn-1.2*(r-edge)/R;
                float f=bent/k-floor(bent/k),idx=floor(bent/k);
                float3 refr=glassApertureCameraPhoto(s-p/max(r,1.0)*10.0*(1.0-(r-edge)/R),photo,F);
                float shade=0.22+0.06*sin(idx*2.3);
                float3 g=refr*shade*glassApertureCameraLin(float3(0.85,0.9,1.0));
                g+=float3(0.9,0.95,1.0)*0.12*pow(max(sin(th+0.8),0.0),6.0);
                g+=float3(1.0)*0.5*exp(-glassApertureCameraSq(f/0.012))*smoothstep(edge,edge+10.0,r);
                g*=1.0-0.6*exp(-glassApertureCameraSq((1.0-f)/0.02));
                g+=float3(1.0,0.97,0.92)*0.6*exp(-glassApertureCameraSq((r-edge)/1.2));
                col=mix(col,g,a);
            } else if(r>=R) {
                float2 n=p/max(r,1.0);
                float3 m=float3(0.42,0.43,0.46)*glassApertureCameraStudio(n)+float3(0.15)*fract(sin(r*91.0)*437.0)*0.3;
                col=mix(col,m*m,a);
            } else {
                col*=1.0-0.25*a*smoothstep(edge*0.6,edge,r);
            }
        }
        col*=1.0-0.25*show*smoothstep(R+8.0,R+40.0,r);
    }
    return float4(pow(clamp(col,float3(0),float3(1)),float3(1.0/2.2)),1.0);
}
