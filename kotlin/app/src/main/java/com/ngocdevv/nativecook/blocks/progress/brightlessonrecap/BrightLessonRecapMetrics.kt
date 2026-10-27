package com.ngocdevv.nativecook.blocks.progress.brightlessonrecap

import kotlin.math.max

/**
 * The window-dependent sizes of the screen, computed with the same formulas as the body of
 * `BrightLessonRecap` in BrightLessonRecap.tsx:115-122. Every value is in dp (1 pt = 1 dp).
 */
internal class BrightLessonRecapMetrics(
    val width: Float,
    val height: Float,
    val fontScale: Float,
    insetTop: Float,
    val insetBottom: Float,
) {
    /** Large text or a narrow window switches to the flow layout with stacked stat cards. */
    val flow = fontScale > 1.3f || width < 350f

    val contentInset = if (flow) 24f else width * 0.04f

    /** The scene starts 18 dp under the top inset; the other positions are fractions of the reference screen. */
    val sectionTop = insetTop + 18f

    val mascotSize = if (flow) 220f else width * 0.8f

    val compactHeight = width * 55f / 272f

    val heroHeight = if (flow) 250f else max(350f, height * 426f / 586f - sectionTop)

    val actionGap = if (flow) 28f else max(12f, height * 525f / 586f - (sectionTop + heroHeight + compactHeight))

    /** The width of the content column, which the hero and its children are positioned in. */
    val contentWidth = width - contentInset * 2
}
