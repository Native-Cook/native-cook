package com.ngocdevv.nativecook.blocks.cards.frostheartrewards

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal data class FrostHeartRewardsV3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: FrostHeartRewardsV3) = FrostHeartRewardsV3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: FrostHeartRewardsV3) = FrostHeartRewardsV3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = FrostHeartRewardsV3(x * k, y * k, z * k)
    operator fun unaryMinus() = FrostHeartRewardsV3(-x, -y, -z)
    infix fun dot(o: FrostHeartRewardsV3) = x * o.x + y * o.y + z * o.z
    fun length() = sqrt(this dot this)
    fun normalized() = this * (1.0 / max(1e-9, length()))
    fun rotX(a: Double) = FrostHeartRewardsV3(x, y * cos(a) - z * sin(a), y * sin(a) + z * cos(a))
    fun rotY(a: Double) = FrostHeartRewardsV3(x * cos(a) + z * sin(a), y, -x * sin(a) + z * cos(a))
    fun rotZ(a: Double) = FrostHeartRewardsV3(x * cos(a) - y * sin(a), x * sin(a) + y * cos(a), z)
    fun cross(o: FrostHeartRewardsV3) = FrostHeartRewardsV3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun refract(n: FrostHeartRewardsV3, eta: Double): FrostHeartRewardsV3? {
        val d = this dot n
        val k = 1 - eta * eta * (1 - d * d)
        if (k < 0) return null
        return this * eta - n * (eta * d + sqrt(k))
    }

    companion object { val ZERO = FrostHeartRewardsV3(0.0, 0.0, 0.0) }
}

/**
 * The Blender-cut brilliant of `atelier-gem.json`: 24 convex sectors (two per display piece),
 * each bounded by the table, six exterior facets and two radial planes through one vertical axis.
 * Sector k's second radial plane is the negated first plane of sector k + 1, so the shader keeps
 * 24 radial planes for 48 faces.
 */
internal class FrostHeartRewardsGem(context: Context) {
    class Face(val indices: IntArray, val normal: FrostHeartRewardsV3, val arrival: Double)
    class Sector(val vertices: List<FrostHeartRewardsV3>, val faces: List<Face>, val star: FrostHeartRewardsV3)

    val sectors: List<Sector>
    /** Uniform arrays of the AGSL gem: six exterior planes per sector, 25 radial planes (wrapped), 12 group spheres. */
    val ext = FloatArray(144 * 4)
    val rad = FloatArray(25 * 4)
    val grp = FloatArray(12 * 4)
    val pivots: List<FrostHeartRewardsV3>
    val table: Double
    /** Outer tip of each display piece in screen points: the 12 portals of the cut's core light. */
    val portals: List<Pair<Double, Double>>

    init {
        val root = JSONObject(context.assets.open("frostheartrewards/frostheartrewards_atelier-gem.json").bufferedReader().use { it.readText() })
        val pieces = root.getJSONArray("pieces")
        require(pieces.length() == 24) { "Frost Heart gem expects 24 sectors" }
        fun v3(a: JSONArray) = FrostHeartRewardsV3(a.getDouble(0), a.getDouble(1), a.getDouble(2))
        val list = ArrayList<Sector>()
        val pivotList = ArrayList<FrostHeartRewardsV3>()
        var tableOffset = 0.65
        for (k in 0 until 24) {
            val p = pieces.getJSONObject(k)
            val vs = p.getJSONArray("vertices").let { a -> (0 until a.length()).map { v3(a.getJSONArray(it)) } }
            val fs = p.getJSONArray("faces")
            val exterior = p.optInt("exteriorFaceCount", 7)
            val faces = ArrayList<Face>()
            for (j in 0 until fs.length()) {
                val f = fs.getJSONObject(j)
                val n = v3(f.getJSONArray("normal"))
                val off = f.getDouble("offset")
                when {
                    j == 0 -> tableOffset = off
                    j in 1..6 -> {
                        val at = (k * 6 + j - 1) * 4
                        ext[at] = n.x.toFloat(); ext[at + 1] = n.y.toFloat(); ext[at + 2] = n.z.toFloat(); ext[at + 3] = off.toFloat()
                    }
                    j == 7 -> {
                        rad[k * 4] = n.x.toFloat(); rad[k * 4 + 1] = n.y.toFloat(); rad[k * 4 + 2] = n.z.toFloat(); rad[k * 4 + 3] = off.toFloat()
                    }
                }
                if (j < exterior) {
                    val idx = f.getJSONArray("indices").let { a -> IntArray(a.length()) { a.getInt(it) } }
                    faces.add(Face(idx, n, arrival(n)))
                }
            }
            list.add(Sector(vs, faces, faces.getOrNull(1)?.normal ?: FrostHeartRewardsV3(0.0, 0.0, 1.0)))
            if (k % 2 == 0) pivotList.add(p.optJSONArray("displayCentroid")?.let { v3(it) } ?: v3(p.getJSONArray("centroid")))
        }
        for (c in 0 until 4) rad[24 * 4 + c] = rad[c]
        sectors = list
        pivots = pivotList
        table = tableOffset
        for (g in 0 until 12) {
            val c = pivots[g]
            val radius = (sectors[g * 2].vertices + sectors[g * 2 + 1].vertices).maxOf { (it - c).length() }
            grp[g * 4] = c.x.toFloat(); grp[g * 4 + 1] = c.y.toFloat(); grp[g * 4 + 2] = c.z.toFloat(); grp[g * 4 + 3] = radius.toFloat()
        }
        portals = (0 until 12).map { g ->
            val outer = (sectors[g * 2].vertices + sectors[g * 2 + 1].vertices).maxBy { hypot(it.x - 0.15, it.y + 0.1) }
            (175 + outer.x * 105) to (278 - outer.y * 105)
        }
    }

