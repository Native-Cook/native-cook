import Foundation

/// Where the tray sits on screen and how it is turned.
struct GlassMarblesTrayPose {
    var cx: Double
    var cy: Double
    var tiltX: Double
    var tiltY: Double

    // Perspective: the eye is `focal` pt above the tray.
    static let focal = 900.0
    // Light comes from the upper left, in front of the tray.
    static let light = (-0.45, -0.62, 0.64)

    /// A point of the tray's frame (u, v, w; w up out of the floor) turned by the tilt, about its centre.
    func turn(_ u: Double, _ v: Double, _ w: Double) -> (x: Double, y: Double, z: Double) {
        let ca = cos(tiltX)
        let sa = sin(tiltX)
        let cb = cos(tiltY)
        let sb = sin(tiltY)
        let y1 = v * ca - w * sa
        let z1 = v * sa + w * ca
        return (x: u * cb + z1 * sb, y: y1, z: -u * sb + z1 * cb)
    }

    /// A point of the tray's frame on screen, and its perspective scale.
    func project(_ u: Double, _ v: Double, _ w: Double) -> (x: Double, y: Double, s: Double) {
        let t = turn(u, v, w)
        let s = GlassMarblesTrayPose.focal / (GlassMarblesTrayPose.focal - t.z)
        return (x: cx + t.x * s, y: cy + t.y * s, s: s)
    }

    /// The homography that draws the tray's floor plane (w = 0) on screen, row-major as Skia's
    /// matrix: x' = (m0 u + m1 v + m2) / (m6 u + m7 v + m8).
    func floorMatrix() -> [Double] {
        let ca = cos(tiltX)
        let sa = sin(tiltX)
        let cb = cos(tiltY)
        let sb = sin(tiltY)
        let f = GlassMarblesTrayPose.focal
        return [
            cb + (cx * sb) / f, sa * sb - (cx * sa * cb) / f, cx,
            (cy * sb) / f, ca - (cy * sa * cb) / f, cy,
            sb / f, -(sa * cb) / f, 1,
        ]
    }

    /// The light in the tray's own frame: it moves across the floor as the tray tilts.
    func trayLight() -> (x: Double, y: Double, z: Double) {
        let l = GlassMarblesTrayPose.light
        let cb = cos(tiltY)
        let sb = sin(tiltY)
        let ca = cos(tiltX)
        let sa = sin(tiltX)
        let x1 = l.0 * cb - l.2 * sb
        let z1 = l.0 * sb + l.2 * cb
        let y1 = l.1
        return (x: x1, y: y1 * ca + z1 * sa, z: -y1 * sa + z1 * ca)
    }
}

/// The tray, its marbles, the autoPlay script and the touch state, stepped like the React Native
/// frame callback: whole ticks of 1/60 s, at most three a frame. Foundation only, so it also runs
/// in a command-line parity check against the React Native code.
final class GlassMarblesTrayEngine {
    private typealias T = GlassMarblesTrayTuning

    // The set: glass tint, the two colours of the swirl, and how many vanes it has. The seventh is
    // the larger shooter.
    static let marbles: [GlassMarblesTrayMarble] = [
        .init(r: 14, glass: (150, 190, 255), vane: (28, 66, 196), vane2: (120, 200, 255), vanes: 3),
        .init(r: 14, glass: (255, 204, 128), vane: (214, 86, 20), vane2: (255, 222, 150), vanes: 3),
        .init(r: 14, glass: (140, 228, 170), vane: (248, 248, 236), vane2: (36, 146, 88), vanes: 3),
        .init(r: 14, glass: (255, 226, 214), vane: (138, 22, 34), vane2: (240, 148, 118), vanes: 4),
        .init(r: 14, glass: (228, 240, 246), vane: (255, 255, 255), vane2: (186, 208, 230), vanes: 3),
        .init(r: 14, glass: (212, 182, 255), vane: (106, 48, 188), vane2: (232, 204, 255), vanes: 3),
        .init(r: 17.5, glass: (242, 240, 232), vane: (214, 38, 40), vane2: (248, 196, 40), vanes: 4),
        .init(r: 14, glass: (150, 238, 232), vane: (0, 136, 148), vane2: (255, 255, 255), vanes: 3),
        .init(r: 14, glass: (204, 204, 210), vane: (28, 28, 34), vane2: (160, 160, 172), vanes: 3),
        .init(r: 14, glass: (255, 226, 150), vane: (158, 88, 28), vane2: (255, 244, 200), vanes: 3),
        .init(r: 14, glass: (255, 202, 216), vane: (218, 58, 108), vane2: (255, 230, 240), vanes: 3),
        .init(r: 14, glass: (214, 248, 160), vane: (88, 158, 30), vane2: (244, 255, 220), vanes: 3),
        .init(r: 14, glass: (172, 176, 255), vane: (30, 30, 118), vane2: (255, 190, 88), vanes: 3),
    ]

