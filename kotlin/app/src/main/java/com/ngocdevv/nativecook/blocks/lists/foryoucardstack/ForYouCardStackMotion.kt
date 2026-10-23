package com.ngocdevv.nativecook.blocks.lists.foryoucardstack

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

internal object ForYouCardStackSpec {
    const val screenWidth = 402f
    const val screenHeight = 874f

    const val LOOP_MS = 13_700L
    const val resumeAfterMs = 3_500L
    const val reducedFadeMs = 200L

    const val deckStart = 800L
    const val deckEnd = 1_100L
    const val pushStart = 1_650L
    const val pushEnd = 2_050L
    const val playTap = 3_450L
    const val openStart = 3_500L
    const val openEnd = 5_000L
    const val pauseTap = 8_450L
    const val closeStart = 8_500L
    const val closeEnd = 10_375L
    // Approximate progress measured from the recovered clip's interactive edge-back pan.
    val popTimes = floatArrayOf(11_900f, 12_000f, 12_100f, 12_200f, 12_300f, 12_400f, 12_500f)
    val popProgress = floatArrayOf(0f, .05f, .15f, .30f, .50f, .80f, 1f)
    const val deckBackStart = 12_950L
    const val deckBackEnd = 13_300L

    const val touchDeckMs = 300L
    const val deckSnapMs = 400L
    const val touchPushMs = 400L
    const val touchPlayMs = 1_500L
    const val touchCloseMs = 1_600L
    const val pressInMs = 120L
    const val pressOutMs = 150L
    const val pressScale = .97f

    const val readDelayMs = 500L
    const val readMs = 4_300L
    const val readLines = 5.2f
}

internal data class ForYouScene(
    val deck: Float,
    val push: Float,
    val playing: Float,
    val open: Float,
    val close: Float,
    val words: Float,
)

internal data class ForYouLook(
    val neighbours: Float,
    val chapter: Float,
    val wave: Float,
    val title: Float,
    val text: Float,
    val scale: Float,
    val spineX: Float,
    val centerY: Float,
    val leaf0: Float,
    val leaf1: Float,
    val leaf2: Float,
)

private data class ForYouSpan(val start: Float, val end: Float)

private val readTimes = floatArrayOf(4_000f, 4_200f, 5_000f, 6_000f, 7_000f, 8_300f)
private val readLineValues = floatArrayOf(0f, .6f, 1.4f, 2f, 3.4f, 5.2f)

private val deckSpan = ForYouSpan(800f, 1_100f)
private val pushSpan = ForYouSpan(1_650f, 2_050f)
private val openSpan = ForYouSpan(3_500f, 5_000f)
private val closeSpan = ForYouSpan(ForYouCardStackSpec.closeStart.toFloat(), ForYouCardStackSpec.closeEnd.toFloat())
private val deckBackSpan = ForYouSpan(12_950f, 13_300f)

private val openNeighbours = ForYouSpan(0f, .133f)
private val openChapter = ForYouSpan(.033f, .167f)
private val openShrink = ForYouSpan(0f, .333f)
private val openWave = ForYouSpan(.067f, .4f)
private val openTitle = ForYouSpan(.067f, .333f)
private val openLeaf0 = ForYouSpan(.333f, .5f)
private val openLeaf1 = ForYouSpan(.6f, .8f)
private val openLeaf2 = ForYouSpan(.833f, 1f)
private val openText = ForYouSpan(.333f, .5f)

private val closeText = ForYouSpan(0f, .125f)
private val closeWave = ForYouSpan(0f, .1875f)
private val closeGrow = ForYouSpan(.0625f, .594f)
private val closeLeaf2 = ForYouSpan(.094f, .25f)
private val closeLeaf1 = ForYouSpan(.3125f, .5f)
private val closeLeaf0 = ForYouSpan(.625f, .9375f)
private val closeTitle = ForYouSpan(.406f, .656f)
private val closeNeighbours = ForYouSpan(.3125f, .47f)
private val closeChapter = ForYouSpan(.406f, .656f)

private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

private fun segment(time: Float, span: ForYouSpan): Float {
    return clamp01((time - span.start) / (span.end - span.start))
}

