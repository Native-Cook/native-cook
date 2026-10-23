package com.ngocdevv.nativecook.blocks.navigation.holoagendacarousel

import android.graphics.RuntimeShader
import android.os.Build
import kotlin.math.*

/** The original SkSL expressions and uniforms, unchanged for Android RuntimeShader. */
@android.annotation.TargetApi(33)
internal class HoloShader {
    val shader = RuntimeShader(SOURCE)
    fun update(kind: Int, width: Float, height: Float, radius: Float, clock: Float,
               shift: Float = 0f, velocity: Float = 0f, detail: Float = 0f,
               age: Float = 900f, fade: Float = 0f, surge: Float = 0f) {
        val loop = HoloTheme.mod(clock, HoloTheme.loop) / HoloTheme.loop
        val angle = loop * PI.toFloat() * 2
        shader.setFloatUniform("uSize", width, height)
        shader.setFloatUniform("uRadius", radius)
        shader.setFloatUniform("uKind", kind.toFloat())
        shader.setFloatUniform("uPal", paletteFloats[kind])
        shader.setFloatUniform("uPhase", HoloTheme.mod(loop+shift+surge+HoloTheme.phases[kind], 1f))
        shader.setFloatUniform("uShift", shift)
        shader.setFloatUniform("uVel", velocity)
        shader.setFloatUniform("uSwirl", cos(angle), sin(angle))
        shader.setFloatUniform("uDetail", detail)
        shader.setFloatUniform("uAge", age)
        shader.setFloatUniform("uFade", fade)
    }
    companion object {
        private val paletteFloats = HoloTheme.palettes.map { palette ->
            palette.flatMap { color -> listOf((color shr 16)/255f, ((color shr 8) and 255)/255f, (color and 255)/255f) }.toFloatArray()
        }
        private const val SOURCE = """

uniform float2 uSize;
uniform float uRadius;
uniform float uKind;
uniform float3 uPal[6];
uniform float uPhase;
uniform float uShift;
uniform float uVel;
uniform float uArrive;
uniform float2 uSwirl;
uniform float uDetail;
uniform float uAge;
uniform float uFade;
float hash21(float2 p) { return fract(sin(dot(p, float2(127.1,311.7))) * 43758.5453); }
float noise21(float2 p) {
  float2 cell = floor(p);
  float2 f = fract(p);
  float2 w = f*f*(3.0-2.0*f);
  return mix(mix(hash21(cell), hash21(cell+float2(1,0)), w.x),
    mix(hash21(cell+float2(0,1)), hash21(cell+float2(1,1)), w.x), w.y);
}
// One cycle of each card's colours, in the order they lie outward from the source at one moment.
// The stops are uneven on purpose: broad bodies of colour with thin bright rings between them.
float3 palette(float v) {
  float u = fract(v);
  float3 c;
  if (uKind < 0.5) {
    // Two identical ring births per cycle: magenta core, blue body, a thin mint ring.
    float w = fract(u*2.0);
    c = mix(uPal[0],uPal[1],smoothstep(0.30,0.44,w));
    c = mix(c,uPal[2],smoothstep(0.44,0.58,w));
    c = mix(c,uPal[3],smoothstep(0.64,0.80,w));
    c = mix(c,uPal[4],smoothstep(0.80,0.88,w));
    c = mix(c,uPal[0],smoothstep(0.91,1.0,w));
  } else if (uKind < 1.5) {
    // A broad red body, then cream, ice and pink rings; a narrow red; the same rings again.
    c = mix(uPal[1],uPal[0],smoothstep(0.0,0.28,u));
    c = mix(c,uPal[2],smoothstep(0.355,0.39,u));
    c = mix(c,uPal[3],smoothstep(0.41,0.45,u));
    c = mix(c,uPal[4],smoothstep(0.48,0.53,u));
    c = mix(c,uPal[0],smoothstep(0.55,0.60,u));
    c = mix(c,uPal[2],smoothstep(0.655,0.69,u));
    c = mix(c,uPal[3],smoothstep(0.71,0.75,u));
    c = mix(c,uPal[4],smoothstep(0.78,0.84,u));
    c = mix(c,uPal[1],smoothstep(0.87,0.95,u));
  } else if (uKind < 2.5) {
    // A purple disc edged in periwinkle and cyan with mint beyond it, a long purple, one thin
    // mint ring. The thin ring is born first and the band a second later; the band is narrow
    // here but doubles its radius as it travels, which is what fills the card around the dome.
    c = mix(uPal[0],uPal[2],smoothstep(0.045,0.075,u));
    c = mix(c,uPal[3],smoothstep(0.075,0.10,u));
    c = mix(c,uPal[4],smoothstep(0.10,0.13,u));
    c = mix(c,uPal[3],smoothstep(0.15,0.18,u));
    c = mix(c,uPal[5],smoothstep(0.18,0.21,u));
    c = mix(c,uPal[1],smoothstep(0.21,0.26,u));
    c = mix(c,uPal[0],smoothstep(0.26,0.40,u));
    c = mix(c,uPal[4],smoothstep(0.495,0.515,u));
    c = mix(c,uPal[0],smoothstep(0.525,0.555,u));
  } else {
    // Magenta, red, a cream band, red, magenta, then the long fall through the pinks.
    c = mix(uPal[0],uPal[1],smoothstep(0.12,0.24,u));
    c = mix(c,uPal[2],smoothstep(0.27,0.31,u));
    c = mix(c,uPal[1],smoothstep(0.35,0.40,u));
    c = mix(c,uPal[0],smoothstep(0.44,0.56,u));
    c = mix(c,uPal[3],smoothstep(0.60,0.70,u));
    c = mix(c,uPal[4],smoothstep(0.70,0.82,u));
    c = mix(c,uPal[5],smoothstep(0.82,0.90,u));
    c = mix(c,uPal[0],smoothstep(0.92,1.0,u));
  }
  return c;
}
// |q - drift*s| = s. In ellipse space |drift| < 1, so there is just one
// non-negative root. Each larger contour moves away from the source.
float eccentricRing(float2 q, float2 drift) {
  float a = 1.0-dot(drift,drift);
  float b = dot(q,drift);
  return max(0.0,(-b+sqrt(max(b*b+a*dot(q,q),0.0)))/a);
}
half4 main(float2 xy) {
  float2 uv = xy/uSize;
  float speed = abs(uVel);
  float belly = sin(clamp(uv.y,0.0,1.0)*3.141593);
  uv.x += clamp(uVel*0.035,-0.24,0.24)*belly;
  float warpAmp = 0.025 + speed*0.004;
  uv += warpAmp * float2(noise21(uv*2.6 + uSwirl*0.6)-0.5,
    noise21(uv*3.1 + uSwirl.yx*0.7 + 7.3)-0.5);
  // Every card is the same law: rings are born at a source and grow away from it, each larger
  // ring centred further out, and a ring's size grows exponentially with time. So the colour is
  // the palette at RINGS*log(1 + s/BIRTH) - phase: continuous, and it never has to restart.
  float ring = 0.0;
  float waist = 0.0;
  float far = 0.0;
  float pale = 0.0;
  if (uKind < 0.5) {
    float upper = eccentricRing((uv-float2(0.5,0.40))/float2(1.9,1.0),float2(0,-0.85));
    float lower = eccentricRing((uv-float2(0.5,0.62))/float2(1.9,1.0),float2(0,0.85));
    ring = min(upper,lower);
    // The near edges crowd at the sources; the white gap flares toward either side.
    float flare = 0.22*(uv.x-0.5)*(uv.x-0.5);
    waist = 1.0-smoothstep(0.073+flare,0.118+flare,abs(uv.y-0.51));
    far = smoothstep(0.22,0.42,ring)*0.85;
    pale = smoothstep(0.07,0.26,ring)*0.42;
  } else if (uKind < 1.5) {
    float2 p = uv-float2(0.34,0.70);
    float2 ellipse = float2(0.819*p.x-0.574*p.y,0.574*p.x+0.819*p.y);
    ring = eccentricRing(ellipse/float2(1.0,0.52),float2(0.40,-0.08));
  } else if (uKind < 2.5) {
    ring = eccentricRing(uv-float2(0.5,0.12),float2(0,0.85));
  } else {
    // Bands are rings so wide they look straight; their source sits just above the card.
    ring = max(0.0,uv.y+0.05);
  }
  // How many palette cycles fit in one e-fold of ring size; with the card's flow rate this sets
  // how fast a ring grows (measured on the recording: 2 to 2.7 e-folds a second).
  float rings = uKind < 0.5 ? 0.17 : uKind < 1.5 ? 0.15 : uKind < 2.5 ? 0.143 : 0.14;
  float field = rings*log(1.0+ring/0.045);
  // A few broad colours survive at icon/capsule size; discs retain the complete field.
  float small = uDetail < 0.5 ? 1.0-smoothstep(20.0,36.0,min(uSize.x,uSize.y)) : 0.0;
  field = mix(field,0.20+uv.x*0.09+uv.y*0.08,small);
  float phase = field-uPhase;
  float fringe = speed*0.012;
  float3 rgb = float3(palette(phase+fringe).r,palette(phase).g,palette(phase-fringe).b);
  // WELCOME pales toward its far corners and keeps a white waist between the two funnels.
  rgb = mix(rgb,mix(rgb,float3(1.0),0.55),pale);
  rgb = mix(rgb,mix(uPal[4],uPal[5],0.6),far);
  rgb = mix(rgb,float3(1.0),waist);
  // The dome's mint is green above it and turns to cyan below its equator.
  if (uKind > 1.5 && uKind < 2.5) {
    float mint = smoothstep(0.10,0.55,rgb.g-rgb.r);
    rgb = mix(rgb,uPal[3],mint*smoothstep(0.25,0.70,uv.y));
  }
  if (uDetail > 0.5) {
    // Lighting uses the unwarped face: the foil moves beneath a fixed studio lamp.
    float2 face = xy / uSize;
    float lamp = 0.5 + 0.55*uShift + 0.05*uVel + 0.05*uSwirl.y;
    float diagonal = face.x*0.76 + face.y*0.34;
    float sheen = exp(-((diagonal-lamp)*(diagonal-lamp))/0.016)
      * (0.14 + 0.16*min(1.0,speed/6.0));
    float passProgress = clamp(uAge/500.0,0.0,1.0);
    float passAt = -0.2 + passProgress*1.6;
    float flash = exp(-((diagonal-passAt)*(diagonal-passAt))/0.014)
      * sin(passProgress*3.141593)*0.34;
    float2 hot = face-float2(0.18+uShift*0.025+uVel*0.005,0.16);
    float spot = exp(-dot(hot,hot)/0.0484)*0.10;
    rgb += (1.0-rgb)*(sheen+flash+spot);
    rgb += (1.0-rgb)*(1.0-smoothstep(0.0,0.38,face.y))*0.05;
    rgb *= 1.0-smoothstep(0.78,1.0,face.y)*0.07;
    float grain = (hash21(floor(xy))-0.5)*0.016;
    float seed = hash21(floor(xy)+91.7);
    float glint = pow(max(0.0,sin(seed*6.283185+uShift*9.0+uVel*0.8)),24.0);
    rgb += grain + step(0.985,seed)*glint*0.14*min(1.0,speed+abs(uShift)*0.2);
    float2 centred = xy-uSize*0.5;
    float2 q = abs(centred)-(uSize*0.5-uRadius);
    float sdf = length(max(q,0.0))+min(max(q.x,q.y),0.0)-uRadius;
    float2 outward = max(q,0.0);
    float2 normal = length(outward)>0.001 ? normalize(outward)*sign(centred)
      : (q.x>q.y ? float2(sign(centred.x),0) : float2(0,sign(centred.y)));
    float inner = max(0.0,-sdf);
    float upper = max(0.0,-normal.y) + max(0.0,-normal.x)*0.65;
    float highlight = (1.0-smoothstep(0.0,1.25,inner))
      * min(1.0,upper)*(0.70-0.45*face.x)*(1.0-face.y*0.6);
    float lower = clamp(normal.y+max(0.0,normal.x)*0.45,0.0,1.0);
    float thickness = (1.0-smoothstep(1.8,3.0,inner))*lower;
    float edge = (1.0-smoothstep(0.0,0.75,inner))*lower;
    float luminance = dot(rgb,float3(0.2126,0.7152,0.0722));
    rgb = mix(rgb,mix(float3(luminance),rgb,1.10)*0.62,thickness);
    rgb *= 1.0-edge*0.22;
    rgb += (1.0-rgb)*highlight;
  }
  // Reflections sample the same field upside down; fade in physical points after mirroring.
  float alpha = uFade > 0.0 ? 1.0-smoothstep(0.0,uFade,uSize.y-xy.y) : 1.0;
  return half4(clamp(rgb,0.0,1.0)*alpha,alpha);
}
        """
    }
}
