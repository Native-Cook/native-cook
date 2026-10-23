import { Skia } from '@shopify/react-native-skia';

export const SOURCE = `
uniform shader artwork;
uniform shader pathData;
uniform shader fields;
uniform float4 times;
uniform float4 weights;
uniform float3 tint;
float sm(float x){x=clamp(x,0.0,1.0);return x*x*(3.0-2.0*x);}
float seg(float t,float a,float b){return sm((t-a)/(b-a));}
float3 recoverBytes(half3 c){
 float3 linear=max(float3(toLinearSrgb(c)),float3(0));
 return mix(linear*12.92,1.055*pow(linear,float3(1.0/2.4))-.055,step(.0031308,linear));
}
float3 encodedSrgbToWorking(float3 c){
 float3 linear=mix(c/12.92,pow((c+.055)/1.055,float3(2.4)),step(.04045,c));
 return fromLinearSrgb(linear);
}
float decode32(float2 q){
 float3 lo=floor(recoverBytes(pathData.eval(q).rgb)*255.0+.5);
 float hi=floor(recoverBytes(pathData.eval(q+float2(1,0)).rgb).r*255.0+.5);
 float signv=hi>=128.0?-1.0:1.0,exponent=mod(hi,128.0)*2.0+floor(lo.z/128.0);
 float mantissa=mod(lo.z,128.0)*65536.0+lo.y*256.0+lo.x;
 if(exponent<.5)return signv*exp2(-126.0)*(mantissa/8388608.0);
 return signv*exp2(exponent-127.0)*(1.0+mantissa/8388608.0);
}
float4 cell(float2 p){float2 q=float2(p.x*8.0+.5,p.y+.5);return float4(decode32(q),decode32(q+float2(2,0)),decode32(q+float2(4,0)),decode32(q+float2(6,0)));}
float4 pathAt(float2 p){
 float2 q=clamp(p/float2(307,274)*63.0,0.0,63.0),a=floor(q),b=min(a+1.0,float2(63)),f=fract(q);
 return mix(mix(cell(a),cell(float2(b.x,a.y)),f.x),mix(cell(float2(a.x,b.y)),cell(b),f.x),f.y);
}
float fieldCell(float2 p,float row){float2 b=floor(recoverBytes(fields.eval(float2(p.x+.5,p.y+row*64.0+.5)).rgb).rg*255.0+.5);float n=b.x+b.y*256.0;return n>=32768.0?1.0+(n-32768.0)/32767.0:1.0-(32768.0-n)/32768.0;}
float fieldAt(float2 p,float row){
 float2 q=clamp(p/float2(307,274)*63.0,0.0,63.0),a=floor(q),b=min(a+1.0,float2(63)),f=fract(q);
 return mix(mix(fieldCell(a,row),fieldCell(float2(b.x,a.y),row),f.x),mix(fieldCell(float2(a.x,b.y),row),fieldCell(b,row),f.x),f.y);
}
float author(float4 p,float time,float length){
 if(time<=1.0||time>=7.55)return 1.0;
 float taper=sm((p.x*length+16.0)/16.0)*sm(((1.0-p.x)*length+16.0)/16.0);
 float band=(1.0-seg(p.y,20.0,33.0))*taper,reverseBand=(1.0-seg(p.y,21.0,34.0))*taper;
 float fLead=sqrt(max(0.0,1089.0-p.y*p.y))/length,rLead=sqrt(max(0.0,1156.0-p.y*p.y))/length;
 float fArrival=1.0+1.76*(.5-sin(asin(clamp(1.0-2.0*(p.x-fLead),-1.0,1.0))/3.0));
 float rArrival=3.0+1.76*(.5-sin(asin(clamp(1.0-2.0*(1.0-p.x-rLead),-1.0,1.0))/3.0));
 float forward=seg(time,fArrival,fArrival+.09),backward=seg(time,rArrival,rArrival+.09);
 float light=clamp((cos(p.z)*.8-sin(p.z)*.6)/.85,-1.0,1.0);
 float bright=(.03+.45*light)*band*forward,dark=(.03-.45*light)*reverseBand*backward;
 return 1.0+(bright*(1.0-backward)+dark)*(1.0-seg(time,5.75+p.w,7.55));
}
half4 main(float2 p){
 half4 base=artwork.eval(p*2.0);if(base.a<=0.0)return base;
 float delta=(author(pathAt(p),times.x,times.z)-1.0)*weights.x;
 if(weights.y!=0.0)delta+=(fieldAt(p,0.0)-1.0)*weights.y;
 if(weights.z!=0.0)delta+=(fieldAt(p,1.0)-1.0)*weights.z;
 if(weights.w!=0.0)delta+=(fieldAt(p,2.0)-1.0)*weights.w;
 float ratio=clamp(1.0+delta*times.y,.53,1.55),dark=clamp((1.0-ratio)/.42,0.0,1.0);
 // Ordinary artwork math uses encoded sRGB, then converts back to the destination.
 // Numerical children alone are rounded to original bytes above.
 float3 color=recoverBytes(base.rgb/max(float(base.a),.00001));
 float theta=.6+.52*sin(.043*p.x+.019*p.y)+.35*sin(.028*p.y-.016*p.x);
 float2 normal=float2(-sin(theta),cos(theta))*.6;
 half4 n0=artwork.eval((p-normal)*2.0),n1=artwork.eval((p+normal)*2.0);
 float3 c0=n0.a>.95?recoverBytes(n0.rgb/float(n0.a)):color,c1=n1.a>.95?recoverBytes(n1.rgb/float(n1.a)):color;
 color=(color+(color-(c0+c1)*.5)*(.12*dark))*ratio*tint;
 return half4(half3(encodedSrgbToWorking(clamp(color,0.0,1.0)))*base.a,base.a);
}`;
export const EFFECT = Skia.RuntimeEffect.Make(SOURCE);
if (!EFFECT) throw new Error('Velvet Nap Studio: material shader failed to compile.');
