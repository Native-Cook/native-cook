#include <metal_stdlib>
#include <SwiftUI/SwiftUI.h>
using namespace metal;

static float VelvetNapStudioSmooth(float x) { x=clamp(x,0.0f,1.0f);return x*x*(3.0f-2.0f*x); }
static float VelvetNapStudioSegment(float t,float a,float b) {return VelvetNapStudioSmooth((t-a)/(b-a));}
static float4 VelvetNapStudioPath(device const void *data,float2 p) {
    device const float4 *grid=(device const float4 *)data;
    float2 q=clamp(p/float2(307,274)*63.0f,0.0f,63.0f);
    int2 a=int2(floor(q)),b=min(a+1,int2(63));float2 f=fract(q);
    return mix(mix(grid[a.y*64+a.x],grid[a.y*64+b.x],f.x),mix(grid[b.y*64+a.x],grid[b.y*64+b.x],f.x),f.y);
}
static float VelvetNapStudioRead(device const void *data,int i) {
    device const uchar4 *grid=(device const uchar4 *)data;uchar4 raw=grid[i];
    int n=int(raw.x)+int(raw.y)*256;
    return n>=32768 ? 1.0f+float(n-32768)/32767.0f : 1.0f-float(32768-n)/32768.0f;
}
static float VelvetNapStudioField(device const void *data,float2 p) {
    float2 q=clamp(p/float2(307,274)*63.0f,0.0f,63.0f);
    int2 a=int2(floor(q)),b=min(a+1,int2(63));float2 f=fract(q);
    return mix(mix(VelvetNapStudioRead(data,a.y*64+a.x),VelvetNapStudioRead(data,a.y*64+b.x),f.x),mix(VelvetNapStudioRead(data,b.y*64+a.x),VelvetNapStudioRead(data,b.y*64+b.x),f.x),f.y);
}
static float VelvetNapStudioAuthor(float4 path,float time,float pathLength) {
    if(time<=1.0f||time>=7.55f)return 1.0f;
    float taper=VelvetNapStudioSmooth((path.x*pathLength+16.0f)/16.0f)*VelvetNapStudioSmooth(((1.0f-path.x)*pathLength+16.0f)/16.0f);
    float band=(1.0f-VelvetNapStudioSegment(path.y,20,33))*taper;
    float reverseBand=(1.0f-VelvetNapStudioSegment(path.y,21,34))*taper;
    float forwardLead=sqrt(max(0.0f,33.0f*33.0f-path.y*path.y))/pathLength;
    float reverseLead=sqrt(max(0.0f,34.0f*34.0f-path.y*path.y))/pathLength;
    float forwardArrival=1.0f+1.76f*(.5f-sin(asin(clamp(1.0f-2.0f*(path.x-forwardLead),-1.0f,1.0f))/3.0f));
    float reverseArrival=3.0f+1.76f*(.5f-sin(asin(clamp(1.0f-2.0f*(1.0f-path.x-reverseLead),-1.0f,1.0f))/3.0f));
    float forward=VelvetNapStudioSegment(time,forwardArrival,forwardArrival+.09f);
    float backward=VelvetNapStudioSegment(time,reverseArrival,reverseArrival+.09f);
    float light=clamp((cos(path.z)*.8f-sin(path.z)*.6f)/.85f,-1.0f,1.0f);
    float bright=(.03f+.45f*light)*band*forward;
    float dark=(.03f-.45f*light)*reverseBand*backward;
    float relax=1.0f-VelvetNapStudioSegment(time,5.75f+path.w,7.55f);
    return 1.0f+(bright*(1.0f-backward)+dark)*relax;
}
[[ stitchable ]] half4 VelvetNapStudioNap(float2 position,SwiftUI::Layer layer,
    device const void *pathData,int pathBytes,
    device const void *fieldA,int bytesA,device const void *fieldB,int bytesB,device const void *fieldC,int bytesC,
    float4 times,float4 weights,float3 tint) {
    half4 base=layer.sample(position);
    if(base.a<=0)return base;
    float4 path=VelvetNapStudioPath(pathData,position);
    float delta=(VelvetNapStudioAuthor(path,times.x,times.w)-1.0f)*weights.x;
    delta+=(VelvetNapStudioField(fieldA,position)-1.0f)*weights.y;
    delta+=(VelvetNapStudioField(fieldB,position)-1.0f)*weights.z;
    delta+=(VelvetNapStudioField(fieldC,position)-1.0f)*weights.w;
    float ratio=clamp(1.0f+delta*times.y,.53f,1.55f);
    float dark=clamp((1.0f-ratio)/.42f,0.0f,1.0f);
    // A negative lay increases the contrast of the cached fine fibers by 12%.
    float3 color=float3(base.rgb)/max(float(base.a),.00001f);
    float theta=.6f+.52f*sin(.043f*position.x+.019f*position.y)+.35f*sin(.028f*position.y-.016f*position.x);
    float2 normal=float2(-sin(theta),cos(theta))*.6f;
    half4 n0=layer.sample(position-normal),n1=layer.sample(position+normal);
    float3 c0=n0.a>.95h ? float3(n0.rgb)/float(n0.a):color;
    float3 c1=n1.a>.95h ? float3(n1.rgb)/float(n1.a):color;
    color=(color+(color-(c0+c1)*.5f)*(.12f*dark))*ratio;
    color*=tint;
    return half4(half3(clamp(color,0.0f,1.0f))*base.a,base.a);
}