    companion object {
        private fun fract(x: Double) = x - floor(x)
        /** Laser arrival of a facet: around the heart from its normal's angle, with a stable per-plane jitter. */
        fun arrival(n: FrostHeartRewardsV3): Double {
            val noise = fract(sin(n.x * 127.1 + n.y * 311.7 + n.z * 91.3) * 43758.5453)
            return 2.42 + fract(atan2(n.y, n.x) / (2 * PI) + 0.5) * 1.03 + noise * 0.12
        }
    }

    /** World pose of one display piece, the CPU twin of the shader's worldPoint/toLocal. */
    class Pose(val pitch: Double, val yaw: Double) {
        fun forward(p: FrostHeartRewardsV3) = p.rotX(pitch).rotY(yaw)
        fun inverse(p: FrostHeartRewardsV3) = p.rotY(-yaw).rotX(-pitch)
        fun offset(c: FrostHeartRewardsV3, spread: Double): FrostHeartRewardsV3 {
            val dir = FrostHeartRewardsV3(c.x * 0.9, c.y * 0.9, c.z * 0.42 + 0.04).normalized()
            return dir * (minOf(0.16, max(-0.025, spread) * 0.16))
        }
        fun spin(group: Int, spread: Double) = spread * (0.055 + (group % 3) * 0.018) * (if (group % 2 == 0) 1.0 else -1.0)
        fun worldPoint(p: FrostHeartRewardsV3, c: FrostHeartRewardsV3, spin: Double, off: FrostHeartRewardsV3) =
            forward((p - c).rotX(spin * 0.6).rotZ(spin * 0.4).rotY(spin) + c + off)
        fun worldNormal(n: FrostHeartRewardsV3, spin: Double) = forward(n.rotX(spin * 0.6).rotZ(spin * 0.4).rotY(spin))
        fun localVector(v: FrostHeartRewardsV3, spin: Double) = inverse(v).rotY(-spin).rotZ(-spin * 0.4).rotX(-spin * 0.6)
    }

    /** Fills the 36 rows (3 per group) of L·p + t, the camera → piece-local transform of the shader. */
    fun transforms(pose: Pose, t: Double, display: Double, out: FloatArray) {
        for (g in 0 until 12) {
            val c = pivots[g]
            val spread = FrostHeartRewardsMotion.spread(t, display, g)
            val assembled = FrostHeartRewardsMotion.assembled(t, display)
            val spin = if (assembled) 0.0 else pose.spin(g, spread)
            val off = if (assembled) FrostHeartRewardsV3.ZERO else pose.offset(c, spread)
            val cols = listOf(FrostHeartRewardsV3(1.0, 0.0, 0.0), FrostHeartRewardsV3(0.0, 1.0, 0.0), FrostHeartRewardsV3(0.0, 0.0, 1.0)).map { pose.localVector(it, spin) }
            // localVector(forward(v)) applies only the spin: t = c − spin⁻¹(off + c).
            val tx = c - pose.localVector(pose.forward(off + c), spin)
            for (r in 0 until 3) {
                val at = (g * 3 + r) * 4
                out[at] = (if (r == 0) cols[0].x else if (r == 1) cols[0].y else cols[0].z).toFloat()
                out[at + 1] = (if (r == 0) cols[1].x else if (r == 1) cols[1].y else cols[1].z).toFloat()
                out[at + 2] = (if (r == 0) cols[2].x else if (r == 1) cols[2].y else cols[2].z).toFloat()
                out[at + 3] = (if (r == 0) tx.x else if (r == 1) tx.y else tx.z).toFloat()
            }
        }
    }
}
