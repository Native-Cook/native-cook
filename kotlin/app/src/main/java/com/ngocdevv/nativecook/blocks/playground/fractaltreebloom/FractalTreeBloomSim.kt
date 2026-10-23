package com.ngocdevv.nativecook.blocks.playground.fractaltreebloom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// A fractal tree grown from a seed, its wind, its foliage through the seasons and what falls from
// it. A port, step for step, of the React Native `fractalTreeSim.ts`.
//
// The tree is built once from a seed: every branch splits into two (now and then three) shorter,
// thinner ones, a leader that keeps close to its parent's line and side shoots that spread, all
// leaning a little towards the light. Branches are stored parents first, so one pass lays them out.
// Growth runs depth by depth; the wind reaches the trunk first and each level after a lag, and every
// branch also sways on its own breeze. The season runs 0 spring, 1 summer, 2 autumn, 3 winter and
// back to 4; each site reads it a little late, so colours and foliage morph across the crown. Leaves
// fall and petals drift down as particles, snow falls in winter; everything is seeded and runs on a
// fixed tick. No Android here: it compiles on its own.

internal const val MAX_DEPTH = 7
// The trunk, dp, and how much shorter and thinner each level is.
private const val TRUNK_LENGTH = 120.0
private const val TRUNK_WIDTH = 15.0
private const val LEADER_RATIO = 0.82
private const val SIDE_RATIO = 0.7
private const val WIDTH_RATIO = 0.68
// Spread of the side shoots, rad, and the pull of every branch back towards upright.
private const val SPREAD_MIN = 0.32
private const val SPREAD_MAX = 0.54
private const val LIGHT_PULL = 0.16
// Foliage on the last levels.
private const val LEAF_LEVELS = 3
internal const val MAX_PARTICLES = 480
// How far ahead of the season the earliest site runs (the latest runs exactly on it).
private const val SITE_LEAD = 0.35
// Wind: how fast the trunk takes it up and each level after it (1/s), and how far it bends a twig.
private const val WIND_TRUNK_RATE = 5.0
private const val WIND_LEVEL_RATE = 3.6
private const val BEND = 0.32
// The breeze every branch sways in: amplitude at the tips, rad, and its pace.
private const val BREEZE = 0.05
private const val BREEZE_SPEED = 1.3

// Particle kinds.
internal const val PETAL = 0
internal const val LEAF = 1
internal const val SNOW = 2

/** xorshift32 in [0, 1) on `state[0]`. */
private fun random(state: IntArray): Double {
    var x = state[0]
    x = x xor (x shl 13)
    x = x xor (x ushr 17)
    x = x xor (x shl 5)
    state[0] = x
    return (x.toLong() and 0xffffffffL).toDouble() / 4294967296.0
}

/** Ease out (cubic). */
internal fun treeEaseOut(t: Double): Double {
    val k = min(1.0, max(0.0, t))
    return 1 - (1 - k) * (1 - k) * (1 - k)
}

/** Ease in and out (cubic). */
internal fun treeEaseInOut(t: Double): Double {
    val k = min(1.0, max(0.0, t))
    return if (k < 0.5) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2
}

/** Ease out with a little overshoot. */
internal fun treeEaseOutBack(t: Double): Double {
    val k = min(1.0, max(0.0, t))
    val u = k - 1
    return 1 + 2.4 * u * u * u + 1.4 * u * u
}

/** The mound the tree stands on: a low hump on a flat ground line. */
internal class TreeGround(val base: Double, val cx: Double, val bump: Double, val spread: Double) {
    /** Height of the ground at x. */
    fun y(x: Double): Double {
        val u = (x - cx) / spread
        return base - bump * exp(-u * u)
    }
}

/** The season (0…4) as a site reads it, wrapped into 0…4. */
internal fun localSeason(season: Double, lag: Double): Double {
    val s = season + SITE_LEAD - lag
    return s - 4 * floor(s / 4)
}

