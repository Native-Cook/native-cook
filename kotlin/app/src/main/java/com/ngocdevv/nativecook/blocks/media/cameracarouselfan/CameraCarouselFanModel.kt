package com.ngocdevv.nativecook.blocks.media.cameracarouselfan

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 The data behind Camera Carousel Fan (a port of CameraCarouselFanModel.swift; the numbers are the
 same): five invented cameras built from a few extruded shapes (rounded rectangles and circles
 pushed out along an axis), the eight drawn "photos" that orbit them, the film looks that tint
 those photos, and the autoPlay timeline.

 Units are dp at a 393 dp wide screen; x right, y up, z towards the viewer.
 */

internal class CcfV3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: CcfV3) = CcfV3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: CcfV3) = CcfV3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = CcfV3(x * k, y * k, z * k)
    operator fun div(k: Double) = CcfV3(x / k, y / k, z / k)
    operator fun unaryMinus() = CcfV3(-x, -y, -z)
    infix fun dot(o: CcfV3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: CcfV3) = CcfV3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(this dot this)
    fun normalized(): CcfV3 {
        val l = length()
        return if (l < 1e-9) CcfV3(0.0, 0.0, 1.0) else this / l
    }
}

internal class CcfV2(val x: Double, val y: Double)

// MARK: Geometry

internal sealed class CcfKind {
    object Solid : CcfKind()
    class Glass(val style: Int) : CcfKind()
    class Decal(val on: Int) : CcfKind()
}

internal class CcfPart(
    val profile: List<CcfV2>,
    val depth: Double,
    val origin: CcfV3,
    val color: CcfV3,
    val gloss: Double,
    val kind: CcfKind = CcfKind.Solid,
    /** Alternate side quads are darkened, like a knurled focus ring. */
    val stripes: Boolean = false,
    /** Added to the depth the faces are sorted by: lenses sit in front of the trim around them. */
    val bias: Double = 0.0,
    val u: CcfV3 = CcfV3(1.0, 0.0, 0.0),
    val v: CcfV3 = CcfV3(0.0, 1.0, 0.0),
    val n: CcfV3 = CcfV3(0.0, 0.0, 1.0),
)

internal class CcfFace(
    /** Side quads: [back k, back k+1, front k+1, front k]. Caps: the profile. */
    val pts: List<CcfV3>,
    val normal: CcfV3,
    /** Side quads only: the smoothed normals of the quad's two long edges (k and k+1). */
    val n0: CcfV3?,
    val n1: CcfV3?,
    val color: CcfV3,
    val gloss: Double,
    /** −1 for a solid face, else the glass style of a lens's front. */
    val glass: Int,
    /** −1, or the index of the face this decal lies on (drawn right after it). */
    val parent: Int,
    val center: CcfV3,
    /** Caps: the profile's v axis and half its height, for the top-to-bottom sheen; glass: its radius. */
    val axis: CcfV3,
    val half: Double,
    val bias: Double,
)

internal class CcfCamera(val name: String, val spec: String, val accent: CcfV3, val look: Int, val faces: List<CcfFace>)

internal object CcfGeo {
    fun roundedRect(w: Double, h: Double, r: Double, seg: Int = 4): List<CcfV2> {
        val cx = w / 2 - r
        val cy = h / 2 - r
        val centers = listOf(CcfV2(cx, -cy), CcfV2(cx, cy), CcfV2(-cx, cy), CcfV2(-cx, -cy))
        val pts = ArrayList<CcfV2>()
        for (k in 0 until 4) for (s in 0..seg) {
            val a = (k - 1 + s.toDouble() / seg) * PI / 2
            pts.add(CcfV2(centers[k].x + cos(a) * r, centers[k].y + sin(a) * r))
        }
        return pts
    }

    fun circle(r: Double, seg: Int = 28): List<CcfV2> = (0 until seg).map { i ->
        val a = i.toDouble() / seg * 2 * PI
        CcfV2(cos(a) * r, sin(a) * r)
    }

