package com.ngocdevv.nativecook.blocks.onboarding.morphingbadgetour

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// The artwork is one cast of PIECES shapes. Every scene gives each of them a place, a size, a
// corner radius and two colours, so a change of scene never cuts: each shape travels and turns
// into what it is in the next scene.
//
// A piece in a scene is ten numbers: x, y (from the stage's centre), width, height, corner radius,
// opacity, the two colours of its gradient (0xRRGGBB), the gradient's direction (0 down, 1 across)
// and the opacity of its glyph.

internal const val FIELDS = 10

// The cast, by place in a scene.
internal const val CAPSULE = 0
internal const val TOKEN = 1
internal const val TOKENS = 8
internal const val EXTRA = 9
internal const val EXTRAS = 10
internal const val HERO = 19
internal const val SPARK = 20
internal const val SPARKS = 3
internal const val DIAMOND = 23
internal const val OUTLINE = 24

/** Not a shape: how much the discs bob (dp) and breathe (share of their size) in a scene. */
internal const val IDLE = 25
internal const val PIECES = 26

/** A change of scene takes MOVE seconds; the pieces start STAGGER apart and land on a spring. */
internal const val MOVE = 0.9f
internal const val STAGGER = 0.03f
internal const val STIFFNESS = 170f
internal const val DAMPING = 15.6f

/** The spring is taken as settled after this long. */
internal const val SETTLE = 0.62f

/** Opacity, colours and glyphs change over this long, without a bounce. */
internal const val BLEND = 0.45f

/** From the last scene to the first, everything first leaves the stage: this long. */
internal const val LEAVE = 0.4f
internal const val LEAVE_STAGGER = 0.01f

/** The caption fades out in the first half of CAPTION and the new one in over the second. */
internal const val CAPTION = 0.5f
internal const val CAPTION_SHIFT = 8f

/** A swipe further than this goes to the next or the previous scene. */
internal const val SWIPE = 40f

// At rest the discs bob or breathe and the sparkles pulse. The periods divide LOOP.
internal val BOB_PERIODS = floatArrayOf(2.5f, 2f, 10f / 3f)
internal const val BREATH_PERIOD = 2f
internal const val PULSE_PERIOD = 1.25f

/** autoPlay: next scene at each of NEXT_AT, every LOOP seconds; the last one leads back to the first. */
internal const val LOOP = 10f
internal val NEXT_AT = floatArrayOf(2f, 4.2f, 6.2f, 8.6f)

/** After a touch autoPlay waits this long before it starts over from the first scene. */
internal const val USER_HOLD = 3f

internal const val BACKGROUND = 0x0e0d10
private const val GREEN = 0x78ca4c
private const val MINT = 0x02ff9b
private const val SKY = 0x90d4f4
private const val BLUE = 0x0f79fc
private const val ROYAL = 0x1f3dff
private const val MAGENTA = 0xd91efd
private const val VIOLET = 0xc586f9
private const val YELLOW = 0xffd22d
private const val LEMON = 0xffe543
private const val ORANGE = 0xde9458
private const val LEAF = 0x32b951
private const val CYAN = 0x35d7e6
internal const val SPARKLE = 0xffa62b
internal const val HERO_RING = ROYAL

/** The two colours of each token, and whether its glyph is white (1) or black (0). */
private val TokenInks = arrayOf(
    intArrayOf(GREEN, MINT, 0), intArrayOf(MAGENTA, BLUE, 1), intArrayOf(SKY, LEAF, 0), intArrayOf(LEMON, MINT, 0),
    intArrayOf(YELLOW, ORANGE, 1), intArrayOf(BLUE, MINT, 1), intArrayOf(ROYAL, SKY, 1), intArrayOf(VIOLET, ORANGE, 0),
)
internal val GlyphWhite = BooleanArray(TOKENS) { TokenInks[it][2] == 1 }
private val ExtraInks = arrayOf(
    intArrayOf(MAGENTA, VIOLET), intArrayOf(BLUE, SKY), intArrayOf(LEMON, ORANGE), intArrayOf(GREEN, LEAF), intArrayOf(ROYAL, MAGENTA),
    intArrayOf(MINT, BLUE), intArrayOf(VIOLET, SKY), intArrayOf(YELLOW, GREEN), intArrayOf(ORANGE, MAGENTA), intArrayOf(SKY, ROYAL),
)

