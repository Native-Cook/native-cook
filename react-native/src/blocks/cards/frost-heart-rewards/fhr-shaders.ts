import { FHR_THEME as T } from './fhr-theme';

// Module-level shader programs; every animated uniform is a native tgfx value slot.
const COLOR = `
uniform float p3;
float sat(float x) { return clamp(x,0.0,1.0); }
float sq(float x) { return x*x; }
float hash2(float2 p) { return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453); }
float noise2(float2 p) {
 float2 i=floor(p); float2 f=fract(p); f=f*f*(3.0-2.0*f);
 return mix(mix(hash2(i),hash2(i+float2(1,0)),f.x),mix(hash2(i+float2(0,1)),hash2(i+1.0),f.x),f.y);
}
float3 srgbP3(float3 c) {
 float3 l=pow(clamp(c,0.0,1.0),float3(2.2));
 float3 m=float3(dot(l,float3(0.8225,0.1774,0)),dot(l,float3(0.0332,0.9669,0)),dot(l,float3(0.0171,0.0724,0.9108)));
 return p3>0.5?pow(max(m,float3(0)),float3(1.0/2.2)):c;
}
float3 tint(float h) { return mix(float3(0.98,0.10,0.83),float3(0.53,0.24,1.0),h); }
`;

export const RAYS_SKSL = COLOR + `
uniform float time;
uniform float charge;
uniform float burst;
uniform float hue;
uniform float angle;
uniform float2 tilt;
half4 main(float2 p) {
 float2 origin=mix(float2(177,203),float2(175,278),charge);
 float2 q=p-origin-tilt*0.35;
 float a=mod(atan(q.y,q.x)+angle+3.14159265,6.2831853)-3.14159265;
 float beam=0.0;
 ${T.rays.angles.map((angle,i)=>`{float d=abs(a-(${(angle*Math.PI/180).toFixed(7)}));d=min(d,6.2831853-d);beam+=${T.rays.strengths[i].toFixed(3)}*exp(-0.5*sq(d/(${(0.025+(i%3)*0.009).toFixed(3)}*(1.0-charge*0.70+-burst*0.10))));}`).join('\n')}
 float dist=length(q);
 beam*=sat(dist/145.0)*exp(-dist/440.0)*sat((510.0-p.y)/270.0);
 float lum=beam*0.082*(1.0+charge*1.6+burst*3.0);
 float3 rgb=float3(0.07451)+float3(lum);
 float2 glow=(p-float2(191,370))/float2(57,32);
 rgb+=mix(float3(0.32,0.1412,0.36),float3(0.22,0.14,0.44),hue)*exp(-0.5*dot(glow,glow))*(1.0+charge*0.5+burst*0.5);
 float2 rim=(p-float2(266,345))/float2(29,43);
 rgb+=mix(float3(0.36,0.09,0.31),float3(0.16,0.07,0.29),hue)*exp(-0.5*dot(rim,rim));
 rgb+=(hash2(floor(p*3.0))-0.5)*0.002;
 return half4(srgbP3(rgb),1);
}
`;

/** Standalone scalar uniforms avoid the native GLSL helper namespace and sampler path. */
export const SLAB_SKSL = `
uniform float time;
uniform float spotlight;
uniform float polish;
uniform float core;
half4 main(float2 p) {
 float2 center=float2(187.5,291.0);
 float2 q=abs(p-center)-float2(33.5,3.0);
 float sd=length(max(q,0.0))+min(max(q.x,q.y),0.0)-38.0;
 float a=1.0-smoothstep(-0.6,0.5,sd);
 float v=clamp((p.y-250.0)/82.0,0.0,1.0);
 float3 c=mix(float3(1.0,0.085,0.84),float3(0.68,0.025,0.62),pow(v,0.9));
 float grain=fract(sin(dot(floor(p*1.5),float2(127.1,311.7)))*43758.5453)-0.5;
 c+=float3(grain*0.012);
 float rim=exp(-pow((sd+1.7)/1.2,2.0));
 float top=1.0-smoothstep(0.24,0.62,v);
 c+=float3(0.94,0.78,0.91)*rim*(0.10+top*0.5);
 c-=float3(0.13,0.045,0.10)*exp(-pow((sd+3.5)/2.8,2.0))*v;
 float shine=exp(-pow((p.y-262.0+(p.x-center.x)*0.12)/8.0,2.0));
 c+=float3(0.43,0.25,0.38)*shine*0.30;
 float sweep=exp(-pow((p.x-center.x-sin(time*0.6283185)*68.0)/13.0,2.0));
 c+=float3(0.21,0.15,0.19)*sweep*(spotlight*0.32+polish*0.44);
 c+=float3(0.15,0.11,0.13)*core*exp(-dot((p-center)/float2(78,43),(p-center)/float2(78,43)));
 return half4(clamp(c,0.0,1.0)*a,a);
}
`;