    /** Turns parts into faces. A decal keeps only its front, tied to the front of the part it names. */
    fun faces(parts: List<CcfPart>): List<CcfFace> {
        val faces = ArrayList<CcfFace>()
        val frontOf = ArrayList<Int>()
        for (part in parts) {
            val p = part.profile
            val m = p.size
            fun point(q: CcfV2, z: Double) = part.origin + part.u * q.x + part.v * q.y + part.n * z
            val hz = part.depth / 2
            val glassStyle = (part.kind as? CcfKind.Glass)?.style ?: -1
            val parent = (part.kind as? CcfKind.Decal)?.let { frontOf[it.on] } ?: -1
            val half = (p.maxOf { it.y } - p.minOf { it.y }) / 2
            if (parent < 0) {
                // Edge normals, then the smoothed normal at each profile point.
                val edge = (0 until m).map { k ->
                    val a = p[k]
                    val b = p[(k + 1) % m]
                    (part.u * (b.y - a.y) - part.v * (b.x - a.x)).normalized()
                }
                for (k in 0 until m) {
                    val k1 = (k + 1) % m
                    val a = (edge[(k + m - 1) % m] + edge[k]).normalized()
                    val b = (edge[k] + edge[k1]).normalized()
                    val color = if (part.stripes && k % 2 == 1) part.color * 0.62 else part.color
                    val quad = listOf(point(p[k], -hz), point(p[k1], -hz), point(p[k1], hz), point(p[k], hz))
                    faces.add(
                        CcfFace(
                            quad, edge[k], a, b, color, part.gloss, -1, -1,
                            (quad[0] + quad[1] + quad[2] + quad[3]) / 4.0, CcfV3(0.0, 1.0, 0.0), 0.0, part.bias,
                        ),
                    )
                }
                faces.add(
                    CcfFace(
                        p.reversed().map { point(it, -hz) }, -part.n, null, null, part.color, part.gloss, -1, -1,
                        part.origin - part.n * hz, part.v, half, part.bias,
                    ),
                )
            }
            frontOf.add(faces.size)
            val r = p.maxOf { sqrt(it.x * it.x + it.y * it.y) }
            faces.add(
                CcfFace(
                    p.map { point(it, hz) }, part.n, null, null, part.color, part.gloss, glassStyle, parent,
                    part.origin + part.n * hz, part.v, if (glassStyle >= 0) r else half, part.bias,
                ),
            )
        }
        return faces
    }
}

// MARK: The five cameras

internal object CcfCameras {
    private fun c(r: Double, g: Double, b: Double) = CcfV3(r, g, b)
    private fun o(x: Double, y: Double, z: Double) = CcfV3(x, y, z)
    private val G = CcfGeo

    /** A short cylinder standing on a top plate. */
    private fun knob(r: Double, h: Double, x: Double, y: Double, z: Double, color: CcfV3, gloss: Double) = CcfPart(
        G.circle(r, 18), h, o(x, y, z), color, gloss,
        u = CcfV3(1.0, 0.0, 0.0), v = CcfV3(0.0, 0.0, -1.0), n = CcfV3(0.0, 1.0, 0.0),
    )

    private fun decal(profile: List<CcfV2>, x: Double, y: Double, z: Double, color: CcfV3, on: Int, gloss: Double = 0.6) =
        CcfPart(profile, 0.0, o(x, y, z), color, gloss, CcfKind.Decal(on))