/** Cubic-bezier easing, matching Reanimated's Easing.bezier for the pin's curves. */
internal fun forYouBezier(progress: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val p = clamp01(progress)
    var low = 0.0
    var high = 1.0
    repeat(16) {
        val middle = (low + high) / 2.0
        val x = 3.0 * (1.0 - middle) * (1.0 - middle) * middle * x1 +
            3.0 * (1.0 - middle) * middle * middle * x2 + middle * middle * middle
        if (x < p) low = middle else high = middle
    }
    val t = (low + high) / 2.0
    val y = 3.0 * (1.0 - t) * (1.0 - t) * t * y1 +
        3.0 * (1.0 - t) * t * t * y2 + t * t * t
    return y.toFloat()
}

private fun pinEase(progress: Float) = forYouBezier(progress, .4f, 0f, .2f, 1f)
private fun sheetEase(progress: Float) = forYouBezier(progress, .32f, .72f, 0f, 1f)
internal fun sampleForYouScene(timeMs: Long): ForYouScene {
    val t = ((timeMs % ForYouCardStackSpec.LOOP_MS) + ForYouCardStackSpec.LOOP_MS) % ForYouCardStackSpec.LOOP_MS
    val tf = t.toFloat()
    val deck = pinEase(segment(tf, deckSpan)) * (1f - pinEase(segment(tf, deckBackSpan)))
    val pop = interpolateKeyframes(tf, ForYouCardStackSpec.popTimes, ForYouCardStackSpec.popProgress)
    val push = sheetEase(segment(tf, pushSpan)) * (1f - pop)
    val playing = if (t >= ForYouCardStackSpec.playTap && t < ForYouCardStackSpec.pauseTap) 1f else 0f
    var open = segment(tf, openSpan)
    var close = segment(tf, closeSpan)
    if (close >= 1f) {
        open = 0f
        close = 0f
    }
    val words = if (t < ForYouCardStackSpec.pauseTap) {
        interpolateReading(tf)
    } else {
        readLineValues.last()
    }
    return ForYouScene(deck, push, playing, open, close, words)
}

private fun interpolateReading(timeMs: Float): Float {
    return interpolateKeyframes(timeMs, readTimes, readLineValues)
}

private fun interpolateKeyframes(value: Float, input: FloatArray, output: FloatArray): Float {
    if (input.isEmpty() || input.size != output.size) return 0f
    if (value <= input.first()) return output.first()
    for (index in 0 until input.lastIndex) {
        if (value <= input[index + 1]) {
            val span = (value - input[index]) / (input[index + 1] - input[index])
            return output[index] + (output[index + 1] - output[index]) * span
        }
    }
    return output.last()
}

internal fun forYouLook(open: Float, close: Float): ForYouLook {
    fun openAmount(span: ForYouSpan) = pinEase(segment(open, span))
    fun closeAmount(span: ForYouSpan) = pinEase(segment(close, span))
    fun mix(from: Float, to: Float, amount: Float) = from + (to - from) * amount

    val readingScale = .4737f
    val readingSpine = 200f
    val readingCenter = 442.5f
    val playerSpine = 100f
    val playerCenter = 291.5f

    if (close > 0f) {
        val grow = closeAmount(closeGrow)
        val shut = closeAmount(closeLeaf0)
        return ForYouLook(
            neighbours = closeAmount(closeNeighbours),
            chapter = closeAmount(closeChapter),
            wave = 1f - closeAmount(closeWave),
            title = 1f - closeAmount(closeTitle),
            text = 1f - closeAmount(closeText),
            scale = mix(readingScale, 1f, grow),
            spineX = mix(readingSpine, playerSpine, shut),
            centerY = mix(readingCenter, playerCenter, grow),
            leaf0 = 1f - shut,
            leaf1 = 1f - closeAmount(closeLeaf1),
            leaf2 = 1f - closeAmount(closeLeaf2),
        )
    }

    val shrink = openAmount(openShrink)
    return ForYouLook(
        neighbours = 1f - openAmount(openNeighbours),
        chapter = 1f - openAmount(openChapter),
        wave = openAmount(openWave),
        title = openAmount(openTitle),
        text = openAmount(openText),
        scale = mix(1f, readingScale, shrink),
        spineX = mix(playerSpine, readingSpine, shrink),
        centerY = mix(playerCenter, readingCenter, shrink),
        leaf0 = openAmount(openLeaf0),
        leaf1 = openAmount(openLeaf1),
        leaf2 = openAmount(openLeaf2),
    )
}

