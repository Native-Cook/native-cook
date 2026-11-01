#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Skia's runtime effects in the reference render into a Display P3 surface.
// Convert that output to the sRGB working space of SwiftUI's non-linear drawing group.
float3 holoCashCardP3ToSRGB(float3 c) {
    float3 linear=select(c/12.92,pow((c+0.055)/1.055,float3(2.4)),c>0.04045);
    float3 rgb=float3(dot(linear,float3(1.2249402,-0.2249402,0.0)),
                      dot(linear,float3(-0.042057,1.042057,0.0)),
                      dot(linear,float3(-0.0196376,-0.078636,1.0982736)));
    rgb=max(rgb,0.0);
    return select(rgb*12.92,1.055*pow(rgb,float3(1.0/2.4))-0.055,rgb>0.0031308);
}

float3 holoCashCardHue(float h) {
    float3 a=float3(1.0,0.70,0.90), b=float3(0.72,0.66,1.0), c=float3(0.62,0.92,1.0);
    float3 d=float3(0.78,1.0,0.84), e=float3(1.0,0.95,0.68);
    float x=fract(h)*5.0;
    if(x<1.0) return mix(a,b,x);
    if(x<2.0) return mix(b,c,x-1.0);
    if(x<3.0) return mix(c,d,x-2.0);
    if(x<4.0) return mix(d,e,x-3.0);
    return mix(e,a,x-4.0);
}
// The text paint's shader coordinates are local to drawText, before translate/scale.
[[stitchable]] half4 holoCashCardFoil(float2 p, half4 source, float2 size, float2 tilt, float glow, float sx) {
    float2 xy=float2((p.x-14.0)/sx, p.y-size.y*0.69);
    float2 uv=xy/size;
    float y=uv.y+tilt.x*0.9;
    float chrome=0.86-0.62*exp(-pow((y-0.56)*6.5,2.0))+0.14*smoothstep(0.62,0.95,y);
    float3 foil=holoCashCardHue(uv.x*0.9+uv.y*0.6+tilt.y*1.4+tilt.x*0.8);
    float3 col=mix(float3(chrome),foil*(0.55+0.5*chrome),0.62);
    float band=exp(-pow((uv.x+uv.y*0.45-0.62-tilt.y*1.6)*9.0,2.0));
    col+=band*(0.5+0.2*glow);
    col=mix(col,foil*1.08,0.28*glow);
    return half4(half3(holoCashCardP3ToSRGB(min(col,1.0)))*source.a,source.a);
}
[[stitchable]] half4 holoCashCardRim(float2 xy, half4 source, float2 size, float2 tilt) {
    float2 uv=xy/size;
    float h=fract(uv.x*0.7-uv.y*0.4+tilt.y*1.2+tilt.x);
    float3 c=0.5+0.5*cos(6.2831*(h+float3(0.0,0.33,0.67)));
    return half4(half3(holoCashCardP3ToSRGB(mix(c,float3(1.0),0.45)))*source.a,source.a);
}
[[stitchable]] half4 holoCashCardLines(float2 xy, half4 source, float2 size, float2 tilt, float glow) {
    float2 uv=xy/size;
    float d=xy.x*0.82+xy.y*0.57;
    float line=smoothstep(0.55,1.0,sin(d*1.15));
    float h=fract(uv.x*0.6+uv.y*0.3+tilt.y*1.3+tilt.x*0.9);
    float3 c=0.5+0.5*cos(6.2831*(h+float3(0.0,0.33,0.67)));
    float band=exp(-pow((uv.x+uv.y*0.45-0.62-tilt.y*1.6)*3.0,2.0));
    float a=line*(0.05+0.22*band)*(1.0+0.6*glow)*float(source.a);
    // Composite before the gamut conversion, as Skia does in its P3 canvas.
    float3 bg=float3(0.0431373,0.0431373,0.0504);
    return half4(half3(holoCashCardP3ToSRGB(c*a+bg*(1.0-a)))*source.a,source.a);
}
