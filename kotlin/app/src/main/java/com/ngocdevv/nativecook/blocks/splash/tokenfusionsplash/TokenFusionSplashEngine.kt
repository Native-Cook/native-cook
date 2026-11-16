package com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash

import com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash.TokenFusionSplashMotion as M
import com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash.TokenFusionSplashTheme as Theme
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Haptic cues (RN `tfs-haptics.ts`): 1 touch-down light, 2 neck snaps soft, 3 capture medium, 4 fast capture heavy, 5 brand lands success. */
internal object TokenFusionSplashCue {
    const val TOUCH = 1
    const val SNAP = 2
    const val CAPTURE = 3
    const val CAPTURE_FAST = 4
    const val SUCCESS = 5
}

/**
 * The splash's frame callback, touch and Reduce Motion handling (RN `TokenFusionSplash`), on the main thread. The loop plays
 * [M.sceneAt]; a finger takes over through the live physics; two seconds after it leaves (autoPlay, interactive) the scene eases
 * back to the first frame and the loop restarts. In `splash` mode the story plays once and stops on the brand.
 */
internal class TokenFusionSplashEngine(
    val autoPlay: Boolean,
    val splash: Boolean,
    val reduce: Boolean,
    val idleMs: Double?,
    val touchCore: Boolean,
) {
    private val count = Theme.TOKENS.size
    var onMerge: ((String, List<String>) -> Unit)? = null
    var onRelease: ((String) -> Unit)? = null
    var onComplete: (() -> Unit)? = null
    var cue: (Int) -> Unit = {}

    private var live: TokenFusionLive = M.liveFrom(M.sceneAt(0.0, count)).also {
        it.lastTouch = 0.0
    }
    var scene: TokenFusionScene = M.sceneOf(live, reduce)
        private set
    var lagTwo: TokenFusionScene = scene
        private set
    private var lagOne: TokenFusionScene = scene
    private var time = 0.0
    private var playing = autoPlay || splash
    private var complete = false
    private var running = true
    private var resumeStart = -1.0
    private var resumeScene: TokenFusionScene = M.sceneAt(0.0, count)

    /** The canvas opacity (Reduce Motion's fade). */
    val opacity = TokenFusionAnim(1.0)

    // ---- layout ----
    var layout: Theme.Layout = Theme.Layout(402.0, 874.0, Theme.SAFE_TOP, Theme.SAFE_BOTTOM)
    private var minX = 0.0
    private var maxX = 0.0
    private var minY = 0.0
    private var maxY = 0.0
    private var insetBottom = 0.0

    fun applyLayout(l: Theme.Layout, insetTop: Double, bottom: Double, width: Double, height: Double) {
        layout = l
        insetBottom = bottom
        minX = (Theme.TOUCH_EDGE + 22 * l.scale - l.offsetX) / l.scale
        maxX = (width - Theme.TOUCH_EDGE - 22 * l.scale - l.offsetX) / l.scale
        minY = (insetTop + Theme.TOUCH_EDGE + 22 * l.scale - l.offsetY) / l.scale
        maxY = (height - bottom - Theme.TOUCH_EDGE - 22 * l.scale - l.offsetY) / l.scale
    }

    /** Frame time pinned at t seconds of the loop (previews and checks). */
    fun frameAt(t: Double) {
        scene = M.sceneAt(t, count)
        lagOne = scene
        lagTwo = M.sceneAt(max(0.0, t - 2.0 / 60), count)
    }

    private fun dispatch(add: Int, remove: Int, mask: Int, cueId: Int, finished: Boolean, stop: Boolean = true) {
        val ids = Theme.TOKENS.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }.map { it.id }
        Theme.TOKENS.forEachIndexed { i, token ->
            if ((add and (1 shl i)) != 0) onMerge?.invoke(token.id, ids)
            if ((remove and (1 shl i)) != 0) onRelease?.invoke(token.id)
        }
        if (cueId != 0) cue(cueId)
        if (finished) {
            if (stop) running = false
            onComplete?.invoke()
        }
    }

    private fun awaken() {
        if (!reduce) running = true
    }

    /** One frame; returns true when the picture changed. */
    fun frame(rawDt: Double): Boolean {
        val dt = min(rawDt, 0.05)
        var changed = false
        if (opacity.running) {
            opacity.step(dt)
            changed = true
        }
        if (reduce || !running || complete) return changed
        var current: TokenFusionScene
        if (playing) {
            val next = time + dt
            if (splash && next >= 5.7) {
                time = 5.5
                playing = false
                current = M.sceneAt(5.5, count)
                if (!complete) {
                    complete = true
                    dispatch(0, 0, 0, 0, true)
                }
            } else {
                time = next % M.LOOP
                current = M.sceneAt(next, count)
            }
        } else {
            var m = live
            M.stepLive(m, dt, reduce, true)
            val held = m.corePointer >= 0 || m.coins.any { it.pointer >= 0 }
            if (!held && idleMs != null && !autoPlay && m.now - m.lastTouch >= idleMs / 1000) m.flood = max(m.flood, 0.001)
            current = M.sceneOf(m, reduce)
            if (!held && autoPlay && !splash && m.now - m.lastTouch > M.RESUME_AFTER) {
                if (resumeStart < 0) {
                    resumeStart = m.now
                    resumeScene = current
                }
                val p = M.seg(m.now - resumeStart, 0.0, M.RESUME_DURATION)
                current = M.blendScene(resumeScene, M.sceneAt(0.0, count), p)
                if (p >= 1) {
                    time = 0.0
                    playing = true
                    m = M.liveFrom(M.sceneAt(0.0, count))
                    resumeStart = -1.0
                }
            }
            val finished = m.flood > 0.995 && !m.completed
            if (finished) {
                if (splash || !autoPlay) complete = true
                m.completed = true
            }
            if (m.mergeEvents != 0 || m.releaseEvents != 0 || m.cue != 0 || finished) {
                dispatch(m.mergeEvents, m.releaseEvents, m.mergedMask, if (finished) 5 else m.cue, finished, splash || !autoPlay)
                m.mergeEvents = 0
                m.releaseEvents = 0
                m.cue = 0
            }
            live = m
        }
        lagTwo = lagOne
        lagOne = scene
        scene = current
        return true
    }

    // ---- touch (dp, in the canvas's own space) ----

    private fun gx(x: Double) = (x - layout.offsetX) / layout.scale
    private fun gy(y: Double) = (y - layout.offsetY) / layout.scale

    /** A finger landed. Fingers within the system back-gesture strip at the left edge and the bottom inset are left to the system. */
    fun inGestureStrip(x: Double, y: Double): Boolean = x < Theme.TOUCH_BACK_INSET || y > layout.height - insetBottom

    fun touchDown(id: Int, x: Double, y: Double) {
        if (reduce) return
        val m = if (playing) M.liveFrom(scene, M.sceneAt(time + 1.0 / 120, count)) else live.copy()
        val any = M.grab(m, id, gx(x), gy(y), touchCore)
        if (!any) return
        playing = false
        resumeStart = -1.0
        live = m
        scene = M.sceneOf(m)
        awaken()
    }

    fun touchMove(id: Int, x: Double, y: Double) {
        if (reduce) return
        val m = live.copy()
        M.movePointer(m, id, gx(x), gy(y), minX, maxX, minY, maxY)
        live = m
        scene = M.sceneOf(m)
    }

    fun touchUp(id: Int, x: Double, y: Double) {
        if (reduce) {
            val px = gx(x)
            val py = gy(y)
            val i = scene.coins.indexOfFirst { it.r > 1 && hypot(it.x - px, it.y - py) < 30 }
            if (i >= 0) reducedAction(i)
        } else {
            val m = live.copy()
            M.releasePointer(m, id)
            live = m
        }
    }

    fun touchCancel(id: Int) {
        val m = live.copy()
        M.releasePointer(m, id, true)
        live = m
    }

    // ---- Reduce Motion: a fade, the geometry replaced while invisible, a fade in ----

    private fun applyReduced(index: Int) {
        val m = live.copy()
        if (index >= 0) {
            m.coins[index].state = 4
            m.coins[index].r = 0.0
            m.mergedMask = m.mergedMask or (1 shl index)
            dispatch(1 shl index, 0, m.mergedMask, 3, false)
        }
        if (index < 0 || m.coins.all { it.state == 4 }) {
            m.flood = 1.0
            complete = true
            dispatch(0, 0, m.mergedMask, 5, true)
        }
        live = m
        scene = M.sceneOf(m, true)
    }

    fun reducedAction(index: Int) {
        opacity.run(
            TokenFusionAnim.Step.Timing(0.0, 0.08, done = {
                applyReduced(index)
                opacity.run(TokenFusionAnim.Step.Timing(1.0, 0.16))
            }),
        )
    }

    /** Skip account linking: jump to the brand. */
    fun skip() {
        val m = live.copy()
        m.flood = 1.0
        m.completed = true
        live = m
        scene = M.sceneOf(m, true)
        complete = true
        running = false
        onComplete?.invoke()
    }

    /** Activate a token for a screen reader: merge it. */
    fun merge(index: Int) {
        if (reduce) {
            reducedAction(index)
            return
        }
        if (playing) live = M.liveFrom(scene, M.sceneAt(time + 1.0 / 120, count))
        complete = false
        live.lastTouch = live.now
        playing = false
        resumeStart = -1.0
        M.capture(live, index)
        awaken()
    }

    /** Splash mode under Reduce Motion: after 240 ms the brand fades in. */
    fun reducedSplash() {
        if (reduce && splash) reducedAction(-1)
    }
}
