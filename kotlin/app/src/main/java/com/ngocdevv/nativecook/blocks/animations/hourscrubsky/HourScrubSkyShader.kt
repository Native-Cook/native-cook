package com.ngocdevv.nativecook.blocks.animations.hourscrubsky
import android.graphics.*
import androidx.annotation.RequiresApi
import kotlin.math.*

@RequiresApi(33)
internal class HourScrubSkyShader(private val low:Bitmap,private val high:Bitmap) {
 private val effect=RuntimeShader(SOURCE)
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { shader=effect }
 init { effect.setInputShader("uLow",BitmapShader(low,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT).apply { filterMode=BitmapShader.FILTER_MODE_LINEAR });effect.setInputShader("uHigh",BitmapShader(high,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT).apply { filterMode=BitmapShader.FILTER_MODE_LINEAR }) }
 fun draw(canvas:Canvas,f:HourScrubSkyFrame,c:HourScrubSkyColors,ch:Double,reduced:Boolean,hold:Double,fx:Double,fy:Double) {
  val m=HourScrubSkyMotion;val sun=m.sun(f.h);val sway=if(reduced)0.0 else f.sway;val ar=abs(f.rate);val deg=PI/180
  val arc=min(15*deg*max(ar-.9,0.0)*.36,40*deg);val sl=low.height/ch;val sh=high.height/600.0;val lw=low.width.toDouble();val hw=high.width.toDouble()
  fun u(name:String,vararg v:Double) { effect.setFloatUniform(name,v.map { it.toFloat() }.toFloatArray()) }
  u("uCH",ch);for(i in 0..3) { u("uD$i",*c.day[i]);u("uN$i",*c.night[i]) }
  u("uYT",c.front);u("uFW",c.width);u("uBandCol",*c.bandColor);u("uBand",*c.band);u("uSun",sun.first,sun.second);u("uSunCol",*c.sun)
  u("uSquash",m.mix(1.0,.95,m.smooth(440.0,540.0,sun.second)));u("uBloomK",1+.25*hold);u("uBreath",if(reduced)0.0 else .08*sin(PI*sway));u("uFlare",f.flare)
  u("uRimD",*c.cloud[0]);u("uBellyD",*c.cloud[1]);u("uRimN",*HourScrubSkyColors.rgb(0x4a5266));u("uBellyN",*HourScrubSkyColors.rgb(0x2a2f3d))
  u("uOff",f.h/24*lw/sl+(if(reduced)0.0 else 10*sin(2*PI*sway/12)),f.h/24*hw/sh+(if(reduced)0.0 else 6*sin(2*PI*sway/12+1.3)))
  u("uScale",sl,sh);u("uPlateW",lw,hw);u("uSmear",if(reduced || ar<1.5)0.0 else min(ar*2.4,10.0));u("uStarRot",(f.h*15*deg)%(2*PI));u("uArc",if(reduced)0.0 else sign(f.rate)*arc);u("uTrailDim",1/(1+arc/(20*deg))+.15);u("uHold",fx,fy,hold)
  canvas.drawRect(-402f,0f,804f,ch.toFloat(),paint)
 }
 companion object { private const val SOURCE="""
half3 toOut(half3 c) { return c; }
uniform shader uLow;    // low heaped plate: r = density, g = blurred density
uniform shader uHigh;   // high thin plate (same packing)
uniform float uCH;      // canvas height (pt)
uniform float3 uD0; uniform float3 uD1; uniform float3 uD2; uniform float3 uD3;   // day stops
uniform float3 uN0; uniform float3 uN1; uniform float3 uN2; uniform float3 uN3;   // night stops
uniform float uYT;      // twilight front (pt)
uniform float uFW;      // half-width of its soft edge (pt)
uniform float3 uBandCol;
uniform float3 uBand;   // strength, centre offset from the front, sigma
uniform float2 uSun;    // sun centre (pt)
uniform float3 uSunCol;
uniform float uSquash;  // vertical scale of the disc on the haze line
uniform float uBloomK;  // bloom scale (1.25 while held)
uniform float uBreath;  // breathing of the hidden sun's patch
uniform float uFlare;   // silver-lining flare (0..1)
uniform float3 uRimD; uniform float3 uBellyD; uniform float3 uRimN; uniform float3 uBellyN;
uniform float2 uOff;    // cloud offsets low / high (pt)
uniform float2 uScale;  // image px per pt, low / high
uniform float2 uPlateW; // plate widths (px), low / high
uniform float uSmear;   // smear half-length along the wind (pt)
uniform float uStarRot; // star field rotation (rad)
uniform float uArc;     // trail length (rad), signed by the direction
uniform float uTrailDim;
uniform float3 uHold;   // finger x, y, held 0..1

const float2 POLE = float2(60.0, -120.0);
const float HAZE = 540.0;

float hash1(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float hash2(float2 p) { return fract(sin(dot(p, float2(269.5, 183.3))) * 24634.6345); }
float hash3(float2 p) { return fract(sin(dot(p, float2(419.2, 371.9))) * 31415.9265); }

float3 grad4(float3 a, float3 b, float3 c, float3 d, float f) {
  if (f <= 0.35) return mix(a, b, clamp(f / 0.35, 0.0, 1.0));
  if (f <= 0.62) return mix(b, c, smoothstep(0.35, 0.62, f));
  return mix(c, d, smoothstep(0.62, 0.8, f));
}

// the clouds part round a held sun: sample from nearer the finger so the cloud seems pushed away
float2 part(float2 p) {
  float2 v = p - uHold.xy;
  float d = length(v);
  float w = uHold.z * (1.0 - smoothstep(0.0, 140.0, d));
  return d > 0.5 ? p - v / d * 6.0 * w : p;
}
float2 low(float2 p) {
  float2 q = float2((p.x + uOff.x) * uScale.x, p.y * uScale.x);
  q.x = mod(q.x, uPlateW.x);
  return uLow.eval(q).rg;
}
float2 high(float2 p) {
  float2 q = float2((p.x + uOff.y) * uScale.y, p.y * uScale.y);
  q.x = mod(q.x, uPlateW.y);
  return uHigh.eval(q).rg;
}

// stars on rings about the pole; each ring is cut into cells of about 44 pt and about a third of them hold a star
float3 stars(float2 p) {
  float2 v = p - POLE;
  float r = length(v);
  const float DR = 44.0;
  float ri = floor(r / DR);
  float rc = (ri + 0.5) * DR;
  float n = floor(6.2831853 * rc / DR);
  float ca = 6.2831853 / n;
  float th = atan(v.y, v.x) - uStarRot;
  float L = abs(uArc);
  // a star's head is at phi; its trail covers [phi - L, phi] going forward, [phi, phi + L] going back
  float lo = uArc >= 0.0 ? th : th - L;
  float k0 = floor(lo / ca) - 1.0;
  float span = L / ca + 2.0;
  float3 acc = float3(0.0);
  for (int i = 0; i < 24; i++) {
    float fi = float(i);
    if (fi > span) break;
    float k = k0 + fi;
    float2 id = float2(mod(k, n), ri);
    if (hash1(id) > 0.34) continue;
    float phi = (k + 0.15 + 0.7 * hash2(id)) * ca;
    float rs = rc + (hash3(id) - 0.5) * DR * 0.5;
    float a0 = uArc >= 0.0 ? phi - L : phi;
    float a1 = uArc >= 0.0 ? phi : phi + L;
    float da = th < a0 ? a0 - th : (th > a1 ? th - a1 : 0.0);
    // the trail tapers towards its tail
    float along = L > 0.001 ? clamp((uArc >= 0.0 ? th - a0 : a1 - th) / L, 0.0, 1.0) : 1.0;
    float d = length(float2(r - rs, da * r));
    float m = hash2(id.yx + 7.0);
    float rad = m < 0.45 ? 1.0 : (m < 0.75 ? 1.5 : 2.2);
    float al = m < 0.45 ? 0.55 : (m < 0.75 ? 0.8 : 1.0);
    float trailA = mix(0.35, 1.0, along);
    float b = (1.0 - smoothstep(rad - 0.5, rad + 0.6, d)) * al * (da > 0.0 ? 1.0 : trailA);
    float tint = hash3(id.yx + 3.0);
    float3 c = tint < 0.08 ? float3(1.0, 0.886, 0.722) : (tint < 0.16 ? float3(0.812, 0.878, 1.0) : float3(1.0));
    acc = max(acc, c * b);
  }
  return acc * uTrailDim;
}

half4 main(float2 p) {
  float f = clamp(p.y / uCH, 0.0, 1.0);
  float3 day = grad4(uD0, uD1, uD2, uD3, f);
  float3 night = grad4(uN0, uN1, uN2, uN3, f);
  float nf = 1.0 - smoothstep(uYT - uFW, uYT + uFW, p.y);
  float bz = (p.y - (uYT + uBand.y)) / uBand.z;
  float band = uBand.x * exp(-bz * bz);
  float3 col = mix(mix(day, uBandCol, band), night, nf);

  // clouds (density first: the stars and the sun sit behind them)
  float2 pc = part(p);
  float2 lo = low(pc);
  if (uSmear > 0.5) {
    lo = (lo + low(pc + float2(uSmear, 0.0)) + low(pc - float2(uSmear, 0.0))) / 3.0;
  }
  float2 hi = high(pc);
  float hiMask = 1.0 - smoothstep(380.0, 560.0, p.y);
  float dl = lo.r;
  float dh = hi.r * hiMask;

  float3 skyCol = col; // the starless sky, for the haze bank the sun sets into (stars go out behind it)
  // stars, only where it is night; drifting cloud cuts them
  if (nf > 0.01) {
    float3 s = stars(p);
    col += s * nf * (1.0 - smoothstep(0.3, 0.6, max(dl, dh)));
  }

  // the sun: disc r 36 (squashed to 0.95 on the haze line, its lower limb dissolving into a lit horizon band over 30 pt),
  // gaussian bloom (sigma 30) and a halo to r 150; dimmed behind thick cloud
  float2 sv = p - uSun;
  sv.y /= uSquash;
  float sd = length(sv);
  float2 atSun = low(uSun);
  float cover = smoothstep(0.35, 0.75, atSun.g);
  float limb = 1.0 - smoothstep(HAZE - 6.0, HAZE + 24.0, p.y);
  float vis = 1.0 - smoothstep(HAZE + 10.0, HAZE + 90.0, uSun.y);
  float disc = (1.0 - smoothstep(35.0, 37.0, sd)) * limb;
  float bk = uBloomK;
  float bloom = (exp(-(sd * sd) / (2.0 * 30.0 * 30.0 * bk * bk)) * 0.55 + exp(-sd / (55.0 * bk)) * 0.22 * (1.0 - smoothstep(0.0, 150.0 * bk, sd) * 0.6)) * vis;
  bloom *= 1.0 + uBreath * cover;
  float3 sc = uSunCol;
  col = mix(col, sc, clamp(bloom * mix(1.0, 0.75, cover), 0.0, 1.0));
  // a hot core with limb darkening towards the sun's colour, and a thin bright corona at the edge
  float rr = clamp(sd / 36.0, 0.0, 1.0);
  float3 dc = mix(mix(sc, float3(1.0, 0.99, 0.95), 0.55), sc, rr * rr);
  col = mix(col, dc, disc * mix(1.0, 0.35, cover));
  float corona = exp(-abs(sd - 37.0) / 5.0) * 0.35 * vis * (1.0 - cover) * limb;
  col += sc * corona;
  // the horizon: while the sun is near the haze line, a haze bank lies over everything below it (the lower limb and
  // the bloom sink behind it instead of fading into empty sky), its top edge lit sideways by the sun
  float nearH = 1.0 - smoothstep(30.0, 110.0, abs(uSun.y - HAZE));
  float bank = smoothstep(HAZE - 6.0, HAZE + 24.0, p.y) * nearH;
  float hx = (p.x - uSun.x) / 190.0;
  float gx = exp(-hx * hx);
  float hy = (p.y - (HAZE + 6.0)) / 9.0;
  float skirt = exp(-max(p.y - HAZE, 0.0) / 60.0) * exp(-hx * hx * 0.35);
  float3 bankCol = mix(skyCol, sc, 0.38 * skirt * (1.0 - cover));
  col = mix(col, bankCol, bank * 0.9);
  float edgeGlow = exp(-hy * hy) * gx * nearH * (1.0 - cover);
  col = mix(col, mix(sc, float3(1.0, 0.97, 0.9), 0.4), clamp(edgeGlow * 0.6, 0.0, 1.0));

  // cloud relighting: one blurred tap toward the sun; more cloud there = shaded, an edge facing it = lit
  float2 toSun = uSun - p;
  float ds = length(toSun);
  float2 dir = ds > 1.0 ? toSun / ds : float2(0.0, -1.0);
  float gT = low(pc + dir * 14.0).g;
  float lit = clamp(0.95 - 0.9 * gT + (lo.g - gT) * 3.5, 0.0, 1.0);
  float near = 1.0 - smoothstep(0.0, 90.0, max(ds - 36.0, 0.0));
  float held = uHold.z * (1.0 - smoothstep(0.0, 140.0, length(p - uHold.xy)));
  float rimBoost = 1.0 + 0.8 * max(near * cover, held);
  lit = clamp(lit * rimBoost, 0.0, 1.0);
  float3 rim = mix(uRimD, uRimN, nf);
  float3 belly = mix(uBellyD, uBellyN, nf);
  float3 cc = mix(belly, rim, lit);
  // silver lining: thin edges near a covered sun burn bright (and flare white just before it shows)
  float edge = clamp(dl * (1.0 - dl) * 4.0, 0.0, 1.0) * near * vis;
  cc = mix(cc, mix(rim, sc, 0.4), clamp(edge * cover * 0.9 * rimBoost, 0.0, 1.0));
  cc = mix(cc, float3(1.0), clamp(edge * uFlare, 0.0, 1.0));
  float al = clamp(dl * 1.05, 0.0, 0.85);
  col = mix(col, cc, al);
  // the high plate: thin and even, lit by the same colours
  float3 hc = mix(belly, rim, 0.75);
  col = mix(col, hc, clamp(dh * 0.6, 0.0, 0.6));

  // the bloom spills over the cloud edges a little (one glow source)
  col += sc * bloom * 0.3 * (1.0 - cover * 0.5) * (1.0 - 0.7 * bank);

  // grain 3 % and a dither at night so the navy does not band
  float g = hash1(floor(p * 2.0)) - 0.5;
  col += g * (0.015 + nf * 0.006);
  return half4(toOut(half3(clamp(col, 0.0, 1.0))), 1.0);
}
""" }
}
