import Foundation

/// The page turn's state machine: one turning sheet a finger drags, a riffle of up to three, the idle nudge, the
/// autoplay finger and the sheet's flex. It is the port of PageCurlJournal.tsx's worklets, driven once per display
/// frame by `step(now:)`; everything it shows is read from its values.
@MainActor
final class PageCurlJournalEngine {
    private typealias Motion = PageCurlJournalMotion
    private typealias Math = PageCurlJournalMath
    private typealias Theme = PageCurlJournalTheme

    enum Mode { case idle, pending, drag, settle, hold, riffling, nudging }
    enum Haptic { case tick, half, land }

    /// One turning sheet: its grabbed point, where that point is now, and which page it is.
    @MainActor
    final class Slot {
        var front = 0.0
        var gx: Double
        var gy = 0.0
        let tx: PageCurlJournalValue
        let ty = PageCurlJournalValue(0)
        var on = 0.0

        init(width: Double) {
            gx = width
            tx = PageCurlJournalValue(width)
        }
    }

    static let lastPage = 3

    // Set by the surface.
    var onHaptic: ((Haptic) -> Void)?
    var onLanded: ((Int) -> Void)?

    private(set) var width: Double
    private(set) var height: Double
    private var s: Double
    var reduced = false
    var autoPlay = false
    private(set) var ready = false

    let slots: [Slot]
    var slot0: Slot { slots[0] }
    private(set) var underIdx = 1.0
    private(set) var cur: Int
    private(set) var mode = Mode.idle
    private var baseX = 0.0
    private var baseY = 0.0
    private var startX = 0.0
    private var startY = 0.0
    private var gain = 1.0
    private var preLifted = false
    private let fade = PageCurlJournalValue(-1)
    private(set) var flex = 0.0
    private var flexV = 0.0
    private var lastTip: (x: Double, y: Double)
    private var scripted = false
    private var lastTouch = -1e9
    private(set) var clock: Double
    private var playing = true
    private var riffleLeft = 0
    private var riffleTo = 0
    private var grainAt = (x: 0.0, y: 0.0)
    private var grainTime = -1e9
    private var pastHalf = false
    private var lastNudge = -1e9
    let twinkle = PageCurlJournalValue(1)
    let foilT = PageCurlJournalValue(0)
    private var lastFoil = 0.0
    private var scriptAt = -1
    private var nudgeDone = false
    private var returningToRest = false
    private var lastLoopIndex = 0
    private var lastFrame: Double?
    private var hideCountdown = 0

    init(width: Double, height: Double, initialPage: Int) {
        self.width = width
        self.height = height
        s = width / Double(Theme.referenceWidth)
        cur = initialPage
        clock = Motion.qaStart
        lastTip = (width, 0)
        slots = [Slot(width: width), Slot(width: width), Slot(width: width)]
    }

    /// The pages are snapshotted: the book answers touches and the autoplay script starts from the top of its loop.
    func markReady() {
        ready = true
        clock = Motion.qaStart
        lastLoopIndex = Int(clock / Motion.loopMs)
    }

    /// A different screen size (rotation, split view): the sheets rest at the new right edge.
    func resize(width: Double, height: Double) {
        guard width != self.width || height != self.height else { return }
        self.width = width
        self.height = height
        s = width / Double(Theme.referenceWidth)
        guard mode == .idle else { return }
        for slot in slots {
            slot.gx = width
            slot.gy = 0
            slot.tx.set(width)
            slot.ty.set(0)
        }
        lastTip = (width, 0)
    }

    private var show: Double {
        get { slots[0].on }
        set { slots[0].on = newValue }
    }

    // MARK: - Arrival payoffs

    /// Payoffs on arrival: the title's sparkles twinkle, the cover's foil catches the light once.
    private func arrive(_ page: Int) {
        if reduced { return }
        if page == 0 {
            twinkle.set(0)
            twinkle.animate(.timing(target: 1, durationMs: Motion.ms(Motion.Twinkle.durationMs), easing: PageCurlJournalEasing.inOut))
        }
        if page == Self.lastPage {
            foilT.set(0)
            lastFoil = clock
            foilT.animate(.timing(target: 1, durationMs: Motion.ms(Motion.Foil.sweepMs), easing: PageCurlJournalEasing.inOut) { [weak self] finished in
                if finished { self?.foilT.set(0) }
            })
        }
    }

