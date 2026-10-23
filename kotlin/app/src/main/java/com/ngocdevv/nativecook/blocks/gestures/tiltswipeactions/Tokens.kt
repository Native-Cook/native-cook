package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color

/**
 * Every value of the React Native block (theme.ts and motion.ts), kept under the same names.
 * Distances are dp (the reference uses points), velocities dp/s, times milliseconds unless the
 * name or the comment says seconds.
 */

/** One autoplay loop, in seconds. */
internal const val LOOP = 10f
internal const val LOOP_MS = 10_000
internal const val RESUME_MS = 3_500
internal const val MAX_FRAME_DELTA_SECONDS = 0.1f

private fun rgb(hex: Long, alpha: Float = 1f) = Color(
    red = ((hex shr 16) and 0xFF) / 255f,
    green = ((hex shr 8) and 0xFF) / 255f,
    blue = (hex and 0xFF) / 255f,
    alpha = alpha,
)

/** theme.ts COLOR. */
internal object TiltColor {
    val gradient = listOf(rgb(0xA8448F), rgb(0xC65F9E), rgb(0xD977A5), rgb(0xDD81A8), rgb(0xDF86A7), rgb(0xE290A7), rgb(0xE89CA7))
    val desk = listOf(rgb(0x3F0F46), rgb(0x4E1C54))
    val gradientStops = floatArrayOf(0f, 0.596f, 0.694f, 0.75f, 0.816f, 0.861f, 1f)
    val plum = rgb(0x3B1D5E)
    val plumInk = rgb(0x2A1638)
    val white = rgb(0xFFFFFF)
    val pink = rgb(0xF26293)
    val pinkDeep = rgb(0xE8447A)
    val title = rgb(0xFFFFFF)
    val subtitle = rgb(0xFFD6EC, 0.85f)
    val cardLabel = rgb(0xE0679E)
    val cardCurrency = rgb(0xB9B2BE)
    val slab = rgb(0xFFFFFF, 0.18f)
    val slabEdge = rgb(0xFFFFFF, 0.36f)
    val slabShadow = rgb(0x5A1F55)
    val cardInk = rgb(0x2D2B35)
    val panel = rgb(0xF9C0C4)
    val panelEdge = rgb(0xE494A7)
    val deskEdge = rgb(0x49184F)
    val panelIcon = rgb(0xD08AA2)
    val panelIconActive = rgb(0xFFFFFF)
    val menuInk = rgb(0x141414)
    val menuPlus = rgb(0x8E6A9A)
    val menuScrim = rgb(0xFAD7E6, 0.06f)
    val income = rgb(0x2FBF71)
    val transfer = rgb(0x8B6CF6)
    val scrim = rgb(0x3B1D5E, 0.18f)
    val finger = rgb(0xFFFFFF, 0.5f)
    val lens = rgb(0xFFFFFF, 0.32f) // the active tab's glass lens
    val lensRim = rgb(0xFFFFFF, 0.7f)
    val glassEdge = rgb(0xFFFFFF, 0.9f)
    val pinkGlow = rgb(0xF26293, 0.35f)
    val sheen = listOf(rgb(0xFFFFFF, 0f), rgb(0xFFFFFF, 0.45f), rgb(0xFFFFFF, 0f))
    val fingerEdge = rgb(0xFFFFFF, 0.8f)
}

/** theme.ts ICON_TINT. */
internal object TiltIconTint {
    val yellow = rgb(0xF2C14E)
    val lilac = rgb(0xB9A4FF)
    val peach = rgb(0xFFB38A)
    val mint = rgb(0x8FE3C0)
    val sky = rgb(0x8CC8FF)
    val rose = rgb(0xFF9DBB)
}

/**
 * theme.ts TILT_VIEW. The source shows the app on a phone lying at an angle. The app is laid out
 * flat (402 x 874 dp) and drawn through one affine matrix that puts it where the frame does.
 * flat (X, Y) -> frame px: P0 + u * X + v * (Y - 874); frame px -> screen dp: (px - cropX) * scale.
 */