/** Builds the tree for a seed (the same seed always grows the same tree). */
internal class Tree(seed: Int) {
    val count: Int
    val parent = IntArray(1200)
    val depth = IntArray(1200)
    private val length = DoubleArray(1200)
    private val angle = DoubleArray(1200)
    private val width = DoubleArray(1200)
    private val phase = DoubleArray(1200)
    /** Laid out each frame: base, tip, absolute angle and drawn width at base and tip. */
    val bx: DoubleArray
    val by: DoubleArray
    val tx: DoubleArray
    val ty: DoubleArray
    val abs: DoubleArray
    val w0: DoubleArray
    val w1: DoubleArray
    val shown: DoubleArray
    /** Foliage sites: branch, place along it (0…1), offset across it, size and look. */
    val sites: Int
    val siteBranch = IntArray(900)
    val siteAt = DoubleArray(900)
    val siteSide = DoubleArray(900)
    val siteSize = DoubleArray(900)
    val siteSpin = DoubleArray(900)
    /** How late the site reads the season (0…0.35), when its leaf falls, and its autumn colour (0…1). */
    val siteLag = DoubleArray(900)
    val siteFall = DoubleArray(900)
    val siteHue = DoubleArray(900)
    /** 0 or 1: which blossom it bears. */
    val siteKind = IntArray(900)
    /** Its season last tick (for leaves falling and petals dropping as it passes). */
    val siteSeen: DoubleArray

    init {
        val state = intArrayOf(seed or 1)
        val max = 1200
        // The trunk, slightly off upright.
        parent[0] = -1
        depth[0] = 0
        length[0] = TRUNK_LENGTH
        angle[0] = (random(state) - 0.5) * 0.08
        width[0] = TRUNK_WIDTH
        phase[0] = 0.0
        var n = 1
        // Breadth first: parents always come before their children.
        val absAngle = DoubleArray(max)
        absAngle[0] = angle[0]
        var i = 0
        while (i < n && n < max - 3) {
            val d = depth[i]
            if (d >= MAX_DEPTH) {
                i += 1
                continue
            }
            val three = if (d == 0) random(state) < 0.6 else random(state) < 0.06
            val branches = if (three) 3 else 2
            val spread = SPREAD_MIN + (SPREAD_MAX - SPREAD_MIN) * random(state)
            // The leader goes to one side a little, the others further the other way.
            val lean = if (random(state) < 0.5) -1.0 else 1.0
            for (k in 0 until branches) {
                val leader = k == 0
                var a = if (leader) {
                    lean * spread * (0.18 + 0.2 * random(state))
                } else if (branches == 2) {
                    -lean * spread * (0.85 + 0.3 * random(state))
                } else {
                    (if (k == 1) -lean else lean) * spread * (0.9 + 0.35 * random(state))
                }
                // Lean back towards the light.
                val absolute = absAngle[i] + a
                a -= absolute * LIGHT_PULL
                val ratio = (if (leader) LEADER_RATIO else SIDE_RATIO) * (0.9 + 0.2 * random(state))
                parent[n] = i
                depth[n] = d + 1
                length[n] = length[i] * ratio
                angle[n] = a
                width[n] = width[i] * WIDTH_RATIO * (if (leader) 1.0 else 0.86)
                phase[n] = random(state) * PI * 2
                absAngle[n] = absAngle[i] + a
                n += 1
            }
            i += 1
        }
        count = n
        // Foliage sites along the last levels.
        val maxSites = 900
        var s = 0
        i = 0
        while (i < count && s < maxSites) {
            if (depth[i] < MAX_DEPTH - LEAF_LEVELS + 1) {
                i += 1
                continue
            }
            // A twig bears one at its tip; the boughs below it one now and then.
            if (depth[i] < MAX_DEPTH && random(state) > 0.6) {
                i += 1
                continue
            }
            val m = if (depth[i] == MAX_DEPTH) 2 else 1
            var k = 0
            while (k < m && s < maxSites) {
                siteBranch[s] = i
                siteAt[s] = if (depth[i] == MAX_DEPTH && k == 0) 0.85 + 0.15 * random(state) else 0.4 + 0.4 * random(state)
                siteSide[s] = (random(state) - 0.5) * 7
                siteSize[s] = 0.75 + 0.5 * random(state)
                siteSpin[s] = random(state) * PI * 2
                siteLag[s] = random(state) * 0.35
                // A few leaves let go during autumn, the rest as winter comes.
                siteFall[s] = if (random(state) < 0.4) 1.85 + random(state) * 0.45 else 2.3 + random(state) * 0.4
                siteHue[s] = random(state)
                siteKind[s] = if (random(state) < 0.62) 0 else 1
                s += 1
                k += 1
            }
            i += 1
        }
        sites = s
        bx = DoubleArray(count)
        by = DoubleArray(count)
        tx = DoubleArray(count)
        ty = DoubleArray(count)
        abs = DoubleArray(count)
        w0 = DoubleArray(count)
        w1 = DoubleArray(count)
        shown = DoubleArray(count)
        siteSeen = DoubleArray(sites) { -1.0 }
    }