private enum class ForYouCurve {
    Linear,
    TimingDefault,
    Out,
    Sheet,
    Pin,
    Spring,
}

private class ForYouTween(
    private val from: Float,
    private val to: Float,
    private val startNs: Long,
    private val durationMs: Long,
    private val curve: ForYouCurve,
    private val velocity: Float = 0f,
) {
    fun value(nowNs: Long): Float {
        if (durationMs <= 0L || nowNs <= startNs) return from
        val progress = ((nowNs - startNs).toDouble() / 1_000_000.0 / durationMs).coerceIn(0.0, 1.0)
        val eased = when (curve) {
            ForYouCurve.Linear -> progress
            ForYouCurve.TimingDefault -> if (progress < .5) {
                2.0 * progress * progress
            } else {
                1.0 - (-2.0 * progress + 2.0) * (-2.0 * progress + 2.0) / 2.0
            }
            ForYouCurve.Out -> forYouBezier(progress.toFloat(), .23f, 1f, .32f, 1f).toDouble()
            ForYouCurve.Sheet -> sheetEase(progress.toFloat()).toDouble()
            ForYouCurve.Pin -> pinEase(progress.toFloat()).toDouble()
            ForYouCurve.Spring -> spring(progress, durationMs / 1_000.0, velocity)
        }
        return from + (to - from) * eased.toFloat()
    }

    fun done(nowNs: Long): Boolean = nowNs >= startNs + durationMs * 1_000_000L

    private fun spring(progress: Double, durationSeconds: Double, inputVelocity: Float): Double {
        val dampingRatio = .8
        val naturalFrequency = 4.0 / (dampingRatio * durationSeconds.coerceAtLeast(.001))
        val dampedFrequency = naturalFrequency * sqrt(1.0 - dampingRatio * dampingRatio)
        val distance = (to - from).toDouble()
        val normalizedVelocity = if (abs(distance) > .0001) inputVelocity / distance else 0.0

        fun raw(time: Double): Double {
            val decay = exp(-dampingRatio * naturalFrequency * time)
            val coefficient = (dampingRatio * naturalFrequency - normalizedVelocity) / dampedFrequency
            return 1.0 - decay * (cos(dampedFrequency * time) + coefficient * sin(dampedFrequency * time))
        }

        val end = raw(durationSeconds)
        return if (abs(end) > .0001) raw(progress * durationSeconds) / end else progress
    }
}

/** Owns the clock-derived scene and the interruptible touch tweens. */
internal class ForYouCardStackMotion(private val startsAutoplay: Boolean) {
    var deck = 0f
        private set
    var push = 0f
        private set
    var playing = 0f
        private set
    var open = 0f
        private set
    var close = 0f
        private set
    var words = 0f
        private set
    var pressBack = 1f
        private set
    var pressPlay = 1f
        private set

    var manual = !startsAutoplay
        private set
    var dragging = false
        private set
    var dragStart = 0f
        private set
    var currentNs = 0L
        private set
    var loopStartNs = -1L
        private set
    var resumeAtNs = -1L
        private set

    /** Bumped by the composable only when status-bar colour needs changing. */
    var onPlayerVisible: ((Boolean) -> Unit)? = null
    var onDeckRevealed: ((Boolean) -> Unit)? = null

    private var returning = false
    private var resetAfterPush = false
    private var resetAfterClose = false
    private var touchActive = false
    private var lastPlayerVisible = false
    private var lastDeckRevealed = false
    private var deckTween: ForYouTween? = null
    private var pushTween: ForYouTween? = null
    private var playingTween: ForYouTween? = null
    private var openTween: ForYouTween? = null
    private var closeTween: ForYouTween? = null
    private var wordsTween: ForYouTween? = null
    private var pressBackTween: ForYouTween? = null
    private var pressPlayTween: ForYouTween? = null

