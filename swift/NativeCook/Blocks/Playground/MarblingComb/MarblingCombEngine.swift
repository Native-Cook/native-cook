import Foundation

/// The numbers of the block, as in the React Native version.
enum MarblingCombTuning {
    // The tick every step of the paint runs on, s, and at most this many per frame (a 1/30 s cap).
    static let tick = 1.0 / 60
    static let maxTicks = 2
    // A tool moves through the paint in steps no longer than this, pt.
    static let substep = 6.0

    // A tapped drop spreads to dropRadius pt over dropTime s (dropTimeReduced with reduced motion).
    static let dropRadius = 46.0
    static let dropTime = 0.9
    static let dropTimeReduced = 0.22
    // Holding a finger still drips a drop every dripEvery s, cycling the paints, for rings.
    static let dripEvery = 0.34
    static let dripRadius = 30.0

    // The stylus: one tine. The comb: tines combSpacing pt apart across the whole tray.
    static let stylus = MarblingCombTool(spacing: 0, side: 0, axisX: 1, axisY: 0, sharpness: 10, gain: 0.85, wake: 60)
    static let combSpacing = 30.0
    static let combSharpness = 6.5
    static let combGain = 0.78
    static let combWake = 44.0

    // The print: the sheet comes down over lay s, soaks for soak s, then peels from its lower right
    // corner over peel s, pausing about two thirds of the way to show the print on its back.
    static let lay = 0.8
    static let soak = 0.5
    static let peel = 2.7
    static let printTime = lay + soak + peel
    // Sheet margins, pt from the screen (top and bottom come from the safe area and the controls).
    static let sheetSide = 18.0
    static let sheetTop = 64.0
    static let sheetBottom = 148.0
    // Paint seen through the wet sheet before it is lifted.
    static let soakGhost = 0.2
    // Paint left on the size after a print, fading over residueFade s.
    static let residue = 0.14
    static let residueFade = 0.7

    // A touch pauses autoPlay this long, s.
    static let userHold = 4.0

    // What happened during a frame, for haptics and the controls.
    static let eventDropped = 1
    static let eventStroked = 2
    static let eventPressed = 4
    static let eventPrintStart = 8
    static let eventPrintDone = 16
}

/// The frame loop of the React Native block (its `useFrameCallback`, script and gestures), step for
/// step: the paint, the tool, the drip, the print and the autoPlay clock on one fixed tick.
final class MarblingCombEngine {
    private typealias T = MarblingCombTuning

    let reduced: Bool
    /// The number of colours in the palette (the ground and the paints).
    let paints: Int
    private(set) var marble: MarblingCombMarble?
    private(set) var elapsed = 0.0
    private var acc = 0.0

    /// The tool picked in the switch (0 stylus, 1 comb) and the paint picked in the row.
    var selectedTool = 0 {
        didSet { toolKind = selectedTool }
    }
    var ink = 2

    // The tool in the paint: where it was last applied, where the finger (or the script) has it.
    private(set) var toolKind = 0
    private(set) var toolOn = false
    private(set) var toolX = 0.0
    private(set) var toolY = 0.0
    private var appliedX = 0.0
    private var appliedY = 0.0
    private(set) var toolAlpha = 0.0
    // The comb's axis and its tines: set at the start of each stroke.
    private(set) var combAX = 1.0
    private(set) var combAY = 0.0
    private(set) var combSpacing = T.combSpacing
    private(set) var combSide = 8.0
    private var combSharp = T.combSharpness
    private var combGain = T.combGain
    private var combWake = T.combWake
    private var scripted = false

    private var dripOn = false
    private var dripX = 0.0
    private var dripY = 0.0
    private var dripTime = 0.0
    private var dripColor = 2

    /// The print: < 0 when no sheet is down, else seconds since it started.
    private(set) var printT = -1.0
    private(set) var residue = 0.0

    private var clock = 0.0
    private var hold = 0.0
    private var looping: Bool

    /// What happened since the events were last read (`MarblingCombTuning.event…` bits).
    var events = 0
    /// Called the moment the sheet lies flat, before the tray is cleared: take the paint up now.
    var onTakePrint: (() -> Void)?

    init(auto: Bool, reduced: Bool, paints: Int) {
        looping = auto
        self.reduced = reduced
        self.paints = paints
    }

    /// Restarts the autoPlay clock (autoPlay switched on or off).
    func setAuto(_ auto: Bool) {
        looping = auto
        clock = 0
        hold = 0
    }

    /// Lays the sheet down: the print runs from here on the shared tick.
    private func startPrint() {
        if printT >= 0 { return }
        printT = 0
        toolOn = false
        dripOn = false
        events |= T.eventPrintStart
    }

    /// Moves the tool to (x, y) through the paint in short steps.
    private func applyTool(_ m: MarblingCombMarble, _ x: Double, _ y: Double) {
        let fx = appliedX
        let fy = appliedY
        let len = hypot(x - fx, y - fy)
        if !(len >= 0.05) || !len.isFinite { return }
        let steps = min(400, max(1, Int((len / T.substep).rounded(.up))))
        let comb = toolKind == 1
        let t = comb
            ? MarblingCombTool(spacing: combSpacing, side: combSide, axisX: combAX, axisY: combAY, sharpness: combSharp, gain: combGain, wake: combWake)
            : T.stylus
        var px = fx
        var py = fy
        for i in 1...steps {
            let nx = fx + (x - fx) * Double(i) / Double(steps)
            let ny = fy + (y - fy) * Double(i) / Double(steps)
            m.moveTool(t, px, py, nx, ny)
            px = nx
            py = ny
        }
        appliedX = x
        appliedY = y
    }