    // The tray (pt): outer size limits, the frame's width and the depth of its sides.
    static let trayMaxW = 372.0
    static let trayRatio = 1.34
    static let frame = 24.0
    static let trayDepth = 16.0
    static let outerCorner = 28.0
    // Below this width (pt) the tray has no room for its cups and is not built.
    static let minTrayW = 150.0
    // Tilt: at most maxTilt (rad) when dragged; a drag of dragScale pt gets about three quarters
    // of the way (tanh), and the tray slides with the finger by slideShare, up to slideMax pt.
    static let maxTilt = 0.24
    static let dragScale = 150.0
    static let slideShare = 0.07
    static let slideMax = 16.0
    // A shake: the finger reverses shakeReversals times within shakeWindow s, each faster than
    // shakeSpeed pt/s.
    static let shakeSpeed = 1000.0
    static let shakeWindow = 0.5
    static let shakeReversals = 2
    static let shakeCooldown = 0.7
    // A tap within this reach (pt) of a marble flicks it away from the finger at flickSpeed pt/s.
    static let tapReach = 12.0
    static let flickSpeed = 620.0
    // After a touch, autoPlay waits this long (s).
    static let userHold = 3.5
    // Haptic clicks, at most one every clickGap s.
    static let clickGap = 0.09
    // The last restBlend s of a loop ease every marble onto the state the loop started from.
    static let restBlend = 1.1
    // A pan starts after this much movement (pt); a tap is shorter than this (s).
    static let panSlop = 4.0
    static let tapTime = 0.5

    // Haptic requests, read and cleared by the view.
    static let eventClick = 1
    static let eventShake = 2
    static let eventFlick = 4
    var events = 0
    /// The hardest click behind the last `eventClick` (pt/s).
    var clickHardness = 0.0

    let reduced: Bool
    private var looping: Bool

    private(set) var width = 0.0
    private(set) var height = 0.0
    private(set) var insetTop = 0.0
    private(set) var insetBottom = 0.0
    private(set) var trayW = 0.0
    private(set) var trayH = 0.0
    private(set) var cx = 0.0
    private(set) var cy = 0.0
    private(set) var tray: GlassMarblesTrayTray?

    private(set) var world: GlassMarblesTrayWorld?
    private var rest: GlassMarblesTrayWorld?
    /// Where each marble was last drawn on screen, for taps.
    private(set) var sx: [Double] = []
    private(set) var sy: [Double] = []
    private(set) var sr: [Double] = []
    /// Seconds since the last click haptic.
    private var sinceClick = 1.0

    private var acc = 0.0
    // What the finger asks of the tray.
    private var dragging = false
    private var wantTiltX = 0.0
    private var wantTiltY = 0.0
    private var wantSlideX = 0.0
    private var wantSlideY = 0.0
    // Shake detection: the last strong direction, when it was seen, and reversals so far.
    private var lastDirX = 0.0
    private var lastDirY = 0.0
    private var lastStrongAt = -10.0
    private var reversals = 0
    private var lastShakeAt = -10.0
    private(set) var now = 0.0
    // Requests carried out on the next tick.
    private var shakeNow = false
    private var flickWho = -1
    private var flickDX = 0.0
    private var flickDY = 0.0
    private(set) var clock = 0.0
    private(set) var hold = 0.0

    // Raw touch state: where it went down, when, and whether the pan has started.
    private var downX = 0.0
    private var downY = 0.0
    private var downAt = 0.0
    private var panning = false

    /// `auto` is passed in here so the very first frame is already the script's first frame.
    init(auto: Bool, reduced: Bool) {
        looping = auto
        self.reduced = reduced
    }

    func setAuto(_ auto: Bool) {
        guard auto != looping else { return }
        looping = auto
        hold = 0
        clock = 0
    }

