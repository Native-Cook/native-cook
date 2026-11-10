#include <metal_stdlib>
using namespace metal;

struct RainWindowForecastUniforms {
    float2 uRes;
    float uT;
    float uLoop;
    float uWipe[72];
};
struct RainWindowForecastVertex {
    float4 position [[position]];
    float2 uv;
};
vertex RainWindowForecastVertex rainWindowForecastVertex(uint id [[vertex_id]]) {
    float2 p = float2((id << 1) & 2, id & 2);
    return {float4(p * float2(2, -2) + float2(-1, 1), 0, 1), p};
}
struct RainWindowForecastShader {
    texture2d<float> sharpTexture;
    texture2d<float> blur;
    constant RainWindowForecastUniforms &u;
    float2 uRes;
    float uT;
    float uLoop;
    constant float *uWipe;
    float uWipeR = 46.0;
    float3 rainWindowForecastSampleImage(texture2d<float> image, float2 xy) {
        constexpr sampler s(coord::normalized, address::clamp_to_edge, filter::linear);
        float2 imageSize = float2(image.get_width(), image.get_height());
        float scale = max(uRes.x/imageSize.x, uRes.y/imageSize.y);
        float2 origin = (uRes - imageSize*scale)*0.5;
        float3 rgb = image.sample(s, (xy-origin)/(imageSize*scale)).rgb;
        // Skia's ImageShader converts its sRGB image into the P3 canvas working space
        // before the parent runtime effect evaluates. Reproduce that child-shader contract;
        // the parent effect's explicit output conversion below remains untouched.
        float3 linear = select(pow((rgb + 0.055)/1.055, float3(2.4)), rgb/12.92, rgb <= 0.04045);
        float3 p3 = float3(dot(linear, float3(0.82246197, 0.17753803, 0.0)),
                           dot(linear, float3(0.03319420, 0.96680580, 0.0)),
                           dot(linear, float3(0.01708263, 0.07239744, 0.91051993)));
        float3 encoded = select(1.055*pow(p3, float3(1.0/2.4)) - 0.055, p3*12.92, p3 <= 0.0031308);
        return float3(half3(encoded));
    }
float rainWindowForecastSq(float v) { return v*v; }
float rainWindowForecastH1(float n) { return fract(sin(n*91.3458)*47453.5453); }
float rainWindowForecastH2(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7)))*43758.5453); }
float rainWindowForecastVnoise(float2 p) {
  float2 i = floor(p), f = fract(p);
  f = f*f*(3.0 - 2.0*f);
  return mix(mix(rainWindowForecastH2(i), rainWindowForecastH2(i + float2(1, 0)), f.x), mix(rainWindowForecastH2(i + float2(0, 1)), rainWindowForecastH2(i + float2(1, 1)), f.x), f.y);
}

float3 rainWindowForecastFogged(float2 xy) {
  float3 b = rainWindowForecastSampleImage(blur, xy);
  float l = dot(b, float3(0.3, 0.55, 0.15));
  // paler and flatter, cool where it is dark, warm lights still glowing through
  float3 f = mix(float3(l), b, 0.65)*0.58 + float3(0.2, 0.22, 0.28);
  return f + b*0.25*smoothstep(0.45, 0.9, l);
}

// the fog cleared by the finger, 0 … 1
float rainWindowForecastWiped(float2 xy) {
  float c = 0.0;
  for (int i = 0; i < 23; i++) {
    float a0 = uWipe[i*3 + 2], a1 = uWipe[i*3 + 5];
    if (a0 >= 0.0 && a1 >= 0.0 && min(a0, a1) < 6.0) {
      float2 p0 = float2(uWipe[i*3], uWipe[i*3 + 1]), p1 = float2(uWipe[i*3 + 3], uWipe[i*3 + 4]);
      float2 pa = xy - p0, ba = p1 - p0;
      float k = clamp(dot(pa, ba)/max(dot(ba, ba), 1e-3), 0.0, 1.0);
      float d = length(pa - ba*k);
      float age = mix(a0, a1, k);
      // the fog comes back from the edges of the stroke inwards, with a ragged front
      float grow = smoothstep(1.2, 5.0, age + 1.2*rainWindowForecastVnoise(xy/7.0) + 0.4*rainWindowForecastVnoise(xy/2.5));
      float r = max(uWipeR*(1.0 - grow), 0.5);
      c = max(c, (1.0 - smoothstep(r*0.55, r, d))*(1.0 - grow*0.6));
    }
  }
  return c;
}

