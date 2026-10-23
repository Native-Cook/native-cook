package com.ngocdevv.nativecook.blocks.lists.foryouinsights

private sealed interface ForYouInsightsTrackKind {
    data object Hold : ForYouInsightsTrackKind

    data class Timing(
        val duration: Double,
        val delay: Double,
        val easing: (Double) -> Double,
    ) : ForYouInsightsTrackKind

    data class Spring(val value: ForYouInsightsSpring) : ForYouInsightsTrackKind
}

private class ForYouInsightsTrack(initial: Double = 0.0) {
    private var from = initial
    private var to = initial
    private var start = 0.0
    private var kind: ForYouInsightsTrackKind = ForYouInsightsTrackKind.Hold

    fun value(time: Double): Double {
        return when (val current = kind) {
            ForYouInsightsTrackKind.Hold -> to
            is ForYouInsightsTrackKind.Timing -> {
                val progress = ForYouInsightsMotion.clamp01(
                    (time - start - current.delay) / current.duration,
                )
                from + (to - from) * current.easing(progress)
            }
            is ForYouInsightsTrackKind.Spring -> {
                from + (to - from) * ForYouInsightsMotion.spring(time - start, current.value)
            }
        }
    }

    fun active(time: Double): Boolean {
        return when (val current = kind) {
            ForYouInsightsTrackKind.Hold -> false
            is ForYouInsightsTrackKind.Timing -> {
                time - start < current.duration + current.delay + 0.02
            }
            is ForYouInsightsTrackKind.Spring -> {
                time - start < current.value.settle + 0.02
            }
        }
    }

    fun set(value: Double) {
        from = value
        to = value
        kind = ForYouInsightsTrackKind.Hold
    }

    fun animate(target: Double, nextKind: ForYouInsightsTrackKind, time: Double) {
        val current = value(time)
        from = current
        to = target
        start = time
        kind = nextKind
    }
}

class ForYouInsightsController {
    var autoPlay = false
        private set

    var quiet = false
        private set

    var onInsights = true
        private set

    var scrollY = 0.0
        private set

    var rowBase = ForYouInsightsTheme.Layout.ROW_TOP
        private set

    var actorIndex = 0
        private set

    private var auto = false
    private var origin = -1.0
    private var lastTouch = 0.0
    private var touching = false
    private var closing = false
    private var scrolledBack = false
    private var streamStart = -100.0
    private var driftStart = 0.0
    private var scrollToken = 0
    private var press = ForYouInsightsTrack()
    private var progress = ForYouInsightsTrack()
    private var drift = ForYouInsightsTrack()
    private var touchX = ForYouInsightsTrack()
    private var touchY = ForYouInsightsTrack()
    private var dragY = ForYouInsightsTrack()
    private var lens = ForYouInsightsTrack()
    private var lastFrame = ForYouInsightsFrame()

    fun configure(autoPlay: Boolean, quiet: Boolean, time: Double) {
        this.quiet = quiet
        val enabled = autoPlay && !quiet
        val changed = this.autoPlay != enabled
        this.autoPlay = enabled
        if (!enabled) {
            auto = false
        } else if (changed) {
            auto = true
            origin = time
        }
    }

    fun setLens(index: Int, animated: Boolean, time: Double) {
        if (animated) {
            lens.animate(
                target = index.toDouble(),
                nextKind = ForYouInsightsTrackKind.Spring(
                    ForYouInsightsMotion.Springs.lens.oscillator,
                ),
                time = time,
            )
        } else {
            lens.set(index.toDouble())
        }
    }

    fun setScrollY(value: Double) {
        scrollY = value
    }

    fun wantsFrames(time: Double): Boolean {
        if (auto || touching) {
            return true
        }
        if (autoPlay && onInsights) {
            return true
        }
        val trackActive = press.active(time) ||
            progress.active(time) ||
            drift.active(time) ||
            touchX.active(time) ||
            touchY.active(time) ||
            dragY.active(time) ||
            lens.active(time)
        if (trackActive) {
            return true
        }
        if (ForYouInsightsMotion.tipPose(time - streamStart).active) {
            return true
        }
        if (quiet) {
            return false
        }
        return progress.value(time) > 0.001
    }

