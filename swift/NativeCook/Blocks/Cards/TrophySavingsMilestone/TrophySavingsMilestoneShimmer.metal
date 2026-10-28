#include <metal_stdlib>
using namespace metal;
[[ stitchable ]] half4 trophyPaperApplauseShimmer(float2 xy, half4 source, float sweep, float strength) {
    float curvedFace=(xy.x-195.5)*(xy.x-195.5)*0.0007;
    float distanceToBand=xy.x+0.28*(xy.y-160.0)+curvedFace-sweep;
    float narrow=distanceToBand/3.4, broad=distanceToBand/9.0;
    float core=exp(-0.5*narrow*narrow), shoulder=exp(-0.5*broad*broad);
    float stemFalloff=1.0-0.18*smoothstep(218.0,266.0,xy.y);
    float alpha=strength*stemFalloff*(0.50*core+0.14*shoulder);
    float3 gold=mix(float3(1.0,0.84,0.30),float3(1.0,0.98,0.82),core);
    return half4(half3(gold*alpha),half(alpha))*source.a;
}