internal object TiltView {
    val p0 = floatArrayOf(275f, 543f) // the screen's bottom-left corner in the 800 x 600 frame
    val u = floatArrayOf(1.112f, -0.699f) // frame px per dp along the screen's width
    val v = floatArrayOf(1.085f, 0.717f) // frame px per dp along the screen's height
    const val cropX = 262f // the portrait window the reference shows of the frame
    const val scale = 1.4567f // screen dp per frame px
    const val screenRadius = 44f
}

/** theme.ts FLAT: the flat app, measured on the rectified f017. */
internal object TiltFlat {
    object Card {
        const val left = 27f
        const val top = 345f
        const val right = 27f
        const val height = 150f
        const val radius = 18f
    }

    object Section {
        const val left = 58f
        const val centreY = 598f
    }

    const val rowsTop = 628.7f
    const val rowLeft = 61f
    const val rowRight = 26.8f

    object Panel {
        const val left = 14f
        const val top = 808f
        const val radiusTop = 30f
        const val radiusBottom = 23f
    }

    val tabs = floatArrayOf(85f, 167f, 247f)
    const val tabY = 850.2f

    object Fab {
        const val left = 318f
        const val top = 787f
        const val size = 80f
        const val radius = 22f
    }

    object Menu {
        const val left = 126f
        const val top = 583.3f
        const val width = 270f
        const val height = 220.7f
        const val radius = 33f
        const val padTop = 13.1f
        const val padLeft = 41f
        const val padRight = 27f
        const val item = 65f
        const val icon = 24f
    }

    /** Overview tab (rectified f005). */
    object Cards {
        const val left = 60f
        const val pitch = 250f
        const val width = 210f
        const val top = 463.5f
        const val height = 219.3f
        const val radius = 18f
        const val badge = 100f
        const val badgeLeft = 28f
        const val badgeTop = 440f
    }
}

/** theme.ts LAYOUT. */
internal object TiltLayout {
    const val gutter = 20f
    const val cardTop = 12f
    const val cardHeight = 112f
    const val cardRadius = 28f
    const val cardBadge = 56f
    const val cardBadgeRadius = 18f
    const val sectionGap = 32f
    const val sectionTitle = 30f
    const val sectionBottom = 8f
    const val row = 82f
    const val circle = 58f
    const val textLeft = 135.95f
    const val rowRadius = 22f
    const val tile = 60f
    const val tileHeight = 56f
    const val tileRadius = 18f
    const val tileGap = 8f
    const val panel = 84f
    const val panelRadius = 32f
    const val fab = 64f
    const val fabRadius = 22f
    const val fabRight = 24f
    const val pillHeight = 52f
    const val pillGap = 16f
    const val menuItem = 48f
    const val lens = 52f
    const val menuWidth = 248f
    const val menuRadius = 28f
    const val finger = 44f
}

/** Where a row rests when its actions are open: two tiles, their gap and the gutter. */
internal const val OPEN_X =
    -(TiltLayout.tile * 2 + TiltLayout.tileGap + TiltLayout.gutter + TiltLayout.tileGap)

internal object TiltSpace {
    const val xs = 4f
    const val s = 8f
    const val m = 12f
    const val l = 16f
    const val xl = 24f
    const val xxl = 32f
}

internal object TiltFont {
    const val medium = "TiltSwipe-Montserrat-Medium"
    const val semibold = "TiltSwipe-Montserrat-SemiBold"
    const val bold = "TiltSwipe-Montserrat-Bold"
}

/** One text style of theme.ts TYPE. Weights are CSS weights (400, 500, 600, 700). */
internal class TiltText(
    val fontSize: Float,
    val lineHeight: Float,
    val weight: Int,
    val letterSpacing: Float = 0f,
)

