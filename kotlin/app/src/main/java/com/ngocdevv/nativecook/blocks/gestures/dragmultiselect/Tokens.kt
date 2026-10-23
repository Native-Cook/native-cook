package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Values copied from the React Native reference block.
 *
 * Dimensions are density independent pixels (the reference uses points), durations are
 * milliseconds unless the property name or comment says seconds, and spring values keep the
 * reference damping/stiffness/mass triplets.
 */
internal object DragMultiSelectTokens {
    object Color {
        val background = ComposeColor(0xFFFBFAFD)
        val ink = ComposeColor(0xFF3A393E)
        val mute = ComposeColor(0xFFAEAEB2)
        val check = ComposeColor(0xFFB8B8BD)
        val note = ComposeColor(0xFFB4B4B9)
        val nav = ComposeColor(0xFF8E8E93)
        val tray = ComposeColor(0xFFE3C454)
        val trayLine = ComposeColor(0xFF9C7F22)
        val blue = ComposeColor(0xFF5B9BEE)
        val blueDeep = ComposeColor(0xFF3F86E6)
        val band = ComposeColor(0xFFD6E8FC)
        val bandSeam = ComposeColor(red = 1f, green = 1f, blue = 1f, alpha = 0.45f)
        val glossStart = ComposeColor(red = 1f, green = 1f, blue = 1f, alpha = 0f)
        val glossPeak = ComposeColor(red = 1f, green = 1f, blue = 1f, alpha = 0.85f)
        val glossEnd = ComposeColor(red = 1f, green = 1f, blue = 1f, alpha = 0f)
        val gloss = listOf(glossStart, glossPeak, glossEnd)
        val circle = ComposeColor(0xFFC7C7CC)
        val toolbar = ComposeColor(0xFF2C2C2E)
        val toolbarInk = ComposeColor(0xFFFFFFFF)
        val white = ComposeColor(0xFFFFFFFF)
        val black = ComposeColor(0xFF000000)
        val finger = ComposeColor(red = 60f / 255f, green = 60f / 255f, blue = 67f / 255f, alpha = 0.22f)
        val fingerEdge = ComposeColor(red = 60f / 255f, green = 60f / 255f, blue = 67f / 255f, alpha = 0.35f)
    }

    /** The spacing scale from theme.ts; source geometry remains authoritative where it is explicit. */
    object Space {
        const val xs = 4f
        const val sm = 8f
        const val md = 12f
        const val lg = 16f
        const val gutter = 20f
        const val xl = 24f
        const val xxl = 32f
    }

    object Layout {
        const val gutter = 20f
        const val nav = 44f
        const val navTop = 6f
        const val row = 48f
        const val listTop = 8f
        const val check = 19f
        const val checkRadius = 5f
        const val textLeft = 48f
        const val circle = 26f
        const val circleRight = 12f
        const val circleColumn = 64f
        const val bandInset = 8f
        const val bandRadius = 10f

        object Done {
            const val width = 68f
            const val height = 36f
            const val radius = 8f
        }

        const val more = 32f

        object Toolbar {
            const val width = 328f
            const val height = 46f
            const val radius = 14f
            const val bottom = 1.3f
        }

        val done = Done
        val toolbar = Toolbar

        val tools = floatArrayOf(51f, 164.5f, 243.7f, 300f)
        const val toolIcon = 22f
        const val fab = 56f
        const val fabBottom = 18f
        const val edge = 72f
        const val finger = 44f
    }

    data class TextSpec(
        val fontSize: Float,
        val lineHeight: Float,
        val fontWeight: Int,
    )

    object Type {
        val row = TextSpec(fontSize = 20f, lineHeight = 24f, fontWeight = 400)
        val nav = TextSpec(fontSize = 18f, lineHeight = 22f, fontWeight = 600)
        val done = TextSpec(fontSize = 17f, lineHeight = 22f, fontWeight = 600)
        val tool = TextSpec(fontSize = 17.5f, lineHeight = 22f, fontWeight = 600)
    }

    object Select {
        const val longPress = 320L
        const val dragSlop = 8f
        const val autoScrollMax = 720f
        const val coastDecay = 3.5f
        const val coastStop = 20f
    }

