package com.ngocdevv.nativecook.blocks.gestures.receiptprinter

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Owns all gesture, scripted, and animation state on Compose's main thread. */
internal class ReceiptPrinterEngine(
    private val autoPlay: Boolean,
    private val haptic: (Kind) -> Unit,
) {
    enum class Kind { TICK, OPEN, TEAR }
    private enum class Touch { NONE, CARD, MENU_ROW, MENU_OUTSIDE, PAPER, BAR, OTHER }

    var phase = ReceiptPrinterPhase.GRID
        private set
    private val grid = ReceiptPrinterValue(1f)
    private val island = ReceiptPrinterValue(0f)
    private val menu = ReceiptPrinterValue(0f)
    private val lift = ReceiptPrinterValue(0f)
    private val feed = ReceiptPrinterValue(0f)
    private val scroll = ReceiptPrinterValue(0f)
    private val morph = ReceiptPrinterValue(0f)
    private val develop = ReceiptPrinterValue(0f)
    private val held = ReceiptPrinterValue(0f)
    private val paperAlpha = ReceiptPrinterValue(1f)
    private val cut = ReceiptPrinterValue(0f)
    private val indicator = ReceiptPrinterValue(0f)
    private val hold = ReceiptPrinterValue(1f)
    private val buttonScale = ReceiptPrinterValue(1f)
    private var pressedIndex = -1
    private var holdIndex = -1
    private var menuPressedRow = -1
    private var buttonPressedIndex = -1
    private var buttonInside = false
    private var morphFrom = 0
    private var fed = 0f
    private var bar = 0f
    private var clockMs = 0f
    private var feedElapsed = 0f
    private var feedStarted = false
    private var feedScripted = false
    private var feedStep = -1
    private var scrollVelocity = 0f
    private var scrollDecay = false
    private var decayElapsed = 0f
    private var decaySpringActive = false
    private var retractElapsed = 0f
    private var retractStart = 0f
    private var retractDone = false
    private var gridReturnStarted = false
    private var gridReturnPending = false
    private var cutSpringPending = false
    private var scriptTime = 0f
    private var scriptSegment = -1
    private var scriptPaused = false
    private var quietMs = 0f
    private var realDown = false
    private var armed = false
    private var touch = Touch.NONE
    private var touchIndex = -1
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var lastMs = 0L
    private var downMs = 0L
    private var touchElapsed = 0f
    private var moved = false
    private var longPressFired = false
    private var dragStart = 0f
    private var dragScripted = false
    private var touchVelocity = 0f
    private var frame = ReceiptPrinterFrame()
    private var indicatorTarget = false
    private var reduced = false
    private var screenWidth = Tokens.SOURCE.width
    private var screenHeight = Tokens.SOURCE.height
    private var islandTop = Tokens.ISLAND.top
    private var islandCenterX = Tokens.SOURCE.width / 2f
    private var paperTop = islandTop + Tokens.ISLAND.openH - Tokens.ISLAND.tuck

    private val maxScroll: Float
        get() = max(
            0f,
            paperTop + ReceiptPrinterMotion.PAPER_LENGTH -
                (ReceiptPrinterMotion.BAR_TOP - Tokens.PAPER.maxScrollFootGap),
        )

    fun updateGeometry(width: Float, height: Float, islandTop: Float, islandCenterX: Float) {
        screenWidth = width
        screenHeight = height
        this.islandTop = islandTop
        this.islandCenterX = islandCenterX
        paperTop = islandTop + Tokens.ISLAND.openH - Tokens.ISLAND.tuck
    }

    fun setReducedMotion(next: Boolean) {
        if (reduced == next) return
        reduced = next
        if (next) {
            scriptPaused = true
            if (phase == ReceiptPrinterPhase.PRINTING) finishFeed()
            morph.snap(0f)
            cut.snap(0f)
        }
    }

    fun snapshot(): ReceiptPrinterFrame = frame

    fun down(x: Float, y: Float, uptimeMs: Long): Boolean {
        // Every real hit pauses the loop, including cards and action buttons.
        scriptPaused = true
        realDown = true
        quietMs = 0f
        downX = x
        downY = y
        lastY = y
        lastMs = uptimeMs
        downMs = uptimeMs
        touchElapsed = 0f
        touchVelocity = 0f
        moved = false
        longPressFired = false
        touchIndex = -1
        touch = when (phase) {
            ReceiptPrinterPhase.GRID -> {
                val i = cardAt(x, y)
                if (i >= 0) {
                    touchIndex = i
                    holdIndex = i
                    hold.snap(1f)
                    if (!reduced) hold.to(ReceiptPrinterMotion.HOLD_SCALE, ReceiptPrinterMotion.LONG_PRESS_MS)
                    Touch.CARD
                } else Touch.OTHER
            }
            ReceiptPrinterPhase.MENU -> {
                val menuX = menuX()
                val menuY = menuY()
                val menuTokens = Tokens.GRID.menu
                if (x in menuX..(menuX + menuTokens.w) && y in menuY..(menuY + menuTokens.h)) {
                    val row = ((y - menuY - Tokens.GRID.menuPaddingVertical) / menuTokens.rowH).toInt().coerceIn(0, 1)
                    menuPressedRow = row
                    Touch.MENU_ROW
                } else Touch.MENU_OUTSIDE
            }
            ReceiptPrinterPhase.PRINTING, ReceiptPrinterPhase.READING -> {
                val i = if (phase == ReceiptPrinterPhase.READING) barAt(x, y) else -1
                if (i >= 0 && bar > 0.5f) {
                    buttonPressedIndex = i
                    buttonInside = true
                    buttonScale.to(ReceiptPrinterMotion.PRESS_SCALE, ReceiptPrinterMotion.PRESS_IN_MS)
                    touchIndex = i
                    Touch.BAR
                } else if (x in Tokens.SLOT.paperX0..Tokens.SLOT.paperX1 && y >= paperTop) {
                    beginDrag(false)
                    Touch.PAPER
                } else Touch.OTHER
            }
            ReceiptPrinterPhase.RETRACTING -> Touch.OTHER
        }
        return touch != Touch.OTHER
    }

    fun move(x: Float, y: Float, uptimeMs: Long) {
        val dt = (uptimeMs - lastMs).coerceAtLeast(1L).toFloat() / 1000f
        touchVelocity = (y - lastY) / dt
        lastY = y
        lastMs = uptimeMs
        if (kotlin.math.abs(x - downX) > Tokens.TOUCH.movementSlop ||
            kotlin.math.abs(y - downY) > Tokens.TOUCH.movementSlop
        ) {
            moved = true
        }
        when (touch) {
            Touch.PAPER -> dragMove(y - downY)
            Touch.CARD -> if (moved) {
                hold.durationSpringTo(1f, Tokens.POLISH.releaseSpring.duration,
                    Tokens.POLISH.releaseSpring.dampingRatio)
                // Keep the index while the release scale settles.
            }
            Touch.MENU_ROW -> {
                val mx = menuX()
                val my = menuY()
                val menuTokens = Tokens.GRID.menu
                menuPressedRow = if (x in mx..(mx + menuTokens.w) && y in my..(my + menuTokens.h)) {
                    ((y - my - Tokens.GRID.menuPaddingVertical) / menuTokens.rowH).toInt().coerceIn(0, 1)
                } else {
                    -1
                }
            }
            Touch.BAR -> {
                val inside = withinBarRetention(touchIndex, x, y)
                if (inside != buttonInside) {
                    buttonInside = inside
                    buttonScale.to(if (inside) ReceiptPrinterMotion.PRESS_SCALE else 1f,
                        if (inside) ReceiptPrinterMotion.PRESS_IN_MS else ReceiptPrinterMotion.PRESS_OUT_MS)
                }
            }
            else -> Unit
        }
    }

    fun up(x: Float, y: Float, uptimeMs: Long, velocityY: Float? = null) {
        when (touch) {
            Touch.CARD -> {
                hold.durationSpringTo(1f, Tokens.POLISH.releaseSpring.duration,
                    Tokens.POLISH.releaseSpring.dampingRatio)
                if (!longPressFired && withinCardRetention(touchIndex, x, y)) print(false, touchIndex)
            }
            Touch.MENU_ROW -> {
                if (menuPressedRow >= 0 && x in menuX()..(menuX() + Tokens.GRID.menu.w)) {
                    print(false)
                }
                menuPressedRow = -1
            }
            Touch.MENU_OUTSIDE -> closeMenu()
            Touch.PAPER -> endDrag(velocityY ?: touchVelocity)
            Touch.BAR -> {
                buttonInside = false
                buttonScale.to(1f, ReceiptPrinterMotion.PRESS_OUT_MS)
                if (withinBarRetention(touchIndex, x, y) && touchIndex == 1) retract(false)
            }
            else -> Unit
        }
        touch = Touch.NONE
        realDown = false
        quietMs = 0f
        lastMs = uptimeMs
    }

    fun cancel() {
        if (touch == Touch.PAPER) endDrag(0f)
        hold.durationSpringTo(1f, Tokens.POLISH.releaseSpring.duration,
            Tokens.POLISH.releaseSpring.dampingRatio)
        buttonScale.to(1f, ReceiptPrinterMotion.PRESS_OUT_MS)
        buttonInside = false
        menuPressedRow = -1
        touch = Touch.NONE
        realDown = false
        quietMs = 0f
    }

    fun accessibilityPrint(index: Int) {
        interruptForAccessibility()
        print(false, index)
    }

    fun accessibilityMenu(index: Int) {
        interruptForAccessibility()
        openMenu(index, false)
    }

    fun accessibilityChoose() {
        interruptForAccessibility()
        print(false)
    }

    fun accessibilityCloseMenu() {
        interruptForAccessibility()
        closeMenu()
    }

    fun accessibilityScroll(by: Float) {
        interruptForAccessibility()
        if (phase == ReceiptPrinterPhase.PRINTING) finishFeed()
        if (phase == ReceiptPrinterPhase.READING) {
            scroll.to(
                (scroll.value + by).coerceIn(0f, maxScroll),
                Tokens.SCROLL.barMs,
            )
        }
    }

    fun accessibilityTear() {
        interruptForAccessibility()
        retract(false)
    }

    private fun interruptForAccessibility() {
        scriptPaused = true
        quietMs = 0f
        realDown = false
    }

    private fun openMenu(index: Int, scripted: Boolean) {
        if (phase != ReceiptPrinterPhase.GRID) return
        pressedIndex = index
        val spring = Tokens.PRESS.menuIn
        lift.springTo(1f, spring.stiffness, spring.damping, spring.mass)
        menu.springTo(1f, spring.stiffness, spring.damping, spring.mass)
        phase = ReceiptPrinterPhase.MENU
        if (!scripted) haptic(Kind.TICK)
    }

    private fun closeMenu() {
        if (phase != ReceiptPrinterPhase.MENU) return
        menu.to(0f, ReceiptPrinterMotion.MENU_OUT_MS)
        lift.to(0f, ReceiptPrinterMotion.MENU_OUT_MS)
        phase = ReceiptPrinterPhase.GRID
    }

    private fun print(scripted: Boolean, tile: Int = -1) {
        if (phase != ReceiptPrinterPhase.GRID && phase != ReceiptPrinterPhase.MENU) return
        if (tile >= 0) pressedIndex = tile
        morphFrom = max(0, pressedIndex)
        morph.snap(0f)
        if (!reduced) morph.to(1f, ReceiptPrinterMotion.MORPH_MS * ReceiptPrinterMotion.QA_SLOWMO)
        develop.to(1f, ReceiptPrinterMotion.DEVELOP_FADE_MS)
        menu.to(0f, ReceiptPrinterMotion.MENU_OUT_MS)
        lift.to(0f, ReceiptPrinterMotion.MENU_OUT_MS)
        grid.to(0f, ReceiptPrinterMotion.GRID_OUT_MS)
        if (reduced) {
            island.snap(1f)
        } else {
            val spring = Tokens.ISLAND_SPRING
            island.springTo(1f, spring.stiffness, spring.damping, spring.mass)
        }
        scroll.snap(0f)
        fed = 0f
        feed.snap(0f)
        paperAlpha.snap(if (reduced) 0f else 1f)
        if (reduced) paperAlpha.to(1f, ReceiptPrinterMotion.BAR_MS, ReceiptPrinterValue.Easing.IN_OUT_QUAD)
        feedElapsed = 0f
        feedStarted = reduced
        feedStep = -1
        feedScripted = scripted
        scrollDecay = false
        bar = 0f
        phase = ReceiptPrinterPhase.PRINTING
        if (!scripted) haptic(Kind.OPEN)
        if (reduced) {
            feed.to(1f, ReceiptPrinterMotion.BAR_MS, ReceiptPrinterValue.Easing.LINEAR)
        }
    }

    private fun finishFeed(interrupted: Boolean = false) {
        feed.snap(1f)
        fed = ReceiptPrinterMotion.PAPER_TOTAL
        phase = ReceiptPrinterPhase.READING
        develop.to(0f, ReceiptPrinterMotion.DEVELOP_FADE_MS, ReceiptPrinterValue.Easing.IN_OUT_QUAD)
        if (!reduced && !interrupted) {
            cut.to(1f, ReceiptPrinterMotion.CUT_DOWN_MS, ReceiptPrinterValue.Easing.OUT_QUAD)
            cutSpringPending = true
        }
        if (!feedScripted && !reduced && !interrupted) haptic(Kind.OPEN)
    }

    private fun retract(scripted: Boolean) {
        if (phase != ReceiptPrinterPhase.READING) return
        phase = ReceiptPrinterPhase.RETRACTING
        scrollDecay = false
        armed = false
        retractStart = scroll.value
        retractElapsed = 0f
        retractDone = false
        gridReturnStarted = false
        if (!scripted) haptic(Kind.TEAR)
        if (reduced) {
            paperAlpha.to(0f, ReceiptPrinterMotion.REDUCED_RETRACT_MS, ReceiptPrinterValue.Easing.OUT)
            grid.to(1f, ReceiptPrinterMotion.REDUCED_RETRACT_MS, ReceiptPrinterValue.Easing.OUT)
            gridReturnStarted = true
            gridReturnPending = true
        }
    }

    private fun finishRetractPaper() {
        if (retractDone) return
        retractDone = true
        fed = 0f
        feed.snap(0f)
        scroll.snap(0f)
        if (reduced) {
            island.snap(0f)
        } else {
            val spring = Tokens.ISLAND_SPRING
            island.springTo(0f, spring.stiffness, spring.damping, spring.mass)
        }
        morph.snap(0f)
    }

    private fun beginDrag(scripted: Boolean) {
        // A mid-feed grab finishes the sheet before handing the current position to the finger.
        if (phase == ReceiptPrinterPhase.PRINTING) finishFeed(interrupted = true)
        if (phase != ReceiptPrinterPhase.READING) return
        scrollDecay = false
        dragStart = scroll.value
        dragScripted = scripted
        held.to(1f, ReceiptPrinterMotion.LIFT_MS, ReceiptPrinterValue.Easing.IN_OUT_QUAD)
    }

    private fun dragMove(dy: Float) {
        if (phase != ReceiptPrinterPhase.READING) return
        scroll.snap(rubber(dragStart - dy))
        val nowArmed = scroll.value < -ReceiptPrinterMotion.DISMISS_BY
        if (nowArmed && !armed && !dragScripted) haptic(Kind.TICK)
        armed = nowArmed
    }

    private fun endDrag(velocityY: Float) {
        held.to(0f, ReceiptPrinterMotion.LIFT_MS, ReceiptPrinterValue.Easing.IN_OUT_QUAD)
        armed = false
        if (phase != ReceiptPrinterPhase.READING) return
        if (scroll.value < -ReceiptPrinterMotion.DISMISS_BY ||
            (scroll.value < -Tokens.TOUCH.dismissFlingOffset && velocityY > ReceiptPrinterMotion.DISMISS_VELOCITY)) {
            retract(dragScripted)
            return
        }
        scrollVelocity = -velocityY
        scrollDecay = true
        decayElapsed = 0f
        decaySpringActive = false
    }

    private fun rubber(x: Float): Float = when {
        x < 0f -> -(x * -ReceiptPrinterMotion.RUBBER) / (1f - x / Tokens.TOUCH.rubberSoftening)
        x > maxScroll -> maxScroll +
            ((x - maxScroll) * ReceiptPrinterMotion.RUBBER) /
            (1f + (x - maxScroll) / Tokens.TOUCH.rubberSoftening)
        else -> x
    }

    fun tick(milliseconds: Float) {
        val dt = milliseconds.coerceIn(0f, Tokens.SOURCE.maxFrameDeltaMs)
        clockMs += dt
        listOf(grid, island, menu, lift, feed, scroll, morph, develop, held, paperAlpha, cut, indicator, hold, buttonScale).forEach { it.step(dt) }
        if (touch == Touch.CARD && !moved && !longPressFired) {
            touchElapsed += dt
            if (touchElapsed >= ReceiptPrinterMotion.LONG_PRESS_MS) {
                longPressFired = true
                hold.durationSpringTo(1f, Tokens.POLISH.releaseSpring.duration,
                    Tokens.POLISH.releaseSpring.dampingRatio)
                openMenu(touchIndex, false)
            }
        }
        if (phase == ReceiptPrinterPhase.PRINTING) {
            if (!feedStarted) {
                feedElapsed += dt
                if (feedElapsed >= ReceiptPrinterMotion.ISLAND_LEAD_MS * ReceiptPrinterMotion.QA_SLOWMO) {
                    feedStarted = true
                    val elapsedAfterLead = feedElapsed -
                        ReceiptPrinterMotion.ISLAND_LEAD_MS * ReceiptPrinterMotion.QA_SLOWMO
                    feed.to(
                        1f,
                        ReceiptPrinterMotion.FEED_MS * ReceiptPrinterMotion.QA_SLOWMO,
                        ReceiptPrinterValue.Easing.LINEAR,
                    )
                    feed.step(elapsedAfterLead)
                }
            }
            if (feedStarted) {
                if (reduced) fed = ReceiptPrinterMotion.PAPER_TOTAL
                else fed = stepped(feed.value) * (screenHeight - paperTop)
                val k = floor(feed.value * ReceiptPrinterMotion.FEED_STEPS).toInt()
                if (k != feedStep) {
                    feedStep = k
                    if (!feedScripted && !reduced) haptic(Kind.TICK)
                }
                if (feed.settled && feed.value >= 1f) finishFeed()
            }
        }
        if (cutSpringPending && cut.settled && cut.value >= 1f) {
            cutSpringPending = false
            val spring = Tokens.UPGRADE.cutSpring
            cut.springTo(0f, spring.stiffness, spring.damping, spring.mass)
        }
        if (phase == ReceiptPrinterPhase.RETRACTING && reduced && !retractDone) {
            if (paperAlpha.settled) finishRetractPaper()
        } else if (phase == ReceiptPrinterPhase.RETRACTING && !retractDone) {
            retractElapsed += dt
            val gridStart = ReceiptPrinterMotion.TUG_MS + ReceiptPrinterMotion.GRID_LEAD_MS
            if (!gridReturnStarted && retractElapsed >= gridStart * ReceiptPrinterMotion.QA_SLOWMO) {
                gridReturnStarted = true
                gridReturnPending = true
                grid.to(
                    1f,
                    ReceiptPrinterMotion.GRID_IN_MS * ReceiptPrinterMotion.QA_SLOWMO,
                )
            }
            if (retractElapsed <= ReceiptPrinterMotion.TUG_MS) {
                val p = (retractElapsed / ReceiptPrinterMotion.TUG_MS).coerceIn(0f, 1f)
                scroll.snap(retractStart - ReceiptPrinterMotion.TUG * (1f - (1f - p) * (1f - p)))
            } else {
                val p = ((retractElapsed - ReceiptPrinterMotion.TUG_MS) / ReceiptPrinterMotion.RETRACT_MS).coerceIn(0f, 1f)
                scroll.snap(retractStart - ReceiptPrinterMotion.TUG +
                    (ReceiptPrinterMotion.PAPER_TOTAL - retractStart + ReceiptPrinterMotion.TUG) *
                    easeInOut(p))
                if (p >= 1f) finishRetractPaper()
            }
        }
        if (gridReturnPending && grid.settled && grid.value >= 1f) {
            gridReturnPending = false
            phase = ReceiptPrinterPhase.GRID
        }
        if (scrollDecay && phase == ReceiptPrinterPhase.READING) stepDecay(dt)
        if (buttonPressedIndex >= 0 && touch != Touch.BAR && buttonScale.settled) buttonPressedIndex = -1
        if (holdIndex >= 0 && touch != Touch.CARD && hold.settled) holdIndex = -1
        val wantBar = phase == ReceiptPrinterPhase.READING && scroll.value > maxScroll - ReceiptPrinterMotion.BAR_WITHIN
        bar = (bar + if (wantBar) dt / ReceiptPrinterMotion.BAR_MS else -dt / ReceiptPrinterMotion.BAR_MS).coerceIn(0f, 1f)
        val wantIndicator = phase == ReceiptPrinterPhase.READING && scroll.value > Tokens.INDICATOR.showAfterScroll
        if (wantIndicator != indicatorTarget) {
            indicatorTarget = wantIndicator
            indicator.to(if (wantIndicator) 1f else 0f, Tokens.INDICATOR.fadeMs)
        }
        if (autoPlay && !reduced) stepScript(dt)
        frame = makeFrame()
    }

    private fun stepDecay(dt: Float) {
        // Same rubberBandDecay integration as Reanimated's installed source (SLOPE_FACTOR=.1).
        val delta = dt.coerceAtMost(64f)
        decayElapsed += delta
        val current = scroll.value
        val edge = if (kotlin.math.abs(current) < kotlin.math.abs(current - maxScroll)) 0f else maxScroll
        val derivative = if (current < 0f || current > maxScroll) current - edge else 0f
        val v = scrollVelocity * exp(-(1f - ReceiptPrinterMotion.DECELERATION) * decayElapsed * 0.1f) -
            derivative * ReceiptPrinterMotion.RUBBER
        if (kotlin.math.abs(derivative) > 0.1f) decaySpringActive = true
        else if (decaySpringActive) {
            scroll.snap(edge)
            scrollDecay = false
            return
        }
        scrollVelocity = v
        scroll.snap(current + v * delta / 1000f)
        if (kotlin.math.abs(v) < 1f) {
            scrollDecay = false
            scroll.snap(scroll.value.coerceIn(0f, maxScroll))
        }
    }

    private fun stepScript(dt: Float) {
        if (scriptPaused) {
            if (!realDown) quietMs += dt
            if (quietMs >= ReceiptPrinterMotion.RESUME_DELAY_MS && phase == ReceiptPrinterPhase.GRID) restartScript()
            return
        }
        val previous = scriptTime
        val t = scriptTime
        val script = Tokens.AUTOPLAY
        if (crossed(previous, previous + dt, script.pressAt)) openMenu(0, true)
        if (crossed(previous, previous + dt, script.pickAt)) print(true)
        val segment = script.scrolls.indexOfFirst { t >= it.start && t < it.end }
        if (segment != scriptSegment) {
            if (scriptSegment >= 0) {
                val old = script.scrolls[scriptSegment]
                dragMove(-old.by)
                endDrag((-old.by / ((old.end - old.start) / 1000f)) * 0.4f)
            }
            if (segment >= 0) beginDrag(true)
        }
        if (segment >= 0) {
            val part = script.scrolls[segment]
            val u = ((t - part.start) / (part.end - part.start)).coerceIn(0f, 1f)
            dragMove(-part.by * u * u * (3f - 2f * u))
        }
        if (crossed(previous, previous + dt, script.tearAt)) retract(true)
        scriptSegment = segment
        val next = if (ReceiptPrinterMotion.DEBUG_FREEZE_MS >= 0f && t >= ReceiptPrinterMotion.DEBUG_FREEZE_MS) t
            else t + dt / ReceiptPrinterMotion.QA_SLOWMO
        if (next >= ReceiptPrinterMotion.LOOP_MS) {
            scriptTime = next - ReceiptPrinterMotion.LOOP_MS
            scriptSegment = -1
        } else scriptTime = next
    }

    private fun restartScript() {
        if (phase != ReceiptPrinterPhase.GRID) return
        scriptTime = 0f
        scriptSegment = -1
        scriptPaused = false
        quietMs = 0f
    }

    private fun makeFrame(): ReceiptPrinterFrame {
        val swing = if (phase == ReceiptPrinterPhase.PRINTING && !reduced) {
            val x = feed.value * ReceiptPrinterMotion.FEED_STEPS
            val since = (x - floor(x)) * (ReceiptPrinterMotion.FEED_MS / ReceiptPrinterMotion.FEED_STEPS) / 1000f
            ReceiptPrinterMotion.FEED_SWING * exp(-ReceiptPrinterMotion.FEED_SWING_DAMPING * since) *
                sin(ReceiptPrinterMotion.FEED_SWING_HZ * 2f * PI.toFloat() * since)
        } else 0f
        val sway = if (phase == ReceiptPrinterPhase.READING && !reduced)
            ReceiptPrinterMotion.SWAY_DEG * sin(clockMs / ReceiptPrinterMotion.SWAY_MS * 2f * PI.toFloat()) else 0f
        val blink = 0.5f + 0.5f * sin(clockMs / 1000f * ReceiptPrinterMotion.LED_BLINK_HZ * 2f * PI.toFloat())
        val led = if (armed) Tokens.POLISH.armedLed else if (phase == ReceiptPrinterPhase.PRINTING) 0.4f + 0.6f * blink else Tokens.UPGRADE.ledIdle
        val ledGlow = if (armed) 0.35f else if (phase == ReceiptPrinterPhase.PRINTING) 0.35f * (0.4f + 0.6f * blink) else 0.12f
        val shapedBar = 1f - (1f - bar) * (1f - bar) * (1f - bar)
        val viewport = ReceiptPrinterMotion.BAR_TOP - Tokens.SPACE.sm -
            (paperTop + Tokens.SPACE.sm)
        val indicatorHeight = max(
            Tokens.INDICATOR.minimumHeight,
            viewport * viewport / (ReceiptPrinterMotion.PAPER_LENGTH + Tokens.INDICATOR.contentExtra),
        )
        val indicatorProgress = (scroll.value / max(1f, maxScroll)).coerceIn(0f, 1f)
        return ReceiptPrinterFrame(
            viewportWidth = screenWidth,
            phase = phase,
            gridOpacity = grid.value,
            islandProgress = island.value,
            islandTop = islandTop,
            islandCenterX = islandCenterX,
            paperTop = paperTop,
            menuOpacity = menu.value,
            menuScale = if (reduced) 1f else Tokens.GRID.menuStartScale +
                (1f - Tokens.GRID.menuStartScale) * menu.value,
            menuX = menuX(),
            menuY = menuY(),
            menuOriginX = Tokens.GRID.gutter +
                (max(0, pressedIndex) % 3) * (Tokens.GRID.cardW + Tokens.GRID.gap) - menuX(),
            menuOriginY = menuOriginY(),
            menuPressedRow = menuPressedRow,
            pressedIndex = pressedIndex,
            holdIndex = holdIndex,
            holdScale = hold.value,
            cardLift = lift.value,
            paperVisible = fed,
            paperAlpha = paperAlpha.value,
            paperScroll = scroll.value,
            cutOffset = cut.value * ReceiptPrinterMotion.CUT_DROP,
            paperRotation = swing + sway,
            paperLift = held.value,
            ledOpacity = led,
            ledGlowOpacity = ledGlow,
            barOpacity = shapedBar,
            barRise = (1f - shapedBar) * Tokens.SPACE.lg,
            buttonPressedIndex = buttonPressedIndex,
            buttonPressScale = buttonScale.value,
            morphProgress = morph.value,
            morphFrom = morphFrom,
            developOpacity = develop.value,
            indicatorOpacity = indicator.value,
            indicatorY = paperTop + Tokens.SPACE.sm +
                indicatorProgress * (viewport - indicatorHeight),
            indicatorHeight = indicatorHeight,
        )
    }

    private fun cardAt(x: Float, y: Float): Int {
        val grid = Tokens.GRID
        val pitchX = grid.cardW + grid.gap
        if (x < grid.gutter || y < grid.rowTop) return -1
        val column = ((x - grid.gutter) / pitchX).toInt()
        val row = ((y - grid.rowTop) / grid.rowPitch).toInt()
        if (column !in 0..2 || row !in 0..4) return -1
        val left = grid.gutter + column * pitchX
        val top = grid.rowTop + row * grid.rowPitch
        return if (x in left..(left + grid.cardW) && y in top..(top + grid.cardH)) {
            row * 3 + column
        } else {
            -1
        }
    }

    private fun barAt(x: Float, y: Float): Int {
        val bar = Tokens.BAR
        if (y !in bar.top..(bar.top + bar.h)) return -1
        val pitch = bar.w + bar.gap
        val i = ((x - bar.x) / pitch).toInt()
        val left = bar.x + i * pitch
        return if (i in 0..2 && x in left..(left + bar.w)) i else -1
    }

    private fun withinCardRetention(index: Int, x: Float, y: Float): Boolean {
        if (index !in 0 until Tokens.CARDS.size) return false
        val grid = Tokens.GRID
        val retention = Tokens.TOUCH.retention
        val left = grid.gutter + (index % 3) * (grid.cardW + grid.gap)
        val top = grid.rowTop + (index / 3) * grid.rowPitch
        return x in (left - retention)..(left + grid.cardW + retention) &&
            y in (top - retention)..(top + grid.cardH + retention)
    }

    private fun withinBarRetention(index: Int, x: Float, y: Float): Boolean {
        if (index !in 0..2) return false
        val bar = Tokens.BAR
        val retention = Tokens.TOUCH.retention
        val left = bar.x + index * (bar.w + bar.gap)
        return x in (left - retention)..(left + bar.w + retention) &&
            y in (bar.top - retention)..(bar.top + bar.h + retention)
    }

    private fun menuX(): Float {
        val i = max(0, pressedIndex)
        val grid = Tokens.GRID
        val cardX = grid.gutter + (i % 3) * (grid.cardW + grid.gap)
        return min(cardX, screenWidth - grid.gutter - grid.menu.w)
    }

    private fun menuY(): Float {
        val rowTop = max(0, pressedIndex) / 3 * Tokens.GRID.rowPitch
        val below = rowTop + Tokens.GRID.cardH + Tokens.SPACE.sm
        val fitsBelow = Tokens.GRID.menu.y + below + Tokens.GRID.menu.h <
            screenHeight - Tokens.SPACE.lg
        val offset = if (fitsBelow) below else rowTop - Tokens.GRID.menu.h - Tokens.SPACE.sm
        return Tokens.GRID.menu.y + offset
    }

    private fun menuOriginY(): Float {
        val rowTop = max(0, pressedIndex) / 3 * Tokens.GRID.rowPitch
        val below = rowTop + Tokens.GRID.cardH + Tokens.SPACE.sm
        val fitsBelow = Tokens.GRID.menu.y + below + Tokens.GRID.menu.h <
            screenHeight - Tokens.SPACE.lg
        return if (fitsBelow) 0f else Tokens.GRID.menu.h
    }

    private fun stepped(progress: Float): Float {
        val n = ReceiptPrinterMotion.FEED_STEPS
        val x = progress.coerceIn(0f, 1f) * n
        val k = min(n - 1, floor(x).toInt())
        val u = ((x - k) / ReceiptPrinterMotion.FEED_PUSH).coerceIn(0f, 1f)
        return (k + 1f - (1f - u) * (1f - u) * (1f - u)) / n
    }

    private fun easeInOut(progress: Float): Float {
        val targetX = progress.coerceIn(0f, 1f)
        var low = 0f
        var high = 1f
        repeat(12) {
            val time = (low + high) / 2f
            val inverse = 1f - time
            val x = 3f * inverse * inverse * time * 0.77f +
                3f * inverse * time * time * 0.175f + time * time * time
            if (x < targetX) low = time else high = time
        }
        val time = (low + high) / 2f
        val inverse = 1f - time
        return 3f * inverse * time * time + time * time * time
    }

    private fun crossed(from: Float, to: Float, threshold: Float) = from < threshold && to >= threshold
}