/** theme.ts TYPE. */
internal object TiltType {
    val cardLabel = TiltText(14f, 18f, 600)
    val balance = TiltText(44f, 52f, 600, -0.5f)
    val currency = TiltText(40f, 52f, 500)
    val section = TiltText(24.3f, 30f, 600, 0.2f)
    val rowTitle = TiltText(18.5f, 24f, 600)
    val rowSub = TiltText(16.6f, 21f, 500)
    val amount = TiltText(18.5f, 24f, 600)
    val tile = TiltText(11f, 13f, 600, 0.2f)
    val pill = TiltText(15f, 20f, 500)
    val pillAction = TiltText(15f, 20f, 700)
    val menu = TiltText(20f, 24f, 600)
}

/** motion.ts EASE_OUT: the strong ease-out of every state change. */
internal val EASE_OUT: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** motion.ts EASE_IN_OUT (declared by the reference, unused there too). */
internal val EASE_IN_OUT: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

/** motion.ts SWIPE. */
internal object TiltSwipe {
    const val activeOffsetX = 12f // horizontal travel before a row takes the touch
    const val failOffsetY = 10f // vertical travel that hands the touch to the list
    const val fullAt = 0.55f // fraction of the width that arms a full-swipe delete
    const val fullRubber = 0.35f // finger-to-row ratio past the full-swipe line
    const val rightRubber = 0.25f // resistance when dragging the wrong way
    const val openAt = 78f // strip width that opens on release
    const val openFlick = -500f // velocity that opens from any distance
    const val deleteFlick = -1400f // velocity that deletes when the strip is past deleteFlickAt
    const val deleteFlickAt = 0.4f
}

/** motion.ts TILT. */
internal object TiltLean {
    const val perVelocity = 0.018f // degrees per dp/s
    const val max = 14f
    const val perspective = 700f
    const val lift = 1.02f // scale while the finger holds the row
}

/** A Reanimated spring: damping, stiffness and mass as the reference's withSpring takes them. */
internal class TiltSpring(val damping: Float, val stiffness: Float, val mass: Float)

/** motion.ts SPRING. */
internal object TiltSprings {
    val follow = TiltSpring(18f, 220f, 0.6f) // tilt chasing the velocity
    val snap = TiltSpring(26f, 300f, 1f) // open and close, takes the finger's velocity
    val lift = TiltSpring(20f, 320f, 0.8f)
    val tileStretch = TiltSpring(22f, 420f, 0.7f)
    val collapse = TiltSpring(28f, 260f, 1f)
    val restore = TiltSpring(24f, 230f, 1f)
    val pill = TiltSpring(20f, 260f, 1f)
    val lens = TiltSpring(18f, 260f, 0.8f)
    val menu = TiltSpring(24f, 300f, 0.8f)
}

/** motion.ts TIMING. */
internal object TiltTiming {
    const val flyOut = 260 // deleted row leaves the screen
    const val tileFade = 120
    const val colour = 160 // Delete tile white -> pink
    const val undoVisible = 4000
    const val reduced = 180
    const val reducedGap = 200
    const val pressScale = 0.94f
    const val tab = 260
    const val roll = 700 // the balance rolls to its new value
    const val breath = 3.33f // s; the FAB's glow, three per 10 s loop
    const val sheen = 0.6f // the slab's specular moves this much against the row
    const val tabSlide = 24f
    const val pressIn = 100
    const val pressOut = 160
    const val menuStagger = 0.12f
}

/** motion.ts DEMO: one scripted finger on one clock, seconds. */
internal object TiltDemo {
    const val fingerFade = 0.15f
    const val tidy = 0.8f

    object Open {
        const val row = 1
        const val down = 0.6f
        const val moveEnd = 1.5f
        const val up = 1.7f
        const val x = 300f
        const val to = -170f
    }

    object Remove {
        const val row = 0
        const val down = 2.5f
        const val moveEnd = 2.95f
        const val up = 3.25f
        const val x = 320f
        const val to = -290f
    }

