import SwiftUI
import UIKit

/*
 Abacus Expense Counter — "Tally", a weekly budget logged on a wooden abacus. Each rod is a
 category, each lacquered bead one purchase at that category's price; flick a bead to the right
 to log it. Beads slide with momentum and friction, knock into their neighbours and bounce off
 the frame with a light click; the rod's amount, the week's total, what is left and the daily
 pace roll to the new numbers, and each category's meter springs.

 One Canvas (AbacusExpenseCounterArt: the board and one bead per colour rendered once; 100
 translated images per frame). Beads: AbacusExpenseCounterMotion, analytic in autoPlay
 (loop = 8 s), a small physics step under the finger. Amounts are digit wheels whose inputs are
 whole cents (AbacusExpenseCounterRolling), so they change only when a bead changes sides.

 Modes: 0 loop · 1 touch physics · 2 gliding back to rest (0.9 s ease-in-out: "Undo today", or
 2 s after the last touch once the beads have settled), then the loop restarts.
 */
struct AbacusExpenseCounter: View {
    /// Plays the 8 s demo: six flicks across three rods, a hold on the totals, then "Undo today".
    var autoPlay = false

    static let loop = AbacusExpenseCounterMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = AbacusExpenseCounterEngine()
    /// Keeps the timeline running while a touch's beads are still moving (outside autoPlay).
    @State private var active = false