    /// A new size makes a new tray and starts the marbles over.
    func setLayout(width: Double, height: Double, insetTop: Double, insetBottom: Double) {
        if width == self.width && height == self.height && insetTop == self.insetTop && insetBottom == self.insetBottom { return }
        self.width = width
        self.height = height
        self.insetTop = insetTop
        self.insetBottom = insetBottom
        // The tray: as wide as fits, centred between the header and the caption.
        let top = insetTop + 70
        let bottom = height - insetBottom - 118
        trayW = max(0, min(GlassMarblesTrayEngine.trayMaxW, width - 30))
        trayH = min(trayW * GlassMarblesTrayEngine.trayRatio, max(200, bottom - top))
        cx = width / 2
        cy = (top + bottom) / 2
        let f = GlassMarblesTrayEngine.frame
        // No tray until there is room for its cups (a transient tiny layout would give it none).
        tray = width > 0 && trayW >= GlassMarblesTrayEngine.minTrayW ? GlassMarblesTrayTray(halfW: trayW / 2 - f, halfH: trayH / 2 - f, marbles: GlassMarblesTrayEngine.marbles.count) : nil
        world = nil
    }

    var pose: GlassMarblesTrayPose? {
        guard let w = world else { return nil }
        return GlassMarblesTrayPose(cx: cx + w.slideX, cy: cy + w.slideY, tiltX: w.tiltX, tiltY: w.tiltY)
    }

    private func tick(_ w: GlassMarblesTrayWorld, _ rest: GlassMarblesTrayWorld, _ tray: GlassMarblesTrayTray) {
        now += T.tick
        sinceClick += T.tick
        if shakeNow {
            shakeNow = false
            w.scatter(reduced ? 0.35 : 1)
        }
        let who = flickWho
        if who >= 0 {
            flickWho = -1
            w.flick(who, flickDX, flickDY, reduced ? GlassMarblesTrayEngine.flickSpeed * 0.5 : GlassMarblesTrayEngine.flickSpeed)
        }
        var input = GlassMarblesTrayInput()
        if dragging {
            input.tiltX = wantTiltX
            input.tiltY = wantTiltY
            input.slideX = wantSlideX
            input.slideY = wantSlideY
        }
        if hold > 0 {
            if !dragging { hold -= T.tick }
            if hold <= 0 { clock = 0 }
        } else if looping {
            let before = clock
            var t = before + T.tick
            if t >= T.loop {
                t -= T.loop
                // Home again: the loop starts over from exactly where it began.
                w.copy(from: rest)
            }
            clock = t
            let pose = GlassMarblesTrayWorld.scriptPose(t)
            input.tiltX = pose.tiltX
            input.tiltY = pose.tiltY
            input.slideX = pose.slideX
            input.guide = pose.guide
            if before < T.shakeAt && t >= T.shakeAt { w.scatter(1) }
            if t > T.loop - GlassMarblesTrayEngine.restBlend {
                let k = (t - (T.loop - GlassMarblesTrayEngine.restBlend)) / GlassMarblesTrayEngine.restBlend
                w.blendPositions(towards: rest, k * k * 0.25)
                w.blendRotations(towards: rest, k * k * 0.12)
            }
        }
        w.step(tray, input, T.tick)
        if w.clicks > 0 && hold > 0 && sinceClick > GlassMarblesTrayEngine.clickGap {
            sinceClick = 0
            events |= GlassMarblesTrayEngine.eventClick
            clickHardness = w.hardest
        }
    }

    /// One display frame of `elapsed` seconds (nil on the first frame). Returns whether it ticked.
    @discardableResult
    func frame(_ elapsed: Double?) -> Bool {
        guard width > 0, let tray else { return false }
        if world == nil {
            world = GlassMarblesTrayWorld(tray: tray, set: GlassMarblesTrayEngine.marbles)
            rest = GlassMarblesTrayWorld(tray: tray, set: GlassMarblesTrayEngine.marbles)
            let n = GlassMarblesTrayEngine.marbles.count
            sx = Array(repeating: 0, count: n)
            sy = Array(repeating: 0, count: n)
            sr = Array(repeating: 0, count: n)
            sinceClick = 1
            clock = 0
        }
        guard let w = world, let rest else { return false }
        let dt = min(elapsed ?? 0.016, Double(T.maxTicks) * T.tick)
        var pending = acc + dt
        var ticks = 0
        while pending >= T.tick && ticks < T.maxTicks {
            tick(w, rest, tray)
            pending -= T.tick
            ticks += 1
        }
        acc = min(pending, T.tick)
        placeMarbles()
        return ticks > 0
    }