    private func landed(page: Int, changed: Bool, feel: Bool) {
        onLanded?(page)
        // After the page is on top, the curl canvases step aside (two frames, so the views are drawn).
        hideCountdown = 3
        if changed && feel { onHaptic?(.land) }
    }

    private func settleDone(over: Bool) {
        let page = over ? Int(underIdx) : Int(slot0.front)
        let changed = page != cur
        cur = page
        mode = .idle
        fade.set(-1)
        preLifted = false
        if changed { arrive(page) }
        landed(page: page, changed: changed, feel: !scripted)
    }

    /// A riffle ends when its last sheet lands.
    private func riffleLanded() {
        riffleLeft -= 1
        if riffleLeft > 0 { return }
        let page = riffleTo
        cur = page
        mode = .idle
        arrive(page)
        landed(page: page, changed: true, feel: !scripted && !returningToRest)
        returningToRest = false
    }

    // MARK: - Releasing a sheet

    /// Riffle: the sheet in hand comes back, and every earlier sheet follows it, staggered, each on its own canvas.
    private func startRiffle(vx: Double, vy: Double) {
        let f0 = Int(slot0.front)
        let n = min(f0, Motion.Riffle.slots - 1)
        mode = .riffling
        riffleLeft = n + 1
        riffleTo = f0 - n
        flexV += abs(vx) * Motion.Turn.releaseKick
        let spring = Motion.Riffle.spring.scaled
        slot0.ty.animate(.spring(target: slot0.gy, config: spring, velocity: vy))
        slot0.tx.animate(.spring(target: slot0.gx, config: spring, velocity: vx) { [weak self] finished in
            if finished { self?.riffleLanded() }
        })
        guard n >= 1 else { return }
        for i in 1...n {
            let slot = slots[i]
            let y = slot0.gy + (i % 2 == 1 ? -1.0 : 1.0) * 18 * Double(i) * s
            slot.front = Double(f0 - i)
            slot.gx = width
            slot.gy = y
            slot.tx.set(-width)
            slot.ty.set(y)
            slot.on = 1
            slot.tx.animate(.delay(
                ms: Motion.ms(Motion.Riffle.staggerMs * Double(i)),
                next: .spring(target: width, config: spring, velocity: Motion.Riffle.launch * s * Motion.qaSpeed) { [weak self] finished in
                    if finished { self?.riffleLanded() }
                }
            ))
        }
    }

    /// Release the sheet with the finger's velocity: it goes over or falls back on a mass-spring.
    private func release(over: Bool, vx: Double, vy: Double) {
        let tip = Math.clampTip(gx: slot0.gx, gy: slot0.gy, tx: slot0.tx.value, ty: slot0.ty.value, height: height)
        slot0.tx.set(tip.x)
        slot0.ty.set(tip.y)
        if !over, gain == Motion.Turn.backFactor, vx / Motion.qaSpeed > Motion.Riffle.velocity, slot0.front > 0, !reduced {
            startRiffle(vx: vx, vy: vy)
            return
        }
        mode = .settle
        // The flung sheet billows: its speed kicks the roll's flex spring.
        flexV += hypot(vx, vy) * Motion.Turn.releaseKick
        let config = (over ? Motion.Turn.over : Motion.Turn.back).scaled
        slot0.ty.animate(.spring(target: slot0.gy, config: config, velocity: vy))
        slot0.tx.animate(.spring(target: over ? -slot0.gx : slot0.gx, config: config, velocity: vx) { [weak self] finished in
            if finished { self?.settleDone(over: over) }
        })
    }

    /// Turn by button, tap or accessibility action: the right edge lifts at 62 % and is flicked over.
    func turnBy(_ step: Int) {
        if mode != .idle || !ready { return }
        let c = cur
        let target = c + step
        if target < 0 || target > Self.lastPage { return }
        show = 1
        if reduced {
            slot0.front = Double(c)
            underIdx = Double(target)
            fade.set(0)
            mode = .settle
            fade.animate(.timing(target: 1, durationMs: Motion.fadeMs, easing: PageCurlJournalEasing.out, completion: { [weak self] finished in
                guard finished, let self else { return }
                slot0.front = Double(target)
                settleDone(over: false)
            }))
            return
        }
        gain = 1
        if step > 0 {
            if !preLifted {
                // Not already lifted by the finger: lift the right edge at 62 %.
                let y = height * Motion.Turn.buttonGrabY
                slot0.gx = width
                slot0.gy = y
                slot0.tx.set(width - 1)
                slot0.ty.set(y)
            }
            slot0.front = Double(c)
            underIdx = Double(target)
            release(over: true, vx: -1600 * s * Motion.qaSpeed, vy: -500 * s * Motion.qaSpeed)
        } else {
            let y = height * Motion.Turn.buttonGrabY
            slot0.front = Double(target)
            underIdx = Double(c)
            slot0.gx = width
            slot0.gy = y
            slot0.tx.set(-width)
            slot0.ty.set(y)
            release(over: false, vx: 1200 * s * Motion.qaSpeed, vy: -400 * s * Motion.qaSpeed)
        }
    }