    object Undo {
        const val down = 5.5f
        const val up = 5.62f
    }

    object Lean {
        const val row = 2
        const val down = 7.2f
        const val turn = 7.55f
        const val moveEnd = 7.9f
        const val up = 7.95f
        const val x = 300f
        const val to = -96f
    }
}

/** Values measured or authored in the RN component files rather than theme.ts. */
internal object TiltDetail {
    const val menuFade = 0.68f
    const val menuLift = 6f
    const val fingerPressScale = 0.15f

    const val cardPadding = 24f
    const val cardGap = 16f
    const val cardRing = 24f
    const val cardRingBorder = 4f
    const val cardShadowOpacity = 0.18f
    const val cardShadowRadius = 24f
    const val cardShadowY = 12f

    const val overviewPaddingLeft = 30f
    const val overviewPaddingBottom = 31f
    const val overviewLabelSize = 15f
    const val overviewAmountInset = 2.4f
    const val overviewBalanceSize = 36f
    const val overviewBalanceLine = 44f
    const val overviewCurrencySize = 34f
    const val overviewBadgeRadius = 22f
    const val overviewRing = 48f
    const val overviewRingBorder = 5f
    const val overviewShadowOpacity = 0.2f
    const val overviewShadowRadius = 20f
    const val overviewShadowY = 12f

    const val rowIconOffsetY = 7f
    const val rowAmountOffsetY = -2.5f
    const val slabInsetX = 8f
    const val slabInsetY = 4f
    const val slabShadowOpacity = 0.25f
    const val slabShadowRadius = 16f
    const val slabShadowY = 8f
    const val sheenWidth = 90f
    const val sheenStartX = 120f
    const val sheenSkewDegrees = -20f

    const val panelFadeHeight = 10f
    const val panelFadeBasis = 78f
    const val panelEdgeMaskOverlap = 30f
    const val tabHitSize = 44f
    const val tabPressScale = 0.88f
    const val tabIconSize = 26f
    const val tabIconStroke = 2.5f
    const val walletIconOffsetY = -3.4f
    const val lensHighlightInset = 10f
    const val lensHighlightTop = 1f
    const val lensHighlightHeight = 1.5f

    const val menuShadowOpacity = 0.22f
    const val menuShadowRadius = 24f
    const val menuShadowY = 12f
    const val menuRowRise = 8f
    const val menuPressScale = 0.97f
    const val menuTintAlpha = 0.1f
    const val menuGlyphSize = 14f
    const val menuStartScale = 0.8f

    const val fabGlowInsetX = 14f
    const val fabGlowInsetTop = 6f
    const val fabGlowExtra = 28f
    const val fabGlowRadiusExtra = 14f
    const val fabGlowShadowRadius = 26f
    const val fabGlowShadowY = 10f
    const val fabShadowOpacity = 0.22f
    const val fabShadowRadius = 18f
    const val fabShadowY = 8f
    const val fabGlyphSize = 38f
    const val fabGlowOpacityBase = 0.35f
    const val fabGlowOpacityRange = 0.35f
    const val fabGlowScaleBase = 0.9f
    const val fabGlowScaleRange = 0.15f

    const val pillRightExtra = 12f
    const val pillShadowOpacity = 0.3f
    const val pillShadowRadius = 18f
    const val pillShadowY = 8f
    const val undoHitHeight = 44f
}

internal val QA_HOLD_AT: Float? = null
internal val QA_STATE: String? = null

/** TiltSwipeActions.tsx: the flat app's size and the plane that holds it. */
internal const val FLAT_WIDTH = 402f
internal const val FLAT_HEIGHT = 874f
internal const val BALANCE = 1148.9f

// The source's screen reaches a little past the 402 x 874 design area on the right and at the bottom.
internal const val PLANE_W = 406f
internal const val PLANE_H = 888f
internal const val PILL_BOTTOM = PLANE_H - TiltFlat.Panel.top + 12f
