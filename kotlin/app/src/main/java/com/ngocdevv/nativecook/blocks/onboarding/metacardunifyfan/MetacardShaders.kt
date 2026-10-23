package com.ngocdevv.nativecook.blocks.onboarding.metacardunifyfan

/** Original SkSL equations, compiled by Android as AGSL. */
internal object MetacardShaders {
    const val PAGE = """

float sq(float v) { return v * v; }
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float2 turn(float2 p, float a) { return float2(cos(a)*p.x - sin(a)*p.y, sin(a)*p.x + cos(a)*p.y); }
float box(float2 p, float2 halfExtent, float radius) {
  float2 q = abs(p) - halfExtent + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float oct(float2 p, float apothem, float radius) {
  p = abs(p);
  // Exact convex intersection with a rounded offset of the inset octagon.
  float a = apothem - radius;
  float2 q = p;
  const float2 normal = float2(0.70710678);
  if (q.x < q.y) q = q.yx;
  float2 v = float2(a, a * 0.41421356);
  float2 edge = float2(-0.58578644 * a, 0.58578644 * a);
  float2 delta = q - v;
  float2 closest = delta - edge * clamp(dot(delta, edge) / dot(edge, edge), 0.0, 1.0);
  float diagonal = length(closest);
  float horizontal = length(float2(q.x-a, max(q.y-v.y,0.0)));
  float dist = min(diagonal, horizontal);
  float inside = max(max(p.x,p.y)-a, dot(p,normal)-a);
  return (inside < 0.0 ? -dist : dist) - radius;
}

uniform float pageMix;
uniform float3 frame;
half4 main(float2 xy) {
  float2 p = (xy - frame.xy) / frame.z;
  float lift = exp(-dot((p-float2(201,336))/float2(190,220),(p-float2(201,336))/float2(190,220)));
  float vignette = clamp(1.0 - length((p-float2(201,440))/float2(500,1000))*0.18,0.65,1.0);
  float3 c = float3(16.0,16.0,17.0)/255.0*pageMix*vignette;
  c += (1.0-pageMix)*lift*float3(2.3,3.3,5.0)/255.0;
  float pool = exp(-dot((p-float2(285,452))/float2(145,140),(p-float2(285,452))/float2(145,140)));
  c += pageMix*pool*float3(5.4,5.5,6.0)/255.0;
  c += (hash(floor(xy))-0.5)*3.0/255.0;
  return half4(max(c,0.0),1.0);
}

    """
    const val FAN = """

float sq(float v) { return v * v; }
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float2 turn(float2 p, float a) { return float2(cos(a)*p.x - sin(a)*p.y, sin(a)*p.x + cos(a)*p.y); }
float box(float2 p, float2 halfExtent, float radius) {
  float2 q = abs(p) - halfExtent + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float oct(float2 p, float apothem, float radius) {
  p = abs(p);
  // Exact convex intersection with a rounded offset of the inset octagon.
  float a = apothem - radius;
  float2 q = p;
  const float2 normal = float2(0.70710678);
  if (q.x < q.y) q = q.yx;
  float2 v = float2(a, a * 0.41421356);
  float2 edge = float2(-0.58578644 * a, 0.58578644 * a);
  float2 delta = q - v;
  float2 closest = delta - edge * clamp(dot(delta, edge) / dot(edge, edge), 0.0, 1.0);
  float diagonal = length(closest);
  float horizontal = length(float2(q.x-a, max(q.y-v.y,0.0)));
  float dist = min(diagonal, horizontal);
  float inside = max(max(p.x,p.y)-a, dot(p,normal)-a);
  return (inside < 0.0 ? -dist : dist) - radius;
}

uniform float2 centre;
uniform float angle;
uniform float scale;
uniform float firstScale;
uniform float lastScale;
uniform float opacity;
uniform float gloss;
uniform float brushed;
uniform float ao;
uniform float press;
uniform float seam;
uniform float isLast;
uniform float3 first;
uniform float3 last;
uniform float handover;
uniform float shadowHandover;
float seamMask(float2 xy) {
  if (seam < 0.5) return 1.0;
  float d = box(turn(xy-last.xy,-last.z)/lastScale,float2(72,45.5),7.3);
  // Include all of 7's antialias fringe. Reusing its fractional alpha would composite it twice
  // and leave a dark hairline through card 0 (A over B over A is not an opaque replacement).
  return (1.0-smoothstep(0.6,1.0,d))*handover;
}

uniform shader face;
half4 main(float2 xy) {
  float2 p = turn(xy-centre,-angle)/scale;
  float d = box(p,float2(72,45.5),7.3);
  float alpha = (1.0-smoothstep(-0.3,0.3,d))*opacity*seamMask(xy);
  if (alpha < 0.001) return half4(0);
  float3 color = face.eval((p/float2(144,91)+0.5)*float2(512,324)).rgb;
  float2 lit = turn(float2(0.45,-0.89),-angle);
  float sheen = exp(-sq((p.x*lit.x+p.y*lit.y-22.0*sin(angle-0.4))/42.0));
  color += gloss * sheen * float3(0.25,0.26,0.28);
  color += brushed*(hash(float2(floor(p.x*2.0),floor(p.y*14.0)))-0.5)*0.024;
  color *= 1.0-0.30*ao*(1.0-smoothstep(-72,-42.75,p.x));
  if (d < -1.2) return half4(color*(1.0+0.05*press)*alpha,alpha);
  float e = 0.15;
  float2 n = normalize(float2(box(p+float2(e,0),float2(72,45.5),7.3)-box(p-float2(e,0),float2(72,45.5),7.3),
                              box(p+float2(0,e),float2(72,45.5),7.3)-box(p-float2(0,e),float2(72,45.5),7.3)) + float2(0.00001));
  float rim = 1.0-smoothstep(0.15,0.8,abs(d+0.3));
  float facing = dot(n,lit);
  color *= 1.0-rim*max(-facing,0.0)*0.42;
  color += rim*max(facing,0.0)*0.20;
  color *= 1.0+0.05*press;
  return half4(color*alpha,alpha);
}

    """
    const val FAN_SHADOW = """

float sq(float v) { return v * v; }
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float2 turn(float2 p, float a) { return float2(cos(a)*p.x - sin(a)*p.y, sin(a)*p.x + cos(a)*p.y); }
float box(float2 p, float2 halfExtent, float radius) {
  float2 q = abs(p) - halfExtent + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float oct(float2 p, float apothem, float radius) {
  p = abs(p);
  // Exact convex intersection with a rounded offset of the inset octagon.
  float a = apothem - radius;
  float2 q = p;
  const float2 normal = float2(0.70710678);
  if (q.x < q.y) q = q.yx;
  float2 v = float2(a, a * 0.41421356);
  float2 edge = float2(-0.58578644 * a, 0.58578644 * a);
  float2 delta = q - v;
  float2 closest = delta - edge * clamp(dot(delta, edge) / dot(edge, edge), 0.0, 1.0);
  float diagonal = length(closest);
  float horizontal = length(float2(q.x-a, max(q.y-v.y,0.0)));
  float dist = min(diagonal, horizontal);
  float inside = max(max(p.x,p.y)-a, dot(p,normal)-a);
  return (inside < 0.0 ? -dist : dist) - radius;
}

uniform float2 centre;
uniform float angle;
uniform float scale;
uniform float firstScale;
uniform float lastScale;
uniform float opacity;
uniform float gloss;
uniform float brushed;
uniform float ao;
uniform float press;
uniform float seam;
uniform float isLast;
uniform float3 first;
uniform float3 last;
uniform float handover;
uniform float shadowHandover;
float seamMask(float2 xy) {
  if (seam < 0.5) return 1.0;
  float d = box(turn(xy-last.xy,-last.z)/lastScale,float2(72,45.5),7.3);
  // Include all of 7's antialias fringe. Reusing its fractional alpha would composite it twice
  // and leave a dark hairline through card 0 (A over B over A is not an opaque replacement).
  return (1.0-smoothstep(0.6,1.0,d))*handover;
}

uniform shader sprite;
half4 main(float2 xy) {
  float2 p = turn(xy-centre-float2(-1.8,3.56),-angle)/scale;
  float2 uv = (p/float2(198,144.92592592592592)+0.5)*float2(528,387);
  if (uv.x<0.0 || uv.x>528.0 || uv.y<0.0 || uv.y>387.0) return half4(0);
  float clipAlpha = seam < 0.5 ? 1.0 : (1.0-smoothstep(-0.25,0.25,box(turn(xy-last.xy,-last.z)/lastScale,float2(72,45.5),7.3)))*handover;
  float a = sprite.eval(uv).a*opacity*clipAlpha;
  if (seam > 0.5) a *= shadowHandover;
  // Card 7 cannot cast a shadow on card 0 while 0 owns their cyclic overlap.
  if (isLast > 0.5 && handover > 0.5) {
    float cover = 1.0-smoothstep(-0.3,0.3,box(turn(xy-first.xy,-first.z)/firstScale,float2(72,45.5),7.3));
    a *= 1.0-cover*shadowHandover;
  }
  return half4(0,0,0,a);
}

    """
    const val DARKNESS = """

uniform float amount;
half4 main(float2 p) {
  float a = p.y < 486.0 ? 0.45*clamp((p.y-396.0)/90.0,0.0,1.0) : mix(0.45,0.68,clamp((p.y-486.0)/79.0,0.0,1.0));
  return half4(0,0,0,a*amount);
}

    """
    const val BLOOM = """

float sq(float v) { return v * v; }
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float2 turn(float2 p, float a) { return float2(cos(a)*p.x - sin(a)*p.y, sin(a)*p.x + cos(a)*p.y); }
float box(float2 p, float2 halfExtent, float radius) {
  float2 q = abs(p) - halfExtent + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float oct(float2 p, float apothem, float radius) {
  p = abs(p);
  // Exact convex intersection with a rounded offset of the inset octagon.
  float a = apothem - radius;
  float2 q = p;
  const float2 normal = float2(0.70710678);
  if (q.x < q.y) q = q.yx;
  float2 v = float2(a, a * 0.41421356);
  float2 edge = float2(-0.58578644 * a, 0.58578644 * a);
  float2 delta = q - v;
  float2 closest = delta - edge * clamp(dot(delta, edge) / dot(edge, edge), 0.0, 1.0);
  float diagonal = length(closest);
  float horizontal = length(float2(q.x-a, max(q.y-v.y,0.0)));
  float dist = min(diagonal, horizontal);
  float inside = max(max(p.x,p.y)-a, dot(p,normal)-a);
  return (inside < 0.0 ? -dist : dist) - radius;
}

uniform float amount;
uniform float reach;
uniform float2 centre;
uniform float2 halfSize;
uniform float corner;
uniform float angle;
uniform float3 colors[8];
half4 main(float2 xy) {
  if (amount <= 0.0) return half4(0);
  float2 p = xy-centre;
  float d = max(box(p,halfSize,corner),0.0);
  // Light leaks from a rounded slab: no stroke, radius shell, or hard cutoff.
  float falloff = exp(-sq(d/reach));
  float theta = atan(p.x,-p.y)-angle;
  float index = fract(theta/6.28318531+1.0)*8.0;
  float3 c = float3(0);
  for (int i=0;i<8;i++) {
    float delta = abs(index-float(i)); delta=min(delta,8.0-delta);
    float weight = delta < 1.0 ? 0.5+0.5*cos(delta*3.14159265) : 0.0;
    c += colors[i]*weight;
  }
  float alpha = amount*falloff;
  float noise = (hash(floor(xy*3.0))-0.5)*2.0/255.0;
  return half4(max(c+noise,0.0)*alpha,alpha);
}

    """
    const val HERO_SHADOW = """

float sq(float v) { return v * v; }
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float2 turn(float2 p, float a) { return float2(cos(a)*p.x - sin(a)*p.y, sin(a)*p.x + cos(a)*p.y); }
float box(float2 p, float2 halfExtent, float radius) {
  float2 q = abs(p) - halfExtent + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float oct(float2 p, float apothem, float radius) {
  p = abs(p);
  // Exact convex intersection with a rounded offset of the inset octagon.
  float a = apothem - radius;
  float2 q = p;
  const float2 normal = float2(0.70710678);
  if (q.x < q.y) q = q.yx;
  float2 v = float2(a, a * 0.41421356);
  float2 edge = float2(-0.58578644 * a, 0.58578644 * a);
  float2 delta = q - v;
  float2 closest = delta - edge * clamp(dot(delta, edge) / dot(edge, edge), 0.0, 1.0);
  float diagonal = length(closest);
  float horizontal = length(float2(q.x-a, max(q.y-v.y,0.0)));
  float dist = min(diagonal, horizontal);
  float inside = max(max(p.x,p.y)-a, dot(p,normal)-a);
  return (inside < 0.0 ? -dist : dist) - radius;
}

uniform float2 centre;
uniform float2 halfSize;
uniform float morph;
uniform float shadowBlur;
half4 main(float2 xy) {
  float2 p = xy-centre-mix(float2(-2,3),float2(-12,18),clamp((shadowBlur-10.0)/26.0,0.0,1.0));
  float d = mix(oct(p,62.0,5.5),box(p,halfSize,13.0),morph);
  float a = exp(-sq(max(d,0.0)/shadowBlur))*0.35;
  return half4(0,0,0,a);
}

    """
    const val HERO = """

float sq(float v) { return v * v; }
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float2 turn(float2 p, float a) { return float2(cos(a)*p.x - sin(a)*p.y, sin(a)*p.x + cos(a)*p.y); }
float box(float2 p, float2 halfExtent, float radius) {
  float2 q = abs(p) - halfExtent + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float oct(float2 p, float apothem, float radius) {
  p = abs(p);
  // Exact convex intersection with a rounded offset of the inset octagon.
  float a = apothem - radius;
  float2 q = p;
  const float2 normal = float2(0.70710678);
  if (q.x < q.y) q = q.yx;
  float2 v = float2(a, a * 0.41421356);
  float2 edge = float2(-0.58578644 * a, 0.58578644 * a);
  float2 delta = q - v;
  float2 closest = delta - edge * clamp(dot(delta, edge) / dot(edge, edge), 0.0, 1.0);
  float diagonal = length(closest);
  float horizontal = length(float2(q.x-a, max(q.y-v.y,0.0)));
  float dist = min(diagonal, horizontal);
  float inside = max(max(p.x,p.y)-a, dot(p,normal)-a);
  return (inside < 0.0 ? -dist : dist) - radius;
}

uniform shader chip;
uniform float2 centre;
uniform float2 halfSize;
uniform float octagon;
uniform float morph;
uniform float corner;
uniform float thickness;
uniform float camera;
uniform float3x3 rot;
uniform float3 mark;
uniform float markTurn;
uniform float chipAlpha;
uniform float lamination;
uniform float sweep;
uniform float tint;
uniform float2 studio;
uniform float3 colors[8];
float3 local(float3 v) { return float3(dot(rot[0],v),dot(rot[1],v),dot(rot[2],v)); }
float outline(float2 p) { return mix(oct(p,octagon,5.5),box(p,halfSize,corner),morph); }
float slab(float3 p) {
  float2 d = float2(outline(p.xy)+0.4,abs(p.z)-thickness*0.5+0.4);
  return min(max(d.x,d.y),0.0)+length(max(d,0.0))-0.4;
}
float3 normalAt(float3 p) {
  const float e = 0.12;
  return normalize(float3(slab(p+float3(e,0,0))-slab(p-float3(e,0,0)),
    slab(p+float3(0,e,0))-slab(p-float3(0,e,0)),slab(p+float3(0,0,e))-slab(p-float3(0,0,e))));
}
float logo(float2 at) {
  float2 p = turn((at-mark.xy)*24.0/mark.z,-markTurn);
  float outside = oct(p,10.5,1.0);
  float slot = box(turn(p,0.38397244),float2(6.25,3.95),1.5);
  return max(outside,-slot)*mark.z/24.0;
}
float3 spectrum(float t) {
  float x=clamp(t,0.0,0.9999)*7.0;
  float3 c=colors[0];
  for (int i=0;i<7;i++) { c=mix(c,colors[i+1],clamp(x-float(i),0.0,1.0)); }
  return c;
}
half4 main(float2 xy) {
  float2 screen = xy-centre;
  float3 origin=local(float3(0,0,camera));
  float3 ray=local(normalize(float3(screen,-camera)));
  // Intersect the object's z envelope first; the slab is thin, so don't march from the camera.
  float start=max(0.0,(thickness*0.5+0.6-origin.z)/ray.z);
  float travel=start;
  float3 p=origin+ray*travel;
  // A ray cannot enter through the side if its entire thin z envelope misses the outline.
  if (outline(p.xy) > (thickness+1.2)*length(ray.xy)/abs(ray.z)+0.7) return half4(0);
  for (int i=0;i<28;i++) {
    p=origin+ray*travel;
    float d=slab(p);
    if (d<0.07 || travel>start+420.0) break;
    travel+=max(d*0.85,0.035);
  }
  float d=slab(p);
  // March termination is a distance tolerance, not face coverage. Keep the interior
  // opaque so changes in iteration count cannot draw diagonal translucent facets.
  float faceDistance=outline(p.xy);
  float alpha=faceDistance < -0.65 ? 1.0 : 1.0-smoothstep(0.02,0.36,d);
  if(alpha<0.001 || travel>start+420.0) return half4(0);
  float3 n=faceDistance<-0.65 ? float3(0,0,1) : normalAt(p);
  float3 nw=rot*n, view=normalize(-rot*ray);
  float3 reflected=reflect(rot*ray,nw);
  float2 uv=p.xy/halfSize;
  float front=smoothstep(0.25,0.92,n.z);
  // Fixed studio environment, sampled by the reflected ray. The diagonal box boundary
  // traverses the face as the normal turns; there is no painted vertical face ramp.
  float env = reflected.y + 0.9*reflected.x;
  float broad = (1.0-smoothstep(studio.x-0.04,studio.x+0.04,env));
  broad *= exp(-sq((reflected.x+0.66)/0.8))*0.93;
  broad *= 1.0-smoothstep(0.15,0.95,uv.y);
  float coat = exp(-sq((env-studio.y)/0.020))*0.35;
  float second = exp(-sq((env+0.66)/0.10)-sq((reflected.x+0.48)/0.28));
  // A second, smaller overhead source is visible to the flat hub in the same environment.
  float overhead = exp(-sq((reflected.y+0.11)/0.10)-sq((reflected.x+0.10)/0.28));
  float3 color=float3(6.0,6.0,7.0)/255.0;
  color+=float3(56.0,56.0,61.0)/255.0*broad;
  color+=float3(138.0,138.0,146.0)/255.0*coat;
  color+=float3(16.0,16.0,18.0)/255.0*second;
  color+=float3(0.13,0.13,0.14)*overhead;
  color+=(hash(floor(p.xy*3.0))-0.5)*0.004;
  float nv=clamp(dot(nw,view),0.0,1.0);
  float fresnel=pow(1.0-nv,5.0);
  float lighting=max(dot(nw,normalize(float3(0.45,-0.89,0.6))),0.0);
  float sideRight = max(n.x,0.0);
  color=mix(float3(0.012)+sideRight*float3(0.32,0.32,0.34)+fresnel*0.022,color,front);
  // The shoulder is lit on the top/right; the bottom/left keeps its dark thickness.
  float edge=exp(-sq((outline(p.xy)+0.4)/0.52));
  color+=edge*(0.022+0.085*max(dot(normalize(float2(p.x/halfSize.x,p.y/halfSize.y)),float2(0.45,-0.89)),0.0));
  float band=exp(-sq((uv.x+0.28*uv.y-mix(-1.15,1.50,sweep))/0.192));
  color+=spectrum((uv.x+1.0)*0.5)*band*tint*0.45*front;
  float layer=clamp((p.z/thickness+0.5)*8.0,0.0,7.999);
  color=mix(color,spectrum(floor(layer)/7.0)*(0.5+lighting*0.5),lamination*(1.0-front)*0.9);
  if (front>0.0) {
    float logoAlpha=1.0-smoothstep(-0.20,0.20,logo(p.xy));
    color=mix(color,float3(0.957,0.957,0.949)*(0.89+0.08*broad+coat*0.18),logoAlpha*front);
    float2 facePoint=(p.xy/halfSize+1.0)*float2(152,96);
    float2 chipUV=(facePoint-float2(26,58))*4.0;
    if(chipUV.x>=0.0 && chipUV.x<=320.0 && chipUV.y>=0.0 && chipUV.y<=160.0) {
      half4 decal=chip.eval(chipUV);
      float seat=max(max(chip.eval(chipUV+float2(2,0)).a,chip.eval(chipUV-float2(2,0)).a),
        max(chip.eval(chipUV+float2(0,2)).a,chip.eval(chipUV-float2(0,2)).a));
      color=mix(color,float3(0.006),max(seat-decal.a,0.0)*chipAlpha*front);
      color=color*(1.0-decal.a*chipAlpha*front)+decal.rgb*chipAlpha*front*(0.88+0.1*broad+coat*0.8);
    }
  }
  return half4(max(color,0.0)*alpha,alpha);
}

    """
    const val HALO = """

float sq(float v) { return v * v; }
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float2 turn(float2 p, float a) { return float2(cos(a)*p.x - sin(a)*p.y, sin(a)*p.x + cos(a)*p.y); }
float box(float2 p, float2 halfExtent, float radius) {
  float2 q = abs(p) - halfExtent + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float oct(float2 p, float apothem, float radius) {
  p = abs(p);
  // Exact convex intersection with a rounded offset of the inset octagon.
  float a = apothem - radius;
  float2 q = p;
  const float2 normal = float2(0.70710678);
  if (q.x < q.y) q = q.yx;
  float2 v = float2(a, a * 0.41421356);
  float2 edge = float2(-0.58578644 * a, 0.58578644 * a);
  float2 delta = q - v;
  float2 closest = delta - edge * clamp(dot(delta, edge) / dot(edge, edge), 0.0, 1.0);
  float diagonal = length(closest);
  float horizontal = length(float2(q.x-a, max(q.y-v.y,0.0)));
  float dist = min(diagonal, horizontal);
  float inside = max(max(p.x,p.y)-a, dot(p,normal)-a);
  return (inside < 0.0 ? -dist : dist) - radius;
}

uniform float halo;
half4 main(float2 p) {
  float glow=exp(-sq(length(p-float2(201,356))/28.0))*halo;
  return half4(float3(0.78,0.84,0.93)*glow,glow);
}

    """
    const val BOUNCE = """

float sq(float v) { return v * v; }
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float2 turn(float2 p, float a) { return float2(cos(a)*p.x - sin(a)*p.y, sin(a)*p.x + cos(a)*p.y); }
float box(float2 p, float2 halfExtent, float radius) {
  float2 q = abs(p) - halfExtent + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float oct(float2 p, float apothem, float radius) {
  p = abs(p);
  // Exact convex intersection with a rounded offset of the inset octagon.
  float a = apothem - radius;
  float2 q = p;
  const float2 normal = float2(0.70710678);
  if (q.x < q.y) q = q.yx;
  float2 v = float2(a, a * 0.41421356);
  float2 edge = float2(-0.58578644 * a, 0.58578644 * a);
  float2 delta = q - v;
  float2 closest = delta - edge * clamp(dot(delta, edge) / dot(edge, edge), 0.0, 1.0);
  float diagonal = length(closest);
  float horizontal = length(float2(q.x-a, max(q.y-v.y,0.0)));
  float dist = min(diagonal, horizontal);
  float inside = max(max(p.x,p.y)-a, dot(p,normal)-a);
  return (inside < 0.0 ? -dist : dist) - radius;
}

uniform float2 pools[8];
uniform float3 colors[8];
uniform float amount;
uniform float darkness;
half4 main(float2 xy) {
  if (amount <= 0.0) return half4(0);
  float3 c = float3(0);
  for (int i=0;i<8;i++) {
    float2 q = (xy-pools[i])/78.75;
    c += colors[i]*exp(-dot(q,q));
  }
  // Overlapping pools never exceed the specified 9/255 peak.
  c *= (9.0/255.0)/max(1.0,max(c.r,max(c.g,c.b)));
  float shade = xy.y < 486.0 ? 0.45*clamp((xy.y-396.0)/90.0,0.0,1.0)
    : mix(0.45,0.68,clamp((xy.y-486.0)/79.0,0.0,1.0));
  float mask = (1.0-shade*darkness)*(1.0-smoothstep(486.0,565.0,xy.y)*darkness);
  c *= amount*mask;
  c += (hash(floor(xy*3.0))-0.5)/255.0*amount*mask*min(1.0,length(c)*25.0);
  return half4(max(c,0.0),0.0);
}

    """
    const val INNER_LIGHT = """

float sq(float v) { return v * v; }
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float2 turn(float2 p, float a) { return float2(cos(a)*p.x - sin(a)*p.y, sin(a)*p.x + cos(a)*p.y); }
float box(float2 p, float2 halfExtent, float radius) {
  float2 q = abs(p) - halfExtent + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float oct(float2 p, float apothem, float radius) {
  p = abs(p);
  // Exact convex intersection with a rounded offset of the inset octagon.
  float a = apothem - radius;
  float2 q = p;
  const float2 normal = float2(0.70710678);
  if (q.x < q.y) q = q.yx;
  float2 v = float2(a, a * 0.41421356);
  float2 edge = float2(-0.58578644 * a, 0.58578644 * a);
  float2 delta = q - v;
  float2 closest = delta - edge * clamp(dot(delta, edge) / dot(edge, edge), 0.0, 1.0);
  float diagonal = length(closest);
  float horizontal = length(float2(q.x-a, max(q.y-v.y,0.0)));
  float dist = min(diagonal, horizontal);
  float inside = max(max(p.x,p.y)-a, dot(p,normal)-a);
  return (inside < 0.0 ? -dist : dist) - radius;
}

uniform float2 centre;
uniform float2 halfSize;
uniform float corner;
uniform float3x3 rot;
uniform float camera;
uniform float inner;
uniform float lightAngle;
uniform float3 colors[8];
half4 main(float2 xy) {
  if (inner <= 0.0) return half4(0);
  float3 v = float3(xy-centre,-camera);
  float3 origin = float3(rot[0].z,rot[1].z,rot[2].z)*camera;
  float3 ray = float3(dot(rot[0],v),dot(rot[1],v),dot(rot[2],v));
  float2 p = (origin+ray*(-origin.z/ray.z)).xy;
  float d = box(p,halfSize,corner);
  // The face occludes the source; subpixel feathering avoids a hard inner cutoff.
  float outside = smoothstep(-0.3,0.3,d);
  float a = inner*outside*exp(-max(d,0.0)/10.0);
  float theta = atan((xy-centre).x,-(xy-centre).y)-lightAngle;
  float index = fract(theta/6.28318531+1.0)*8.0;
  float3 c = float3(0);
  for (int i=0;i<8;i++) {
    float delta=abs(index-float(i)); delta=min(delta,8.0-delta);
    c += colors[i]*(delta<1.0 ? 0.5+0.5*cos(delta*3.14159265) : 0.0);
  }
  c=mix(c,float3(1.0),0.35*exp(-max(d,0.0)/2.0));
  return half4(c*a,a);
}

    """
}