    // MARK: - Finger

    private func down(x: Double, y: Double) {
        let m = mode
        if m == .hold || m == .riffling || !ready { return }
        if (m == .settle || m == .nudging) && fade.value < 0 {
            // Catch the sheet mid-flight: it stays where it is and follows the finger from here.
            slot0.tx.cancel()
            slot0.ty.cancel()
            let tip = Math.clampTip(gx: slot0.gx, gy: slot0.gy, tx: slot0.tx.value, ty: slot0.ty.value, height: height)
            baseX = tip.x
            baseY = tip.y
            startX = x
            startY = y
            if m == .nudging { gain = 1 }
            mode = .drag
            return
        }
        if m != .idle { return }
        startX = x
        startY = y
        mode = .pending
        pastHalf = false
        grainAt = (x, y)
        // Anticipation: a touch near the right edge lifts the corner within a frame.
        let c = cur
        if !reduced, c < Self.lastPage, x > width * Motion.Turn.preLiftZone {
            slot0.front = Double(c)
            underIdx = Double(c + 1)
            slot0.gx = x
            slot0.gy = y
            slot0.tx.set(x)
            slot0.ty.set(y)
            gain = 1
            preLifted = true
            show = 1
            slot0.tx.animate(.spring(target: x - Motion.Turn.preLift * s, config: Motion.Spring(durationMs: 150, dampingRatio: 1).scaled))
        }
    }

    private func move(x: Double, y: Double) {
        if mode == .pending {
            let dx = x - startX
            if abs(dx) < Motion.Turn.pickSlop, abs(y - startY) < Motion.Turn.pickSlop { return }
            let c = cur
            if reduced { return }
            slot0.tx.cancel()
            if dx < 0, c < Self.lastPage {
                // Forward: the point under the finger is the point of the sheet that follows it.
                let lift = preLifted ? Motion.Turn.preLift * s : 0
                slot0.front = Double(c)
                underIdx = Double(c + 1)
                slot0.gx = startX
                slot0.gy = startY
                baseX = startX - lift
                baseY = startY
                gain = 1
            } else if dx > 0, c > 0 {
                // Back: the previous sheet lies turned over the spine; its fold follows the finger 1:1.
                slot0.front = Double(c - 1)
                underIdx = Double(c)
                slot0.gx = width
                slot0.gy = startY
                baseX = -width
                baseY = startY
                gain = Motion.Turn.backFactor
            } else {
                return
            }
            preLifted = false
            mode = .drag
            show = 1
        }
        if mode != .drag { return }
        let nx = baseX + gain * (x - startX)
        let ny = baseY + gain * (y - startY)
        slot0.tx.set(nx)
        slot0.ty.set(ny)
        if scripted { return }
        // Haptic grain: a tick per step of fold travel, and a light catch where the sheet passes half a turn.
        let now = clock
        if hypot(x - grainAt.x, y - grainAt.y) >= Motion.Grain.stepPt * s, now - grainTime >= Motion.Grain.minGapMs {
            grainAt = (x, y)
            grainTime = now
            onHaptic?(.tick)
        }
        let tip = Math.clampTip(gx: slot0.gx, gy: slot0.gy, tx: nx, ty: ny, height: height)
        let half = Math.turnProgress(gx: slot0.gx, gy: slot0.gy, tx: tip.x, ty: tip.y) > Motion.Turn.commitProgress
        if half != pastHalf {
            pastHalf = half
            onHaptic?(.half)
        }
    }