    fileprivate static let background = Color(hex: 0x16110D)
    fileprivate static let ink = Color(hex: 0xF4ECE0)
    fileprivate static let inkSoft = Color(hex: 0xAD9E8E)
    fileprivate static let accent = Color(hex: 0xD9A441)
    private static let headerH = 128.0
    private static let tilesH = 70.0
    /// Room kept under the abacus: the two tiles, the week strip and the hint.
    private static let footerH = 20 + tilesH + 12 + AbacusExpenseCounterWeek.height + 44

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Self.background.ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let bottom = max(insets.bottom, 12)
        let g = AbacusExpenseCounterGeo(w: size.width, h: size.height, boardY: insets.top + 12 + Self.headerH, reserveBelow: Self.footerH + bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let art = engine.art(for: g, scale: displayScale)
        return TimelineView(.animation(paused: !auto && !active)) { timeline in
            let f = engine.step(to: timeline.date, g: g, auto: auto, reduced: reduced)
            ZStack(alignment: .topLeading) {
                Canvas { c, _ in
                    AbacusExpenseCounterArt.draw(c, g, art, f.x, f.finger)
                }
                .frame(width: size.width, height: size.height)
                .allowsHitTesting(false)

                header(f, reduced: reduced)
                    .frame(width: size.width - 40, height: Self.headerH, alignment: .top)
                    .offset(x: 20, y: insets.top + 12)

                Color.clear
                    .frame(width: g.boardW, height: g.boardH)
                    .contentShape(Rectangle())
                    .offset(x: g.boardX, y: g.boardY)
                    .gesture(drag(g: g, reduced: reduced))
                    .accessibilityElement()
                    .accessibilityAddTraits(.allowsDirectInteraction)
                    .accessibilityLabel("Abacus. Flick a bead right to log a purchase, push it back to remove it.")

                ForEach(0..<AbacusExpenseCounterMotion.rods, id: \.self) { r in
                    rodLabel(r, cents: f.rodCents[r], reduced: reduced)
                        .frame(width: g.innerR - g.innerL, height: 16)
                        .offset(x: g.innerL, y: g.boardY + g.frame + Double(r) * g.band + 4)
                        .allowsHitTesting(false)
                }

                tiles(total: f.total, reduced: reduced)
                    .frame(width: size.width - 32, height: Self.tilesH)
                    .offset(x: 16, y: g.boardY + g.boardH + 20)
                    .allowsHitTesting(false)

                let weekY = g.boardY + g.boardH + 20 + Self.tilesH + 12
                if weekY + AbacusExpenseCounterWeek.height <= size.height - bottom - 34 {
                    AbacusExpenseCounterWeek(total: f.total, base: AbacusExpenseCounterEngine.restTotal, color: Self.accent, reduced: reduced)
                        .frame(width: size.width - 32)
                        .offset(x: 16, y: weekY)
                        .allowsHitTesting(false)
                }

                Text("Flick a bead right to log it · push it back to remove it")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Self.ink.opacity(0.45))
                    .multilineTextAlignment(.center)
                    .frame(width: size.width - 48, height: size.height - bottom - 10, alignment: .bottom)
                    .offset(x: 24)
                    .allowsHitTesting(false)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .coordinateSpace(name: "abacus")
            .onChange(of: f.idle) { _, idle in
                if idle { active = false }
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func header(_ f: AbacusExpenseCounterFrame, reduced: Bool) -> some View {
        let total = f.total
        return VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text("TALLY · WEEK 43")
                    .font(.system(size: 12, weight: .heavy))
                    .kerning(2)
                    .foregroundStyle(Self.accent)
                Spacer(minLength: 0)
                Button {
                    active = true
                    engine.undo()
                    AbacusExpenseCounterEngine.click()
                } label: {
                    Text("Undo today")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color(hex: 0xF1D79E))
                        .padding(.horizontal, 14)
                        .frame(height: 30)
                        .background(Capsule().fill(Color(hex: 0xD9A441, opacity: 0.14 + 0.16 * f.undoPress)))
                        .overlay(Capsule().strokeBorder(Color(hex: 0xD9A441, opacity: 0.45), lineWidth: 0.5))
                        .scaleEffect(1 - 0.05 * f.undoPress)
                        .contentShape(Capsule().inset(by: -8))
                }
                .buttonStyle(AbacusExpenseCounterChipStyle())
            }
            .frame(height: 30)
            Text("Weekly spending")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Self.inkSoft)
                .padding(.top, 2)
            AbacusExpenseCounterAmount(cents: total, initial: AbacusExpenseCounterEngine.restTotal, size: 44, color: Self.ink, reduced: reduced)
            HStack(spacing: 12) {
                AbacusExpenseCounterMeter(value: Double(total) / Double(AbacusExpenseCounterMotion.weekBudget), color: Self.accent, height: 6, reduced: reduced)
                Text("of $520 · Oct 20 – 26")
                    .font(.system(size: 13, weight: .medium).monospacedDigit())
                    .foregroundStyle(Self.inkSoft)
                    .fixedSize()
            }
            .padding(.top, 4)
        }
    }

    private func rodLabel(_ r: Int, cents: Int, reduced: Bool) -> some View {
        let color = Color(hex: AbacusExpenseCounterArt.beadColors[r])
        return HStack(spacing: 8) {
            Circle().fill(color).frame(width: 7, height: 7)
            Text(AbacusExpenseCounterMotion.categories[r])
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color(hex: 0xE9DCCB))
                .lineLimit(1)
                .frame(width: 58, alignment: .leading)
            Spacer(minLength: 0)
            AbacusExpenseCounterMeter(value: Double(cents) / Double(AbacusExpenseCounterMotion.budgets[r]), color: color, height: 3, reduced: reduced)
                .frame(width: 120)
            AbacusExpenseCounterAmount(cents: cents, initial: AbacusExpenseCounterEngine.restCents[r], size: 13, color: Self.ink, weight: .semibold, reduced: reduced)
        }
    }

    private func tiles(total: Int, reduced: Bool) -> some View {
        let rest = AbacusExpenseCounterEngine.restTotal
        return HStack(spacing: 12) {
            tile("Left this week", AbacusExpenseCounterAmount(cents: max(0, AbacusExpenseCounterMotion.weekBudget - total), initial: AbacusExpenseCounterMotion.weekBudget - rest, size: 20, color: Self.ink, reduced: reduced))
            tile("Daily pace", AbacusExpenseCounterAmount(cents: Int((Double(total) / 7).rounded()), initial: Int((Double(rest) / 7).rounded()), size: 20, color: Self.ink, intDigits: 2, reduced: reduced))
        }
    }

    private func tile(_ label: String, _ amount: AbacusExpenseCounterAmount) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Self.inkSoft)
            amount
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .modifier(AbacusExpenseCounterCard())
    }

    private func drag(g: AbacusExpenseCounterGeo, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("abacus"))
            .onChanged { value in
                active = true
                engine.touch(at: value.location, g: g)
            }
            .onEnded { value in
                engine.release(velocityX: reduced ? 0 : value.velocity.width)
            }
    }
}

private struct AbacusExpenseCounterChipStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.85 : 1)
    }
}

/// What one frame shows.
struct AbacusExpenseCounterFrame {
    var x: [Double]
    var finger: (Double, Double, Double)
    var undoPress: Double
    var rodCents: [Int]
    var total: Int
    /// Nothing moves and no touch is down (lets the timeline pause outside autoPlay).
    var idle: Bool
}

/// The beads, the autoPlay clock and the touch physics, stepped once a frame.
@MainActor
final class AbacusExpenseCounterEngine {
    private typealias M = AbacusExpenseCounterMotion

    static let restCents = (0..<M.rods).map { M.restCounts[$0] * M.prices[$0] }
    static let restTotal = restCents.reduce(0, +)
    private static let returnAfter = 2.0
    private static let returnFor = 0.9

    private var images: AbacusExpenseCounterArt.Images?
    private var imagesKey: (AbacusExpenseCounterGeo, Double)?

    private var now = -1.0
    private var loopStart = 0.0
    private var lastDate: Date?
    private var mode: Int?
    private var x: [Double] = []
    private var v: [Double] = []
    /// (rod, bead, target x) while a bead is held.
    private var grab: (Int, Int, Double)?
    private var grabOffset = 0.0
    private var touching = false
    private var lastTouch = 0.0
    private var lastClick = 0.0
    private var glideStart = 0.0
    private var glideFrom: [Double] = []
    private var pendingUndo = false
    private var last: AbacusExpenseCounterFrame?