    fun advance(nowNs: Long, autoPlay: Boolean) {
        currentNs = nowNs
        if (autoPlay && !manual) {
            if (loopStartNs < 0L) loopStartNs = nowNs
            val scene = sampleForYouScene((nowNs - loopStartNs) / 1_000_000L)
            deck = scene.deck
            push = scene.push
            playing = scene.playing
            open = scene.open
            close = scene.close
            words = scene.words
            notifyPlayerVisible()
            notifyDeckRevealed()
            return
        }

        deckTween = update(deckTween, nowNs) { deck = it }
        pushTween = update(pushTween, nowNs) { push = it }
        playingTween = update(playingTween, nowNs) { playing = it }
        openTween = update(openTween, nowNs) { open = it }
        closeTween = update(closeTween, nowNs) { close = it }
        wordsTween = update(wordsTween, nowNs) { words = it }
        pressBackTween = update(pressBackTween, nowNs) { pressBack = it }
        pressPlayTween = update(pressPlayTween, nowNs) { pressPlay = it }

        if (resetAfterPush && pushTween == null) {
            open = 0f
            close = 0f
            words = 0f
            resetAfterPush = false
        }
        if (resetAfterClose && closeTween == null) {
            open = 0f
            close = 0f
            words = 0f
            resetAfterClose = false
        }

        if (resumeAtNs >= 0L && nowNs >= resumeAtNs && !returning) {
            beginReturnToRest(nowNs)
        }
        if (returning && pushTween == null) {
            returning = false
            manual = false
            loopStartNs = -1L
            resumeAtNs = -1L
        }
        notifyPlayerVisible()
        notifyDeckRevealed()
    }

    fun beginTouch() {
        // The finite frame driver may have slept since its last sample.
        currentNs = System.nanoTime()
        manual = true
        touchActive = true
        returning = false
        resumeAtNs = -1L
        resetAfterPush = false
        resetAfterClose = false
        deckTween = null
        pushTween = null
        playingTween = null
        openTween = null
        closeTween = null
        wordsTween = null
        dragStart = deck
    }

    fun beginDeckDrag() {
        dragging = true
        dragStart = deck
        deckTween = null
    }

    fun updateDeckDrag(translationX: Float, step: Float) {
        var value = dragStart + translationX / step
        if (value < 0f) value *= .3f
        if (value > 1f) value = 1f + (value - 1f) * .3f
        deck = value
    }

    fun endDeckDrag(velocityX: Float, step: Float, reduced: Boolean): Boolean {
        dragging = false
        val projected = deck + velocityX * .2f / step
        val target = if (projected > .5f) 1f else 0f
        if (reduced) {
            deck = target
            deckTween = null
        } else {
            deckTween = ForYouTween(deck, target, currentNs, ForYouCardStackSpec.deckSnapMs, ForYouCurve.Spring, velocityX / step)
        }
        return target != kotlin.math.round(dragStart)
    }

    fun openBook(reduced: Boolean) {
        beginTouch()
        pushTween = ForYouTween(push, 1f, currentNs, if (reduced) ForYouCardStackSpec.reducedFadeMs else ForYouCardStackSpec.touchPushMs, ForYouCurve.Sheet)
    }

    fun back(reduced: Boolean) {
        beginTouch()
        wordsTween = null
        playingTween = ForYouTween(playing, 0f, currentNs, 150L, ForYouCurve.TimingDefault)
        pushTween = ForYouTween(push, 0f, currentNs, if (reduced) ForYouCardStackSpec.reducedFadeMs else ForYouCardStackSpec.touchPushMs, ForYouCurve.Sheet)
        openTween = null
        closeTween = null
        resetAfterPush = true
    }