    private func up(x: Double, y: Double, vx: Double, vy: Double) {
        let m = mode
        if m == .pending {
            mode = .idle
            let dx = x - startX
            let c = cur
            if reduced, abs(dx) > 30 {
                turnBy(dx < 0 ? 1 : -1)
                return
            }
            // A tap: the right margin or the hint turns forward, the left margin back; elsewhere a lifted corner drops.
            let hintTop = (Double(Theme.Typography.hint.top) - 12) * s
            let onHint = c == 0 && y > hintTop && y < hintTop + 44 * s
            if x > width * 0.66 || onHint {
                turnBy(1)
            } else if x < width * 0.33 {
                turnBy(-1)
            } else if preLifted {
                gain = 1
                release(over: false, vx: 0, vy: 0)
            }
            return
        }
        if m != .drag { return }
        let g = gain
        let p = Math.turnProgress(gx: slot0.gx, gy: slot0.gy, tx: slot0.tx.value, ty: slot0.ty.value)
        let v = (g * vx) / Motion.qaSpeed // thresholds are in real time, also in slow-motion review
        let over = v < -Motion.Turn.commitVelocity || (p > Motion.Turn.commitProgress && v < Motion.Turn.commitVelocity)
        release(over: over, vx: g * vx, vy: g * vy)
    }

    /// Idle nudge: the bottom-right corner lifts and falls back with a wobble.
    private func nudge() {
        let c = cur
        if mode != .idle || c >= Self.lastPage || reduced || !ready { return }
        lastNudge = clock
        mode = .nudging
        slot0.front = Double(c)
        underIdx = Double(c + 1)
        gain = 1
        slot0.gx = width
        slot0.gy = height
        slot0.tx.set(width)
        slot0.ty.set(height)
        show = 1
        let lx = width + Motion.Nudge.lift.x * s
        let ly = height + Motion.Nudge.lift.y * s
        let riseSpring = Motion.Nudge.up.scaled
        let dropSpring = Motion.Nudge.down.scaled
        slot0.ty.animate(.sequence([
            .spring(target: ly, config: riseSpring),
            .delay(ms: Motion.ms(Motion.Nudge.holdMs), next: .spring(target: height, config: dropSpring)),
        ]))
        slot0.tx.animate(.sequence([
            .spring(target: lx, config: riseSpring),
            .delay(ms: Motion.ms(Motion.Nudge.holdMs), next: .spring(target: width, config: dropSpring) { [weak self] finished in
                guard finished, let self else { return }
                flexV += Motion.Nudge.kick
                settleDone(over: false)
            }),
        ]))
    }

    // MARK: - Touch entry points (the pan gesture)

    func touchDown(x: Double, y: Double) {
        lastTouch = clock
        playing = false
        if scripted {
            // A real finger takes over from the script mid-stroke.
            scripted = false
            if mode == .drag {
                baseX = slot0.tx.value
                baseY = slot0.ty.value
                startX = x
                startY = y
                return
            }
            if mode == .pending { mode = .idle }
        }
        down(x: x, y: y)
    }

    func touchMove(x: Double, y: Double) {
        lastTouch = clock
        move(x: x, y: y)
    }

    func touchEnd(x: Double, y: Double, vx: Double, vy: Double) {
        lastTouch = clock
        up(x: x, y: y, vx: vx, vy: vy)
    }

    // MARK: - Frame

    /// One display frame: the animated values, then the sheet's flex, the ambient timers and the autoplay script.
    func step(now: Double) {
        for slot in slots {
            slot.tx.step(now: now)
            slot.ty.step(now: now)
        }
        fade.step(now: now)
        twinkle.step(now: now)
        foilT.step(now: now)

        let dt = min(0.05, lastFrame.map { now - $0 } ?? 0.016) * Motion.qaSpeed
        lastFrame = now
        clock += dt * 1000
        if Motion.qaHold == .a {
            // Re-assert the held frame every frame, so nothing at start-up can move it.
            mode = .hold
            slot0.front = 0
            underIdx = 1
            slot0.gx = Motion.holdA.grab.x * s
            slot0.gy = Motion.holdA.grab.y * s
            slot0.tx.set(Motion.holdA.tip.x * s)
            slot0.ty.set(Motion.holdA.tip.y * s)
            show = 1
        }

        let tip = Math.clampTip(gx: slot0.gx, gy: slot0.gy, tx: slot0.tx.value, ty: slot0.ty.value, height: height)
        let speed = hypot(tip.x - lastTip.x, tip.y - lastTip.y) / max(dt, 1e-3)
        lastTip = tip
        let target = mode == .hold || reduced ? 0 : min(Motion.Turn.flexMax, speed * Motion.Turn.flexPerSpeed)
        let k = Motion.Turn.flex.stiffness / Motion.Turn.flex.mass
        let c = Motion.Turn.flex.damping / Motion.Turn.flex.mass
        if target != 0 || abs(flex) > 1e-4 || abs(flexV) > 1e-4 {
            flexV += (k * (target - flex) - c * flexV) * dt
            flex = max(-0.5, flex + flexV * dt)
        } else if flex != 0 {
            flex = 0
            flexV = 0
        }

        runTimers()
        if hideCountdown > 0 {
            hideCountdown -= 1
            if hideCountdown == 0 { hideCurls() }
        }
    }