    /** Lays the tree out from its root at (x, y), bent by each level's share of the wind and its own breeze. */
    fun layout(weather: Weather, x: Double, y: Double, growth: Double, sway: Double) {
        val t = weather.time
        for (i in 0 until count) {
            val d = depth[i]
            val p = parent[i]
            val grown = treeEaseOut(growth - d)
            // Thin branches give more: the bend grows towards the tips.
            val flex = (d + 1).toDouble() / (MAX_DEPTH + 1)
            val lean = flex * flex * (BEND * weather.levels[d] + BREEZE * sin(t * BREEZE_SPEED * (1 + d * 0.06) + phase[i] - d * 0.5)) * sway
            val a = (if (p < 0) 0.0 else abs[p]) + angle[i] + lean * (if (d == 0) 0.25 else 1.0)
            abs[i] = a
            val baseX = if (p < 0) x else tx[p]
            val baseY = if (p < 0) y else ty[p]
            val len = length[i] * grown
            bx[i] = baseX
            by[i] = baseY
            tx[i] = baseX + sin(a) * len
            ty[i] = baseY - cos(a) * len
            shown[i] = grown
            val w = width[i] * (0.35 + 0.65 * grown)
            w0[i] = if (p < 0) w else min(w, w1[p])
            w1[i] = w * WIDTH_RATIO * 1.05
        }
    }

    /** Whether a site's leaf drops as a falling leaf (most do); the rest wither on the twig. */
    fun leafFalls(i: Int) = siteSpin[i] < PI * 1.4
}

internal class Weather(seed: Int) {
    /** The wind the finger (or the script) asks for, the wind now, its speed, and each level's share. */
    var target = 0.0
    var wind = 0.0
    var windSpeed = 0.0
    val levels = DoubleArray(MAX_DEPTH + 1)
    var time = 0.0
    var seed = seed
    var snowDue = 0.0

    /** One tick of the wind: it springs towards the target; each level follows the one below it, late. */
    fun step(dt: Double) {
        time += dt
        // A soft, slightly underdamped spring, so a released bough swings back past rest once.
        val accel = 26 * (target - wind) - 6.5 * windSpeed
        windSpeed += accel * dt
        wind += windSpeed * dt
        levels[0] += (wind - levels[0]) * min(1.0, WIND_TRUNK_RATE * dt)
        for (d in 1..MAX_DEPTH) levels[d] += (levels[d - 1] - levels[d]) * min(1.0, WIND_LEVEL_RATE * dt)
    }
}

/** A fixed pool of falling petals, leaves and snowflakes. */
internal class Particles {
    var count = 0
    val kind = IntArray(MAX_PARTICLES)
    val x = DoubleArray(MAX_PARTICLES)
    val y = DoubleArray(MAX_PARTICLES)
    private val vx = DoubleArray(MAX_PARTICLES)
    private val vy = DoubleArray(MAX_PARTICLES)
    val rot = DoubleArray(MAX_PARTICLES)
    private val spin = DoubleArray(MAX_PARTICLES)
    val size = DoubleArray(MAX_PARTICLES)
    val hue = DoubleArray(MAX_PARTICLES)
    private val phase = DoubleArray(MAX_PARTICLES)
    /** Seconds since it came to rest on the ground (−1 while falling). */
    val rest = DoubleArray(MAX_PARTICLES)
    /** Next slot to reuse when the pool is full. */
    private var cursor = 0
    private val state = IntArray(1)