    /** Orange top plate on a plum body, a big pearl lens. */
    private val oka: CcfCamera by lazy {
        val plum = c(0.20, 0.15, 0.23)
        val orange = c(0.78, 0.33, 0.12)
        val parts = listOf(
            CcfPart(G.roundedRect(172.0, 78.0, 16.0), 62.0, o(0.0, -16.0, 0.0), plum, 0.3),
            CcfPart(G.roundedRect(172.0, 34.0, 12.0), 64.0, o(0.0, 40.0, 0.0), orange, 0.85),
            CcfPart(G.roundedRect(70.0, 26.0, 9.0), 48.0, o(0.0, 68.0, -4.0), c(0.72, 0.29, 0.10), 0.85),
            knob(9.0, 8.0, 56.0, 60.0, 6.0, c(0.84, 0.38, 0.15), 0.9),
            knob(13.0, 6.0, -54.0, 59.0, 4.0, c(0.16, 0.13, 0.17), 0.5),
            CcfPart(G.circle(42.0), 26.0, o(0.0, -14.0, 44.0), c(0.10, 0.10, 0.11), 0.5, stripes = true, bias = 6.0),
            CcfPart(G.circle(31.0), 6.0, o(0.0, -14.0, 59.0), c(0.08, 0.08, 0.09), 0.4, CcfKind.Glass(0), bias = 8.0),
            decal(G.circle(5.5, 14), 62.0, 4.0, 31.0, c(0.80, 0.12, 0.12), 0),
            decal(G.roundedRect(28.0, 12.0, 3.0), 0.0, 68.0, 20.0, c(0.05, 0.05, 0.07), 2, gloss = 1.0),
        )
        CcfCamera("OKA 35", "35MM · F/2.8 · WARM 400", c(0.85, 0.38, 0.14), 0, G.faces(parts))
    }

    /** A rangefinder: cognac leather under a black top plate, silver trim. */
    private val rf2: CcfCamera by lazy {
        val silver = c(0.78, 0.79, 0.82)
        val parts = listOf(
            CcfPart(G.roundedRect(182.0, 70.0, 12.0), 52.0, o(0.0, -14.0, 0.0), c(0.46, 0.22, 0.12), 0.12),
            CcfPart(G.roundedRect(182.0, 36.0, 12.0), 54.0, o(0.0, 39.0, 0.0), c(0.13, 0.13, 0.14), 0.95),
            knob(8.0, 8.0, 64.0, 61.0, 4.0, silver, 1.0),
            knob(14.0, 6.0, -52.0, 60.0, 2.0, c(0.15, 0.15, 0.16), 0.7),
            CcfPart(G.circle(38.0), 5.0, o(0.0, -10.0, 28.0), silver, 1.0),
            CcfPart(G.circle(35.0), 30.0, o(0.0, -10.0, 44.0), c(0.09, 0.09, 0.10), 0.6, stripes = true, bias = 6.0),
            CcfPart(G.circle(25.0), 4.0, o(0.0, -10.0, 60.0), c(0.06, 0.06, 0.07), 0.4, CcfKind.Glass(1), bias = 8.0),
            decal(G.roundedRect(32.0, 14.0, 3.0), -54.0, 40.0, 27.0, c(0.12, 0.16, 0.24), 1, gloss = 1.0),
            decal(G.roundedRect(18.0, 14.0, 3.0), 44.0, 40.0, 27.0, c(0.12, 0.16, 0.24), 1, gloss = 1.0),
        )
        CcfCamera("RF-2", "RANGEFINDER · F/2 · MONO", c(0.62, 0.36, 0.20), 1, G.faces(parts))
    }