    fun tick(time: Double): ForYouInsightsFrame {
        var frame = ForYouInsightsFrame(
            actorIndex = actorIndex,
            touchX = touchX.value(time),
            touchY = touchY.value(time),
            dragY = dragY.value(time),
            lens = lens.value(time),
            scrollToken = scrollToken,
        )
        if (auto) {
            if (origin < 0.0) {
                origin = time
            }
            val pose = ForYouInsightsMotion.pose(time - origin)
            frame = frame.copy(
                tip = pose.first.toFrame(),
                press = pose.second.press,
                progress = pose.second.progress,
                driftTime = pose.second.driftTime,
                drift = pose.second.drift,
                readerLive = false,
            )
            lastFrame = frame
            return frame
        }
        val tipPose = ForYouInsightsMotion.tipPose(time - streamStart)
        frame = frame.copy(
            tip = tipPose.toFrame(),
            press = press.value(time),
            progress = progress.value(time),
            driftTime = time - driftStart,
            drift = drift.value(time),
            readerLive = progress.value(time) > 0.98,
        )
        lastFrame = frame
        resume(time, tipPose)
        return frame.copy(actorIndex = actorIndex)
    }

    private fun resume(time: Double, tipPose: ForYouInsightsTipPose) {
        if (!autoPlay || touching || !onInsights) {
            return
        }
        if (time - lastTouch < ForYouInsightsMotion.RESUME_AFTER) {
            return
        }
        if (progress.value(time) > 0.001) {
            if (!closing) {
                closing = true
                drift.animate(
                    target = 0.0,
                    nextKind = timing(0.26, ForYouInsightsMotion::inOutQuad),
                    time = time,
                )
                touchX.animate(
                    target = 0.0,
                    nextKind = ForYouInsightsTrackKind.Spring(
                        ForYouInsightsMotion.Springs.tiltBack.oscillator,
                    ),
                    time = time,
                )
                touchY.animate(
                    target = 0.0,
                    nextKind = ForYouInsightsTrackKind.Spring(
                        ForYouInsightsMotion.Springs.tiltBack.oscillator,
                    ),
                    time = time,
                )
                dragY.animate(
                    target = 0.0,
                    nextKind = ForYouInsightsTrackKind.Spring(
                        ForYouInsightsMotion.Springs.dragBack.oscillator,
                    ),
                    time = time,
                )
                progress.animate(
                    target = 0.0,
                    nextKind = ForYouInsightsTrackKind.Spring(
                        ForYouInsightsMotion.Springs.touchClose.oscillator,
                    ),
                    time = time,
                )
            }
            return
        }
        if (scrollY > 2.0) {
            if (!scrolledBack) {
                scrolledBack = true
                scrollToken += 1
            }
            return
        }
        if (tipPose.active || press.value(time) > 0.001) {
            return
        }
        closing = false
        scrolledBack = false
        actorIndex = 0
        rowBase = ForYouInsightsTheme.Layout.ROW_TOP
        origin = time
        auto = true
    }

    fun touchBegan(time: Double) {
        touching = true
        takeOver(time)
    }

    fun touchEnded(time: Double) {
        touching = false
        lastTouch = time
    }

    fun takeOver(time: Double) {
        lastTouch = time
        closing = false
        if (!auto) {
            return
        }
        auto = false
        val remainder = (time - origin) % ForYouInsightsMotion.LOOP
        val loopTime = if (remainder >= 0.0) remainder else remainder + ForYouInsightsMotion.LOOP
        val pose = ForYouInsightsMotion.pose(loopTime)
        streamStart = if (pose.first.active) {
            time - (loopTime - ForYouInsightsMotion.Timeline.DIM_START)
        } else {
            -100.0
        }
        press.set(pose.second.press)
        press.animate(
            target = 0.0,
            nextKind = timing(0.2, ForYouInsightsMotion::inOutQuad),
            time = time,
        )
        driftStart = time - pose.second.driftTime
        progress.set(pose.second.progress)
        drift.set(pose.second.drift)
        if (pose.second.progress > 0.001) {
            progress.animate(
                target = if (pose.second.opening) 1.0 else 0.0,
                nextKind = ForYouInsightsTrackKind.Spring(
                    if (pose.second.opening) {
                        ForYouInsightsMotion.Springs.touchOpen.oscillator
                    } else {
                        ForYouInsightsMotion.Springs.touchClose.oscillator
                    },
                ),
                time = time,
            )
            if (pose.second.opening) {
                drift.animate(
                    target = 1.0,
                    nextKind = timing(0.5, ForYouInsightsMotion::inOutQuad),
                    time = time,
                )
            }
        }
    }