    /// One step of the autoPlay script, from `before` to `t` seconds into the loop.
    private func script(_ m: MarblingCombMarble, _ before: Double, _ t: Double) {
        let w = m.width
        let h = m.height
        for d in MarblingCombScript.drops where MarblingCombScript.crossed(before, t, d.at) {
            m.drop(x: d.x * w, y: d.y * h, radius: d.r * w, color: d.color, time: T.dropTime)
        }
        var active = -1
        for (k, p) in MarblingCombScript.passes.enumerated() where t >= p.from && t <= p.to {
            active = k
        }
        if active >= 0 {
            let p = MarblingCombScript.passes[active]
            let e = MarblingCombScript.easeInOut((t - p.from) / (p.to - p.from))
            let x = (p.x0 + (p.x1 - p.x0) * e) * w
            let y = (p.y0 + (p.y1 - p.y0) * e) * h
            if !toolOn || !scripted {
                scripted = true
                toolOn = true
                toolKind = 1
                combAX = p.horizontal ? 1 : 0
                combAY = p.horizontal ? 0 : 1
                combSpacing = p.spacing * w
                combSide = ((p.horizontal ? w : h) / (p.spacing * w)).rounded(.up) + 1
                combSharp = p.sharpness
                combGain = p.gain
                combWake = p.wake
                appliedX = x
                appliedY = y
            }
            toolX = x
            toolY = y
        } else if scripted {
            scripted = false
            toolOn = false
            toolKind = selectedTool
        }
        if MarblingCombScript.crossed(before, t, MarblingCombScript.printAt) { startPrint() }
    }

    /// One frame `frameDt` s after the last one, on a tray `width` × `height` pt.
    func frame(_ frameDt: Double, width: Double, height: Double) {
        if width == 0 { return }
        let m: MarblingCombMarble
        if let existing = marble {
            m = existing
        } else {
            m = MarblingCombMarble(width: width, height: height, ground: 0)
            marble = m
        }
        // The tray follows the layout (a rotation or a resize keeps the paint where it is).
        m.width = width
        m.height = height
        let dt = min(frameDt, Double(T.maxTicks) * T.tick)
        var pending = acc + dt
        var ticks = 0
        while pending >= T.tick && ticks < T.maxTicks {
            pending -= T.tick
            ticks += 1
            elapsed += T.tick

            if hold > 0 {
                hold -= T.tick
                if hold <= 0 { clock = 0 }
            } else if looping {
                let before = clock
                let t = (before + T.tick).truncatingRemainder(dividingBy: MarblingCombScript.loop)
                clock = t
                script(m, before, t)
            }

            if toolOn { applyTool(m, toolX, toolY) }

            if dripOn {
                dripTime += T.tick
                if dripTime >= T.dripEvery {
                    dripTime -= T.dripEvery
                    let c = dripColor
                    m.drop(x: dripX, y: dripY, radius: T.dripRadius, color: c, time: reduced ? T.dropTimeReduced : T.dropTime * 0.8)
                    dripColor = (c % max(1, paints - 1)) + 1
                    events |= T.eventDropped
                }
            }

            m.step(T.tick)

            // The print: the sheet takes up the paint the moment it lies flat.
            let p = printT
            if p >= 0 {
                let next = p + T.tick
                if p < T.lay && next >= T.lay {
                    onTakePrint?()
                    m.clear(ground: 0)
                    residue = 1
                    events |= T.eventPressed
                }
                if next >= T.printTime {
                    printT = -1
                    events |= T.eventPrintDone
                } else {
                    printT = next
                }
            }
            if residue > 0 && p < 0 { residue = max(0, residue - T.tick / T.residueFade) }
        }
        acc = min(pending, T.tick)
        toolAlpha += ((toolOn ? 1 : 0) - toolAlpha) * min(1, dt * 14)
    }

    // MARK: Gestures

    private func touched() {
        hold = T.userHold
        if scripted {
            scripted = false
            toolOn = false
        }
    }

    /// A drag began at (x0, y0) and is now at (x, y).
    func panStart(x0: Double, y0: Double, x: Double, y: Double) {
        guard let m = marble, printT < 0 else { return }
        touched()
        toolKind = selectedTool
        // The comb lies across the stroke: drawn sideways, its axis runs up and down.
        let sideways = abs(x - x0) > abs(y - y0)
        combAX = sideways ? 0 : 1
        combAY = sideways ? 1 : 0
        combSpacing = T.combSpacing
        combSide = (max(m.width, m.height) / T.combSpacing).rounded(.up) + 1
        combSharp = T.combSharpness
        combGain = T.combGain
        combWake = T.combWake
        appliedX = x0
        appliedY = y0
        toolX = x
        toolY = y
        toolOn = true
        events |= T.eventStroked
    }

    func panMove(x: Double, y: Double) {
        if !toolOn || scripted { return }
        toolX = x
        toolY = y
    }

    func panEnd() {
        if !scripted { toolOn = false }
    }

    func tap(x: Double, y: Double) {
        guard let m = marble, printT < 0 else { return }
        touched()
        m.drop(x: x, y: y, radius: T.dropRadius, color: ink, time: reduced ? T.dropTimeReduced : T.dropTime)
        events |= T.eventDropped
    }

    func dripStart(x: Double, y: Double) {
        if printT >= 0 { return }
        touched()
        dripX = x
        dripY = y
        dripColor = ink
        // The first drop lands now; the rest follow every dripEvery s.
        dripTime = T.dripEvery
        dripOn = true
    }

    func dripEnd() {
        dripOn = false
    }

    /// The Print button.
    func printButton() {
        hold = T.userHold + T.printTime
        scripted = false
        startPrint()
    }
}