    /** A red plastic toy camera with a cream flash and lens. */
    private val pip: CcfCamera by lazy {
        val cream = c(0.93, 0.89, 0.80)
        val parts = listOf(
            CcfPart(G.roundedRect(158.0, 98.0, 28.0), 60.0, o(0.0, -6.0, 0.0), c(0.80, 0.15, 0.12), 0.7),
            CcfPart(G.roundedRect(56.0, 24.0, 7.0), 36.0, o(40.0, 54.0, -2.0), cream, 0.5),
            knob(8.0, 8.0, -46.0, 46.0, 4.0, cream, 0.6),
            CcfPart(G.circle(40.0), 18.0, o(0.0, -12.0, 39.0), cream, 0.6, bias = 6.0),
            CcfPart(G.circle(26.0), 5.0, o(0.0, -12.0, 50.0), c(0.06, 0.08, 0.08), 0.4, CcfKind.Glass(2), bias = 8.0),
            decal(G.roundedRect(44.0, 14.0, 4.0), 40.0, 54.0, 16.0, c(0.98, 0.97, 0.92), 1, gloss = 1.0),
            decal(G.roundedRect(24.0, 16.0, 4.0), -52.0, 24.0, 30.0, c(0.07, 0.07, 0.08), 0, gloss = 1.0),
            decal(G.roundedRect(150.0, 7.0, 3.4), 0.0, -44.0, 30.0, c(0.97, 0.94, 0.86), 0),
        )
        CcfCamera("PIP", "TOY · PLASTIC LENS · RED 200", c(0.88, 0.20, 0.16), 2, G.faces(parts))
    }

    /** A boxy olive medium-format camera with a waist-level finder. */
    private val moss: CcfCamera by lazy {
        val parts = listOf(
            CcfPart(G.roundedRect(148.0, 108.0, 12.0), 88.0, o(0.0, -10.0, 0.0), c(0.30, 0.36, 0.22), 0.2),
            CcfPart(G.roundedRect(148.0, 16.0, 6.0), 90.0, o(0.0, 52.0, 0.0), c(0.80, 0.81, 0.83), 1.0),
            CcfPart(G.roundedRect(96.0, 22.0, 6.0), 70.0, o(0.0, 68.0, -6.0), c(0.12, 0.12, 0.13), 0.7),
            CcfPart(G.circle(44.0), 30.0, o(0.0, -14.0, 59.0), c(0.09, 0.09, 0.10), 0.6, stripes = true, bias = 6.0),
            CcfPart(G.circle(32.0), 5.0, o(0.0, -14.0, 76.0), c(0.06, 0.06, 0.07), 0.4, CcfKind.Glass(1), bias = 8.0),
            decal(G.roundedRect(40.0, 10.0, 3.0), -44.0, 30.0, 44.0, c(0.80, 0.81, 0.83), 0, gloss = 1.0),
        )
        CcfCamera("MOSS 6", "6×6 · F/3.5 · GREEN FADE", c(0.45, 0.56, 0.30), 3, G.faces(parts))
    }

    /** A silver half-frame compact with a blue band. */
    private val half: CcfCamera by lazy {
        val silver = c(0.76, 0.77, 0.80)
        val parts = listOf(
            CcfPart(G.roundedRect(170.0, 86.0, 18.0), 44.0, o(0.0, -4.0, 0.0), silver, 1.0),
            knob(7.0, 6.0, 60.0, 42.0, 0.0, silver, 1.0),
            CcfPart(G.circle(31.0), 22.0, o(-34.0, -8.0, 33.0), silver, 1.0, stripes = true, bias = 6.0),
            CcfPart(G.circle(21.0), 4.0, o(-34.0, -8.0, 45.0), c(0.06, 0.06, 0.07), 0.4, CcfKind.Glass(1), bias = 8.0),
            decal(G.roundedRect(164.0, 34.0, 4.0), 0.0, -12.0, 22.0, c(0.17, 0.27, 0.50), 0, gloss = 0.15),
            decal(G.roundedRect(24.0, 14.0, 3.0), 52.0, 22.0, 22.0, c(0.06, 0.07, 0.10), 0, gloss = 1.0),
        )
        CcfCamera("HALF", "HALF FRAME · F/3.2 · BLUE HOUR", c(0.30, 0.46, 0.85), 4, G.faces(parts))
    }

    val all: List<CcfCamera> by lazy { listOf(oka, rf2, pip, moss, half) }
}

// MARK: Photos

