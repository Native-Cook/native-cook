package com.ngocdevv.nativecook.blocks.animations.gemorbcollect

/** Native AGSL translation of the source SkSL material. */
internal object GemOrbCollectPillSource {
    val source = """
uniform float2 uCentre;
uniform float2 uSize;
uniform float uTone;
uniform float uPress;
uniform float uGlow;
uniform float uSheen;
uniform float uOpacity;
uniform float uScale;
uniform float uTime;
uniform float uPixelWidth;

const float PI = 3.14159265359;
float sqr(float v) { return v * v; }
float stadium(float2 q, float straight, float rad) {
  return length(float2(max(abs(q.x) - straight, 0.0), q.y)) - rad;
}
float3 studio(float3 n, float2 q, float depth, float facetU) {
  // Perspective varies substantially within a facet, despite its constant plane normal.
  float3 viewRay = float3(-q.x/max(uSize.x,1.0)*1.65,-q.y/max(uSize.y,1.0)*1.1,1.0);
  viewRay /= max(length(viewRay),0.001);
  float3 reflected = reflect(-viewRay,n);
  float upper = max(-n.y,0.0);
  float left = 1.0-smoothstep(-uSize.x*0.48,uSize.x*0.35,q.x);
  // A bounded softbox image in each plane. Its spectrum lives only at the card edge.
  float cardCoord = depth + reflected.x*0.26 + reflected.y*0.18 + facetU*0.30;
  float panel = smoothstep(0.10,0.31,cardCoord)*(1.0-smoothstep(0.57,0.84,cardCoord));
  float key = panel*(0.18+0.65*upper)*(0.28+0.72*left);
  float fireEdge = cardCoord-0.62;
  float3 fire = float3(exp(-sqr((fireEdge-0.028)/0.038)),
    exp(-sqr(fireEdge/0.038)),exp(-sqr((fireEdge+0.028)/0.038)));
  float pink = pow(max(dot(reflected,float3(0.83,-0.18,0.53)),0.0),8.0)
    *exp(-sqr((depth-0.60)/0.23)-sqr((facetU+0.10)/0.42));
  float cyan = pow(max(dot(reflected,float3(-0.72,0.55,0.42)),0.0),9.0)
    *exp(-sqr((depth-0.46)/0.21)-sqr((facetU-0.12)/0.38));
  // Small coloured cards occupy only part of their facing end facets.
  cyan += pow(clamp(dot(n.xy,float2(-0.81,0.59))/0.67,0.0,1.0),8.0)
    *exp(-sqr((depth-0.44)/0.17)-sqr((facetU-0.06)/0.26))*0.78;
  pink += pow(clamp(dot(n.xy,float2(1.0,-0.08))/0.67,0.0,1.0),8.0)
    *exp(-sqr((depth-0.57)/0.19)-sqr((facetU+0.10)/0.28))*0.72;
  float3 body = mix(float3(0.086,0.071,0.478),float3(0.26,0.18,0.69),upper);
  body *= 0.78+0.22*smoothstep(0.0,1.0,depth);
  return body + float3(0.74,0.77,1.0)*key + fire*key*0.22
    + float3(0.78,0.28,0.77)*pink*0.8 + float3(0.12,0.78,0.55)*cyan*0.85;
}
half4 main(float2 xy) {
  float sc = max(uScale,0.01);
  float2 q = (xy-uCentre)/sc;
  float rad = max(uSize.y*0.5,1.0);
  float straight = max(uSize.x*0.5-rad,0.0);
  float d = stadium(q,straight,rad);
  float aa = max(uPixelWidth/sc,0.16);
  float coverage = 1.0-smoothstep(-aa*0.5,aa*0.5,d);
  float capsule = clamp(uTone,0.0,1.0);
  float pressed = clamp(uPress,0.0,1.0);
  float lit = clamp(uGlow,0.0,1.0);
  float bevel = mix(9.0,7.0,capsule)*min(uSize.y/44.0,1.0);
  float2 tableQ = q-float2(1.6,1.8)*pressed;
  float td = stadium(tableQ,straight,rad-bevel);
  float tableMask = 1.0-smoothstep(-aa*0.5,aa*0.5,td);
  float2 radial = float2(max(abs(q.x)-straight,0.0),q.y);
  float a = atan(radial.y,max(radial.x,0.0001));
  float sector = clamp((a+PI*0.5)/PI,0.0,0.99999)*5.0;
  float facetAngle = (floor(sector)+0.5)*PI/5.0-PI*0.5;
  float sectorEdge = (fract(sector)-0.5)*PI/5.0;
  float seamDistance = (abs(sectorEdge)-PI/10.0)*max(length(radial),1.0);
  float rounding = 1.0-smoothstep(0.0,aa*0.8+0.18,-seamDistance);
  facetAngle += sign(sectorEdge)*PI/5.0*rounding*0.5;
  float2 facet = float2(cos(facetAngle)*sign(q.x),sin(facetAngle));
  float endMask = smoothstep(-aa,aa,abs(q.x)-straight);
  facet = mix(float2(0.0,sign(q.y)),facet,endMask);
  float depth = clamp(-d/max(bevel,1.0),0.0,1.0);
  // One crown plane per facet: no secondary girdle ring.
  float3 normal = float3(facet*0.67,0.7424);
  normal.xy -= float2(0.16,0.22)*pressed;
  normal /= max(length(normal),0.001);
  float facetU = mix(q.x/max(uSize.x,1.0),fract(sector)-0.5,endMask);
  float3 crown = studio(normal,q,depth,facetU);
  crown *= 0.97+0.03*cos(uTime*2.0*PI/12.0);
  // A narrow lower-left rim reflection slides under pressure, without flooding a facet.
  float rimSpot = exp(-sqr((q.x+uSize.x*(0.28-0.07*pressed))/(uSize.x*0.18)));
  float rim = exp(-sqr((depth-0.18-0.06*pressed)/0.09))*smoothstep(0.0,rad*0.5,q.y)*rimSpot;
  crown += float3(0.12,0.62,0.40)*rim*(0.58+0.12*pressed);
  // Refraction through the table: saturated vertical depth, broad low-contrast internal cards.
  float tableY = clamp((tableQ.y+rad-bevel)/max(2.0*(rad-bevel),1.0),0.0,1.0);
  float diagonal = q.x+1.45*q.y;
  float bands = exp(-sqr((diagonal+uSize.x*0.25)/(uSize.x*0.13)))
    + 0.65*exp(-sqr((diagonal-uSize.x*0.28)/(uSize.x*0.10)));
  float3 tableColour = mix(float3(0.3333,0.2745,0.9098),float3(0.1686,0.1216,0.6510),tableY);
  tableColour *= 1.0+bands*0.055;
  float tableKey = exp(-sqr((q.x+uSize.x*0.23)/(uSize.x*0.18))-sqr((tableY-0.16)/0.28));
  tableColour += float3(0.065,0.058,0.080)*tableKey;
  float innerShadow = exp(-sqr((td+0.85)/0.85))*smoothstep(0.0,rad*0.4,tableQ.y);
  tableColour *= (1.0-0.22*innerShadow)*(1.0-0.12*pressed);
  float centreLight = exp(-sqr(q.x/(uSize.x*0.31))-sqr(q.y/(rad*0.95)));
  tableColour = mix(tableColour,float3(0.49,0.40,0.78),lit*centreLight*0.82);
  // True low-opacity indigo glass; the page remains visible through the capsule.
  float3 smokedTable = float3(0.3765,0.3608,0.8627)*(1.04-0.16*tableY+0.045*bands);
  smokedTable *= 1.0-0.12*pressed;
  smokedTable = mix(smokedTable,float3(0.57,0.45,0.87),lit*centreLight*0.6);
  tableColour = mix(tableColour,smokedTable,capsule);
  crown = mix(crown,float3(0.27,0.25,0.67)+crown*0.30,capsule);
  float3 colour = mix(crown,tableColour,tableMask);
  // The outline is a single 0.75 pt light. The table edge only has a selective reflection.
  float upperLeft = clamp(0.50-facet.y*0.39-facet.x*0.24,0.05,1.0);
  float outerLine = exp(-sqr((d+0.38)/(0.38+aa*0.15)));
  float tableLine = exp(-sqr(td/(0.20+aa*0.20)));
  float topKey = max(-facet.y,0.0)*(1.0-smoothstep(-uSize.x*0.35,uSize.x*0.42,q.x));
  colour += float3(0.67,0.69,0.93)*tableLine*topKey*0.21*(1.0-capsule*0.85);
  float3 edgeColour = mix(float3(0.55,0.23,0.72),float3(0.86,0.88,1.0),upperLeft);
  colour = mix(colour,edgeColour,outerLine*mix(0.12+0.76*upperLeft,0.92,capsule));
  float sheenOn = smoothstep(-0.10,0.0,uSheen)*(1.0-smoothstep(1.0,1.10,uSheen));
  float sweep = q.x+q.y*0.55-(uSheen-0.5)*uSize.x;
  float sheenBand = exp(-sqr(sweep/6.0))*sheenOn;
  float3 sheenFire = float3(exp(-sqr((sweep-1.0)/0.7)),exp(-sqr(sweep/0.7)),exp(-sqr((sweep+1.0)/0.7)))*sheenOn;
  colour += (float3(0.55,0.60,0.72)*sheenBand+sheenFire*0.35)
    *mix(0.62,0.035,tableMask)*(1.0-capsule*0.6);
  // This ceiling preserves saturated blue while protecting the white label in every state.
  colour = mix(colour,min(colour,float3(0.50,0.43,0.94)),tableMask*(1.0-capsule));
  float glassAlpha = 0.16+lit*centreLight*0.15;
  glassAlpha = mix(glassAlpha,0.055+0.50*upperLeft,outerLine);
  float solidAlpha = mix(0.98,glassAlpha,capsule);
  float haloWidth = mix(16.0,23.0,lit)*(1.0-pressed*0.38);
  float haloD = stadium(q-float2(0.0,4.0-pressed*2.0),straight,rad);
  float halo = exp(-sqr(max(haloD,0.0)/max(haloWidth,1.0)))
    *(1.0-smoothstep(haloWidth,haloWidth*1.55,max(haloD,0.0)));
  float haloAlpha = halo*mix(0.25,0.016,capsule)*(1.0+lit*0.3);
  float bodyAlpha = coverage*solidAlpha;
  float alpha = bodyAlpha+haloAlpha*(1.0-bodyAlpha);
  float3 premul = clamp(colour,0.0,1.0)*bodyAlpha
    +float3(0.22,0.085,0.62)*haloAlpha*(1.0-bodyAlpha);
  float opacity = clamp(uOpacity,0.0,1.0);
  return half4(half3(premul*opacity),half(alpha*opacity));
}
"""
}
