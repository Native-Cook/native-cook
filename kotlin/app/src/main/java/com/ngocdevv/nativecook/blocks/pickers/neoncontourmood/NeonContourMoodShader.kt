package com.ngocdevv.nativecook.blocks.pickers.neoncontourmood

import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.pickers.neoncontourmood.NeonContourMoodTheme as Theme
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

/** Moods as flat numbers: shapes (8 per mood) and ramps (4 colours x 3 channels per mood, sRGB 0..1). */
internal class NeonContourMoodPack(val n: Int, val shapes: DoubleArray, val ramps: DoubleArray) {
    companion object {
        const val STRIDE = 8
        const val RAMP_STRIDE = 12

        fun of(moods: List<NeonContourMoodMood>): NeonContourMoodPack {
            val shapes = ArrayList<Double>()
            val ramps = ArrayList<Double>()
            for (m in moods) {
                val s = m.shape
                val widthY = s.notchWidth
                shapes.addAll(listOf(s.w, s.h, s.corner, s.notchY, widthY, s.notchX, s.notchXWidth ?: widthY, m.warmth))
                for (h in m.ramp) {
                    ramps.add(((h shr 16) and 255) / 255.0)
                    ramps.add(((h shr 8) and 255) / 255.0)
                    ramps.add((h and 255) / 255.0)
                }
            }
            return NeonContourMoodPack(moods.size, shapes.toDoubleArray(), ramps.toDoubleArray())
        }
    }
}

/** Uniform block of the contour shader; one object, mutated in place every frame. */
internal class NeonContourMoodField {
    val center = floatArrayOf(0f, 0f)
    var scale = 1f
    val shapeA = FloatArray(28)
    val shapeB = FloatArray(28)
    val tints = FloatArray(21)
    val lowerTints = FloatArray(21)
    val coreTint = FloatArray(3)
    val ground = FloatArray(3)
    val coreCol = FloatArray(3)
    val lean = floatArrayOf(0f, 0f)
    var wake = 0f
    var breath = 0f
    var radiate = 0f
    var warm = 0f
    var pulse = -1f
    var cutoff = 1e6f
    var fade = 1f
    var spacing = 26f
    var rings = 7f

    init {
        // valid (the rest shape) from the first draw
        for (k in 0 until 7) {
            shapeA[k * 4] = Theme.CORE_WIDTH.toFloat(); shapeA[k * 4 + 1] = Theme.CORE_HEIGHT.toFloat()
            shapeA[k * 4 + 2] = Theme.CORE_CORNER.toFloat(); shapeA[k * 4 + 3] = Theme.NOTCH.toFloat()
            shapeB[k * 4] = Theme.NOTCH_WIDTH.toFloat(); shapeB[k * 4 + 1] = 0f
            shapeB[k * 4 + 2] = Theme.NOTCH_WIDTH.toFloat(); shapeB[k * 4 + 3] = 0f
        }
    }
}

internal object NeonContourMoodShaderMath {
    /** Position of each ring on the colour ramp, from its offset: hot ridge at 1, mid band at 2, outer glow at 3. */
    private val RING_STOP = DoubleArray(7) { i ->
        val o = Theme.RING_OFFSETS
        val off = o[i]
        val hot = o[2]
        val mid = o[4]
        val outer = o[6]
        if (off < hot) off / hot else if (off < mid) 1 + (off - hot) / (mid - hot) else 2 + (off - mid) / (outer - mid)
    }

    private fun lin(n: Double) = if (n <= 0.04045) n / 12.92 else ((n + 0.055) / 1.055).pow(2.4)

