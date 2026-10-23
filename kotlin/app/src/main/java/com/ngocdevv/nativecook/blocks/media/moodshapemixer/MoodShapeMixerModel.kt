package com.ngocdevv.nativecook.blocks.media.moodshapemixer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/** Seven source-measured contours on a clamped horizontal mood slider. */
internal object MoodShapeMixerModel {
    const val LOOP = 10.0
    data class Mood(val name: String, val color: Int, val smile: Double, val tired: Double, val radii: DoubleArray)
    val moods = listOf(
        Mood("MELANCHOLY", 0x6A49D4, -1.0, 0.0, doubleArrayOf(
            0.7275, 0.7732, 0.8395, 0.932, 1.0414, 1.1443, 1.2136, 1.237, 1.2127, 1.1555, 1.0875, 1.0259, 0.978, 0.943, 0.9195, 0.9059, 0.9018, 0.9059, 0.9195, 0.943, 0.978, 1.0259, 1.0907, 1.1625, 1.2295, 1.2543, 1.2345, 1.1636, 1.0566, 0.9511, 0.8611, 0.7968, 0.7491, 0.7182, 0.6948, 0.6855, 0.6818, 0.6818, 0.6818, 0.6827, 0.6868, 0.6995, 0.7236, 0.7643, 0.817, 0.883, 0.9398, 0.9732, 0.978, 0.9566, 0.9155, 0.8548, 0.7995, 0.7491, 0.7116, 0.6909, 0.6832, 0.6818, 0.6818, 0.6818, 0.6818, 0.6832, 0.6877, 0.7018
        )),
        Mood("TIRED", 0x6D9AE8, -0.75, 1.0, doubleArrayOf(
            0.938, 0.9261, 0.9166, 0.9114, 0.91, 0.92, 0.9377, 0.9586, 0.9795, 0.9959, 0.9968, 0.9898, 0.9759, 0.9591, 0.9411, 0.9284, 0.9236, 0.9284, 0.9411, 0.9591, 0.9727, 0.9805, 0.975, 0.9714, 0.9555, 0.9425, 0.9209, 0.9039, 0.8959, 0.8923, 0.8959, 0.9034, 0.9155, 0.9241, 0.9305, 0.9273, 0.915, 0.8961, 0.8793, 0.8645, 0.8514, 0.8418, 0.8359, 0.8407, 0.8477, 0.8725, 0.8861, 0.8966, 0.8973, 0.8966, 0.8911, 0.8825, 0.8682, 0.852, 0.8405, 0.8382, 0.84, 0.8609, 0.8832, 0.9127, 0.937, 0.952, 0.955, 0.948
        )),
        Mood("BORED", 0x63CCEA, -0.4, 0.0, doubleArrayOf(
            0.9009, 0.9055, 0.9168, 0.9302, 0.9405, 0.9448, 0.947, 0.9375, 0.9195, 0.8995, 0.8873, 0.8798, 0.8891, 0.8986, 0.9168, 0.9341, 0.9414, 0.9398, 0.9257, 0.9055, 0.8864, 0.8795, 0.8757, 0.8873, 0.9086, 0.9293, 0.9505, 0.9584, 0.9582, 0.9448, 0.9355, 0.9264, 0.9223, 0.9218, 0.9264, 0.9423, 0.9609, 0.9714, 0.9673, 0.9548, 0.9409, 0.9245, 0.9191, 0.918, 0.9261, 0.9393, 0.9568, 0.9711, 0.9805, 0.978, 0.9614, 0.9416, 0.9257, 0.9184, 0.92, 0.9291, 0.9432, 0.9561, 0.9673, 0.9714, 0.9609, 0.94, 0.9193, 0.9055
        )),
        Mood("CALM", 0xECE2D9, 0.0, 0.0, doubleArrayOf(
            0.9691, 0.9643, 0.957, 0.9468, 0.9452, 0.9455, 0.9548, 0.9589, 0.9636, 0.9552, 0.9475, 0.9418, 0.9434, 0.9468, 0.9568, 0.9639, 0.9659, 0.9639, 0.9568, 0.9468, 0.9452, 0.9455, 0.9548, 0.9589, 0.9664, 0.9634, 0.9664, 0.9657, 0.9689, 0.9691, 0.9757, 0.9777, 0.9841, 0.9868, 0.9848, 0.9825, 0.9802, 0.9845, 0.9905, 0.995, 1.0036, 1.0052, 1.0, 0.992, 0.9857, 0.988, 0.9923, 0.9995, 0.9945, 0.9918, 0.985, 0.9825, 0.9802, 0.9845, 0.9905, 0.992, 0.9973, 0.99, 0.9864, 0.9741, 0.9716, 0.9691, 0.9736, 0.9714
        )),
        Mood("HAPPY", 0x71DD6B, 0.55, 0.0, doubleArrayOf(
            0.9525, 0.9241, 0.898, 0.8805, 0.8836, 0.9011, 0.9307, 0.9564, 0.9795, 0.9984, 1.0034, 1.0052, 0.9918, 0.9739, 0.9452, 0.9266, 0.9155, 0.9266, 0.9452, 0.9739, 0.9943, 1.0102, 1.0166, 1.0127, 1.0034, 0.9848, 0.9627, 0.937, 0.915, 0.9109, 0.9234, 0.9484, 0.9725, 0.9845, 0.9873, 0.9855, 0.9807, 0.9616, 0.9364, 0.9059, 0.8827, 0.8657, 0.8602, 0.8761, 0.9075, 0.9341, 0.9568, 0.9689, 0.9734, 0.9639, 0.9468, 0.9291, 0.9073, 0.8782, 0.8643, 0.8618, 0.8809, 0.9052, 0.928, 0.9536, 0.9684, 0.9782, 0.9782, 0.9689
        )),
        Mood("SUPER HAPPY", 0xFAC576, 0.9, 0.0, doubleArrayOf(
            0.7948, 0.808, 0.8461, 0.8861, 0.9223, 0.9327, 0.9259, 0.8943, 0.8495, 0.802, 0.7802, 0.773, 0.7973, 0.8291, 0.8718, 0.9066, 0.9227, 0.9123, 0.8832, 0.8423, 0.8036, 0.7759, 0.7739, 0.7989, 0.845, 0.8898, 0.9143, 0.9232, 0.91, 0.8811, 0.8436, 0.808, 0.7948, 0.803, 0.8361, 0.8839, 0.927, 0.9486, 0.9445, 0.9184, 0.8814, 0.8427, 0.8141, 0.8023, 0.8168, 0.8545, 0.9041, 0.9425, 0.9518, 0.9357, 0.8995, 0.8523, 0.8159, 0.8005, 0.8105, 0.8432, 0.885, 0.9275, 0.9491, 0.9509, 0.927, 0.8839, 0.8361, 0.803
        )),
        Mood("AWESOME", 0xF38B9D, 1.0, 0.0, doubleArrayOf(
            0.9441, 0.9236, 0.8816, 0.8457, 0.8391, 0.8586, 0.8993, 0.9275, 0.9336, 0.9127, 0.8766, 0.8364, 0.8223, 0.8361, 0.872, 0.9066, 0.9227, 0.9102, 0.8816, 0.8468, 0.8382, 0.8509, 0.8839, 0.9152, 0.9336, 0.93, 0.9043, 0.8707, 0.85, 0.86, 0.893, 0.9316, 0.9527, 0.95, 0.9232, 0.8864, 0.8695, 0.8789, 0.9114, 0.945, 0.965, 0.9593, 0.9282, 0.8873, 0.8723, 0.8864, 0.9255, 0.9568, 0.9686, 0.9523, 0.9164, 0.8818, 0.8677, 0.8827, 0.9191, 0.9548, 0.96, 0.9377, 0.8968, 0.8661, 0.8609, 0.8777, 0.9095, 0.9364
        )),
    )
    val count = moods.size
    fun clamp(value: Double): Double = value.coerceIn(0.0, 6.0)
    fun nearest(value: Double): Int = floor(clamp(value) + 0.5).toInt()
    fun segment(t: Double, a: Double, b: Double): Double = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
    fun ease(p: Double): Double = p * p * (3 - 2 * p)
    fun mix(a: Double, b: Double, p: Double): Double = a + (b - a) * p
    fun itemWidth(m: Double): Double = mix(200.0, 256.0, ease(segment(m, 4.0, 5.0)))
    fun menuTrack(p: Double, a: Double, b: Double, c: Double): Double =
        if (p <= 0.355) mix(a, b, p / 0.355) else mix(b, c, (p - 0.355) / 0.645)
    fun normalized(time: Double): Double = ((time % LOOP) + LOOP) % LOOP
    data class Blend(val from: Mood, val to: Mood, val f: Double)
    fun blend(m: Double): Blend {
        val value = clamp(m)
        val index = floor(value).toInt()
        return Blend(moods[index], moods[min(index + 1, 6)], ease(value - index))
    }
    private fun radius(mood: Mood, theta: Double): Double {
        val p = theta / (2 * PI) * mood.radii.size
        val index = floor(p).toInt() % mood.radii.size
        return mix(mood.radii[index], mood.radii[(index + 1) % mood.radii.size], p - floor(p))
    }
    fun outline(m: Double): DoubleArray {
        val b = blend(m)
        return DoubleArray(120) { index ->
            val theta = index / 120.0 * 2 * PI
            mix(radius(b.from, theta), radius(b.to, theta), b.f)
        }
    }
    fun color(m: Double): Int {
        val b = blend(m)
        fun channel(shift: Int): Int = mix(((b.from.color shr shift) and 255).toDouble(), ((b.to.color shr shift) and 255).toDouble(), b.f).let { floor(it + 0.5).toInt() }
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    fun title(m: Double, step: Int, ghost: Boolean = false, still: Boolean = false): String {
        val name = moods[nearest(m)].name
        val distance = abs(m - floor(m + 0.5))
        val reveal = 1 - ease(segment(distance, 0.02, 0.42))
        if (still || reveal >= 1) return if (ghost) "" else name
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return name.mapIndexed { index, char ->
            if (char == ' ' || (!ghost && (index + 1).toDouble() / name.length <= reveal)) char
            else {
                val hash = abs(sin(index * 12.9898 + (step + if (ghost) 7 else 0) * 78.233) * 43758.5453)
                alphabet[((hash % 1) * 26).toInt() % 26]
            }
        }.joinToString("")
    }
    data class Shown(val chooser: Double, val panel: Double, val m: Double, val clock: Double, val closing: Boolean = false, val origin: Double = 0.0)
    private val expansionKeys = listOf(
        0.20 to 0.0, 0.233 to 0.015, 0.267 to 0.155, 0.300 to 0.355, 0.333 to 0.51, 0.367 to 0.64, 0.400 to 0.73,
        0.433 to 0.82, 0.467 to 0.865, 0.500 to 0.915, 0.533 to 0.955, 0.567 to 0.98, 0.600 to 0.99, 0.633 to 1.0,
    )
    fun expansion(source: Double): Double {
        for (index in 1 until expansionKeys.size) {
            if (source <= expansionKeys[index].first) {
                val a = expansionKeys[index - 1]
                val b = expansionKeys[index]
                return mix(a.second, b.second, segment(source, a.first, b.first))
            }
        }
        return 1.0
    }
    private val moodKeys = listOf(
        0.0 to 5.0, 1.12 to 5.0, 1.88 to 0.0, 2.50 to 0.0, 3.02 to 2.0, 3.38 to 3.0, 3.64 to 4.0, 3.90 to 5.0, 4.12 to 6.0, 4.55 to 6.0, 4.88 to 5.0, 5.18 to 5.0, 5.40 to 4.0, 6.15 to 4.0, 6.65 to 2.0, 6.90 to 2.0, 7.25 to 3.0, 7.60 to 3.0, 7.88 to 4.0, 8.42 to 4.0, 8.70 to 3.0, 9.30 to 3.0, 9.50 to 4.0, 17.2 to 4.0,
    )
    fun moodAt(time: Double): Double {
        for (index in 1 until moodKeys.size) {
            if (time <= moodKeys[index].first) {
                val a = moodKeys[index - 1]
                val b = moodKeys[index]
                return mix(a.second, b.second, ease(segment(time, a.first, b.first)))
            }
        }
        return 4.0
    }
    fun scripted(time: Double): Shown {
        val t = normalized(time)
        if (t >= 8.8) {
            val p = ease(segment(t, 8.8, 9.45))
            return Shown(p, 0.0, mix(4.0, 5.0, p), t)
        }
        val source = t * 1.4
        val exit = ease(segment(source, 9.58, 9.95))
        return Shown(1 - ease(segment(source, 10.08, 10.40)), expansion(source) * (1 - exit), moodAt(source), t, closing = source >= 9.58 && source < 9.95)
    }
}