    private static let haptic = UIImpactFeedbackGenerator(style: .light)

    static func click() {
        haptic.impactOccurred()
    }

    func art(for g: AbacusExpenseCounterGeo, scale: Double) -> AbacusExpenseCounterArt.Images {
        if let images, let key = imagesKey, key.0 == g, key.1 == scale { return images }
        let made = AbacusExpenseCounterArt.render(g, scale: scale)
        images = made
        imagesKey = (g, scale)
        return made
    }

    func step(to date: Date, g: AbacusExpenseCounterGeo, auto: Bool, reduced: Bool) -> AbacusExpenseCounterFrame {
        let dt: Double
        if let lastDate {
            let d = date.timeIntervalSince(lastDate)
            if d <= 0, let last { return last }
            dt = min(d, 1 / 30)
        } else {
            dt = 1 / 60
        }
        lastDate = date
        let t0 = (now < 0 ? 0 : now) + dt
        if now < 0 { loopStart = t0 }
        now = t0
        if mode == nil { mode = auto ? 0 : 1 }
        if x.count != M.rods * M.beads { x = g.rest() }
        if pendingUndo {
            pendingUndo = false
            lastTouch = t0
            grab = nil
            glideStart = t0
            glideFrom = x
            mode = 2
        }

        var finger = (0.0, 0.0, 0.0)
        var undoPress = 0.0
        var idle = false
        switch mode {
        case 0:
            let t = (t0 - loopStart).truncatingRemainder(dividingBy: M.loop)
            M.loopBeads(g, t, &x)
            finger = M.loopFinger(g, t)
            undoPress = M.loopUndoPress(t)
        case 1:
            if v.count != x.count { v = Array(repeating: 0, count: x.count) }
            let hit = M.stepBeads(g, &x, &v, grab: grab, dt: dt)
            if hit > 0 && t0 - lastClick > 0.1 {
                lastClick = t0
                Self.click()
            }
            let still = grab == nil && !touching && M.settled(g, x, v)
            if auto && still && t0 - lastTouch > Self.returnAfter {
                glideStart = t0
                glideFrom = x
                mode = 2
            }
            idle = !auto && still
        default:
            let k = M.easeInOut(M.seg(t0 - glideStart, 0, reduced ? 0.3 : Self.returnFor))
            let rest = g.rest()
            if glideFrom.count == rest.count {
                for i in rest.indices { x[i] = glideFrom[i] + (rest[i] - glideFrom[i]) * k }
            }
            if k >= 1 {
                v = Array(repeating: 0, count: x.count)
                if auto && t0 - lastTouch > Self.returnAfter {
                    mode = 0
                    loopStart = t0
                } else {
                    mode = 1
                }
            }
        }

        // Amounts: whole cents, so the wheels roll once per bead.
        let counts = M.countBeads(g, x)
        let cents = (0..<M.rods).map { counts[$0] * M.prices[$0] }
        let frame = AbacusExpenseCounterFrame(x: x, finger: finger, undoPress: undoPress, rodCents: cents, total: cents.reduce(0, +), idle: idle)
        last = frame
        return frame
    }

    private func takeOver() {
        if mode != 1 {
            mode = 1
            v = Array(repeating: 0, count: M.rods * M.beads)
        }
    }

    func touch(at p: CGPoint, g: AbacusExpenseCounterGeo) {
        lastTouch = max(now, 0)
        if !touching {
            touching = true
            takeOver()
            var r = 0
            for k in 1..<M.rods where abs(g.rodY[k] - p.y) < abs(g.rodY[r] - p.y) { r = k }
            guard abs(g.rodY[r] - p.y) <= g.band * 0.55, x.count == M.rods * M.beads else { return }
            var best = -1
            var bestD = g.bw * 0.85
            for j in 0..<M.beads {
                let d = abs(x[r * M.beads + j] - p.x)
                if d < bestD {
                    bestD = d
                    best = j
                }
            }
            guard best >= 0 else { return }
            grabOffset = p.x - x[r * M.beads + best]
            grab = (r, best, x[r * M.beads + best])
            return
        }
        guard let gr = grab else { return }
        grab = (gr.0, gr.1, p.x - grabOffset)
    }

    func release(velocityX: Double) {
        lastTouch = max(now, 0)
        touching = false
        guard let gr = grab else { return }
        grab = nil
        // The flick: the bead leaves with the finger's speed (the push chain follows by contact).
        if v.count == M.rods * M.beads {
            v[gr.0 * M.beads + gr.1] = max(-2200, min(2200, velocityX))
        }
    }

    func undo() {
        touching = false
        pendingUndo = true
    }
}