private fun disc(x: Float, y: Float, size: Float, a: Int, b: Int, alpha: Float = 1f, glyph: Float = 0f) =
    floatArrayOf(x, y, size, size, size / 2f, alpha, a.toFloat(), b.toFloat(), 0.5f, glyph)

private fun plain(x: Float, y: Float, w: Float, h: Float, radius: Float, alpha: Float) = floatArrayOf(x, y, w, h, radius, alpha, 0f, 0f, 0f, 0f)

/** A scene from its parts: the capsule, where the tokens and the extras are, the hero and the rest. */
private fun scene(
    capsule: FloatArray,
    tokens: (Int) -> FloatArray,
    extras: (Int) -> FloatArray,
    hero: FloatArray,
    sparks: List<FloatArray>,
    diamond: Float,
    outline: Float,
    idle: FloatArray,
): List<FloatArray> = buildList {
    add(capsule)
    repeat(TOKENS) { add(tokens(it)) }
    repeat(EXTRAS) { add(extras(it)) }
    add(plain(hero[0], hero[1], hero[2], hero[2], hero[2] / 2f, hero[3]))
    sparks.forEach { add(plain(it[0], it[1], it[2], it[2], 0f, it[3])) }
    // The diamond waits at the top of the beam and drops to its foot in the last scene.
    add(plain(0f, if (diamond > 0f) -42f else -140f, 60f, 60f, 0f, diamond))
    add(plain(0f, 60f, 172f, 122f, 6f, outline))
    add(floatArrayOf(idle[0], idle[1], 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
}

private fun badge(index: Int, x: Float, y: Float, size: Float) = disc(x, y, size, TokenInks[index][0], TokenInks[index][1], 1f, 1f)

private fun small(index: Int, x: Float, y: Float, size: Float, alpha: Float) = disc(x, y, size, ExtraInks[index][0], ExtraInks[index][1], alpha)

/** Where the eight badges float round the capsule. */
private val Floating = arrayOf(
    floatArrayOf(-118f, -120f), floatArrayOf(-22f, -192f), floatArrayOf(122f, -150f), floatArrayOf(-142f, 12f),
    floatArrayOf(134f, 24f), floatArrayOf(-190f, -62f), floatArrayOf(188f, -74f), floatArrayOf(150f, -218f),
)
private val SparksWelcome = arrayOf(floatArrayOf(-92f, -44f, 18f), floatArrayOf(98f, 84f, 22f), floatArrayOf(66f, -98f, 14f))

/** The grid's cells, as column and row from the middle one, which is the hero's. */
private val Cells = arrayOf(
    floatArrayOf(-1f, -1f), floatArrayOf(0f, -1f), floatArrayOf(1f, -1f), floatArrayOf(-1f, 0f),
    floatArrayOf(1f, 0f), floatArrayOf(-1f, 1f), floatArrayOf(1f, 1f), floatArrayOf(0f, 1f),
)
private const val CELL = 88f
private const val PITCH = 100f
private const val GRID_Y = -10f
private val Scattered = arrayOf(
    floatArrayOf(-108f, -118f, 56f), floatArrayOf(-18f, -150f, 44f), floatArrayOf(104f, -128f, 60f), floatArrayOf(-138f, -22f, 40f),
    floatArrayOf(118f, -20f, 48f), floatArrayOf(-112f, 92f, 58f), floatArrayOf(112f, 96f, 52f), floatArrayOf(10f, 128f, 36f),
)
private val ScatteredExtras = arrayOf(
    floatArrayOf(-60f, -70f, 22f), floatArrayOf(52f, -66f, 18f), floatArrayOf(-70f, 30f, 26f), floatArrayOf(66f, 40f, 20f), floatArrayOf(-10f, -78f, 14f),
    floatArrayOf(0f, 70f, 16f), floatArrayOf(-150f, -90f, 18f), floatArrayOf(150f, -70f, 16f), floatArrayOf(-45f, 150f, 20f), floatArrayOf(150f, 30f, 24f),
)
private const val WALLET_Y = 60f
private const val STRIPE = 37.5f
private val StripeInks = arrayOf(intArrayOf(VIOLET, YELLOW), intArrayOf(YELLOW, LEAF), intArrayOf(LEAF, CYAN), intArrayOf(CYAN, VIOLET))

/** The hero badge on a capsule, badges floating round it. */
private val Welcome = scene(
    capsule = floatArrayOf(0f, 20f, 78f, 150f, 39f, 1f, 0xb7c523.toFloat(), YELLOW.toFloat(), 0f, 0f),
    tokens = { badge(it, Floating[it][0], Floating[it][1], 44f) },
    extras = { small(it, 0f, -16f, 10f, 0f) },
    hero = floatArrayOf(0f, -16f, 78f, 1f),
    sparks = SparksWelcome.map { floatArrayOf(it[0], it[1], it[2], 1f) },
    diamond = 0f,
    outline = 0f,
    idle = floatArrayOf(6f, 0f),
)

/** Three by three: the badges are the cells round the hero. */
private val Grid = scene(
    capsule = floatArrayOf(0f, GRID_Y, 78f, 0f, 0f, 0f, 0xb7c523.toFloat(), YELLOW.toFloat(), 0f, 0f),
    tokens = { badge(it, Cells[it][0] * PITCH, GRID_Y + Cells[it][1] * PITCH, CELL) },
    extras = { small(it, 0f, GRID_Y, 10f, 0f) },
    hero = floatArrayOf(0f, GRID_Y, CELL, 1f),
    sparks = SparksWelcome.map { floatArrayOf(it[0], it[1], 0f, 0f) },
    diamond = 0f,
    outline = 0f,
    idle = floatArrayOf(0f, 0.03f),
)

/** A constellation: the badges and ten smaller discs scattered round a small hero. */
private val Constellation = scene(
    capsule = floatArrayOf(0f, 0f, 78f, 0f, 0f, 0f, 0xb7c523.toFloat(), YELLOW.toFloat(), 0f, 0f),
    tokens = { badge(it, Scattered[it][0], Scattered[it][1], Scattered[it][2]) },
    extras = { small(it, ScatteredExtras[it][0], ScatteredExtras[it][1], ScatteredExtras[it][2], 1f) },
    hero = floatArrayOf(0f, 0f, 40f, 1f),
    sparks = SparksWelcome.map { floatArrayOf(it[0], it[1], 0f, 0f) },
    diamond = 0f,
    outline = 0f,
    idle = floatArrayOf(3f, 0f),
)

/** A wallet: four badges are its stripes, a fifth its clasp, and the capsule is the beam a diamond drops along. */
private val Wallet = scene(
    capsule = floatArrayOf(0f, -70f, 66f, 110f, 0f, 1f, 0xff8a1f.toFloat(), YELLOW.toFloat(), 0f, 0f),
    tokens = { i ->
        when {
            i < 4 -> floatArrayOf(0f, WALLET_Y + (i - 1.5f) * STRIPE, 200f, STRIPE, 4f, 1f, StripeInks[i][0].toFloat(), StripeInks[i][1].toFloat(), 1f, 0f)
            i == 4 -> floatArrayOf(88f, WALLET_Y, 36f, 24f, 12f, 1f, BACKGROUND.toFloat(), BACKGROUND.toFloat(), 0.5f, 1f)
            else -> disc(0f, WALLET_Y, 10f, TokenInks[i][0], TokenInks[i][1], 0f)
        }
    },
    extras = { small(it, 0f, WALLET_Y, 6f, 0f) },
    hero = floatArrayOf(0f, WALLET_Y, 20f, 0f),
    sparks = listOf(floatArrayOf(-88f, -62f, 20f, 1f), floatArrayOf(72f, -24f, 14f, 1f), floatArrayOf(-58f, -132f, 12f, 1f)),
    diamond = 1f,
    outline = 1f,
    idle = floatArrayOf(0f, 0f),
)

/** Off the stage: the capsule as a tall bar under it, the badges out past its edges. */
private val Offstage = scene(
    capsule = floatArrayOf(0f, 540f, 78f, 330f, 39f, 1f, 0x8a3ffc.toFloat(), CYAN.toFloat(), 0f, 0f),
    tokens = { badge(it, Floating[it][0] * 2.6f, Floating[it][1] * 2.6f, 44f) },
    extras = { small(it, 0f, 414f, 10f, 0f) },
    hero = floatArrayOf(0f, 414f, 78f, 1f),
    sparks = SparksWelcome.map { floatArrayOf(it[0], it[1], 0f, 0f) },
    diamond = 0f,
    outline = 0f,
    idle = floatArrayOf(0f, 0f),
)

/** The scenes in order, and after them the place everything leaves to on the way back to the first. */
internal val Scenes = listOf(Welcome, Grid, Constellation, Wallet, Offstage)
internal const val COUNT = 4
internal const val AWAY = 4

internal fun clamp01(t: Float) = t.coerceIn(0f, 1f)

internal fun ease(t: Float): Float {
    val u = clamp01(t)
    return if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
}

/** Where a spring of mass 1 that was let go at 0 is after [t] seconds, on its way to 1. */
internal fun spring(t: Float): Float {
    if (t <= 0f) return 0f
    if (t >= SETTLE) return 1f
    val w = sqrt(STIFFNESS)
    val z = DAMPING / (2f * w)
    val wd = w * sqrt(1f - z * z)
    return 1f - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
}

// A colour travels as a number, 0xRRGGBB, which a Float holds exactly.
private fun mixColor(a: Float, b: Float, t: Float): Float {
    val from = a.toInt()
    val to = b.toInt()
    fun channel(shift: Int): Int {
        val start = (from shr shift) and 0xff
        return (start + (((to shr shift) and 0xff) - start) * t).roundToInt()
    }
    return ((channel(16) shl 16) or (channel(8) shl 8) or channel(0)).toFloat()
}

/** How long the run to a scene takes when it starts by leaving the stage ([away]) or not. */
internal fun runLength(away: Boolean) = if (away) LEAVE + MOVE else MOVE

/**
 * Field [i] of piece [k], [r] seconds into a run from where the pieces were ([from], a flat list
 * of PIECES × FIELDS numbers) to the scene [to]. With [away] set, everything first leaves the stage.
 */
internal fun pieceValue(from: FloatArray, to: Int, away: Boolean, r: Float, k: Int, i: Int): Float {
    val start = from[k * FIELDS + i]
    // The hero rides on the capsule: they leave together.
    val order = if (k == HERO) 0 else k % 9
    val colour = i == 6 || i == 7
    if (away && r < LEAVE) {
        // Leaving speeds up: nothing lands.
        val u = clamp01((r - order * LEAVE_STAGGER) / (LEAVE - 8f * LEAVE_STAGGER))
        val end = Scenes[AWAY][k][i]
        return if (colour) mixColor(start, end, u * u) else start + (end - start) * u * u * u
    }
    val base = if (away) Scenes[AWAY][k][i] else start
    val end = Scenes[to][k][i]
    val t = (if (away) r - LEAVE else r) - order * STAGGER
    if (colour) return mixColor(base, end, ease(t / BLEND))
    // Places and sizes land on the spring; the rest blends.
    val landing = i < 5 && k != IDLE
    return base + (end - base) * if (landing) spring(t) else ease(t / BLEND)
}

/** How far up a bobbing disc is, [clock] seconds after the block appeared. */
internal fun bob(k: Int, amount: Float, clock: Float) = amount * sin(2f * PI.toFloat() * clock / BOB_PERIODS[k % 3] + k * 1.7f)

/** The size of a breathing disc at (x, y): the breath runs across the grid as a diagonal wave. */
internal fun breathe(x: Float, y: Float, amount: Float, clock: Float) =
    1f + amount * (0.5f + 0.5f * sin(2f * PI.toFloat() * clock / BREATH_PERIOD - (x + y) * 0.006f))

internal fun pulse(index: Int, clock: Float) = 0.8f + 0.2f * sin(2f * PI.toFloat() * clock / PULSE_PERIOD + index * 2.1f)

/** How far the caption that leaves has gone, [r] seconds into a run: 0 to 1. */
internal fun captionGone(r: Float) = clamp01(r / (CAPTION / 2f))

/** How far the caption that arrives has come: 0 to 1. */
internal fun captionCome(r: Float) = clamp01((r - CAPTION / 2f) / (CAPTION / 2f))

// The glyphs, in a box from -1 to 1. A part is a polygon (0, then its points), a rounded
// rectangle (1: x, y, width, height, radius) or a circle (2: x, y, radius).
internal val Glyphs = listOf(
    // A diamond.
    listOf(floatArrayOf(0f, 0f, -1f, 0.85f, 0f, 0f, 1f, -0.85f, 0f)),
    // Three bars.
    listOf(
        floatArrayOf(1f, -0.9f, -0.85f, 1.8f, 0.42f, 0.21f),
        floatArrayOf(1f, -0.9f, -0.21f, 1.8f, 0.42f, 0.21f),
        floatArrayOf(1f, -0.9f, 0.43f, 1.8f, 0.42f, 0.21f),
    ),
    // A face: two eyes and a mouth.
    listOf(floatArrayOf(2f, -0.45f, -0.3f, 0.24f), floatArrayOf(2f, 0.45f, -0.3f, 0.24f), floatArrayOf(1f, -0.6f, 0.3f, 1.2f, 0.3f, 0.15f)),
    // A star with four points.
    listOf(floatArrayOf(0f, 0f, -1f, 0.25f, -0.25f, 1f, 0f, 0.25f, 0.25f, 0f, 1f, -0.25f, 0.25f, -1f, 0f, -0.25f, -0.25f)),
    // A coin, which is the dot of the wallet's clasp.
    listOf(floatArrayOf(2f, 0f, 0f, 0.42f)),
    // Two arrows, one up and one down.
    listOf(
        floatArrayOf(0f, -0.45f, -0.95f, -0.9f, -0.25f, -0.6f, -0.25f, -0.6f, 0.9f, -0.3f, 0.9f, -0.3f, -0.25f, 0f, -0.25f),
        floatArrayOf(0f, 0.45f, 0.95f, 0.9f, 0.25f, 0.6f, 0.25f, 0.6f, -0.9f, 0.3f, -0.9f, 0.3f, 0.25f, 0f, 0.25f),
    ),
    // A lightning bolt.
    listOf(floatArrayOf(0f, 0.2f, -1f, -0.6f, 0.15f, -0.05f, 0.15f, -0.25f, 1f, 0.6f, -0.2f, 0.05f, -0.2f)),
    // A speech bubble.
    listOf(floatArrayOf(1f, -0.9f, -0.75f, 1.8f, 1.2f, 0.4f), floatArrayOf(0f, -0.45f, 0.3f, -0.05f, 0.3f, -0.5f, 0.95f)),
)

/** A glyph is this share of its disc's size, from its middle to its edge. */
internal const val GLYPH_SCALE = 0.27f

/** A sparkle: a star with four thin points, in a box from -1 to 1. */
internal val SparkShape = floatArrayOf(0f, -1f, 0.18f, -0.18f, 1f, 0f, 0.18f, 0.18f, 0f, 1f, -0.18f, 0.18f, -1f, 0f, -0.18f, -0.18f)

/** The diamond's facets in a box from -1 to 1, back to front: a colour, then the points. */
internal val DiamondFacets = listOf(
    floatArrayOf(BLUE.toFloat(), -1f, -0.35f, -0.55f, -0.85f, 0.55f, -0.85f, 1f, -0.35f, 0f, 1f),
    floatArrayOf(SKY.toFloat(), -1f, -0.35f, -0.55f, -0.85f, 0.55f, -0.85f, 1f, -0.35f),
    floatArrayOf(ROYAL.toFloat(), -0.4f, -0.35f, 0.4f, -0.35f, 0f, 1f),
    floatArrayOf(0xddf1ff.toFloat(), -0.2f, -0.85f, 0.2f, -0.85f, 0.4f, -0.35f, -0.4f, -0.35f),
)