    /// Where each marble shows on screen, as the React Native picture records it for taps.
    func placeMarbles() {
        guard let w = world, let pose else { return }
        for i in 0..<w.n {
            let p = pose.project(w.x[i], w.y[i], w.r[i] + w.z[i])
            sx[i] = p.x
            sy[i] = p.y
            sr[i] = w.r[i] * p.s
        }
    }

    private func touched() {
        hold = GlassMarblesTrayEngine.userHold
    }

    // The gestures, as React Native's Gesture Handler reports them.

    func panStart() {
        touched()
        dragging = true
        reversals = 0
    }

    func panUpdate(translationX: Double, translationY: Double, velocityX: Double, velocityY: Double) {
        let limit = reduced ? GlassMarblesTrayEngine.maxTilt * 0.45 : GlassMarblesTrayEngine.maxTilt
        // Pressing the tray down where the finger goes: that side dips away and the marbles follow.
        wantTiltY = limit * tanh(translationX / GlassMarblesTrayEngine.dragScale)
        wantTiltX = -limit * tanh(translationY / GlassMarblesTrayEngine.dragScale)
        let slide = reduced ? 0 : GlassMarblesTrayEngine.slideMax
        wantSlideX = slide * tanh((translationX * GlassMarblesTrayEngine.slideShare) / GlassMarblesTrayEngine.slideMax)
        wantSlideY = slide * tanh((translationY * GlassMarblesTrayEngine.slideShare) / GlassMarblesTrayEngine.slideMax)
        // A shake: strong strokes that keep reversing.
        let speed = hypot(velocityX, velocityY)
        if speed < GlassMarblesTrayEngine.shakeSpeed { return }
        let dx = velocityX / speed
        let dy = velocityY / speed
        let t = now
        if dx * lastDirX + dy * lastDirY < -0.5 && t - lastStrongAt < GlassMarblesTrayEngine.shakeWindow {
            reversals += 1
            if reversals >= GlassMarblesTrayEngine.shakeReversals && t - lastShakeAt > GlassMarblesTrayEngine.shakeCooldown {
                reversals = 0
                lastShakeAt = t
                shakeNow = true
                events |= GlassMarblesTrayEngine.eventShake
            }
        } else if t - lastStrongAt >= GlassMarblesTrayEngine.shakeWindow {
            reversals = 0
        }
        lastDirX = dx
        lastDirY = dy
        lastStrongAt = t
    }

    func panFinalize() {
        dragging = false
        hold = GlassMarblesTrayEngine.userHold
    }

    func tap(x: Double, y: Double) {
        guard world != nil else { return }
        var best = -1
        var bestD = Double.infinity
        for i in 0..<sx.count {
            let d = hypot(x - sx[i], y - sy[i])
            if d < sr[i] + GlassMarblesTrayEngine.tapReach && d < bestD {
                bestD = d
                best = i
            }
        }
        if best < 0 { return }
        touched()
        // Away from the finger; a tap dead centre sends it up the tray.
        var dx = sx[best] - x
        var dy = sy[best] - y
        if hypot(dx, dy) < sr[best] * 0.3 {
            dx = 0
            dy = -1
        }
        flickDX = dx
        flickDY = dy
        flickWho = best
        events |= GlassMarblesTrayEngine.eventFlick
    }

    // Raw touches from the view: a pan once the finger has moved panSlop, otherwise a tap if it lifts
    // within tapTime. `time` is in seconds on any steady clock; velocities come from the view.

    func touchDown(x: Double, y: Double, time: Double) {
        downX = x
        downY = y
        downAt = time
        panning = false
    }

    func touchMove(x: Double, y: Double, time: Double, velocityX: Double, velocityY: Double) {
        let tx = x - downX
        let ty = y - downY
        if !panning && tx * tx + ty * ty >= GlassMarblesTrayEngine.panSlop * GlassMarblesTrayEngine.panSlop {
            panning = true
            panStart()
        }
        if panning { panUpdate(translationX: tx, translationY: ty, velocityX: velocityX, velocityY: velocityY) }
    }

    func touchUp(x: Double, y: Double, time: Double) {
        if !panning && time - downAt <= GlassMarblesTrayEngine.tapTime { tap(x: x, y: y) }
        panning = false
        panFinalize()
    }

    /// The touch was taken away (the system claimed it): the tray lets go, no tap.
    func touchCancel() {
        if !panning { return }
        panning = false
        panFinalize()
    }
}