    /// The curl canvases step aside once the landed page is on top.
    private func hideCurls() {
        if mode != .idle { return }
        for slot in slots { slot.on = 0 }
    }

    private func runTimers() {
        if Motion.qaHold != nil || !ready { return }
        let now = clock
        // Ambient: the cover's foil sweeps every few seconds while it rests.
        if !reduced, cur == Self.lastPage, mode == .idle, now - lastFoil > Motion.Foil.everyMs { arrive(Self.lastPage) }

        if !autoPlay {
            let idle = now - lastTouch
            let nudgeWindow = Motion.Nudge.afterMs + Motion.Nudge.everyMs * Double(Motion.Nudge.max)
            if idle > Motion.Nudge.afterMs, idle < nudgeWindow,
               now - lastNudge > Motion.Nudge.everyMs {
                nudge()
            }
            return
        }
        if !playing {
            // Owner override to motion.ts IDLE_RESUME_MS=3500: 2 s after the last real touch,
            // ease every remaining sheet back to the title before restarting the complete loop.
            if mode != .idle || now - lastTouch < 2000 { return }
            if cur > 0 {
                returnToRest()
                return
            }
            playing = true
            scriptAt = -1
            nudgeDone = false
            clock = 0
            lastLoopIndex = 0
            return
        }
        let loopIndex = Int(clock / Motion.loopMs)
        if loopIndex != lastLoopIndex {
            // The RN nudge begins at 12.2 s. Its energy-threshold spring can outlive 14 s by a
            // few frames, so normalize the virtually flat paper at the cut for a seamless video.
            // Keep all RN script event times and spring settings intact.
            lastLoopIndex = loopIndex
            normalizeLoopSeam()
        }
        let t = clock.truncatingRemainder(dividingBy: Motion.loopMs)
        if t < 100 { nudgeDone = false }
        if t >= Motion.nudgeAtMs, !nudgeDone {
            nudgeDone = true
            nudge()
        }
        var active = -1
        for (index, stroke) in Motion.script.enumerated() where t >= stroke.t0 && t <= stroke.t1 {
            active = index
        }
        let previous = scriptAt
        if previous >= 0, previous != active {
            // The stroke ended: lift with the path's speed at its end.
            let stroke = Motion.script[previous]
            let flick = stroke.t1 - stroke.t0 < 700
            let a = Math.strokePoint(from: stroke.from, via: stroke.via, to: stroke.to, u: 0.98, flick: flick)
            let b = Math.strokePoint(from: stroke.from, via: stroke.via, to: stroke.to, u: 1, flick: flick)
            let span = (0.02 * (stroke.t1 - stroke.t0)) / 1000
            move(x: b.x * s, y: b.y * s)
            up(
                x: b.x * s, y: b.y * s,
                vx: ((b.x - a.x) * s / span) * Motion.qaSpeed, vy: ((b.y - a.y) * s / span) * Motion.qaSpeed
            )
            scripted = false
        }
        if active >= 0 {
            let stroke = Motion.script[active]
            let u = (t - stroke.t0) / (stroke.t1 - stroke.t0)
            let point = Math.strokePoint(from: stroke.from, via: stroke.via, to: stroke.to, u: u, flick: stroke.t1 - stroke.t0 < 700)
            if previous != active {
                scripted = true
                down(x: point.x * s, y: point.y * s)
            } else {
                move(x: point.x * s, y: point.y * s)
            }
        }
        scriptAt = active
    }

    private func normalizeLoopSeam() {
        if cur != 0 {
            cur = 0
            onLanded?(0)
        }
        for slot in slots {
            slot.tx.set(slot.gx)
            slot.ty.set(slot.gy)
            slot.on = 0
        }
        mode = .idle
        fade.set(-1)
        flex = 0
        flexV = 0
        lastTip = (slot0.gx, slot0.gy)
        twinkle.set(1)
        foilT.set(0)
        hideCountdown = 0
    }