internal sealed class CcfOp {
    class Rect(val x: Double, val y: Double, val w: Double, val h: Double, val c: CcfV3) : CcfOp()
    /** A vertical gradient filling a rect, top colour then bottom colour. */
    class Grad(val x: Double, val y: Double, val w: Double, val h: Double, val top: CcfV3, val bottom: CcfV3) : CcfOp()
    class Circle(val x: Double, val y: Double, val r: Double, val c: CcfV3) : CcfOp()
    class Poly(val pts: List<CcfV2>, val c: CcfV3) : CcfOp()
}

internal object CcfPhotos {
    private fun v(r: Double, g: Double, b: Double) = CcfV3(r, g, b)
    private fun p(vararg pts: Double) = (pts.indices step 2).map { CcfV2(pts[it], pts[it + 1]) }
    private fun rect(x: Double, y: Double, w: Double, h: Double, c: CcfV3) = CcfOp.Rect(x, y, w, h, c)
    private fun grad(x: Double, y: Double, w: Double, h: Double, t: CcfV3, b: CcfV3) = CcfOp.Grad(x, y, w, h, t, b)
    private fun circle(x: Double, y: Double, r: Double, c: CcfV3) = CcfOp.Circle(x, y, r, c)
    private fun poly(pts: List<CcfV2>, c: CcfV3) = CcfOp.Poly(pts, c)

