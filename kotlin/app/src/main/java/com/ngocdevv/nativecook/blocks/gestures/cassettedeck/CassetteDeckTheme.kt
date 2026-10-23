package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import kotlin.math.roundToInt

/** `rgba()` in the React Native theme, as an Android ARGB int. */
internal fun cassetteDeckArgb(r: Int, g: Int, b: Int, a: Double = 1.0): Int {
    val alpha = (a * 255.0).roundToInt().coerceIn(0, 255)
    return (alpha shl 24) or (r shl 16) or (g shl 8) or b
}

/** `#rrggbb` in the React Native theme, as an opaque ARGB int. */
internal fun cassetteDeckHex(rgb: Int): Int = (0xFF shl 24) or rgb

/**
 * Every colour and size of the block (RN `theme.ts`). Sizes are points on the source's 402 x 874 pt screen
 * and are scaled by width / 402 when drawn.
 */
internal object CassetteDeckTheme {
    const val SCREEN_W = 402.0
    const val SCREEN_H = 874.0

    // RN theme.ts spacing scale, step 0.
    object Space {
        const val xs = 4.0
        const val sm = 8.0
        const val md = 12.0
        const val lg = 16.0
        const val xl = 24.0
        const val xxl = 32.0
    }

    // RN theme.ts:7-9. Measured: flat #242225 with a faint lift above the cassette and a darker pool under it.
    val bg = cassetteDeckHex(0x242225)
    // Unused by the RN drawing code (the lift is derived from `tints`); kept so every theme value exists.
    val bgLift = cassetteDeckArgb(255, 252, 255, 0.045)
    val bgPool = cassetteDeckArgb(0, 0, 0, 0.16)
    val grille = cassetteDeckHex(0x19181B)
    val grilleRim = cassetteDeckArgb(255, 255, 255, 0.05)
    val ledOff = cassetteDeckHex(0x3A393D)
    val ledOn = cassetteDeckHex(0x35E39A)
    val ledGlow = cassetteDeckArgb(53, 227, 154, 0.55)
    val glyphOff = cassetteDeckHex(0x2D2C30)
    val glyphOn = cassetteDeckHex(0xE9E8EC)
    val glyphGlow = cassetteDeckArgb(255, 255, 255, 0.35)
    val labelInk = cassetteDeckHex(0x1C1B1D)
    val labelMute = cassetteDeckHex(0x5E5C60)
    val wordmark = cassetteDeckHex(0xECEAE4)
    val pillTop = cassetteDeckHex(0xA9A9AB)
    val pillBottom = cassetteDeckHex(0x7E7E81)
    val pillInk = cassetteDeckHex(0x3A3A3C)
    val tape = cassetteDeckHex(0x2B2421)
    val tapeEdge = cassetteDeckArgb(150, 118, 92, 0.35)

    // A narrow band of the key light, soft at both ends.
    val sheen = intArrayOf(
        cassetteDeckArgb(255, 255, 255, 0.0),
        cassetteDeckArgb(255, 255, 255, 0.06),
        cassetteDeckArgb(255, 255, 255, 0.13),
        cassetteDeckArgb(255, 255, 255, 0.06),
        cassetteDeckArgb(255, 255, 255, 0.0),
    )
    val sheenStops = floatArrayOf(0.3f, 0.46f, 0.5f, 0.54f, 0.7f)

    // The shell's side faces: lit from the top left, so the left face is brighter.
    val edgeL = intArrayOf(cassetteDeckHex(0x2A292C), cassetteDeckHex(0x141316))
    val edgeR = intArrayOf(cassetteDeckHex(0x111013), cassetteDeckHex(0x08080A))

    // The room takes the hue of the loaded cassette's cover: r, g, b, alpha per track.
    val tints = arrayOf(
        doubleArrayOf(255.0, 252.0, 255.0, 0.045),
        doubleArrayOf(150.0, 140.0, 255.0, 0.085),
        doubleArrayOf(80.0, 220.0, 190.0, 0.075),
    )
    val redSpill = cassetteDeckArgb(230, 40, 64, 0.32)
    val ledSpill = cassetteDeckArgb(53, 227, 154, 0.10)

    // The room lift's far end (RN CassetteDeck.tsx:251).
    val liftFar = cassetteDeckArgb(255, 255, 255, 0.0)

    class Grille(val cx: Double, val cy: Double, val pitch: Double, val dot: Double, val radius: Double)
    class Led(val cx: Double, val cy: Double, val r: Double)
    class Box(val x: Double, val y: Double, val w: Double, val h: Double)
    class Point(val cx: Double, val cy: Double)
    class Pack(val min: Double, val max: Double)
    class Cover(val x: Double, val y: Double, val size: Double, val radius: Double)
    class Label(val x: Double, val y: Double, val size: Double, val tracking: Double)
    class Wordmark(val right: Double, val y: Double, val size: Double, val tracking: Double)
    class Keys(val x0: Double, val pitch: Double, val y: Double, val w: Double, val h: Double, val travel: Double)
    class Glyphs(val y: Double, val size: Double, val x0: Double)

    // RN theme.ts:36-50. Absolute positions in points on the 402 x 874 screen.
    val grilleDots = Grille(cx = 44.3, cy = 88.7, pitch = 6.6, dot = 2.1, radius = 20.5)
    val led = Led(cx = 374.3, cy = 77.7, r = 2.1)
    val cassette = Box(x = 16.67, y = 223.33, w = 370.0, h = 240.0)

    // Inside the cassette frame.
    val reels = arrayOf(Point(119.7, 106.3), Point(248.0, 106.3))
    const val REEL_SIZE = 70.0

    // The dark band through the label, where the tape packs show around the hubs.
    val window = Box(x = 95.0, y = 80.7, w = 179.0, h = 49.3)
    val tapePack = Pack(min = 26.0, max = 62.0)
    val cover = Cover(x = 38.3, y = 35.0, size = 37.3, radius = 1.0)
    val title = Label(x = 86.0, y = 36.5, size = 16.0, tracking = -0.6)
    val artist = Label(x = 86.0, y = 56.0, size = 11.2, tracking = -0.1)
    val wordmarkLabel = Wordmark(right = 43.3, y = 145.5, size = 11.6, tracking = 1.6)
    val keys = Keys(x0 = 60.0, pitch = 53.75, y = 588.3, w = 65.0, h = 96.67, travel = 3.3)
    val glyphs = Glyphs(y = 580.7, size = 10.0, x0 = 92.5)
    val pill = Box(x = 17.7, y = 805.7, w = 31.3, h = 20.0)

    class Track(val title: String, val artist: String, val cover: Int)

    val tracks = listOf(
        Track("Glass Hours", "Northern Pilots", 0),
        Track("Afterglow", "Northern Pilots", 1),
        Track("Undertow", "Northern Pilots", 2),
    )

    // An invented wordmark in the source's bold extended style.
    const val BRAND = "VELOR"

    val keyLabels = listOf("Play", "Pause", "Next track", "Rewind", "Fast forward")
}
