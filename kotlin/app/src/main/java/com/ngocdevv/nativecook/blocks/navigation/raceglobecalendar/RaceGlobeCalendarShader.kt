package com.ngocdevv.nativecook.blocks.navigation.raceglobecalendar
import android.graphics.*
import androidx.annotation.RequiresApi
import kotlin.math.*

@RequiresApi(33)
internal class RaceGlobeCalendarShader(private val images:List<Bitmap>) {
 private val effect=RuntimeShader(SOURCE);private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { shader=effect }
 init { listOf("dayTex","nightTex","maskTex","glowTex").forEachIndexed { i,name->effect.setInputShader(name,BitmapShader(images[i],Shader.TileMode.REPEAT,Shader.TileMode.CLAMP).apply { filterMode=BitmapShader.FILTER_MODE_LINEAR }) } }
 fun draw(canvas:Canvas,f:RaceGlobeCalendarFrame,ch:Double,px:Double) {
  val m=RaceGlobeCalendarMotion.rows(f.lat,f.lon);val r=300*f.zoom;val cap=20/(r*max(cos(f.lat*PI/180),.3))
  fun u(name:String,vararg v:Double) { effect.setFloatUniform(name,v.map { it.toFloat() }.toFloatArray()) }
  u("uP3",0.0);u("uC",201.0,372.0);u("uR",r);u("uDist",4.0);u("uM0",m[0],m[1],m[2]);u("uM1",m[3],m[4],m[5]);u("uM2",m[6],m[7],m[8]);u("uSun",*f.sun)
  u("uLonC",RaceGlobeCalendarMotion.wrap(atan2(m[6],m[8])*180/PI)*PI/180);u("uBlur",min(abs(f.spin)*PI/180/120,cap));u("uTwk",2*PI*7*f.t/10.5);u("uPx",px)
  listOf("uDaySz","uNightSz","uMaskSz","uGlowSz").forEachIndexed { i,name->u(name,images[i].width.toDouble(),images[i].height.toDouble()) }
  canvas.drawRect(0f,0f,402f,ch.toFloat(),paint)
 }
 companion object { private const val SOURCE="""
uniform shader dayTex;
uniform shader nightTex;
uniform shader maskTex;
uniform shader glowTex;

uniform float uP3;
uniform float2 uC;      // globe centre (pt)
uniform float uR;       // limb radius on screen (pt)
uniform float uDist;    // eye distance, globe radii
uniform float3 uM0;     // world -> view rotation, rows
uniform float3 uM1;
uniform float3 uM2;
uniform float3 uSun;    // world direction to the sun
uniform float uLonC;    // longitude of the view axis (rad): the texture seam is kept behind the globe
uniform float uBlur;    // spin blur, half width in radians of longitude
uniform float uTwk;     // twinkle phase (rad), a whole number of cycles per loop
uniform float uPx;      // one device pixel in pt
uniform float2 uDaySz;
uniform float2 uNightSz;
uniform float2 uMaskSz;
uniform float2 uGlowSz;

const float PI = 3.14159265;
const float TAU = 6.2831853;

float lin1(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc1(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
float3 lin3(float3 c) { return float3(lin1(c.r), lin1(c.g), lin1(c.b)); }
// linear sRGB in, encoded canvas colour out
float3 outP3(float3 l) {
  l = clamp(l, 0.0, 1.0);
  float3 p = l;
  if (uP3 > 0.5) {
    p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  }
  return float3(enc1(p.r), enc1(p.g), enc1(p.b));
}

float hash21(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
// value noise: lamps ignite in soft clusters, never in square cells
float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  float a = hash21(i);
  float b = hash21(i + float2(1.0, 0.0));
  float c = hash21(i + float2(0.0, 1.0));
  float d = hash21(i + float2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float3 toWorld(float3 v) { return v.x * uM0 + v.y * uM1 + v.z * uM2; }

// The night map holds a moon-blue floor plus warm lamps: split one sample into the two (assets-report.md).
const float3 MOON = float3(0.0549, 0.1020, 0.1804);
const float3 LAMP = float3(0.9451, 0.6863, 0.3608);
// Luma based (JPEG keeps luma at full resolution; a chroma based split shows its 16 px blocks).
float lampAmp(float3 c) {
  float3 yw = float3(0.2126, 0.7152, 0.0722);
  return max(0.0, (dot(c, yw) - dot(MOON, yw)) / (dot(LAMP, yw) - dot(MOON, yw)));
}

half4 main(float2 xy) {
  float2 o = xy - uC;
  float rho = length(o);
  float R = uR;
  float haloW = 16.0 * R / 300.0;
  if (rho > R + haloW * 3.5) return half4(0.0);

  float3 atmo = lin3(float3(0.435, 0.706, 1.0));   // #6FB4FF
  float sinL = 1.0 / uDist;
  float cosL = sqrt(1.0 - sinL * sinL);
  float2 dir2 = rho > 0.001 ? o / rho : float2(0.0, 1.0);
  float3 limbV = float3(dir2.x * cosL, -dir2.y * cosL, sinL);
  float limbLit = smoothstep(-0.35, 0.45, dot(toWorld(limbV), uSun));

  // the outer halo: 16 pt at R 300, brighter over the lit limb
  float beyond = max(rho - R, 0.0);
  float halo = exp(-beyond / (haloW * 0.33)) * mix(0.10, 0.62, limbLit);
  float cover = clamp((R - rho) / uPx + 0.5, 0.0, 1.0);
  if (cover <= 0.0) {
    float a = halo;
    return half4(half3(outP3(atmo) * a), half(a));
  }

  // ray from the eye to the sphere
  float F = R * sqrt(uDist * uDist - 1.0);
  float3 rd = normalize(float3(o.x / F, -o.y / F, -1.0));
  float bq = uDist * rd.z;
  float disc = max(1.0 - (uDist * uDist - bq * bq), 0.0);
  float tt = -bq - sqrt(disc);
  float3 nv = normalize(float3(0.0, 0.0, uDist) + tt * rd);
  float3 ev = normalize(float3(0.0, 0.0, uDist) - nv);
  float3 nw = toWorld(nv);
  float3 ew = toWorld(ev);

  // texture coordinates; latitude clamped to ±85°, longitude continuous over the visible side
  float lat = clamp(asin(clamp(nw.y, -1.0, 1.0)), -1.4835, 1.4835);
  float lon = atan(nw.x, nw.z);
  float rel = lon - uLonC;
  rel -= TAU * floor((rel + PI) / TAU);
  float uu = (uLonC + rel) / TAU + 0.5;
  float vv = 0.5 - lat / PI;

  float3 dayS = float3(0.0);
  float3 nightS = float3(0.0);
  if (uBlur > 0.0001) {
    // spin: twelve jittered taps along the longitude (addendum (d) "ghost spin")
    float jit = hash21(xy) - 0.5;
    for (int i = 0; i < 12; i++) {
      float k = (float(i) + 0.5 + jit) / 12.0 - 0.5;
      float du = k * 2.0 * uBlur / TAU;
      dayS += dayTex.eval(float2((uu + du) * uDaySz.x, vv * uDaySz.y)).rgb;
      nightS += nightTex.eval(float2((uu + du) * uNightSz.x, vv * uNightSz.y)).rgb;
    }
    dayS /= 12.0;
    nightS /= 12.0;
  } else {
    dayS = dayTex.eval(float2(uu * uDaySz.x, vv * uDaySz.y)).rgb;
    nightS = nightTex.eval(float2(uu * uNightSz.x, vv * uNightSz.y)).rgb;
  }
  float water = maskTex.eval(float2(uu * uMaskSz.x, vv * uMaskSz.y)).r;
  float3 glowS = glowTex.eval(float2(uu * uGlowSz.x, vv * uGlowSz.y)).rgb;

  float ndl = dot(nw, uSun);
  float lit = smoothstep(-0.10, 0.12, ndl);
  // the sphere's own shading: twilight light dims towards the limb
  float shadeV = pow(clamp(dot(nv, ev), 0.0, 1.0), 1.4);

  // day: the albedo under a soft Lambert, a little lifted so the lit side reads like a photograph
  float3 alb = lin3(dayS);
  float lam = clamp(ndl, 0.0, 1.0);
  // (a smooth start at the terminator: a bare pow(ndl, 0.62) has an infinite slope there and drew an edge)
  float amt = 1.65 * pow(clamp((ndl + 0.06) / 1.06, 0.0, 1.0), 0.62) * smoothstep(-0.06, 0.10, ndl);
  float3 dayC = alb * amt;
  // twilight, day side: the last few degrees before the boundary warm to a soft amber (#FF8A3D, desaturated),
  // a Gaussian so there is no plateau and no edge
  float qa = (ndl - 0.04) / 0.045;
  float wA = exp(-qa * qa);
  dayC = dayC * mix(float3(1.0), float3(1.20, 0.90, 0.70), 0.55 * wA) + lin3(float3(1.0, 0.72, 0.52)) * 0.024 * wA * shadeV;

  // night: moon-blue floor (baked at about 10 %) and lamps that ignite cell by cell as the sun sinks below them
  float3 nC = nightS;
  float amp = lampAmp(nC);
  float3 floorC = max(nC - amp * LAMP, 0.0);
  float ampG = lampAmp(glowS);
  float hh = vnoise(float2(fract(uu) * 1400.0, vv * 700.0));
  float on = smoothstep(0.0, 0.06, -ndl - 0.02 - 0.05 * hh);
  float twk = 1.0 + 0.05 * sin(uTwk + hh * TAU);
  float3 lamps = lin3(LAMP) * (amp * 2.2 + ampG * 0.9) * on * twk;
  // moonlight keeps the geography faintly readable (the floor sits only on land; the sea gets a navy floor)
  float3 nightC = lin3(floorC) * 0.75 + alb * 0.075 + lin3(float3(0.020, 0.027, 0.051)) + lamps;

  float3 col = mix(nightC, dayC, lit);

  // twilight, night side: a faint rose-violet afterglow just past the boundary, fading the same way
  float qr = (ndl + 0.05) / 0.045;
  float wR = exp(-qr * qr);
  col += lin3(float3(0.80, 0.58, 0.74)) * 0.015 * wR * shadeV;

  // sun glint on water: a sharp lobe over a broad sheen (addendum (c) 3)
  float3 hv = normalize(uSun + ew);
  float nh = max(dot(nw, hv), 0.0);
  float glint = water * smoothstep(0.0, 0.25, ndl) * (0.55 * pow(nh, 420.0) + 0.022 * pow(nh, 22.0));
  col += mix(float3(1.0, 0.86, 0.66), float3(0.55, 0.75, 1.0), 0.35) * glint * (0.6 + 0.4 * lam);

  // atmosphere: Fresnel rim, 70 % on the lit limb, 12 % on the dark one
  float fr = pow(1.0 - clamp(dot(nv, ev), 0.0, 1.0), 3.2);
  float litRim = smoothstep(-0.30, 0.40, ndl);
  col = mix(col, atmo * mix(0.25, 1.0, litRim), fr * mix(0.12, 0.70, litRim));

  float3 outC = outP3(col);
  // limb antialiasing over the halo
  float a = cover + halo * (1.0 - cover);
  float3 pre = outC * cover + outP3(atmo) * halo * (1.0 - cover);
  return half4(half3(pre), half(a));
}
""" }
}