    /** Eight little scenes in unit coordinates (0…1, y down). */
    val scenes: List<List<CcfOp>> = listOf(
        // Red flowers on dark leaves.
        listOf(
            grad(0.0, 0.0, 1.0, 1.0, v(0.11, 0.24, 0.13), v(0.04, 0.11, 0.06)),
            poly(p(0.0, 0.55, 0.35, 0.42, 0.5, 0.6, 0.2, 0.72), v(0.16, 0.36, 0.18)),
            poly(p(0.55, 0.95, 0.75, 0.5, 1.0, 0.45, 0.85, 0.8), v(0.14, 0.32, 0.16)),
            circle(0.30, 0.34, 0.16, v(0.86, 0.16, 0.12)), circle(0.66, 0.26, 0.13, v(0.92, 0.22, 0.14)),
            circle(0.52, 0.64, 0.18, v(0.80, 0.12, 0.10)), circle(0.84, 0.72, 0.11, v(0.90, 0.28, 0.16)),
            circle(0.30, 0.34, 0.04, v(0.98, 0.80, 0.30)), circle(0.66, 0.26, 0.035, v(0.98, 0.80, 0.30)),
            circle(0.52, 0.64, 0.045, v(0.98, 0.80, 0.30)),
        ),
        // A white building against a blue sky.
        listOf(
            grad(0.0, 0.0, 1.0, 1.0, v(0.28, 0.52, 0.90), v(0.72, 0.85, 0.98)),
            poly(p(0.22, 1.0, 0.22, 0.36, 0.56, 0.18, 0.56, 1.0), v(0.95, 0.95, 0.96)),
            poly(p(0.56, 1.0, 0.56, 0.18, 0.80, 0.32, 0.80, 1.0), v(0.66, 0.70, 0.78)),
            rect(0.30, 0.48, 0.08, 0.08, v(0.30, 0.36, 0.46)), rect(0.42, 0.44, 0.08, 0.08, v(0.30, 0.36, 0.46)),
            rect(0.30, 0.68, 0.08, 0.08, v(0.30, 0.36, 0.46)), rect(0.42, 0.66, 0.08, 0.08, v(0.30, 0.36, 0.46)),
        ),
        // A portrait in silhouette.
        listOf(
            grad(0.0, 0.0, 1.0, 1.0, v(0.64, 0.62, 0.60), v(0.36, 0.35, 0.34)),
            circle(0.5, 1.02, 0.36, v(0.12, 0.12, 0.13)), circle(0.5, 0.46, 0.16, v(0.14, 0.13, 0.13)),
            circle(0.44, 0.40, 0.05, v(0.30, 0.28, 0.27)),
        ),
        // A sunset over the sea.
        listOf(
            grad(0.0, 0.0, 1.0, 0.66, v(0.98, 0.68, 0.38), v(0.94, 0.42, 0.48)),
            circle(0.5, 0.60, 0.15, v(1.0, 0.90, 0.62)),
            rect(0.0, 0.64, 1.0, 0.36, v(0.18, 0.20, 0.34)),
            rect(0.38, 0.70, 0.24, 0.025, v(1.0, 0.78, 0.52)), rect(0.43, 0.78, 0.14, 0.02, v(0.96, 0.66, 0.50)),
            rect(0.46, 0.86, 0.08, 0.02, v(0.90, 0.56, 0.50)),
        ),
        // Mountains with snow.
        listOf(
            grad(0.0, 0.0, 1.0, 1.0, v(0.58, 0.75, 0.92), v(0.90, 0.93, 0.95)),
            poly(p(-0.05, 0.85, 0.34, 0.30, 0.72, 0.85), v(0.44, 0.52, 0.62)),
            poly(p(0.28, 1.0, 0.68, 0.40, 1.08, 1.0), v(0.24, 0.30, 0.37)),
            poly(p(0.68, 0.40, 0.59, 0.53, 0.66, 0.50, 0.70, 0.55, 0.77, 0.50), v(0.97, 0.97, 0.98)),
            rect(0.0, 0.86, 1.0, 0.14, v(0.20, 0.27, 0.22)),
        ),
        // A potted plant on a warm wall.
        listOf(
            grad(0.0, 0.0, 1.0, 1.0, v(0.96, 0.90, 0.76), v(0.88, 0.78, 0.60)),
            poly(p(0.5, 0.74, 0.18, 0.40, 0.30, 0.20, 0.52, 0.66), v(0.22, 0.50, 0.30)),
            poly(p(0.5, 0.74, 0.86, 0.34, 0.74, 0.16, 0.48, 0.64), v(0.30, 0.60, 0.36)),
            poly(p(0.5, 0.74, 0.42, 0.24, 0.56, 0.08, 0.56, 0.66), v(0.18, 0.44, 0.26)),
            poly(p(0.34, 0.72, 0.66, 0.72, 0.62, 1.0, 0.38, 1.0), v(0.78, 0.42, 0.28)),
        ),
        // A city at night.
        listOf(
            grad(0.0, 0.0, 1.0, 1.0, v(0.05, 0.06, 0.17), v(0.20, 0.11, 0.28)),
            circle(0.78, 0.20, 0.08, v(0.96, 0.93, 0.84)),
            rect(0.04, 0.50, 0.20, 0.50, v(0.08, 0.08, 0.13)), rect(0.27, 0.34, 0.19, 0.66, v(0.10, 0.09, 0.16)),
            rect(0.49, 0.56, 0.20, 0.44, v(0.08, 0.08, 0.13)), rect(0.72, 0.42, 0.24, 0.58, v(0.10, 0.09, 0.16)),
            rect(0.09, 0.58, 0.04, 0.04, v(1.0, 0.82, 0.40)), rect(0.32, 0.42, 0.04, 0.04, v(1.0, 0.82, 0.40)),
            rect(0.38, 0.56, 0.04, 0.04, v(1.0, 0.82, 0.40)), rect(0.55, 0.64, 0.04, 0.04, v(1.0, 0.82, 0.40)),
            rect(0.78, 0.50, 0.04, 0.04, v(1.0, 0.82, 0.40)), rect(0.86, 0.66, 0.04, 0.04, v(1.0, 0.82, 0.40)),
        ),
        // Dunes under a low sun.
        listOf(
            grad(0.0, 0.0, 1.0, 1.0, v(0.98, 0.87, 0.68), v(0.96, 0.72, 0.54)),
            circle(0.26, 0.30, 0.09, v(1.0, 0.96, 0.82)),
            poly(p(0.0, 0.62, 0.40, 0.50, 1.0, 0.70, 1.0, 1.0, 0.0, 1.0), v(0.86, 0.58, 0.36)),
            poly(p(0.0, 0.82, 0.55, 0.68, 1.0, 0.86, 1.0, 1.0, 0.0, 1.0), v(0.68, 0.40, 0.25)),
        ),
    )

