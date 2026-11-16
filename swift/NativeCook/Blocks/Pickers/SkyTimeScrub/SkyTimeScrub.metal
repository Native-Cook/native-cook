#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Faithful sts-shaders.ts translation. The two passes share every scattering and ground term.
namespace SkyTimeScrubShader {
constant float BLOOM_CORE = 16.0;
constant float BLOOM_HALO = 18.0;
constant float BLOOM_HIGH = 7.0;
struct Scene {
    float scale;
    float horizon;
    float arcCentre;
    float arcAmplitude;
    float rulerY;
    float rulerOffset;
    float sunX;
    float sunH;
    float elevation;
    float ev;
    float evTarget;
    float held;
    float press;
    float turbidity;
    float layer;
    float outputP3;
    float3 tickColor;
    float3 groundColor;


float sq(float x) { return x*x; }
float stsSaturate(float x) { return clamp(x, 0.0, 1.0); }
float hash21(float2 p) {
  float3 p3 = fract(float3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}
float roundedBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
// Source arch: rounded crest with flatter, steeper-turning tails than a Gaussian.
// Returns height and analytic derivative for constant-width screen-space antialiasing.
float2 sourceArc(float x, float centre, float amplitude) {
  float q = clamp((x-201.0)/201.0,-1.0,1.0);
  float q2 = q*q;
  float exponent = 1.75-0.75*q2+1.63*q2*q2;
  float base = max(0.0001,cos(q*1.57079633));
  float kern = pow(base,exponent);
  float slope = -2.0*amplitude*kern/201.0*((-1.5*q+6.52*q*q2)*log(base)
    -exponent*1.57079633*sin(q*1.57079633)/base);
  return float2(centre+amplitude-2.0*amplitude*kern,slope);
}
float3 toLinear(float3 c) { return pow(max(c, 0.0), float3(2.2)); }
float3 toP3(float3 c) {
  float3 v = toLinear(c);
  return pow(max(float3(0.82259287*v.r+0.17753395*v.g,
    0.03319951*v.r+0.96678350*v.g,
    0.01708535*v.r+0.07239572*v.g+0.91030148*v.b), 0.0), float3(1.0/2.2));
}
// The sensor: exposure, a soft shoulder, display gamma. Sky, bloom and the sun disc all go through it.
float3 display(float3 radiance, float exposure) {
  float3 r = radiance * exp2(exposure);
  float3 film = r / (1.0 + r);
  return pow(max(film,0.0),float3(1.0/2.2));
}
// Sun disc colour by air mass: #FFF6DA above 6 degrees, #FFC24A at 3, #FF6A1E at 0, #E0301A at -1.
float3 sunColour(float e) {
  float3 c = float3(0.8784,0.1882,0.1020);
  c = mix(c, float3(1.0,0.4157,0.1176), smoothstep(-1.0,0.0,e));
  c = mix(c, float3(1.0,0.7608,0.2902), smoothstep(0.0,3.0,e));
  c = mix(c, float3(1.0,0.9647,0.8549), smoothstep(3.0,6.0,e));
  return c;
}
void over(thread float3 &c, thread float &a, float3 sc, float sa) {
  float outA = sa + a*(1.0-sa);
  c = (sc*sa + c*a*(1.0-sa)) / max(outA, 0.0001);
  a = outA;
}
// Bloom: two analytic lobes (sigma 9 and 34 pt, weights 1 and 0.25) scaled by 2^(ev - evTarget), so the light
// flares exactly while the iris lags behind the scene. It is light in the lens: the ground does not cut it.
float3 sunBloom(float2 p, float h, float sx, float sunY, float elev, float exposureTarget) {
  float sunVis = smoothstep(-2.5,0.0,elev);
  float2 bloomD = p-float2(sx,sunY);
  float coreR = length(bloomD*float2(1.0,1.1));
  float wideR = length(bloomD*float2(1.0,1.45));
  float coreLobe = exp(-0.5*sq(coreR/9.0));
  float wideLobe = 0.25*exp(-0.5*sq(wideR/34.0))*(1.0-smoothstep(60.0,110.0,wideR));
  float horizonSun = exp(-sq(elev/11.0));
  float3 haloColour = mix(float3(1.0,0.26,0.01),float3(1.0,0.46,0.03),smoothstep(0.0,4.0,elev));
  haloColour = mix(haloColour,float3(1.0,0.72,0.30),smoothstep(5.0,12.0,elev));
  float3 coreColour = mix(float3(1.0,0.50,0.12),float3(1.0,0.84,0.52),smoothstep(0.0,5.0,elev));
  float hug = 0.25+0.75*exp(-abs(h-p.y)/50.0);
  float3 r = (coreColour*coreLobe*BLOOM_CORE+haloColour*wideLobe*BLOOM_HALO)*hug*horizonSun*sunVis*exp2(-exposureTarget);
  // A high afternoon sun still blooms in the pin (16:42 is a large white glow); it fades out well before noon.
  float highSun = smoothstep(8.0,16.0,elev)*exp(-sq(elev/22.0));
  r += float3(1.0,0.88,0.66)*highSun*exp(-0.5*sq(length(bloomD)/22.0))*BLOOM_HIGH*exp2(-exposureTarget);
  return r;
}
float3 atmosphere(float2 p, float h, float sx, float sh, float elev, float exposure, float exposureTarget, float haze) {
  float sy = sin(elev * 0.017453293);
  float vy = max((h-p.y) / 230.0, 0.002);
  float3 view = normalize(float3((p.x-201.0)/420.0, vy, 1.0));
  float sunY = h - sh;
  float3 sunDir = normalize(float3((sx-201.0)/420.0, max((h-sunY)/230.0, -0.3), 1.0));
  float mu = clamp(dot(view, sunDir), -1.0, 1.0);
  float rayPhase = 0.0596831*(1.0+mu*mu);
  float g = 0.76;
  float miePhase = 0.119366*(1.0-g*g)*(1.0+mu*mu)/((2.0+g*g)*pow(max(0.01,1.0+g*g-2.0*g*mu),1.5));
  float3 betaR = float3(0.0058,0.0135,0.0331);
  float3 betaM = float3(0.0007*haze);
  float3 betaO = float3(0.00065,0.00188,0.000085);
  float rayLength = min(300.0, 72.0 / max(view.y,0.12));
  float2 depth = float2(0.0);
  float ozoneDepth = 0.0;
  float3 scatter = float3(0.0);
  float sunAir = 1.0 / sqrt(max(0.00005,sy*sy+0.0007));
  float sunlight = smoothstep(-0.11,0.015,sy);
  for (int i=0; i<8; i++) {
    float u = (float(i)+0.5)/8.0;
    float distance = rayLength*u*u;
    float stepLength = rayLength*(2.0*float(i)+1.0)/64.0;
    float altitude = 0.06 + distance*view.y + distance*distance/12742.0;
    float r = exp(-altitude/8.0);
    float m = exp(-altitude/1.2);
    float ozone = altitude > 25.0 ? exp(-sq((altitude-25.0)/15.0)) : 0.0;
    depth += float2(r,m)*stepLength;
    ozoneDepth += ozone*stepLength;
    float3 viewExt = betaR*depth.x+betaM*depth.y+betaO*ozoneDepth;
    float3 sunExt = (betaR*r*8.0+betaM*m*1.2+betaO*ozone*15.0)*sunAir;
    float3 transmission = exp(-viewExt-sunExt);
    scatter += transmission*(betaR*r*rayPhase+betaM*m*miePhase*0.08)*stepLength;
  }
  float sunVis = smoothstep(-2.5,0.0,elev);
  // Forward scattering gathers the horizon within +-40 pt of the sun's azimuth (x1.6 at the sun's own column).
  float mieLobe = 1.0+0.6*exp(-sq((p.x-sx)/28.0))*exp(-max(h-p.y,0.0)/60.0)*exp(-sq(elev/18.0))*sunVis;
  // The pin's camera response: a cool, underexposed zenith with an open pale horizon.
  float3 radiance = scatter*sunlight*2.8*mieLobe;
  float twilight = exp(-sq((elev+3.0)/(elev > -3.0 ? 8.5 : 3.5)));
  float horizonAir = exp(-max(h-p.y,0.0)/30.0);
  radiance += float3(0.20,0.11,0.022)*twilight*horizonAir;
  float z = clamp(p.y/h,0.0,1.0);
  float night = 1.0-smoothstep(-14.0,1.0,elev);
  // Night is never one flat colour: indigo overhead falling to near black at the horizon, as in the pin (>= 6 L* apart).
  radiance += float3(0.0040,0.0031,0.0130)*night*(1.0-0.94*z*z);
  // Measured lens/sensor spectral calibration, fitted to seven source-day samples.
  // This grades integrated radiance, never substitutes a painted sky or state gradients.
  float3 logGain = float3(2.3669435,1.2419523,0.4597411)*z*z*z
    +float3(-2.4132490,0.2786327,0.8060074)*z*z
    +float3(1.7388733,0.6625861,0.9247634)*z
    +float3(-1.0389898,-0.6913476,0.0993291);
  radiance *= exp(logGain*smoothstep(2.0,25.0,elev));
  float3 duskGain = float3(-5.2695903,-15.9149735,-27.4408656)*z*z*z
    +float3(7.4814408,23.1133266,39.7888614)*z*z
    +float3(-2.9764492,-9.3795444,-16.2189541)*z
    +float3(0.9775943,1.8143568,2.5695081);
  radiance *= exp(duskGain*exp(-sq(elev/8.0)));
  // Golden hour (about 3 to 15 degrees): the sky holds far more warm light low on the horizon than the day and
  // dusk fits alone give. Fitted to source frames 170 to 175 (17:37 to 18:00) along the left edge, away from the sun.
  float goldenBump = exp(-sq((elev-9.5)/5.0));
  float3 golden = float3(1.0,0.85,0.0)*(0.55+2.7*z*z*z)*goldenBump;
  golden.b = -0.8*smoothstep(0.85,1.0,z)*goldenBump;
  radiance *= (1.0+golden)*(1.0-0.3*smoothstep(h-9.0,h,p.y));
  // Right at the golden hour the pin's zenith turns olive: blue and a little green leave it.
  float olive = exp(-sq((elev-4.5)/3.2));
  radiance *= float3(1.0,1.0-0.12*olive,1.0-0.3*olive*(1.0-0.5*smoothstep(0.85,1.0,z)));
  radiance += sunBloom(p,h,sx,sunY,elev,exposureTarget);
  float horizonSun = exp(-sq(elev/11.0));
  float coreR = length((p-float2(sx,sunY))*float2(1.0,1.1));
  // Low sunlight has lost its blue: near the sun the air only carries red and yellow (the red band along a sunset horizon).
  float nearSun = exp(-sq(coreR/70.0))*horizonSun*sunVis;
  radiance *= float3(1.0,1.0-0.35*nearSun,1.0-0.88*nearSun);
  float sunsetBand = exp(-sq((elev-0.5)/4.5))*smoothstep(0.86,1.0,z);
  radiance *= 1.0+float3(0.15,-0.25,-0.5)*sunsetBand;
  float3 colour = display(radiance, exposure);
  // Optical vignette, no separate decorative glow.
  colour *= 1.0-0.075*sq(abs(p.x-201.0)/201.0);
  // Twilight arch: #1B2A6B at the horizon to #0B0B1E overhead, centred on the sun's azimuth from -18 to 0 degrees.
  // It is the eye's dark adaptation made visible, so it arrives with the exposure.
  float adapt = smoothstep(-0.35,0.9,exposure);
  float archStrength = smoothstep(-18.0,-5.0,elev)*(1.0-smoothstep(-2.0,0.5,elev))*adapt;
  float archShape = exp(-sq((p.x-sx)/150.0))*exp(-max(h-p.y,0.0)/110.0);
  float3 archColour = mix(float3(0.0431,0.0431,0.1176),float3(0.1059,0.1647,0.4196),exp(-max(h-p.y,0.0)/45.0));
  colour = mix(colour,max(colour,archColour),archStrength*archShape);
  return colour;
}
// Below the horizon: the ground is not a slab. The horizon's colour hangs over it as haze and falls off toward the
// viewer, the lens glow carries on over it, and the low sun lays a soft reflection under itself. hz is the sky's
// display colour on the horizon line at this column. Both layers call this, so the full-resolution cut matches.
float3 groundAt(float2 p, float h, float3 hz, float sx, float sh, float elev, float exposure, float exposureTarget, float3 page) {
  float below = max(p.y-h,0.0);
  float fade = 1.0-smoothstep(100.0,160.0,below);
  // Distant ground takes the horizon's colour (aerial perspective), so the line is a soft band at every hour, not a cut:
  // 92 % of the horizon colour on the line, a quarter of it 10 pt below, the sky's faint fill on the near ground.
  float k = 0.70*exp(-below/5.0)+0.20*exp(-below/34.0)+0.05*exp(-below/120.0);
  float3 c = page+(hz-page)*k*fade;
  float sunVis = smoothstep(-2.5,0.0,elev);
  // The lens glow lies down on the ground: squashed toward the horizon, so it reads as lit haze, not a second disc.
  float3 glow = sunBloom(float2(p.x,h+below*2.4),h,sx,h-sh,elev,exposureTarget)*0.42;
  float spread = 12.0+below*0.4;
  float refl = exp(-sq((p.x-sx)/spread))*exp(-below/30.0)*exp(-sq(elev/7.0))*sunVis;
  glow += toLinear(sunColour(elev))*refl*0.9*exp2(-exposureTarget);
  return c+display(glow*fade,exposure);
}
float ticks(float2 p, float y, float offset, float focus, float swellAmount) {
  float x = p.x;
  float cell = floor((x-26.0+offset)/12.0+0.5);
  float tickX = 26.0+cell*12.0-offset;
  float swell = 10.0*exp(-sq((tickX-focus)/22.0))*swellAmount;
  float lengthY = 14.0+swell;
  float d = roundedBox(float2(x-tickX,p.y-y),float2(1.1,lengthY*0.5),0.8);
  return (1.0-smoothstep(-0.35,0.65,d))*smoothstep(23.0,27.0,x)*(1.0-smoothstep(374.0,379.0,x));
}
half4 render(float2 xy) {
  float2 p = xy / scale;
  float3 colour = groundColor;
  float alpha = 1.0;
  float sunY = horizon-sunH;
  if (layer > 0.5 &&
      (p.y < min(min(arcCentre-arcAmplitude-10.0,rulerY-32.0),sunY-10.0) ||
       p.y > max(arcCentre+arcAmplitude+10.0,rulerY+32.0))) return half4(0.0);
  if (layer < 0.5) {
    if (p.y < horizon+165.0) {
      float3 sky = atmosphere(float2(p.x,min(p.y,horizon)),horizon,sunX,sunH,elevation,ev,evTarget,turbidity);
      // The sky holds to just under the horizon; layer 1 cuts the line itself at full resolution.
      float edge = 1.0/(1.0+exp(clamp((p.y-horizon-3.0)/1.1,-30.0,30.0)));
      float3 ground = p.y > horizon-6.0 ? groundAt(p,horizon,sky,sunX,sunH,elevation,ev,evTarget,groundColor) : groundColor;
      colour = mix(ground,sky,edge);
      // Grain keeps the dark gradient from banding and rises as the eye dark-adapts: 0.5/255 to 2.5/255.
      float grain = mix(0.5,2.5,smoothstep(-0.35,0.9,ev));
      colour += (hash21(floor(xy*3.0))-0.5)*2.0*grain/255.0;
    }
  } else {
    colour = float3(0.0); alpha = 0.0;
    // The ground line is cut here at full resolution (the sky texture is a third of the size): black from the
    // horizon down, easing out over the reflection below. The sun pillar stands on it.
    float cut = smoothstep(horizon-1.6,horizon+1.4,p.y)*(1.0-smoothstep(horizon+6.0,horizon+9.0,p.y));
    colour = groundColor; alpha = cut;
    if (cut > 0.0) {
      float3 hz = atmosphere(float2(p.x,horizon),horizon,sunX,sunH,elevation,ev,evTarget,turbidity);
      colour = groundAt(p,horizon,hz,sunX,sunH,elevation,ev,evTarget,groundColor);
    }
    float pillarX = 1.0-smoothstep(3.0,7.0,abs(p.x-sunX));
    float pillarY = pow(1.0-smoothstep(horizon,horizon+46.0,p.y),1.3)*smoothstep(horizon-1.0,horizon+1.0,p.y);
    float pillar = 0.32*exp(-sq(elevation/5.0))*pillarX*pillarY*smoothstep(-3.0,-0.5,elevation);
    over(colour,alpha,sunColour(elevation)*0.6,pillar);
    float2 arc = sourceArc(p.x,arcCentre,arcAmplitude);
    float pathY = arc.x;
    float derivative = arc.y;
    float pathD = abs(p.y-pathY)/sqrt(1.0+derivative*derivative);
    float dashed = smoothstep(0.1,0.4,fract((p.x+p.y*0.3)/3.0));
    float line = (1.0-smoothstep(0.35,0.95,pathD))*mix(0.45,1.0,dashed)*0.22;
    over(colour,alpha,float3(0.57,0.62,0.65),line);
    // The sun is light: an HDR disc, limb-darkened, reddened by air mass, flattened by refraction,
    // its lower limb clipped by the horizon, and run through the same sensor as the sky.
    float flatten = mix(0.72,1.0,smoothstep(0.0,2.0,elevation));
    float2 sunDelta = (p-float2(sunX,sunY))/float2(1.0,flatten);
    float sunDistance = length(sunDelta);
    float cover = 1.0-smoothstep(6.6,8.8,sunDistance);
    float rho = clamp(sunDistance/7.7,0.0,1.0);
    float limb = 1.0-0.45*(1.0-sqrt(1.0-rho*rho));
    float gain = mix(60.0,140.0,smoothstep(0.0,10.0,elevation));
    float clipped = 1.0-smoothstep(horizon-1.2,horizon+0.8,p.y);
    // The limb is redder than the centre: it looks through more air at a glancing angle.
    float3 discBase = mix(sunColour(elevation),sunColour(elevation-1.2),rho*rho*rho*rho);
    float3 discColour = display(toLinear(discBase)*gain*limb,ev);
    over(colour,alpha,discColour,cover*clipped);
    // A dim sun marker only when it lies below the horizon; the shader sun owns daylight.
    float markerY = sourceArc(sunX,arcCentre,arcAmplitude).x;
    float marker = (1.0-smoothstep(7.4,8.5,length(p-float2(sunX,markerY))))*(1.0-smoothstep(-3.0,-1.0,elevation))*held;
    over(colour,alpha,float3(0.40,0.41,0.44),marker);
    // The handle inflates from a single tick; magnified ticks live 1.5 pt inside its rim and the
    // unmagnified ones are hidden beneath it, so no tick is ever drawn twice.
    float inflate = smoothstep(0.0,1.0,held);
    float lensAmount = smoothstep(0.05,0.6,held);
    float2 q = p-float2(201.0,rulerY);
    float2 lensHalf = float2(mix(1.1,14.0,inflate),mix(7.0,26.0,inflate));
    float lensD = roundedBox(q,lensHalf,min(lensHalf.x,13.5));
    float insideLens = (1.0-smoothstep(-0.5,0.5,lensD))*smoothstep(0.02,0.12,held);
    float clipIn = 1.0-smoothstep(-2.0,-1.0,lensD);
    float magnification = mix(1.0,1.6,inflate);
    float magnified = 201.0+(p.x-201.0)/magnification;
    float swellAmount = max(lensAmount,press);
    float tickOut = ticks(p,rulerY,rulerOffset,201.0,swellAmount)*(1.0-(1.0-smoothstep(0.4,1.6,lensD))*smoothstep(0.02,0.12,held));
    float tickIn = ticks(float2(magnified,p.y),rulerY,rulerOffset,201.0,swellAmount)*clipIn;
    float tick = max(tickOut,tickIn);
    float crossed = 1.0-smoothstep(199.0,204.0,p.x);
    float warm = exp(-sq(elevation/6.0))*0.65;
    float3 tickWarm = mix(tickColor,float3(0.86,0.69,0.34),warm);
    float3 tickColourNow = mix(float3(0.09,0.10,0.11),tickWarm,crossed);
    over(colour,alpha,tickColourNow,tick);
    if(lensAmount>0.0) {
      float rim = exp(-abs(lensD)/1.1);
      float innerRim = exp(-abs(lensD+3.5)/0.8)*0.18;
      float side = stsSaturate(0.5-q.x/28.0);
      float3 glass = mix(float3(0.22,0.26,0.29),tickWarm,0.12);
      float body = 0.12*insideLens*lensAmount;
      float3 rimLight = float3(0.16,0.21,0.26)*(rim*mix(0.32,0.85,side)+innerRim)*lensAmount;
      over(colour,alpha,glass,body);
      colour += rimLight;
      alpha = max(alpha,rim*0.7*lensAmount);
    }
  }
  colour = clamp(colour,0.0,1.0);
  if(outputP3>0.5) colour=toP3(colour);
  return half4(half3(colour*alpha),half(alpha));
}
};
inline half4 displaySRGB(half4 color) {
    if (color.a <= 0.0h) return half4(0.0h);
    float a = float(color.a);
    float3 c = float3(color.rgb) / a;
    c = select(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), c > 0.04045);
    c = float3(dot(c, float3(1.22474527, -0.22490437, 0.0)),
               dot(c, float3(-0.04205797, 1.04208100, 0.0)),
               dot(c, float3(-0.01964227, -0.07865492, 1.09853719)));
    c = max(c, 0.0);
    c = select(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, c > 0.0031308);
    return half4(half3(c * a), color.a);
}


}

[[ stitchable ]] half4 skyTimeScrub(float2 position, half4 color, device const float *u, int count) {
    if (count < 22) return half4(0.0h);
    SkyTimeScrubShader::Scene s;
    s.scale = u[0];
    s.horizon = u[1];
    s.arcCentre = u[2];
    s.arcAmplitude = u[3];
    s.rulerY = u[4];
    s.rulerOffset = u[5];
    s.sunX = u[6];
    s.sunH = u[7];
    s.elevation = u[8];
    s.ev = u[9];
    s.evTarget = u[10];
    s.held = u[11];
    s.press = u[12];
    s.turbidity = u[13];
    s.layer = u[14];
    s.outputP3 = u[15];
    s.tickColor = float3(u[16],u[17],u[18]);
    s.groundColor = float3(u[19],u[20],u[21]);
    return SkyTimeScrubShader::displaySRGB(s.render(position));
}