    /** Adds a particle; when the pool is full the oldest one makes room. */
    private fun spawn(k: Int, px: Double, py: Double, s: Double, h: Double) {
        var i = count
        if (i >= MAX_PARTICLES) {
            i = cursor % MAX_PARTICLES
            cursor = i + 1
        } else {
            count += 1
        }
        kind[i] = k
        x[i] = px
        y[i] = py
        vx[i] = (random(state) - 0.5) * 20
        vy[i] = if (k == SNOW) 20.0 else -6 - random(state) * 10
        rot[i] = random(state) * PI * 2
        spin[i] = (random(state) - 0.5) * 5
        size[i] = s
        hue[i] = h
        phase[i] = random(state) * PI * 2
        rest[i] = -1.0
    }

    /**
     * Leaves fall and petals drop as each site passes its moment in the season; snow falls in
     * winter; every particle drifts down on the wind and rests on the ground a while, fading.
     */
    fun step(tree: Tree, weather: Weather, season: Double, dt: Double, width: Double, ground: TreeGround, falling: Boolean) {
        state[0] = weather.seed
        for (i in 0 until tree.sites) {
            val s = localSeason(season, tree.siteLag[i])
            val before = tree.siteSeen[i]
            tree.siteSeen[i] = s
            if (before < 0 || s < before || !falling) continue
            val b = tree.siteBranch[i]
            if (tree.shown[b] < 0.99) continue
            val px = tree.bx[b] + (tree.tx[b] - tree.bx[b]) * tree.siteAt[i]
            val py = tree.by[b] + (tree.ty[b] - tree.by[b]) * tree.siteAt[i]
            // A leaf lets go at its own moment in autumn.
            if (tree.leafFalls(i) && before < tree.siteFall[i] && s >= tree.siteFall[i]) spawn(LEAF, px, py, tree.siteSize[i], tree.siteHue[i])
            // About half the blossoms shed a petal as the leaves come.
            val shed = 0.5 + tree.siteHue[i] * 0.4
            if (tree.siteKind[i] == 0 && tree.siteHue[i] < 0.55 && before < shed && s >= shed) spawn(PETAL, px, py, tree.siteSize[i], tree.siteHue[i])
        }
        // Snow from above the screen while it is winter.
        val sea = season - 4 * floor(season / 4)
        val snowing = falling && sea > 2.85 && sea < 3.85
        if (snowing) {
            weather.snowDue += dt * 24
            while (weather.snowDue >= 1) {
                weather.snowDue -= 1
                val sx = random(state) * (width + 120) - 60
                val sz = 0.5 + random(state) * 0.7
                val h = random(state)
                spawn(SNOW, sx, -10.0, sz, h)
            }
        } else {
            weather.snowDue = 0.0
        }
        weather.seed = state[0]

        val wind = weather.wind
        for (i in 0 until count) {
            if (rest[i] >= 0) {
                rest[i] += dt
                continue
            }
            val k = kind[i]
            // Each kind drifts down at its own pace, swept by the wind and fluttering side to side.
            val fall = if (k == SNOW) 60 + size[i] * 30 else if (k == LEAF) 105.0 else 66.0
            val flutter = if (k == SNOW) 10.0 else 26.0
            val drift = wind * (if (k == SNOW) 70 else 95) + sin(weather.time * 2.1 + phase[i]) * flutter
            vx[i] += (drift - vx[i]) * min(1.0, dt * 2.4)
            vy[i] += (fall - vy[i]) * min(1.0, dt * 1.8)
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            rot[i] += spin[i] * dt * (if (k == SNOW) 0.3 else 1.0) + sin(weather.time * 3 + phase[i]) * dt
            val floorY = ground.y(x[i]) - (if (k == SNOW) 1 else 2)
            if (y[i] >= floorY) {
                y[i] = floorY
                rest[i] = 0.0
            }
        }
        // Drop what has rested long enough, keeping the pool packed.
        var n = 0
        for (i in 0 until count) {
            val keep = rest[i] < (if (kind[i] == SNOW) 1.2 else 2.0) && x[i] > -80 && x[i] < width + 80
            if (!keep) continue
            if (n != i) {
                kind[n] = kind[i]
                x[n] = x[i]
                y[n] = y[i]
                vx[n] = vx[i]
                vy[n] = vy[i]
                rot[n] = rot[i]
                spin[n] = spin[i]
                size[n] = size[i]
                hue[n] = hue[i]
                phase[n] = phase[i]
                rest[n] = rest[i]
            }
            n += 1
        }
        count = n
    }
}