    /** The film look of each camera, applied to every colour of a photo. */
    fun look(c: CcfV3, look: Int): CcfV3 {
        val l = 0.3 * c.x + 0.59 * c.y + 0.11 * c.z
        val o = when (look) {
            0 -> CcfV3(c.x * 1.06 + 0.05, c.y * 0.97 + 0.02, c.z * 0.80)
            1 -> {
                val m = (l - 0.5) * 1.18 + 0.5
                CcfV3(m * 1.02, m, m * 0.96)
            }
            2 -> CcfV3(c.x * 1.10 + 0.06, c.y * 0.86, c.z * 0.84)
            3 -> CcfV3(c.x * 0.84 + 0.04, c.y * 0.96 + 0.06, c.z * 0.86 + 0.04)
            else -> CcfV3(c.x * 0.80, c.y * 0.92 + 0.02, c.z * 1.08 + 0.06)
        }
        return CcfV3(o.x.coerceIn(0.0, 1.0), o.y.coerceIn(0.0, 1.0), o.z.coerceIn(0.0, 1.0))
    }

    /** Where each of the eight photos rides on the ring: height (dp) and in-plane tilt (degrees). */
    val heights = doubleArrayOf(-36.0, -4.0, -60.0, -22.0, -50.0, 0.0, -68.0, -28.0)
    val rolls = doubleArrayOf(-16.0, 10.0, -6.0, 20.0, -12.0, 6.0, 14.0, -22.0)
}

// MARK: Timeline

internal object CcfTimeline {
    /** autoPlay: rest on OKA 35, flip to RF-2, rest, flip to PIP, rest, spin back to OKA 35. */
    const val LOOP = 8.0
    /** (start, end, from, to) of each move. */
    private val moves = listOf(
        doubleArrayOf(1.2, 2.4, 0.0, 1.0),
        doubleArrayOf(3.6, 4.8, 1.0, 2.0),
        doubleArrayOf(5.9, 7.4, 2.0, 0.0),
    )
    /** Each photo flips this much later than the one before it. */
    const val STAGGER = 0.06

    fun p(time: Double): Double {
        var t = time % LOOP
        if (t < 0) t += LOOP
        var value = 0.0
        for (m in moves) {
            if (t < m[0]) return value
            if (t < m[1]) return m[2] + (m[3] - m[2]) * easeInOutBack((t - m[0]) / (m[1] - m[0]))
            value = m[3]
        }
        return value
    }

    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** Ease in and out with a small wind-up before and overshoot after (about 4 %). */
    fun easeInOutBack(x: Double): Double {
        val u = x.coerceIn(0.0, 1.0)
        val c2 = 1.0 * 1.525
        if (u < 0.5) return ((2 * u).pow(2) * ((c2 + 1) * 2 * u - c2)) / 2
        return ((2 * u - 2).pow(2) * ((c2 + 1) * (u * 2 - 2) + c2) + 2) / 2
    }

    /** A damped spring (period 0.55 s, damping ratio 0.72) from x0 with velocity v0 towards [to]. */
    fun spring(t: Double, x0: Double, v0: Double, to: Double): Double {
        val w = 2 * PI / 0.55
        val z = 0.72
        val wd = w * sqrt(1 - z * z)
        val a = x0 - to
        val b = (v0 + z * w * a) / wd
        return to + exp(-z * w * t) * (a * cos(wd * t) + b * sin(wd * t))
    }
}