    data class SpringSpec(
        val damping: Float,
        val stiffness: Float,
        val mass: Float,
    )

    object Spring {
        val band = SpringSpec(damping = 22f, stiffness = 380f, mass = 0.7f)
        val circles = SpringSpec(damping = 24f, stiffness = 300f, mass = 0.8f)
        val toolbar = SpringSpec(damping = 22f, stiffness = 260f, mass = 1f)
        val collapse = SpringSpec(damping = 26f, stiffness = 260f, mass = 1f)
        val trash = SpringSpec(damping = 7f, stiffness = 380f, mass = 0.6f)
        val ripple = SpringSpec(damping = 20f, stiffness = 200f, mass = 1f)
    }

    object Timing {
        const val corner = 140L
        const val bandFade = 120L
        const val circleSlide = 16f
        const val circleStagger = 18L
        const val toolbarRise = 72L
        const val fab = 200L
        const val roll = 160L
        const val rollLift = 10f
        const val reduced = 180L
        const val scrollHome = 600L
        const val press = 120L
        const val trashUp = 80L
        const val toolStagger = 0.05f
        const val breathPeriod = 3.25f
        const val gloss = 460L
        const val ripple = 340L
        const val streamOut = 300L
        const val streamStagger = 55L
        const val restore = 320L
        const val fabPressIn = 100L
        const val fabPressOut = 160L
    }

    /** Drawing constants copied from the reference component styles. */
    object View {
        const val iconStroke = 2f

        object Nav {
            const val barExtraHeight = 2f
            const val horizontalInsetAdjustment = 12f
            const val doneEndAdjustment = 4f
            const val sideWidth = 80f
            const val sideHeight = 44f
            const val backIcon = 28f
            const val titleGap = 6f
            const val trayWidth = 18f
            const val trayHeight = 14f
            const val trayRadius = 3f
            const val trayTitleGap = 8f
            const val trayLidTop = 4f
            const val trayLidWidth = 8f
            const val trayLidHeight = 2f
            const val trayLidRadius = 1f
            const val titleChevron = 15f
            const val moreChevron = 20f
            const val moreStroke = 1.5f
            const val pressedAlpha = 0.7f
        }

        object Row {
            const val previewStartScale = 0.15f
            const val previewEndScale = 0.85f
            const val previewAlpha = 0.6f
            const val pressScale = 0.015f
            const val bandStartScale = 0.25f
            const val bandScaleRange = 0.75f
            const val bandOvershootLimit = 1.05f
            const val bandOpacityMultiplier = 2f
            const val glossWidth = 90f
            const val glossTravelWidth = 402f
            const val glossSkewDegrees = -18f
            const val seamHeight = 1.5f
            const val checkStroke = 1.2f
            const val checkDash = 3.6f
            const val noteGap = 6f
            const val noteIcon = 14f
            const val circleStroke = 1.5f
            const val circleOverflow = 20f
            const val selectedCircleOutset = 1.5f
            const val selectedStartScale = 0.6f
            const val selectedScaleRange = 0.4f
            const val selectedInnerInset = 4f
            const val selectedInnerStroke = 2f
            const val rippleStroke = 2f
            const val rippleScaleRange = 0.9f
            const val rippleStartAlpha = 0.6f
        }

        object Toolbar {
            const val toolSlot = 120f
            const val toolHeight = 44f
            const val toolMinimumWidth = 44f
            const val toolGap = 6f
            const val toolHorizontalPadding = 10f
            const val toolStart = 0.15f
            const val toolDuration = 0.25f
            const val toolRise = 6f
            const val shadowOffsetY = 8f
            const val shadowRadius = 12f
            const val shadowBreathRadius = 4f
            const val shadowOpacity = 0.3f
            const val shadowBreathOpacity = 0.12f
            const val breathScale = 0.025f
            const val pressScale = 0.03f
            const val colorEnd = 0.6f
            const val plusIcon = 28f
            const val plusFadeMultiplier = 3f
            const val plusRotation = 45f
            const val trashRise = 6f
            const val trashScale = 0.35f
            const val trashRotation = -14f
            const val disabledAlpha = 0.5f
            const val pressRetention = 16f
            const val trashPointRightInset = 8f
            const val trashPointSlotHalf = 44f
            const val trashPointIconHalf = 22f
            const val api26ShadowLayers = 24
            const val colorGamma = 2.2f
        }