/** A warm gallery lamp and 25% edge vignette light the hero; the fixed UI composites above. */
export const SPOTLIGHT_SKSL = `
uniform float light;
half4 main(float2 p) {
 float2 q=(p-float2(175,270))/float2(205,245);
 float boundary=smoothstep(96.0,130.0,p.y)*(1.0-smoothstep(395.0,480.0,p.y));
 float vignette=smoothstep(0.28,1.08,length(q))*0.25*light*boundary;
 float coneWidth=28.0+max(0.0,p.y-76.0)*0.47;
 float cone=(1.0-smoothstep(coneWidth*0.52,coneWidth,abs(p.x-174.0)));
 cone*=smoothstep(78.0,142.0,p.y)*(1.0-smoothstep(300.0,442.0,p.y));
 float lamp=cone*0.11*light*boundary;
 float alpha=vignette+lamp*(1.0-vignette);
 float3 color=float3(1.0,0.955,0.865)*lamp*(1.0-vignette);
 return half4(color,alpha);
}
`;

/** A silver tag has a dark engraved edge, fine brushed grain and one moving key light. */
export const TAG_SKSL = `
uniform float light;
uniform float time;
uniform float polish;
uniform float platinum;
float tagHash(float2 p) { return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453); }
half4 main(float2 p) {
 float v=clamp(dot(p-float2(295,144),float2(67,86))/11885.0,0.0,1.0);
 float3 purple=mix(float3(0.761,0.408,0.953),float3(0.631,0.286,0.890),min(v/0.5,1.0));
 purple=mix(purple,float3(0.690,0.294,0.894),max(0.0,(v-0.5)*2.0));
 float brush=(tagHash(float2(floor(p.x*0.14),floor(p.y*2.6)))-0.5)*mix(0.065,0.018,platinum);
 float sweep=exp(-pow((p.x-mix(282.0,371.0,clamp((time-6.65)/0.6,0.0,1.0)))/7.0,2.0));
 float band=0.5+0.5*cos(v*12.0-0.7);
 float3 steel=mix(float3(0.25,0.31,0.41),float3(0.65,0.72,0.81),band);
 float3 bright=mix(float3(0.77,0.80,0.84),float3(0.99,0.98,0.95),band);
 bright+=float3(0.045,0.022,-0.018)*pow(abs(2.0*v-1.0),6.0);
 float3 metal=mix(steel,bright,platinum);
 metal+=float3(brush)+float3(0.22,0.24,0.27)*sweep*(0.35+polish*0.5);
 return half4(mix(purple,clamp(metal,0.0,1.0),light),1.0);
}
`;

/** Soft refracted light stays below the cutting edges and never becomes an explosion. */
export const CAUSTIC_SKSL = `
uniform float time;
uniform float light;
uniform float display;
uniform float polish;
half4 main(float2 p) {
 float2 q=(p-float2(177,332))/float2(124,39);
 float mask=exp(-dot(q,q)*1.8);
 float a=sin(p.x*0.053+sin(p.y*0.095+time*0.6283185)*2.0);
 float b=cos(p.y*0.108-p.x*0.024-time*1.256637);
 float line=pow(max(0.0,1.0-abs(a+b)*0.58),12.0);
 float alpha=mask*line*light*(0.07+display*0.04+polish*0.055);
 return half4(float3(0.78,0.74,1.0)*alpha,alpha);
}
`;


/** Alpha-only world matte keeps image fading independent of the image fit and rotation. */
export const SPRITE_MASK_SKSL = `
uniform float floorY;
uniform float fade;
half4 main(float2 p) {
 float a=1.0-smoothstep(floorY,floorY+fade,p.y);
 return half4(a,a,a,a);
}
`;

/** A soft world-space color front grows from the gem; reduced motion uses only a fade. */
export const WAVE_MASK_SKSL = `
uniform float wave;
uniform float reduced;
half4 main(float2 p) {
 float radius=mix(-24.0,230.0,wave);
 float a=1.0-smoothstep(radius-18.0,radius+18.0,distance(p,float2(175,278)));
 if(wave<=0.0)a=0.0;
 if(wave>=1.0)a=1.0;
 if(reduced>0.5)a=wave;
 return half4(a,a,a,a);
}
`;
