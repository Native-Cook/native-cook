package com.ngocdevv.nativecook.blocks.gestures.raffledrumspin

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

internal class RaffleDrumSpinHostView(
    context: Context,
    var autoPlay: Boolean,
    private val reducedMotion: Boolean,
    private val debugTime: Double?,
    private val scripted: Boolean,
    private val captureId: String?,
    private val restoreNavigationBars: () -> Unit,
) : FrameLayout(context), Choreographer.FrameCallback {
    private val painter = RaffleDrumSpinPainter(context, reducedMotion)
    private val audio = RaffleDrumSpinAudio(context)
    private val driver = RdsDriver()
    private val page = RdsCanvasView(context, painter, top = false)
    private val top = RdsCanvasView(context, painter, top = true)
    private val drum = if (reducedMotion) null else RaffleDrumSpinGlView(context)
    private val foil = if (reducedMotion) null else RaffleDrumSpinGlView(context, foil = true)
    private val backgroundGroup = FrameLayout(context)
    // UIVisualEffect's dark material keeps the page's violet chroma while it racks focus.
    private val darkTint = View(context).apply { setBackgroundColor(Color.rgb(42, 25, 59)) }
    private val hitViews = ArrayList<View>()
    private val rackFocusBlur = if (Build.VERSION.SDK_INT >= 31) {
        RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP)
    } else null
    private val rackFocusEffects = LinkedHashMap<Int, RenderEffect>(256, .75f, true)

    private fun focusEffect(focus: Float): RenderEffect? {
        if (Build.VERSION.SDK_INT < 31 || focus <= 0f) return null
        val blur = rackFocusBlur ?: return null
        if (focus >= 1f) return blur
        val key = focus.toRawBits()
        return rackFocusEffects.getOrPut(key) {
            if (rackFocusEffects.size >= 256) {
                val oldest = rackFocusEffects.entries.iterator()
                oldest.next()
                oldest.remove()
            }
            val sharp = RenderEffect.createColorFilterEffect(alphaFilter(1f - focus))
            val soft = RenderEffect.createColorFilterEffect(alphaFilter(focus), blur)
            RenderEffect.createBlendModeEffect(sharp, soft, BlendMode.PLUS)
        }
    }

    private var captureReady = false
    private var currentFrame: RdsFrame? = null
    private var previousFrame: RdsFrame? = null
    private var fadeStartNanos = 0L
    private var lastFrameNanos = 0L
    private var released = false
    private var clockSeconds = 0.0
    private var scriptStart = Double.NaN
    private var scriptStage = 0
    private var scriptMark = 0.0
    private var scriptCrank = 0.0
    private var lastRatchet = 0.0
    private var touchKind = TouchKind.None
    private var touchCard = -1
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var stripStart = 0.0
    private var stripDragging = false
    private var ticketDragging = false
    private var velocity: VelocityTracker? = null

    private enum class TouchKind { None, Card, Play, Drum, Ticket, Close }

    init {
        setBackgroundColor(Color.rgb(20, 5, 58))
        isClickable = true
        clipChildren = false
        backgroundGroup.addView(page, match())
        drum?.let { backgroundGroup.addView(it, match()) }
        addView(backgroundGroup, match())
        addView(darkTint, match())
        foil?.let { addView(it, match()) }
        addView(top, match())
        darkTint.alpha = 0f
        installAccessibilityViews()
    }

    private fun match() = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        painter.release()
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (released || !isAttachedToWindow) return
        val dt = if (lastFrameNanos == 0L) 0.0 else (frameTimeNanos - lastFrameNanos) / 1_000_000_000.0
        lastFrameNanos = frameTimeNanos
        if (debugTime == null && !reducedMotion) clockSeconds += min(dt, .05)
        val now = clockSeconds
        val before = driver.state.t
        when {
            debugTime != null -> driver.state.t = debugTime
            reducedMotion -> Unit
            else -> stepDriver(driver.state, dt, now, autoPlay)
        }
        if (scripted && debugTime == null && !reducedMotion) stepScript(now)
        emitCues(before, driver.state.t, now)
        render(frameTimeNanos)
        val settledStill = reducedMotion && previousFrame == null &&
            currentFrame?.let { page.hasDrawn(it.t) && top.hasDrawn(it.t) } == true
        if ((debugTime == null || !captureReady) && !settledStill) Choreographer.getInstance().postFrameCallback(this)
    }

    private fun render(frameTimeNanos: Long) {
        if (width <= 0 || height <= 0) return
        val frame = raffleFrameAt(driver.time, width / RDS_REF_W, height / RDS_REF_H, driver)
        currentFrame = frame
        var fade = 1f
        if (previousFrame != null) {
            val raw = ((frameTimeNanos - fadeStartNanos) / 200_000_000.0).coerceIn(0.0, 1.0)
            fade = bezier(raw, .23, 1.0, .32, 1.0).toFloat()
            if (raw >= 1.0) previousFrame = null
        }
        page.setFrames(previousFrame, frame, fade)
        top.setFrames(previousFrame, frame, fade)
        drum?.present(frame, width.toFloat(), height.toFloat())
        foil?.present(frame, width.toFloat(), height.toFloat())
        val focus = frame.focus.coerceIn(0f, 1f)
        if (Build.VERSION.SDK_INT >= 31) {
            backgroundGroup.setRenderEffect(focusEffect(focus))
        }
        darkTint.alpha = focus * (45f / 255f)
        updateAccessibility(frame)
        if (!captureReady && debugTime != null &&
            page.hasDrawn(frame.t) && top.hasDrawn(frame.t) &&
            drum?.hasPresented(frame.t) != false && foil?.hasPresented(frame.t) != false
        ) {
            captureReady = true
            if (captureId != null) android.util.Log.i("RaffleDrumSpinReady", "PIN_READY $captureId")
        }
    }

    private fun emitCues(before: Double, after: Double, now: Double) {
        val state = driver.state
        if (state.mode == 0) return
        val tickBefore = floor((cageAngle(before) + state.spin) / (Math.PI * 2.0 / 7.0)).toInt()
        val tickAfter = floor((cageAngle(after) + state.spin) / (Math.PI * 2.0 / 7.0)).toInt()
        if (tickAfter != tickBefore && now - lastRatchet > .06) {
            lastRatchet = now
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            audio.play("ratchet")
        }
        fun crossed(mark: Double) = before < mark && after >= mark
        for (index in 0..state.to) if (crossed(T.fly[1] + index * T.flyStagger)) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            audio.play("land")
        }
        if (crossed(T.spin[0])) audio.play("spin")
        if (crossed(T.eject[0])) audio.play("pop")
        if (crossed(T.stitches[0])) audio.play("stitches")
        if (crossed(T.morph[0])) audio.play("morph")
        if (crossed(T.burst)) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            audio.play("burst")
        }
        repeat(12) { index ->
            val k = index / 12.0
            val mark = T.number[0] + (T.number[1] - T.number[0]) * (1.0 - (1.0 - k) * (1.0 - k))
            if (crossed(mark)) audio.play("tick")
        }
        if (crossed(T.tear[0])) audio.play("tear")
        if (crossed(T.burn[0] + .08)) audio.play("dust")
        if (crossed(T.tear[1])) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            audio.play("landStub")
        }
    }

    private fun alphaFilter(alpha: Float): ColorMatrixColorFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, alpha, 0f,
            ),
        ),
    )

    private fun beginCrank(now: Double) {
        touched(driver.state, now)
        driver.state.spinV = 0.0
    }

    private fun turnCrank(deltaY: Double, now: Double) {
        driver.state.spin += deltaY / RdsLayout.drumR * 1.2
        driver.state.last = now
    }

    private fun endCrank(velocityY: Double, now: Double) {
        driver.state.spinV = (velocityY / RdsLayout.drumR * 1.2).coerceIn(-24.0, 24.0)
        driver.state.last = now
        if (abs(driver.state.spinV) > 3.0) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Debug input is routed through the same driver entry points as onTouchEvent. */
    private fun stepScript(now: Double) {
        if (scriptStart.isNaN()) scriptStart = now
        val elapsed = now - scriptStart
        fun next() {
            scriptStage++
            scriptMark = now
            android.util.Log.i("RaffleDrumSpinScript", "stage=$scriptStage t=${driver.state.t} mode=${driver.state.mode}")
        }
        when (scriptStage) {
            0 -> if (elapsed >= .35) {
                pressIn(driver.state, 0)
                pressOut(driver.state)
                selectCard(driver.state, 0, now)
                next()
            }
            1 -> if (now - scriptMark >= .45) {
                grabStrip(driver.state, now)
                stripStart = driver.state.strip
                next()
            }
            2 -> {
                val progress = ((now - scriptMark) / .45).coerceIn(0.0, 1.0)
                driver.state.strip = rubber(stripStart - 120.0 * progress)
                driver.state.last = now
                if (progress >= 1.0) { releaseStrip(driver.state, -650.0, now); next() }
            }
            3 -> if (now - scriptMark >= .4) {
                pressIn(driver.state, 3, 184.0)
                pressOut(driver.state)
                pressPlay(driver.state, now)
                beginCrank(now)
                scriptCrank = 0.0
                next()
            }
            4 -> {
                val progress = ((now - scriptMark) / .8).coerceIn(0.0, 1.0)
                val position = progress * Math.PI * 2.0 * RdsLayout.drumR / 1.2
                turnCrank(position - scriptCrank, now)
                scriptCrank = position
                if (progress >= 1.0) { endCrank(300.0, now); next() }
            }
            5 -> if (driver.state.t >= T.hold - .01) {
                grabTicket(driver.state, now)
                next()
            }
            6 -> {
                val progress = ((now - scriptMark) / .6).coerceIn(0.0, 1.0)
                tiltTicket(driver.state, 54.0 * progress, -28.0 * progress, now)
                if (progress >= 1.0) { releaseTicket(driver.state, 240.0, -120.0, now); next() }
            }
            7 -> if (now - scriptMark >= 2.5) {
                pressIn(driver.state, 3, 292.0)
                pressOut(driver.state)
                pressPlay(driver.state, now)
                next()
            }
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (debugTime != null) return true
        val sx = width / RDS_REF_W
        val sy = height / RDS_REF_H
        val x = event.x / sx
        val y = event.y / sy
        val now = clockSeconds
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                lastY = event.y
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                touchCard = cardAt(x, y, driver.state.strip)
                touchKind = when {
                    x in (RdsLayout.closeX - 26f)..(RdsLayout.closeX + 26f) && y in (RdsLayout.closeY - 26f)..(RdsLayout.closeY + 26f) -> TouchKind.Close
                    !reducedMotion && canTilt(driver.state.t) && x in 51.0..351.0 && y in 376.5..487.5 -> TouchKind.Ticket
                    touchCard >= 0 -> TouchKind.Card
                    x in RdsLayout.playX..(RdsLayout.playX + RdsLayout.playW) && y in RdsLayout.playY..(RdsLayout.playY + RdsLayout.playH) -> TouchKind.Play
                    !reducedMotion && x in 100f..350f && y in 90f..310f -> TouchKind.Drum
                    else -> TouchKind.None
                }
                when (touchKind) {
                    TouchKind.Card -> {
                        pressIn(driver.state, touchCard)
                        stripStart = driver.state.strip
                    }
                    TouchKind.Play -> pressIn(driver.state, 3, (x - RdsLayout.playX).toDouble())
                    TouchKind.Drum -> {
                        beginCrank(now)
                    }
                    TouchKind.Ticket -> {
                        grabTicket(driver.state, now)
                        ticketDragging = true
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    else -> Unit
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                val dx = event.x - downX
                val dy = event.y - downY
                when (touchKind) {
                    TouchKind.Card -> {
                        if (!reducedMotion && !stripDragging && abs(dx) > 8f * sx && abs(dy) < 14f * sy) {
                            stripDragging = true
                            pressOut(driver.state)
                            grabStrip(driver.state, now)
                            stripStart = driver.state.strip
                        }
                        if (stripDragging) {
                            driver.state.strip = rubber(stripStart + dx / sx)
                            driver.state.last = now
                        }
                    }
                    TouchKind.Drum -> {
                        turnCrank(((event.y - lastY) / sx).toDouble(), now)
                        lastY = event.y
                    }
                    TouchKind.Ticket -> if (ticketDragging) tiltTicket(driver.state, (dx / sx).toDouble(), (dy / sy).toDouble(), now)
                    else -> Unit
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finishTouch(event, sx, sy, now)
        }
        return true
    }

    private fun finishTouch(event: MotionEvent, sx: Float, sy: Float, now: Double): Boolean {
        velocity?.addMovement(event)
        velocity?.computeCurrentVelocity(1000)
        val vx = (velocity?.xVelocity ?: 0f) / sx
        val vy = (velocity?.yVelocity ?: 0f) / sy
        val cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL
        when (touchKind) {
            TouchKind.Card -> {
                pressOut(driver.state)
                if (stripDragging) releaseStrip(driver.state, vx.toDouble(), now)
                else if (!cancelled && reducedMotion) reducedCard(touchCard, event.eventTime * 1_000_000L)
                else if (!cancelled && selectCard(driver.state, touchCard, now)) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    audio.play("select")
                }
            }
            TouchKind.Play -> {
                pressOut(driver.state)
                if (!cancelled) {
                    if (reducedMotion) reducedPlay(event.eventTime * 1_000_000L)
                    else if (pressPlay(driver.state, now)) performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            }
            TouchKind.Drum -> {
                endCrank(vy.toDouble(), now)
            }
            TouchKind.Ticket -> if (ticketDragging) releaseTicket(driver.state, vx.toDouble(), vy.toDouble(), now)
            TouchKind.Close -> if (!cancelled) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            else -> Unit
        }
        velocity?.recycle()
        velocity = null
        stripDragging = false
        ticketDragging = false
        touchKind = TouchKind.None
        parent?.requestDisallowInterceptTouchEvent(false)
        return true
    }

    private fun reducedPlay(frameTimeNanos: Long) {
        beginReducedTransition(frameTimeNanos)
        driver.state.t = if (driver.state.t >= T.hold - .01 && driver.state.t < T.claimPress[0]) T.reselect[0] - .05 else T.hold
        driver.state.mode = 2
        driver.state.last = clockSeconds
    }

    private fun reducedCard(card: Int, frameTimeNanos: Long) {
        beginReducedTransition(frameTimeNanos)
        driver.state.from = card
        driver.state.to = card
        driver.state.t = 0.0
        driver.state.mode = 2
        driver.state.last = clockSeconds
    }

    private fun beginReducedTransition(frameTimeNanos: Long) {
        previousFrame = currentFrame
        fadeStartNanos = frameTimeNanos
        Choreographer.getInstance().removeFrameCallback(this)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun cardAt(x: Float, y: Float, strip: Double): Int {
        if (y !in (RdsLayout.cardY - 8f)..(RdsLayout.cardY + RdsLayout.cardH + 8f)) return -1
        repeat(3) { card ->
            val left = RdsLayout.cardX0 + card * (RdsLayout.cardW + RdsLayout.cardGap) + strip.toFloat()
            if (x in left..(left + RdsLayout.cardW)) return card
        }
        return -1
    }

    private fun installAccessibilityViews() {
        fun hit(label: String, click: () -> Unit): View = View(context).apply {
            contentDescription = label
            isFocusable = true
            isClickable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityDelegate = object : AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Button"
                }
            }
            setOnClickListener { click() }
            hitViews += this
            addView(this)
        }
        hit("Close") { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
        repeat(3) { card ->
            hit(RdsCopy.cardLines[card].joinToString(" ")) {
                val now = clockSeconds
                if (reducedMotion) reducedCard(card, System.nanoTime()) else selectCard(driver.state, card, now)
            }.accessibilityDelegate = object : AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.RadioButton"
                    info.isCheckable = true
                    @Suppress("DEPRECATION")
                    info.isChecked = host.isSelected
                }
            }
        }
        hit(RdsCopy.play) {
            val now = clockSeconds
            if (reducedMotion) reducedPlay(System.nanoTime()) else pressPlay(driver.state, now)
        }.tooltipText = "Spins the drum and draws a winning ball."
        hit("Raffle drum") {}.apply {
            tooltipText = "Drag up or down to turn the crank."
            accessibilityDelegate = object : AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.SeekBar"
                }
            }
        }
        hit("Golden ticket") {}.tooltipText = "Drag to tilt it in the light."
    }

    private fun updateAccessibility(frame: RdsFrame) {
        if (hitViews.size < 7 || width == 0 || height == 0) return
        val sx = width / RDS_REF_W
        val sy = height / RDS_REF_H
        fun place(view: View, x: Float, y: Float, w: Float, h: Float) {
            val params = view.layoutParams as? LayoutParams ?: LayoutParams(0, 0)
            val newWidth = (w * sx).toInt()
            val newHeight = (h * sy).toInt()
            val newLeft = (x * sx).toInt()
            val newTop = (y * sy).toInt()
            if (params.width != newWidth || params.height != newHeight || params.leftMargin != newLeft || params.topMargin != newTop) {
                params.width = newWidth
                params.height = newHeight
                params.leftMargin = newLeft
                params.topMargin = newTop
                view.layoutParams = params
            }
        }
        place(hitViews[0], RdsLayout.closeX - 26f, RdsLayout.closeY - 26f, 52f, 52f)
        repeat(3) { card ->
            place(hitViews[card + 1], RdsLayout.cardX0 + card * (RdsLayout.cardW + RdsLayout.cardGap) + frame.strip, RdsLayout.cardY, RdsLayout.cardW, RdsLayout.cardH)
            hitViews[card + 1].isSelected = card == frame.to
        }
        place(hitViews[4], RdsLayout.playX, RdsLayout.playY, RdsLayout.playW, RdsLayout.playH)
        val label = if (frame.claim > .5f) RdsCopy.claim else RdsCopy.play
        if (hitViews[4].contentDescription != label) {
            hitViews[4].contentDescription = label
            hitViews[4].tooltipText = if (frame.claim > .5f) "Tears off the stub and enters it with your card." else "Spins the drum and draws a winning ball."
        }
        place(hitViews[5], 100f, 90f, 250f, 220f)
        place(hitViews[6], 51f, 376.5f, 300f, 111f)
    }

    fun release() {
        if (released) return
        released = true
        restoreNavigationBars()
        Choreographer.getInstance().removeFrameCallback(this)
        audio.release()
        drum?.onPause()
        foil?.onPause()
    }
}

private class RdsCanvasView(
    context: Context,
    private val painter: RaffleDrumSpinPainter,
    private val top: Boolean,
) : View(context) {
    private var previous: RdsFrame? = null
    private var current: RdsFrame? = null
    private var fade = 1f
    private var drawnTime = Float.NaN
    fun hasDrawn(time: Float): Boolean = drawnTime == time

    init {
        setWillNotDraw(false)
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setFrames(previous: RdsFrame?, current: RdsFrame, fade: Float) {
        this.previous = previous
        this.current = current
        this.fade = fade
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = current ?: return
        previous?.let { before ->
            val beforeLayer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), ((1f - fade) * 255).toInt())
            drawFrame(canvas, before)
            canvas.restoreToCount(beforeLayer)
            val currentLayer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (fade * 255).toInt())
            drawFrame(canvas, now)
            canvas.restoreToCount(currentLayer)
        } ?: drawFrame(canvas, now)
        drawnTime = now.t
    }

    private fun drawFrame(canvas: Canvas, frame: RdsFrame) {
        if (top) painter.drawTop(canvas, width.toFloat(), height.toFloat(), frame)
        else painter.drawPage(canvas, width.toFloat(), height.toFloat(), frame)
    }
}
