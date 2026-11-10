// Filament Bulb Dimmer — a dark room and a hanging glass filament bulb, in one Skia shader (2.5D).
//
// The bulb hangs from a cord under the lamp's card and swings about that point (uSwing); pulling it
// stretches the cord (uPull). Its glass is a circle and a neck melted together (smooth minimum): the
// wall behind is read magnified towards its middle, with a tinted rim, two window highlights and a
// dark edge. Inside, an Edison squirrel-cage filament: hairpin loops between two support wires, dark
// copper when off, glowing from deep orange to the chosen colour temperature as power rises; the glass
// fills with a warm haze and the room takes the light (a soft pool round the bulb and a wide falloff
// over the wall). sRGB → Display P3.
export const FBD_SKSL = `
uniform float2 uRes;
uniform float2 uAnchor;     // where the cord leaves the ceiling (pt)
uniform float uCord;        // cord length to the top of the cap (pt)
uniform float uR;           // the bulb's radius (pt)
uniform float uSwing;       // rad
uniform float uPull;        // pt
uniform float uPower;       // 0 off … 1 full (brightness included)
uniform float3 uTint;       // the light's colour at full power
uniform float uPx;

float sq(float v) { return v*v; }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float seg(float2 p, float2 a, float2 b) { float2 pa = p - a, ba = b - a; return length(pa - ba*clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0)); }

// the filament's colour for a power: dull red, orange, then the chosen tint, brighter than 1
float3 glowColor(float p) {
  float3 c = mix(float3(0.9, 0.2, 0.02), float3(1.0, 0.5, 0.12), smoothstep(0.0, 0.4, p));
  return mix(c, uTint, smoothstep(0.08, 0.5, p));
}

float3 room(float2 xy, float2 bulbC) {
  // the pin's page: #262630, a touch warmer towards the top left
  float3 col = float3(0.149, 0.149, 0.188);
  col = mix(col, float3(0.173, 0.149, 0.188), (1.0 - smoothstep(0.0, 0.5, xy.y/uRes.y))*(1.0 - smoothstep(0.0, 0.8, xy.x/uRes.x))*0.8);
  float p = uPower;
  float3 light = glowColor(p);
  // a soft cone of light falling from the bulb onto the dial below it
  float2 d = xy - bulbC;
  float down = d.y/uR;
  float cone = smoothstep(0.6, 1.6, down)*(1.0 - smoothstep(2.6, 5.2, down))*(1.0 - smoothstep(0.9 + 0.35*down, 1.4 + 0.5*down, abs(d.x)/uR));
  col += light*p*0.045*cone;
  // the pool round the bulb
  float r = length(d)/uR;
  col += light*p*(0.13*exp(-sq(r/1.6)) + 0.018*exp(-r/4.0));
  return col;
}

half4 main(float2 xy) {
  // the bulb's frame: rotate about the anchor, y down the cord
  float c = cos(uSwing), s = sin(uSwing);
  float2 d0 = xy - uAnchor;
  float2 p = float2(c*d0.x + s*d0.y, -s*d0.x + c*d0.y);
  float R = uR;
  float capTop = uCord + uPull;
  float sockTop = capTop - 0.5*R;
  float capH = 0.62*R;
  float neckY = capTop + capH;
  float2 cen = float2(0.0, neckY + 0.62*R + R);
  float2 bulbScreen = uAnchor + float2(c*cen.x - s*cen.y, s*cen.x + c*cen.y);

  float3 col = room(xy, bulbScreen);

  // the cord
  float cord = max(abs(p.x) - 0.6, max(-p.y, p.y - sockTop));
  if (cord < 1.0) col = mix(col, float3(0.5, 0.48, 0.52), 0.85*(1.0 - smoothstep(-0.7, 0.4, cord)));
  // the socket: aged brass, rounded at the top
  float2 sp = p - float2(0.0, sockTop + 0.25*R);
  float2 sq2 = abs(sp) - float2(0.34*R, 0.19*R);
  float sock = length(max(sq2, float2(0.0))) + min(max(sq2.x, sq2.y), 0.0) - 0.06*R;
  if (sock < 1.0) {
    float u = clamp(sp.x/(0.4*R), -1.0, 1.0);
    float3 aged = mix(float3(0.2, 0.15, 0.08), float3(0.72, 0.56, 0.34), 0.35 + 0.65*pow(1.0 - abs(u + 0.3)/1.3, 2.0));
    col = mix(col, aged, 1.0 - smoothstep(-0.6, 0.6, sock));
  }

  // the cap: a brass screw base with threads, a little dome on top
  float2 cp = p - float2(0.0, capTop + capH*0.5);
  float cap = max(abs(cp.x) - 0.3*R + 0.02*R*sin(cp.y/R*44.0), abs(cp.y) - capH*0.5);
  cap = min(cap, length((p - float2(0.0, capTop + 0.04*R))/float2(0.22*R, 0.1*R)) - 1.0);
  if (cap < 1.0) {
    float u = clamp(cp.x/(0.3*R), -1.0, 1.0);
    float shade = 0.45 + 0.55*pow(1.0 - abs(u + 0.3)/1.3, 2.0);
    float3 brass = mix(float3(0.28, 0.2, 0.09), float3(0.95, 0.78, 0.45), shade);
    brass *= 0.8 + 0.2*sin(cp.y/R*44.0);
    brass += glowColor(uPower)*uPower*0.25*smoothstep(-capH*0.5, capH*0.5, cp.y);
    col = mix(col, brass, 1.0 - smoothstep(-0.6, 0.6, cap));
  }

  // the glass: a round bulb melted into the neck under the cap
  float sph = length(p - cen) - R;
  float neck = seg(p, float2(0.0, neckY), float2(0.0, cen.y - 0.5*R)) - 0.3*R;
  float glass = smin(sph, neck, 0.45*R);
  if (glass < 1.5) {
    float2 q = (p - cen)/R;
    float r = clamp(length(q), 0.0, 1.0);
    float nz = sqrt(max(1.0 - r*r, 0.0));
    // the room behind, magnified towards the middle
    float2 src = xy - (xy - bulbScreen)*0.18*nz;
    float3 g = room(src, bulbScreen)*0.9;
    // the glass fills with warm haze when lit
    float3 lc = glowColor(uPower);
    g += lc*uPower*(0.07 + 0.13*nz);
    // the filament: support wires, then hairpin loops of a squirrel cage
    float2 f = q*0.78;
    float wires = min(seg(f, float2(-0.08, -1.25), float2(-0.32, 0.12)), seg(f, float2(0.08, -1.25), float2(0.32, 0.12)));
    wires = min(wires, seg(f, float2(0.0, -1.25), float2(0.0, 0.35)));
    // four hairpin arches, each as four straight pieces
    float fil = 1e5;
    for (int i = 0; i < 4; i++) {
      float x0 = -0.32 + 0.16*float(i);
      float x1 = x0 + 0.16;
      float2 a0 = float2(x0, 0.12), a1 = float2(x0 + 0.015, -0.18), a2 = float2(x0 + 0.08, -0.3), a3 = float2(x1 - 0.015, -0.18), a4 = float2(x1, 0.12);
      fil = min(fil, min(min(seg(f, a0, a1), seg(f, a1, a2)), min(seg(f, a2, a3), seg(f, a3, a4))));
    }
    float w = 0.012 + 0.006*uPower;
    float3 wireCol = mix(float3(0.25, 0.22, 0.2), lc*0.8, uPower*0.6);
    g = mix(g, wireCol, (1.0 - smoothstep(0.0, 0.012, wires))*0.8);
    float3 filOff = float3(0.35, 0.18, 0.08);
    float core = 1.0 - smoothstep(w*0.4, w, fil);
    g = mix(g, filOff, core*(1.0 - uPower));
    g += mix(lc, float3(1.0, 0.95, 0.85), 0.5*uPower)*uPower*core*3.0 + lc*uPower*1.1*exp(-fil/(0.03 + 0.06*uPower));
    // glass: a tinted rim, two window highlights, a dark outer edge
    float edge = pow(1.0 - nz, 3.0);
    g = mix(g, g*0.6 + float3(0.11, 0.1, 0.095), edge*0.7);
    g += float3(1.0)*0.32*exp(-sq(length(q - float2(-0.48, -0.42))/0.13));
    g += float3(1.0)*0.12*smoothstep(0.06, 0.0, abs(length(q - float2(0.08, 0.08)) - 0.82))*smoothstep(0.2, -0.5, q.x + q.y);
    g += lc*uPower*0.25*edge;
    float a = 1.0 - smoothstep(-0.6, 0.6, glass);
    col = mix(col, g, a);
    // a thin bright line where the glass turns away
    col += float3(0.9, 0.85, 0.8)*(0.08 + 0.25*uPower)*exp(-sq(glass/0.9))*smoothstep(R*0.2, -R*0.6, p.y - cen.y + p.x);
  }

  float3 l = pow(max(col, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  float3 o = pow(max(m, float3(0.0)), float3(1.0/2.2));
  o += (fract(sin(dot(xy, float2(12.9898, 78.233)))*43758.5453) - 0.5)/255.0;
  return half4(half3(o), 1.0);
}
`;