        object Delete {
            const val restoreOffsetY = 25f
            const val restoreDamping = 18f
            const val defaultSpringStiffness = 900f
            const val reanimatedDefaultMass = 4f
        }

        object Engine {
            const val maxFrameDeltaSeconds = 0.1f
            const val maxScriptStepSeconds = 0.05f
            const val scriptEpsilon = 0.000_001f
            const val touchResumeMillis = 2_000L
            const val restoredCleanupDelayMillis = 100L
        }
    }

    /** The exact cubic-bezier curves used by the React Native reference. */
    object Ease {
        val out = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
        val inOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    }

    const val QA_HIDE_FINGER = false
    const val LOOP_MS = 13_000L
    /** Kotlin's required loop constant uses the same milliseconds as the React Native clock. */
    const val LOOP = LOOP_MS
    const val RESUME_MS = 3_500L

    // Lower-case aliases keep Kotlin call sites readable while preserving the reference names above.
    const val qaHideFinger = QA_HIDE_FINGER
    const val loopMs = LOOP_MS
    const val resumeMs = RESUME_MS

    data class DemoHold(
        val row: Int,
        val down: Float,
    )

    data class DemoDragPoint(
        val at: Float,
        val row: Int,
    )

    data class DemoTap(
        val row: Int,
        val at: Float,
    )

    data class DemoEdge(
        val row: Int,
        val down: Float,
        val reach: Float,
        val up: Float,
    )

    data class DemoRemove(
        val from: Int,
        val to: Int,
        val down: Float,
        val up: Float,
        val trash: Float,
        val restore: Float,
    )

    object Demo {
        const val fingerFade = 0.15f
        const val fingerMidXRatio = 0.42f
        const val fingerPressedScale = 0.25f
        const val tapFingerLead = 0.25f
        const val edgeBottomClearance = 8f
        const val edgeDepth = 0.3f
        const val doneFingerLead = 0.3f
        const val removeDragLead = 0.1f
        const val trashFingerLead = 0.3f
        val hold = DemoHold(row = 5, down = 0.6f)
        val drag = listOf(
            DemoDragPoint(at = 1.9f, row = 5),
            DemoDragPoint(at = 2.6f, row = 9),
            DemoDragPoint(at = 3.0f, row = 7),
            DemoDragPoint(at = 3.55f, row = 12),
        )
        const val release = 3.75f
        val tap = DemoTap(row = 1, at = 4.1f)
        val edge = DemoEdge(row = 14, down = 4.9f, reach = 5.6f, up = 6.9f)
        const val done = 7.2f
        const val scrollHome = 7.5f
        val remove = DemoRemove(from = 8, to = 9, down = 8.7f, up = 9.5f, trash = 10.1f, restore = 11.8f)
        const val tidy = 0.6f
    }
}

// Compatibility views for the block implementation files. They point at the single source of
// truth above, so ports cannot drift when a value is adjusted during visual verification.
internal val DragMultiSelectColors = DragMultiSelectTokens.Color
internal val DragMultiSelectSpace = DragMultiSelectTokens.Space
internal val DragMultiSelectLayout = DragMultiSelectTokens.Layout
internal val DragMultiSelectType = DragMultiSelectTokens.Type

internal object DragMultiSelectMotion {
    val select = DragMultiSelectTokens.Select
    val SELECT = select
    val spring = DragMultiSelectTokens.Spring
    val SPRING = spring
    val timing = DragMultiSelectTokens.Timing
    val TIMING = timing
    val demo = DragMultiSelectTokens.Demo
    val DEMO = demo
    val ease = DragMultiSelectTokens.Ease
    val EASE = ease
    const val LOOP = DragMultiSelectTokens.LOOP
    const val LOOP_MS = DragMultiSelectTokens.LOOP_MS
    const val RESUME_MS = DragMultiSelectTokens.RESUME_MS
    const val loopMs = LOOP_MS
    const val resumeMs = RESUME_MS
}