    /** The ramp colour at [stop] for a (fractional) mood, blended like the shape, written to out[o..o+2] (linear light or sRGB). */
    fun rampAt(out: DoubleArray, o: Int, pack: NeonContourMoodPack, mood: Double, stop: Double, linear: Boolean) {
        val last = pack.n - 1
        val m = if (mood < 0) 0.0 else if (mood > last) last.toDouble() else mood
        val i = floor(m).toInt()
        val j = min(last, i + 1)
        val fm = m - i
        val u = fm * fm * (3 - 2 * fm)
        val k = if (stop < 0) 0.0 else if (stop > 3) 3.0 else stop
        val a = floor(k).toInt()
        val b = min(3, a + 1)
        val v = k - a
        val r = pack.ramps
        val rs = NeonContourMoodPack.RAMP_STRIDE
        for (c in 0 until 3) {
            val l = r[i * rs + a * 3 + c] + (r[i * rs + b * 3 + c] - r[i * rs + a * 3 + c]) * v
            val h = r[j * rs + a * 3 + c] + (r[j * rs + b * 3 + c] - r[j * rs + a * 3 + c]) * v
            val s = l + (h - l) * u
            out[o + c] = if (linear) lin(s) else s
        }
    }

    private val tmp = DoubleArray(3)

    /** Per-ring shape parameters and tints from the seven ring moods. */
    fun packField(u: NeonContourMoodField, echoes: DoubleArray, pack: NeonContourMoodPack) {
        val last = pack.n - 1
        val sh = pack.shapes
        val st = NeonContourMoodPack.STRIDE
        for (k in 0 until 7) {
            val e = echoes[k]
            val m = if (e < 0) 0.0 else if (e > last) last.toDouble() else e
            val a = floor(m).toInt()
            val b = min(last, a + 1)
            val fm = m - a
            val w = fm * fm * (3 - 2 * fm)
            val ia = a * st
            val ib = b * st
            val o = k * 4
            for (c in 0 until 4) u.shapeA[o + c] = (sh[ia + c] + (sh[ib + c] - sh[ia + c]) * w).toFloat()
            u.shapeB[o] = (sh[ia + 4] + (sh[ib + 4] - sh[ia + 4]) * w).toFloat()
            u.shapeB[o + 1] = (sh[ia + 5] + (sh[ib + 5] - sh[ia + 5]) * w).toFloat()
            u.shapeB[o + 2] = (sh[ia + 6] + (sh[ib + 6] - sh[ia + 6]) * w).toFloat()
            u.shapeB[o + 3] = 0f
            rampAt(tmp, 0, pack, e, RING_STOP[k], true)
            for (c in 0 until 3) u.tints[k * 3 + c] = tmp[c].toFloat()
            rampAt(tmp, 0, pack, e, min(RING_STOP[k], 2.0), true)
            for (c in 0 until 3) u.lowerTints[k * 3 + c] = tmp[c].toFloat()
            if (k == 0) {
                rampAt(tmp, 0, pack, e, 0.0, true)
                for (c in 0 until 3) u.coreTint[c] = tmp[c].toFloat()
                u.warm = (sh[ia + 7] + (sh[ib + 7] - sh[ia + 7]) * w).toFloat()
            }
        }
    }
}

private fun f(v: Double): String = if (v == Math.floor(v) && !v.isInfinite()) "${v.toLong()}.0" else "$v"

private val RING_IFS = (0 until 7).joinToString("\n    ") { i ->
    "if(k==$i) { off=${f(Theme.RING_OFFSETS[i])}; strength=${f(Theme.RING_STRENGTH[i])}; vr=${f(Theme.VERTICAL_RATIOS[i])}; }"
}

private const val COMMON_FNS = """
float3 linearize(float3 c) { return mix(c/12.92,pow((c+0.055)/1.055,float3(2.4)),step(float3(0.04045),c)); }
float3 encode(float3 c) { c=max(c,float3(0.0)); return mix(c*12.92,1.055*pow(c,float3(1.0/2.4))-0.055,step(float3(0.0031308),c)); }
float3 display(float3 c, float mode) {
  if(mode < 0.5) return c;
  float3 l=linearize(c);
  float3 p=float3(dot(l,float3(0.8225929,0.177534,0.0)),dot(l,float3(0.0331995,0.9667835,0.0)),dot(l,float3(0.0170853,0.0723957,0.9103015)));
  return mix(c,encode(p),mode);
}
"""

/**
 * The RN block's contour field (`ncm-shaders.ts`) as AGSL (API 33+): one field, seven rings of a notched rounded box blended between
 * moods, lit by a colour ramp. All distances are reference pt (dp here); p3 stays 0 on Android.
 */
