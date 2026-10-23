package com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite

import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteMotion as M
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The contacts: the four avatars melt into one liquid blob, contact droplets are pulled in from the
 * screen edges, and the blob splits back into the four avatars. The field is drawn by an AGSL
 * shader on API 33+ (a plain union of circles below it, without the bridges).
 */
internal object FindFriendsInviteGoo {
    const val BALLS = 9

    class Ball(val x: Double, val y: Double, val r: Double)

    class Geo(val rest: List<Ball>, val cx: Double, val cy: Double, val width: Double)

    class Face(var tx: Double = 0.0, var ty: Double = 0.0, var s: Double = 1.0, var o: Double = 1.0)

    class DropState(var x: Double = 0.0, var y: Double = 0.0, var s: Double = 1.0, var o: Double = 0.0, var live: Boolean = false)

    class Frame {
        var on = false
        var goo = 0.0
        var gloss = 0.0
        /** Per ball: x, y, r (the shader's colours are set once). */
        val balls = FloatArray(BALLS * 3)
        val faces = Array(4) { Face() }
        val drops = Array(5) { DropState() }
    }

    /** sRGB floats for the four avatars, then the droplets. */
    fun colors(avatars: List<Int>): List<FloatArray> {
        fun rgb(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)
        return avatars.map(::rgb) + M.Goo.drops.map { rgb(it.color) }
    }

    fun avatarsShown(sigma: Double): Boolean = sigma < M.Goo.rnOff || sigma >= M.Goo.rnOn

    fun gooOn(sigma: Double): Boolean = sigma >= M.Goo.skiaOn && sigma < M.Goo.skiaOff

    fun frame(sigma: Double, g: Geo): Frame {
        val out = Frame()
        out.on = gooOn(sigma)
        if (!out.on) return out
        val goo = M.ease(M.easeInOut, M.seg(sigma, M.Goo.gooIn[0], M.Goo.gooIn[1])) *
            (1 - M.ease(M.easeOut, M.seg(sigma, M.Goo.gooOut[0], M.Goo.gooOut[1])))
        out.goo = goo
        out.gloss = goo

        val u = M.Goo.pullAmount * M.springStep(sigma - M.Goo.pullStart, M.Goo.pullOmega, M.Goo.pullZeta) *
            (1 - M.springStep(sigma - M.Goo.splitStart, M.Goo.splitOmega, M.Goo.splitZeta))

        var swell = 0.0
        for (d in M.Goo.drops) {
            val at = d.start + M.Goo.dropDur * 0.85
            swell += M.Goo.kickAmp * M.kick(sigma - at, M.Goo.kickPeriod, M.Goo.kickDecay, M.Goo.kickFadeFrom, M.Goo.kickEnd)
        }
        val env = M.seg(sigma, M.Goo.wobbleOn[0], M.Goo.wobbleOn[1]) *
            (1 - M.seg(sigma, M.Goo.wobbleOff[0], M.Goo.wobbleOff[1]))

        for (i in 0..3) {
            val b = g.rest[i]
            val ph = i * 1.7
            val ox = M.Goo.wobbleAmp * env * sin(2 * PI * M.Goo.wobbleFreq * sigma + ph)
            val oy = M.Goo.wobbleAmp * env * cos(2 * PI * M.Goo.wobbleFreq * 1.3 * sigma + ph)
            val x = b.x + u * (g.cx - b.x) + ox
            val y = b.y + u * (g.cy - b.y) + oy
            val r = b.r * (1 - M.Goo.pullShrink * M.clamp01(u)) * (1 + swell)
            out.balls[i * 3] = x.toFloat()
            out.balls[i * 3 + 1] = y.toFloat()
            out.balls[i * 3 + 2] = r.toFloat()

            val rk = M.Goo.rank[i]
            val back = M.Goo.faceStart + rk * M.Goo.faceStagger
            val o: Double
            val s: Double
            if (sigma < back) {
                val a = M.Goo.sinkStart + rk * M.Goo.sinkStagger
                val e = M.ease(M.easeOut, M.seg(sigma, a, a + M.Goo.sinkDur))
                o = 1 - e
                s = 1 - (1 - M.Goo.sinkScale) * e
            } else {
                o = M.ease(M.easeOut, M.seg(sigma, back, back + M.Goo.faceDur))
                s = M.Goo.faceFrom + (1 - M.Goo.faceFrom) * M.springStep(sigma - back, M.Goo.faceOmega, M.Goo.faceZeta)
            }
            out.faces[i] = Face(x - b.x, y - b.y, s, o)
        }

        for ((j, dr) in M.Goo.drops.withIndex()) {
            val q = M.seg(sigma, dr.start, dr.start + M.Goo.dropDur)
            if (q <= 0 || q >= 1) continue
            val e = M.ease(M.easeInOut, q)
            val sx = if (dr.side < 0) -M.Goo.dropOffscreen - dr.r else g.width + M.Goo.dropOffscreen + dr.r
            val sy = g.cy + dr.dy
            val ty = g.cy + dr.dy * 0.15
            val bow = (if (dr.dy >= 0) 1.0 else -1.0) * M.Goo.dropArc * sin(PI * e)
            val x = sx + (g.cx - sx) * e
            val y = sy + (ty - sy) * e + bow
            val r = dr.r * (1 - M.seg(e, M.Goo.dropAbsorb, 1.0))
            out.balls[(4 + j) * 3] = x.toFloat()
            out.balls[(4 + j) * 3 + 1] = y.toFloat()
            out.balls[(4 + j) * 3 + 2] = r.toFloat()
            out.drops[j] = DropState(
                x, y, r / dr.r,
                1 - M.ease(M.easeOut, M.seg(e, M.Goo.dropFade[0], M.Goo.dropFade[1])),
                true,
            )
        }
        return out
    }