    fun togglePlay(reduced: Boolean) {
        beginTouch()
        playingTween = ForYouTween(playing, if (playing < .5f) 1f else 0f, currentNs, 150L, ForYouCurve.Out)
        if (playing < .5f) {
            if (close > 0f) {
                close = 0f
                open = 0f
            }
            if (reduced) {
                open = 1f
                words = ForYouCardStackSpec.readLines
                openTween = null
                wordsTween = null
            } else {
                val left = ((1f - open).coerceIn(0f, 1f) * ForYouCardStackSpec.touchPlayMs).toLong()
                openTween = ForYouTween(open, 1f, currentNs, left, ForYouCurve.TimingDefault)
                words = 0f
                wordsTween = ForYouTween(0f, ForYouCardStackSpec.readLines, currentNs + ForYouCardStackSpec.readDelayMs * 1_000_000L, ForYouCardStackSpec.readMs, ForYouCurve.TimingDefault)
            }
        } else {
            wordsTween = null
            if (open < 1f) {
                openTween = if (reduced) null else ForYouTween(open, 0f, currentNs, (open * ForYouCardStackSpec.touchPlayMs).toLong(), ForYouCurve.TimingDefault)
                if (reduced) open = 0f
            } else if (reduced) {
                open = 0f
                close = 0f
                words = 0f
            } else {
                closeTween = ForYouTween(0f, 1f, currentNs, ForYouCardStackSpec.touchCloseMs, ForYouCurve.TimingDefault)
                openTween = null
                resetAfterClose = true
            }
        }
    }

    fun press(which: ForYouPress, down: Boolean, reduced: Boolean) {
        if (reduced) {
            if (which == ForYouPress.Back) {
                pressBack = 1f
                pressBackTween = null
            } else {
                pressPlay = 1f
                pressPlayTween = null
            }
            return
        }
        val start = if (which == ForYouPress.Back) pressBack else pressPlay
        val target = if (down) ForYouCardStackSpec.pressScale else 1f
        val tween = ForYouTween(start, target, currentNs, if (down) ForYouCardStackSpec.pressInMs else ForYouCardStackSpec.pressOutMs, ForYouCurve.Out)
        if (which == ForYouPress.Back) pressBackTween = tween else pressPlayTween = tween
    }

    fun releaseForResume() {
        touchActive = false
        if (startsAutoplay) resumeAtNs = currentNs + ForYouCardStackSpec.resumeAfterMs * 1_000_000L
    }

    fun hasMotion(): Boolean {
        return startsAutoplay || touchActive || dragging || returning || resumeAtNs >= 0L ||
            deckTween != null || pushTween != null || playingTween != null || openTween != null ||
            closeTween != null || wordsTween != null || pressBackTween != null || pressPlayTween != null
    }

    private fun beginReturnToRest(nowNs: Long) {
        returning = true
        deckTween = ForYouTween(deck, 0f, nowNs, ForYouCardStackSpec.touchPushMs, ForYouCurve.Out)
        pushTween = ForYouTween(push, 0f, nowNs, ForYouCardStackSpec.touchPushMs, ForYouCurve.Sheet)
        playingTween = ForYouTween(playing, 0f, nowNs, 150L, ForYouCurve.TimingDefault)
        openTween = ForYouTween(open, 0f, nowNs, 150L, ForYouCurve.TimingDefault)
        closeTween = ForYouTween(close, 0f, nowNs, 150L, ForYouCurve.TimingDefault)
        wordsTween = ForYouTween(words, 0f, nowNs, 150L, ForYouCurve.TimingDefault)
        resumeAtNs = -1L
    }

    private inline fun update(tween: ForYouTween?, nowNs: Long, set: (Float) -> Unit): ForYouTween? {
        if (tween == null) return null
        set(tween.value(nowNs))
        return if (tween.done(nowNs)) null else tween
    }

    private fun notifyPlayerVisible() {
        val visible = push > .5f
        if (visible != lastPlayerVisible) {
            lastPlayerVisible = visible
            onPlayerVisible?.invoke(visible)
        }
    }

    private fun notifyDeckRevealed() {
        val revealed = deck > .5f
        if (revealed != lastDeckRevealed) {
            lastDeckRevealed = revealed
            onDeckRevealed?.invoke(revealed)
        }
    }
}

internal enum class ForYouPress {
    Back,
    Play,
}