    fun pressRow(index: Int, down: Boolean, time: Double): Boolean {
        if (down) {
            takeOver(time)
            if (progress.value(time) > 0.001) {
                return false
            }
            actorIndex = index
            press.animate(
                target = 1.0,
                nextKind = timing(
                    ForYouInsightsMotion.PRESS_IN_SECONDS,
                    ForYouInsightsMotion::easeOut,
                ),
                time = time,
            )
            return true
        } else {
            press.animate(
                target = 0.0,
                nextKind = timing(0.2, ForYouInsightsMotion::easeOut),
                time = time,
            )
            return false
        }
    }

    fun open(index: Int, base: Double, time: Double): Boolean {
        takeOver(time)
        if (progress.value(time) > 0.001) {
            return false
        }
        actorIndex = index
        rowBase = base
        if (quiet) {
            progress.animate(
                target = 1.0,
                nextKind = timing(0.2, ForYouInsightsMotion::inOutQuad),
                time = time,
            )
        } else {
            driftStart = time + 0.4
            drift.set(0.0)
            drift.animate(
                target = 1.0,
                nextKind = timing(
                    duration = 0.55,
                    easing = ForYouInsightsMotion::easeInOut,
                    delay = 0.4,
                ),
                time = time,
            )
            progress.animate(
                target = 1.0,
                nextKind = ForYouInsightsTrackKind.Spring(
                    ForYouInsightsMotion.Springs.touchOpen.oscillator,
                ),
                time = time,
            )
        }
        return true
    }

    fun close(time: Double) {
        takeOver(time)
        drift.animate(
            target = 0.0,
            nextKind = timing(0.26, ForYouInsightsMotion::inOutQuad),
            time = time,
        )
        touchX.animate(
            target = 0.0,
            nextKind = ForYouInsightsTrackKind.Spring(
                ForYouInsightsMotion.Springs.tiltBack.oscillator,
            ),
            time = time,
        )
        touchY.animate(
            target = 0.0,
            nextKind = ForYouInsightsTrackKind.Spring(
                ForYouInsightsMotion.Springs.tiltBack.oscillator,
            ),
            time = time,
        )
        dragY.animate(
            target = 0.0,
            nextKind = ForYouInsightsTrackKind.Spring(
                ForYouInsightsMotion.Springs.dragBack.oscillator,
            ),
            time = time,
        )
        val kind = if (quiet) {
            timing(0.2, ForYouInsightsMotion::inOutQuad)
        } else {
            ForYouInsightsTrackKind.Spring(
                ForYouInsightsMotion.Springs.touchClose.oscillator,
            )
        }
        progress.animate(target = 0.0, nextKind = kind, time = time)
    }

    fun regenerate(time: Double) {
        if (quiet) {
            return
        }
        takeOver(time)
        if (!ForYouInsightsMotion.tipPose(time - streamStart).active) {
            streamStart = time
        }
    }

    fun selectSection(insights: Boolean, time: Double) {
        takeOver(time)
        onInsights = insights
    }

    fun dragChanged(translationX: Double, translationY: Double, scale: Double) {
        touchX.set(translationX)
        touchY.set(translationY)
        dragY.set(ForYouInsightsMotion.rubber(translationY / scale) * scale)
    }

    fun dragEnded(
        translationY: Double,
        velocityY: Double,
        scale: Double,
        time: Double,
    ): Boolean {
        val dismiss = translationY > ForYouInsightsMotion.Dismiss.DISTANCE * scale ||
            velocityY > ForYouInsightsMotion.Dismiss.VELOCITY
        if (dismiss) {
            close(time)
            return true
        }
        touchX.animate(
            target = 0.0,
            nextKind = ForYouInsightsTrackKind.Spring(
                ForYouInsightsMotion.Springs.tiltBack.oscillator,
            ),
            time = time,
        )
        touchY.animate(
            target = 0.0,
            nextKind = ForYouInsightsTrackKind.Spring(
                ForYouInsightsMotion.Springs.tiltBack.oscillator,
            ),
            time = time,
        )
        dragY.animate(
            target = 0.0,
            nextKind = ForYouInsightsTrackKind.Spring(
                ForYouInsightsMotion.Springs.dragBack.oscillator,
            ),
            time = time,
        )
        return false
    }

    private fun timing(
        duration: Double,
        easing: (Double) -> Double,
        delay: Double = 0.0,
    ): ForYouInsightsTrackKind.Timing {
        return ForYouInsightsTrackKind.Timing(
            duration = duration,
            delay = delay,
            easing = easing,
        )
    }
}

private fun ForYouInsightsTipPose.toFrame(): ForYouInsightsTipFrame {
    return ForYouInsightsTipFrame(
        ghost = ghost,
        stream = stream,
        caret = caret,
        shimmer = shimmer,
    )
}