internal val NeonContourMoodFieldSource = """
uniform float2 center;
uniform float scale;
uniform float4 shapeA[7];
uniform float4 shapeB[7];
uniform float3 tints[7];
uniform float3 lowerTints[7];
uniform float3 coreTint;
uniform float3 ground;
uniform float3 coreCol;
uniform float2 lean;
uniform float wake;
uniform float breath;
uniform float radiate;
uniform float warm;
uniform float pulse;
uniform float cutoff;
uniform float fade;
uniform float spacing;
uniform float rings;
uniform float p3;

float distanceAt(float2 p, float4 a, float4 b) {
  float2 q0=abs(p);
  float2 pp=float2(q0.x+b.y*exp(-q0.y*q0.y/(b.z*b.z)), q0.y+a.w*exp(-q0.x*q0.x/(b.x*b.x)));
  float2 q=pp-a.xy+float2(a.z);
  return length(max(q,float2(0.0)))+min(max(q.x,q.y),0.0)-a.z;
}
$COMMON_FNS
half4 main(float2 xy) {
  float2 p=(xy-center)/max(scale,0.01);
  if (xy.y > cutoff) return half4(half3(display(ground,p3)),1.0);
  float2 p0=p-lean;
  float d0=distanceAt(p0,shapeA[0],shapeB[0])-breath;
  float core=1.0-smoothstep(-8.0,2.0,d0);
  float3 col=mix(ground,coreCol,core);
  float3 energy=float3(0.0);
  for(int k=0;k<7;k++) {
    float fk=float(k); float off=0.0; float strength=0.0; float vr=0.0;
    $RING_IFS
    float4 A=shapeA[k]; float4 B=shapeB[k];
    float2 pk=p-lean*(1.0-fk/7.0);
    float ny=max(0.0,A.w)/${f(Theme.NOTCH)}; float nx=max(0.0,B.y)/${f(Theme.NOTCH)};
    float hw=60.0+off*${f(Theme.HOLLOW_WIDTH_GAIN)}; float hw2=hw*hw;
    float hollow=ny*off*${f(Theme.HOLLOW_BASE)}*exp(-(pk.x*pk.x)/hw2)*smoothstep(20.0,95.0,abs(pk.y))
      +nx*off*${f(Theme.HOLLOW_BASE)}*exp(-(pk.y*pk.y)/hw2)*smoothstep(20.0,95.0,abs(pk.x));
    float vs=1.0+ny*off*vr/(${f(Theme.CORE_HEIGHT)}+off);
    float hs=1.0+nx*off*vr/(${f(Theme.CORE_WIDTH)}+off);
    float dk=distanceAt(float2(pk.x/hs,pk.y/vs),A,B)-breath;
    float travel=radiate*fk/6.0;
    float d=dk+hollow-off*(spacing/26.0)*(1.0+0.10*wake)-travel;
    float zone=smoothstep(-9.0,6.0,d0);
    float line=exp(-d*d/0.52)*strength;
    float halo=exp(-abs(d)/${f(Theme.RING_HALO)})*strength;
    float softness=k==2 ? 145.0 : (k==6 ? 1000.0 : 420.0);
    float band=exp(-d*d/softness);
    float lower=smoothstep(-10.0,150.0,p.y);
    float3 light=mix(tints[k],lowerTints[k],lower*0.85);
    float weight=k==2 ? ${f(Theme.HOT_WEIGHT)} : (k==4 ? ${f(Theme.MID_WEIGHT)} : (k==6 ? ${f(Theme.OUTER_WEIGHT)} : ${f(Theme.OTHER_WEIGHT)}));
    float enabled=1.0-step(rings,fk);
    float3 lineTint=mix(light,float3(1.0),k==2 ? 0.12 : (k==4 ? 0.06 : 0.035));
    float pq=(pulse-${f(Theme.PULSE_STEP)}*fk-0.06)/0.045;
    float boost=1.0+(pulse<0.0 ? 0.0 : 1.4*exp(-(pq*pq)));
    energy+=(light*(halo*0.32+band*weight*(1.0+lower*0.3))+lineTint*line*(0.8+lower*0.3)/${f(Theme.GAIN)})*enabled*zone*(1.0+0.20*wake)*boost;
  }
  float3 inner=coreTint*exp(-d0*d0/40.0)*0.9;
  energy+=inner;
  float notchPhase=(abs(p0.y)-${f(Theme.WARM_Y)})/${f(Theme.WARM_SPREAD)};
  float notchWarm=exp(-(p0.x*p0.x)/${f(Theme.WARM_WIDTH * Theme.WARM_WIDTH)})*exp(-(notchPhase*notchPhase))*(1.0-smoothstep(180.0,220.0,abs(p0.y)));
  energy+=coreTint*notchWarm*${f(Theme.WARM_STRENGTH)}*(max(0.0,shapeA[0].w)/${f(Theme.NOTCH)})*warm*smoothstep(-9.0,6.0,d0);
  float quiet=1.0-smoothstep(cutoff-fade,cutoff,xy.y);
  float3 lin=linearize(col)+energy*quiet*${f(Theme.GAIN)};
  float3 shoulder=lin/(1.0+lin*0.38);
  col=encode(shoulder);
  float grain=fract(sin(dot(floor(xy*3.0),float2(12.9898,78.233)))*43758.5453)-0.5;
  col+=grain/510.0;
  return half4(half3(display(clamp(col,0.0,1.0),p3)),1.0);
}
"""