// one rainWindowForecastColumn of running drops; returns the drop's lens in xyz (offset x, offset y, mask) and the trail in w
float4 rainWindowForecastColumn(float2 xy, float cw, float seed, float size) {
  float ci = floor(xy.x/cw);
  float4 o = float4(0.0);
  for (int k = 0; k < 2; k++) {
    float id = ci*7.13 + float(k)*3.71 + seed;
    if (rainWindowForecastH1(id) < 0.5) continue;
    float laps = 1.0 + floor(rainWindowForecastH1(id + 1.0)*2.0);      // whole laps per loop
    float span = uRes.y + 160.0;
    float u = fract(rainWindowForecastH1(id + 2.0) - uT/uLoop*laps);
    // stick and slip: the pace swings, but never backwards
    float y = (1.0 - u)*span - 80.0;
    y += 22.0*sin(y*0.045 + id);
    float x = (ci + 0.25 + 0.5*rainWindowForecastH1(id + 3.0))*cw + 2.0*sin(y*0.08 + id);
    float r = size*(0.75 + 0.5*rainWindowForecastH1(id + 4.0));
    float2 d = (xy - float2(x, y))/float2(r, r*1.25);
    float l = length(d);
    if (l < 1.0) o.xyz = float3(d, 1.0 - smoothstep(0.82, 1.0, l));
    // the trail above it: a clear wavy line, a few small beads
    float above = y - xy.y;
    if (above > 0.0 && above < 150.0) {
      float tx = x + 2.0*sin((xy.y)*0.08 + id);
      float wdt = r*0.6*(1.0 - above/150.0);
      float tr = 1.0 - smoothstep(wdt*0.6, wdt + 0.6, abs(xy.x - tx));
      o.w = max(o.w, tr*(1.0 - above/150.0));
      float by = (above - 14.0*floor(above/14.0)) - 7.0;
      float br = r*0.3*(1.0 - above/150.0)*step(0.4, rainWindowForecastH1(floor(above/14.0) + id));
      float2 bd = float2(xy.x - tx, by)/max(br, 0.01);
      float bl = length(bd);
      if (bl < 1.0 && o.z == 0.0) o.xyz = float3(bd*0.6, 1.0 - smoothstep(0.7, 1.0, bl));
    }
  }
  return o;
}

half4 rainWindowForecastShade(float2 xy) {
  float clear = rainWindowForecastWiped(xy);
  float4 a = rainWindowForecastColumn(xy, 40.0, 0.0, 8.0);
  float4 b = rainWindowForecastColumn(xy + float2(20.0, 0.0), 40.0, 19.0, 5.0);
  float trail = max(a.w, b.w);
  float c = max(clear, trail*0.85);
  float3 sharp = rainWindowForecastSampleImage(sharpTexture, xy);
  float3 col = mix(rainWindowForecastFogged(xy), sharp*0.92, c);
  // condensation beads on the fog
  float2 g = xy/11.0;
  float2 gi = floor(g);
  float br = (0.06 + 0.2*rainWindowForecastSq(rainWindowForecastH2(gi + 5.0)))*step(0.45, rainWindowForecastH2(gi));
  float2 bp = fract(g) - (0.25 + 0.5*float2(rainWindowForecastH2(gi + 1.0), rainWindowForecastH2(gi + 2.0)));
  float bl = length(bp)/max(br, 0.001);
  if (bl < 1.0) {
    float3 bead = col*0.82 + float3(1.0)*0.3*exp(-rainWindowForecastSq(length(bp/br - float2(-0.3, -0.35))/0.3));
    col = mix(col, bead, (1.0 - smoothstep(0.6, 1.0, bl))*(1.0 - c)*0.7);
  }
  // running drops: each a small lens, the street upside down and magnified
  float4 dr = a.z > 0.0 ? a : b;
  if (dr.z > 0.0) {
    float2 n = dr.xy;
    float3 seen = rainWindowForecastSampleImage(sharpTexture, xy - n*22.0);
    float nz = sqrt(max(1.0 - dot(n, n), 0.0));
    seen *= 0.55 + 0.45*nz;
    seen += float3(1.0)*0.55*exp(-rainWindowForecastSq(length(n - float2(-0.35, -0.45))/0.22));
    col = mix(col, seen, dr.z);
  }
  // a slight darkening at the screen's edges, as of a window frame's shadow
  float2 e = xy/uRes;
  col *= 0.86 + 0.14*smoothstep(0.0, 0.18, min(min(e.x, 1.0 - e.x), min(e.y, 1.0 - e.y)*0.6));
  float3 l = pow(max(col, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  float3 o = pow(max(m, float3(0.0)), float3(1.0/2.2));
  o += (fract(sin(dot(xy, float2(12.9898, 78.233)))*43758.5453) - 0.5)/255.0;
  return half4(half3(o), 1.0);
}

};
fragment half4 rainWindowForecastFragment(RainWindowForecastVertex in [[stage_in]],
    constant RainWindowForecastUniforms &u [[buffer(0)]],
    texture2d<float> sharp [[texture(0)]], texture2d<float> blur [[texture(1)]]) {
    RainWindowForecastShader shader {sharp, blur, u, u.uRes, u.uT, u.uLoop, u.uWipe};
    return shader.rainWindowForecastShade(in.uv*u.uRes);
}