    private func returnToRest() {
        returningToRest = true
        show = 1
        if reduced {
            slot0.front = Double(cur)
            underIdx = 0
            fade.set(0)
            mode = .settle
            fade.animate(.timing(target: 1, durationMs: Motion.fadeMs, easing: PageCurlJournalEasing.out) { [weak self] finished in
                guard finished, let self else { return }
                cur = 0
                mode = .idle
                fade.set(-1)
                landed(page: 0, changed: true, feel: false)
                returningToRest = false
            })
            return
        }
        slot0.front = Double(cur - 1)
        underIdx = Double(cur)
        slot0.gx = width
        slot0.gy = height * Motion.Turn.buttonGrabY
        slot0.tx.set(-width)
        slot0.ty.set(slot0.gy)
        gain = Motion.Turn.backFactor
        startRiffle(vx: Motion.Riffle.launch * s * Motion.qaSpeed, vy: 0)
    }

    // MARK: - What to draw

    /// Whether the curl shader has anything to draw.
    var curlVisible: Bool { ready && slot0.on > 0 }

    /// motion.ts / PageCurlJournal.tsx `makeUniforms`: the uniforms of one sheet.
    /// Only the sheet a finger turns (slot 0) has a page beneath it; the riffle sheets draw over it.
    func curlUniforms(slot index: Int, pixelRatio: Double) -> PageCurlJournalCurlUniforms {
        let slot = slots[index]
        let main = index == 0
        let g0 = slot.gx
        let g1 = slot.gy
        let tip = Math.clampTip(gx: g0, gy: g1, tx: slot.tx.value, ty: slot.ty.value, height: height)
        let d = hypot(g0 - tip.x, g1 - tip.y)
        let ramp = mode == .hold ? 1 : Math.rollRamp(d, full: 2 * g0, rampIn: Motion.Turn.rampIn * s, rampOut: Motion.Turn.rampOut * s)
        // The higher the sheet, the softer and wider its shadow (equal to the fitted values at the source's lift).
        let p = min(1, d / max(1, 2 * g0))
        let curl = Theme.Curl.self
        let lift = Motion.Lift.self
        var u = PageCurlJournalCurlUniforms()
        u.sizeX = Float(width)
        u.sizeY = Float(height)
        u.grabX = Float(g0)
        u.grabY = Float(g1)
        u.tipX = Float(tip.x)
        u.tipY = Float(tip.y)
        u.r0 = Float(curl.radius * s * ramp * (1 + flex))
        u.r1 = Float(curl.radiusSlope)
        u.rmin = Float(curl.radiusMin * s)
        u.frontIdx = Float(slot.front)
        u.underIdx = main ? Float(underIdx) : 0
        u.shA = Float(curl.shadow * (lift.darkBase + lift.darkPerTurn * p))
        u.shW = Float(curl.shadowWidth * s * (lift.widthBase + lift.widthPerTurn * p))
        u.eA = Float(curl.edgeShadow)
        u.eW = Float(curl.edgeShadowWidth * s)
        u.amb1 = Float(curl.frontLight)
        u.amb2 = Float(curl.backLight)
        u.g2 = Float(curl.backGamma)
        u.bg = Float(curl.backPaper)
        u.ba = Float(curl.showThrough)
        u.grain = Float(curl.grain)
        u.edgeInk = Float(curl.edgeInk)
        u.px = Float(pixelRatio)
        u.fade = main ? Float(fade.value) : -1
        u.clearUnder = main ? 0 : 1
        u.spec = Float(curl.sheen)
        return u
    }

    /// Foil on the cover title: the sweep, or (with no sweep running) the phone's tilt. Nil when it has no light.
    func foilUniforms(roll: Double, pixelRatio: Double) -> PageCurlJournalFoilUniforms? {
        let foil = Motion.Foil.self
        let sweeping = foilT.value > 0
        let center = sweeping
            ? (foil.rest + (foil.reach - foil.rest) * foilT.value) * s
            : (150 + roll * foil.tiltGain) * s
        let amount = sweeping ? foil.amount : reduced ? 0 : foil.amount * min(1, abs(roll) * 6)
        if amount <= 0 { return nil }
        let angle = foil.angleDeg * .pi / 180
        var u = PageCurlJournalFoilUniforms()
        u.dirX = Float(cos(angle))
        u.dirY = Float(sin(angle))
        u.center = Float(center)
        u.width = Float(foil.width * s)
        u.amount = Float(amount)
        u.rectX = Float(40 * s)
        u.rectY = Float(120 * s)
        u.rectWidth = Float(322 * s)
        u.rectHeight = Float(180 * s)
        u.sizeX = Float(width)
        u.sizeY = Float(height)
        u.px = Float(pixelRatio)
        return u
    }
}