    private val ballDecls = (0 until BALLS).joinToString("\n") { "uniform float4 b$it;\nuniform float3 k$it;" }
    private val ballCalls = (0 until BALLS).joinToString("\n") {
        "  ball(p, b$it, k$it, fs, gs, fu, gu, cw, ws, ctop, cmax);"
    }

    /** The metaball field: each ball adds r^2/d^2, the surface is the iso-line f = 1. */
    val AGSL = """
$ballDecls
uniform float goo;
uniform float gloss;

void ball(float2 p, float4 b, float3 k, inout float fs, inout float2 gs, inout float fu, inout float2 gu,
          inout float3 cw, inout float ws, inout float3 ctop, inout float3 cmax) {
  if (b.z <= 0.0) { return; }
  float2 dv = p - b.xy;
  float d2 = max(dot(dv, dv), 0.0001);
  float q = b.z * b.z / d2;
  float2 gq = -2.0 * q * dv / d2;
  fs += q;
  gs += gq;
  if (q > fu) { fu = q; gu = gq; cmax = k; }
  if (q >= 1.0) { ctop = k; }
  float e = d2 + 30.0;
  float w = b.z / (e * e * e);
  cw += k * w;
  ws += w;
}

half4 main(float2 p) {
  float fs = 0.0;
  float fu = 0.0;
  float2 gs = float2(0.0);
  float2 gu = float2(0.0);
  float3 cw = float3(0.0);
  float ws = 0.0;
  float3 ctop = float3(-1.0);
  float3 cmax = float3(0.0);
$ballCalls
  float f = mix(fu, fs, goo);
  float2 gr = mix(gu, gs, goo);
  float gl = max(length(gr), 0.0001);
  float sd = (f - 1.0) / gl;
  float a = clamp(sd * 3.0 + 0.5, 0.0, 1.0);
  if (a <= 0.0) { return half4(0.0); }
  float3 base = ctop.r >= 0.0 ? ctop : cmax;
  float3 mixc = ws > 0.0 ? cw / ws : base;
  float3 col = mix(base, mixc, smoothstep(0.0, 0.5, goo));
  float dep = max(clamp(sd / 16.0, 0.0, 1.0), clamp((f - 1.0) / 1.5, 0.0, 1.0));
  float2 outw = -gr / gl;
  float tilt = pow(1.0 - dep, 1.6) * 1.4;
  float3 n = normalize(float3(outw * tilt, 1.0));
  float3 lv = normalize(float3(-0.45, -0.65, 0.75));
  float dif = dot(n, lv) - lv.z;
  float3 hv = normalize(lv + float3(0.0, 0.0, 1.0));
  float spec = pow(max(dot(n, hv), 0.0), 60.0);
  float3 lit = col * (1.0 + 0.35 * dif) + float3(spec * 0.55);
  col = mix(col, lit, gloss);
  float3 outc = clamp(col, 0.0, 1.0);
  return half4(half3(outc * a), half(a));
}
"""
}