/** The thumb: dark glass centre, reflected pool in the mood's colour and a fine rim. */
internal val NeonContourMoodThumbSource = """
uniform float2 center;
uniform float radius;
uniform float3 hue;
uniform float3 ground;
uniform float press;
uniform float p3;
$COMMON_FNS
half4 main(float2 xy) {
 float2 p=(xy-center)/radius; float r=length(p);
 float3 col=ground;
 col+=hue*(exp(-dot(p,p)/0.045)*0.65+exp(-r*r/0.5)*0.11);
 float rim=(r-0.975)/0.018;
 float edge=exp(-(rim*rim));
 col+=mix(hue,float3(0.65),0.4)*edge*(0.25+0.10*p.x+0.15*p.y+press*0.3);
 return half4(half3(display(clamp(col,0.0,1.0),p3)),1.0-smoothstep(0.98,1.02,r));
}
"""

@RequiresApi(33)
internal class NeonContourMoodFieldShader {
    val shader = RuntimeShader(NeonContourMoodFieldSource)
    val paint = Paint().apply { shader = this@NeonContourMoodFieldShader.shader }

    fun apply(u: NeonContourMoodField) {
        shader.setFloatUniform("center", u.center)
        shader.setFloatUniform("scale", u.scale)
        shader.setFloatUniform("shapeA", u.shapeA)
        shader.setFloatUniform("shapeB", u.shapeB)
        shader.setFloatUniform("tints", u.tints)
        shader.setFloatUniform("lowerTints", u.lowerTints)
        shader.setFloatUniform("coreTint", u.coreTint)
        shader.setFloatUniform("ground", u.ground)
        shader.setFloatUniform("coreCol", u.coreCol)
        shader.setFloatUniform("lean", u.lean)
        shader.setFloatUniform("wake", u.wake)
        shader.setFloatUniform("breath", u.breath)
        shader.setFloatUniform("radiate", u.radiate)
        shader.setFloatUniform("warm", u.warm)
        shader.setFloatUniform("pulse", u.pulse)
        shader.setFloatUniform("cutoff", u.cutoff)
        shader.setFloatUniform("fade", u.fade)
        shader.setFloatUniform("spacing", u.spacing)
        shader.setFloatUniform("rings", u.rings)
        shader.setFloatUniform("p3", 0f)
    }
}

@RequiresApi(33)
internal class NeonContourMoodThumbShader {
    val shader = RuntimeShader(NeonContourMoodThumbSource)
    val paint = Paint().apply { shader = this@NeonContourMoodThumbShader.shader }

    fun apply(cx: Float, cy: Float, radius: Float, hue: FloatArray, ground: FloatArray, press: Float) {
        shader.setFloatUniform("center", cx, cy)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("hue", hue)
        shader.setFloatUniform("ground", ground)
        shader.setFloatUniform("press", press)
        shader.setFloatUniform("p3", 0f)
    }
}

