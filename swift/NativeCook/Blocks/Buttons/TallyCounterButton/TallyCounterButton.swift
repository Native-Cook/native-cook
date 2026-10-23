import SwiftUI

/// Stepper pill "−  0  +" whose number sits on a draggable knob. Tap − or + (or the knob) to count,
/// drag the knob sideways and let go for ±1, pull it down and let go to reset.
struct TallyCounterButton: View {
    /// Controlled count. Leave `nil` to let the button keep its own.
    var value: Binding<Int>? = nil
    /// Plays the reference's demo by itself: a ghost fingertip taps, drags and resets the counter.
    var autoPlay = false
    var accessibilityLabel = "Counter"
    var onValueChange: (Int) -> Void = { _ in }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var inner = 0
    @State private var motion = Motion()
    @State private var touch: Touch?
    @GestureState private var fingerDown = false
    @State private var touches = 0
    @State private var ghostPaused = false
    @State private var resumeToken = 0
    @State private var settling = false
    @State private var settleToken = 0

    private var count: Int { value?.wrappedValue ?? inner }
    private var ghostActive: Bool { autoPlay && !reduceMotion && !ghostPaused }

    var body: some View {
        TimelineView(.animation(paused: !(ghostActive || touch != nil || settling))) { timeline in
            Color.clear
                .frame(width: Metrics.width, height: Metrics.height)
                .background {
                    Canvas { context, _ in
                        draw(context, at: timeline.date)
                    }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)
                }
        }
        .contentShape(Capsule())
        .gesture(drag)
        .sensoryFeedback(.impact(weight: .light), trigger: touches)
        .onChange(of: fingerDown) { _, down in
            // The system cancelled the gesture (onEnded never fires for those): let go without counting.
            guard !down else { return }
            Task {
                try? await Task.sleep(for: .milliseconds(50))
                if touch != nil { end(commit: false) }
            }
        }
        .task(id: ghostActive) {
            guard ghostActive else { return }
            await runGhost()
        }
        .task(id: resumeToken) {
            guard resumeToken > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.ghostResume))
            if !Task.isCancelled { ghostPaused = false }
        }
        .task(id: settleToken) {
            // Keep the timeline running until the last tween has landed, then let it pause.
            try? await Task.sleep(for: .seconds(0.6))
            if !Task.isCancelled { settling = false }
        }
        .accessibilityElement()
        .accessibilityLabel(accessibilityLabel)
        .accessibilityValue("\(count)")
        .accessibilityAdjustableAction { direction in
            setCount(count + (direction == .increment ? 1 : -1))
        }
    }

    // MARK: Value

    private func setCount(_ next: Int) {
        let clamped = min(max(next, Metrics.minimum), Metrics.maximum)
        guard clamped != count else { return }
        if let value { value.wrappedValue = clamped } else { inner = clamped }
        onValueChange(clamped)
    }

    // MARK: Motion shared by the finger and the ghost

    private func kick() {
        settling = true
        settleToken += 1
    }

    private func pressKnob(_ down: Bool) {
        motion.press.retarget(down ? 1 : 0, duration: down ? 0.13 : 0.26, curve: Ease.out)
        kick()
    }

    private func releaseKnob() {
        motion.kx.retarget(0, duration: 0.38, curve: back)
        motion.ky.retarget(0, duration: 0.38, curve: back)
        pressKnob(false)
    }

    private func flash(_ side: Side, _ down: Bool) {
        let target = down ? 0.92 : 0.0
        let duration = down ? 0.09 : 0.38
        if side == .minus {
            motion.flashMinus.retarget(target, duration: duration, curve: Ease.out)
        } else {
            motion.flashPlus.retarget(target, duration: duration, curve: Ease.out)
        }
        kick()
    }

    // MARK: Touch: one gesture for the whole pill, split into knob / − / + by where the finger lands

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($fingerDown) { _, down, _ in down = true }
            .onChanged { gesture in
                if touch == nil { grant(at: gesture.startLocation) }
                move(gesture.translation)
            }
            .onEnded { _ in end(commit: true) }
    }

    private func grant(at point: CGPoint) {
        // Take over from the ghost, wherever it was.
        ghostPaused = true
        motion.ballAlpha.retarget(0, duration: 0.16, curve: .linear)
        flash(.minus, false)
        flash(.plus, false)
        touches += 1

        if hypot(point.x - Metrics.cx, point.y - Metrics.cy) <= Metrics.knobRadius + 4 {
            touch = Touch(zone: .knob)
            pressKnob(true)
        } else {
            let side: Side = point.x < Metrics.cx ? .minus : .plus
            touch = Touch(zone: .side(side))
            releaseKnob()
            flash(side, true)
        }
    }

    private func move(_ translation: CGSize) {
        guard var current = touch, current.zone == .knob else { return }
        let dx = translation.width
        let dy = translation.height
        if current.axis == nil {
            guard max(abs(dx), abs(dy)) >= Metrics.dragSlop else { return }
            current.axis = abs(dx) >= abs(dy) ? .horizontal : .vertical
            touch = current
        }
        if current.axis == .horizontal {
            motion.kx.retarget(min(max(dx, -Metrics.maxX), Metrics.maxX), duration: 0, curve: .linear)
        } else {
            motion.ky.retarget(min(max(dy, 0), Metrics.maxY), duration: 0, curve: .linear)
        }
    }

    private func end(commit: Bool) {
        guard let current = touch else { return }
        touch = nil
        switch current.zone {
        case .knob:
            if commit {
                let kx = motion.kx.value(at: .now)
                let ky = motion.ky.value(at: .now)
                switch current.axis {
                case nil: setCount(count + 1)
                case .horizontal: if abs(kx) > Metrics.maxX * Metrics.commit { setCount(count + (kx > 0 ? 1 : -1)) }
                case .vertical: if ky > Metrics.maxY * Metrics.commit { setCount(Metrics.minimum) }
                }
            }
            releaseKnob()
        case .side(let side):
            if commit { setCount(count + (side == .minus ? -1 : 1)) }
            flash(side, false)
        }
        resumeToken += 1
    }

    // MARK: Ghost fingertip: replays the reference's demo while autoPlay is on

    private func runGhost() async {
        let restX = Metrics.cx + 4
        let restY = Metrics.cy + 6
        let away = Metrics.height + 60

        func wait(_ seconds: Double) async throws {
            try await Task.sleep(for: .seconds(seconds))
        }
        func moveBall(_ x: Double, _ y: Double, _ duration: Double, curve: UnitCurve = inOut) {
            motion.ballX.retarget(x, duration: duration, curve: curve)
            motion.ballY.retarget(y, duration: duration, curve: curve)
        }
        func press(_ down: Bool) {
            motion.ballScale.retarget(down ? 0.82 : 1, duration: 0.12, curve: Ease.out)
        }
        func tapKnob() async throws {
            press(true)
            pressKnob(true)
            try await wait(0.17)
            setCount(count + 1)
            press(false)
            pressKnob(false)
            try await wait(0.36)
        }
        func tapSide(_ side: Side) async throws {
            let x = side == .minus ? Metrics.glyphInset + 4 : Metrics.width - Metrics.glyphInset + 4
            moveBall(x, Metrics.cy + 5, 0.42)
            try await wait(0.52)
            for _ in 0..<2 {
                press(true)
                flash(side, true)
                try await wait(0.17)
                setCount(count + (side == .minus ? -1 : 1))
                press(false)
                flash(side, false)
                try await wait(0.42)
            }
        }
        func dragKnob(dx: Double, dy: Double) async throws {
            moveBall(restX, restY, 0.42)
            try await wait(0.48)
            press(true)
            pressKnob(true)
            try await wait(0.16)
            motion.kx.retarget(dx, duration: 0.42, curve: Ease.out)
            motion.ky.retarget(dy, duration: 0.42, curve: Ease.out)
            moveBall(restX + dx, restY + dy, 0.42, curve: Ease.out)
            try await wait(0.7)
            setCount(dy > 0 ? Metrics.minimum : count + (dx > 0 ? 1 : -1))
            press(false)
            releaseKnob()
            try await wait(0.24)
        }

        do {
            while true {
                try await wait(0.9)
                moveBall(restX, away, 0)
                motion.ballAlpha.retarget(1, duration: 0.25, curve: .linear)
                moveBall(restX, restY, 0.56)
                try await wait(0.66)
                try await tapKnob()
                try await tapKnob()
                try await dragKnob(dx: Metrics.maxX, dy: 0)
                try await dragKnob(dx: Metrics.maxX, dy: 0)
                try await dragKnob(dx: -Metrics.maxX, dy: 0)
                try await tapSide(.plus)
                try await tapSide(.minus)
                try await dragKnob(dx: 0, dy: Metrics.maxY)
                moveBall(restX, away, 0.52)
                motion.ballAlpha.retarget(0, duration: 0.42, curve: .linear)
                try await wait(1.1)
            }
        } catch {
            // Cancelled: the user took over or the button went away. Let go of whatever the ghost held.
            motion.ballAlpha.retarget(0, duration: 0.16, curve: .linear)
            motion.ballScale.retarget(1, duration: 0, curve: .linear)
            if touch == nil {
                releaseKnob()
                flash(.minus, false)
                flash(.plus, false)
            }
            kick()
        }
    }

    // MARK: Drawing

    private func draw(_ context: GraphicsContext, at date: Date) {
        let x = Metrics.bleed
        let y = Metrics.bleed
        let cx = x + Metrics.cx
        let cy = y + Metrics.cy
        let kx = motion.kx.value(at: date)
        let ky = motion.ky.value(at: date)
        let press = motion.press.value(at: date)
        let flashMinus = motion.flashMinus.value(at: date)
        let flashPlus = motion.flashPlus.value(at: date)
        // -1..1 sideways, 0..1 downwards.
        let side = kx / Metrics.maxX
        let down = ky / Metrics.maxY

        // The whole pill gives a little when the knob is pulled down.
        var pillLayer = context
        pillLayer.translateBy(x: 0, y: Metrics.pillGive * down)
        let pill = capsule(CGRect(x: x, y: y, width: Metrics.width, height: Metrics.height))
        pillLayer.fill(pill, with: .color(Color(hex: 0x282828)))
        pillLayer.fill(pill, with: .color(Color(hex: 0x1e1e1e, opacity: max(abs(side), down))))

        let minusAt = CGPoint(x: x + Metrics.glyphInset, y: cy)
        let plusAt = CGPoint(x: x + Metrics.width - Metrics.glyphInset, y: cy)
        pillLayer.fill(circle(minusAt, Metrics.flashRadius), with: .color(.white.opacity(flashMinus)))
        pillLayer.fill(circle(plusAt, Metrics.flashRadius), with: .color(.white.opacity(flashPlus)))

        // The glyph the knob moves away from dims, the one it approaches lights up; both go when
        // pulling down. A tapped glyph turns dark on its white flash.
        let idle = Color(hex: 0x7a7a7a)
        let lit = Color(hex: 0xf2f2f2)
        let tapped = Color(hex: 0x4a4a4a)
        let minus = glyph(.minus, at: minusAt)
        pillLayer.stroke(minus, with: .color(idle.opacity(clamp(1 - 0.75 * max(side, 0) - down))), lineWidth: 1.5)
        pillLayer.stroke(minus, with: .color(lit.opacity(max(-side, 0))), lineWidth: 1.5)
        pillLayer.stroke(minus, with: .color(tapped.opacity(flashMinus)), lineWidth: 1.5)
        let plus = glyph(.plus, at: plusAt)
        pillLayer.stroke(plus, with: .color(idle.opacity(clamp(1 - 0.75 * max(-side, 0) - down))), lineWidth: 1.5)
        pillLayer.stroke(plus, with: .color(lit.opacity(max(side, 0))), lineWidth: 1.5)
        pillLayer.stroke(plus, with: .color(tapped.opacity(flashPlus)), lineWidth: 1.5)

        var cross = Path()
        cross.move(to: CGPoint(x: cx - 6, y: cy - 6))
        cross.addLine(to: CGPoint(x: cx + 6, y: cy + 6))
        cross.move(to: CGPoint(x: cx + 6, y: cy - 6))
        cross.addLine(to: CGPoint(x: cx - 6, y: cy + 6))
        pillLayer.stroke(cross, with: .color(Color(hex: 0x9a9a9a, opacity: down)), lineWidth: 1.5)

        // A tight shadow at rest and a wide soft one while the knob is held.
        let knobAt = CGPoint(x: cx + kx, y: cy + ky)
        var lift = context
        lift.addFilter(.blur(radius: 14))
        lift.fill(circle(CGPoint(x: knobAt.x, y: knobAt.y + 14), Metrics.knobRadius), with: .color(.black.opacity(0.55 * press)))
        var rest = context
        rest.addFilter(.blur(radius: 3))
        rest.fill(circle(CGPoint(x: knobAt.x, y: knobAt.y + 2), Metrics.knobRadius), with: .color(.black.opacity(0.4)))
        let knobScale = 1 - 0.04 * press
        context.fill(circle(knobAt, Metrics.knobRadius * knobScale), with: .color(Color(hex: 0x3b3b3b)))

        let ballAlpha = motion.ballAlpha.value(at: date)
        if ballAlpha > 0 {
            let at = CGPoint(x: x + motion.ballX.value(at: date), y: y + motion.ballY.value(at: date))
            let r = Metrics.ballRadius * motion.ballScale.value(at: date)
            var ball = context
            ball.opacity = ballAlpha
            ball.fill(
                circle(at, r),
                with: .linearGradient(
                    Gradient(colors: [.rgba(178, 178, 178, 0.9), .rgba(84, 84, 84, 0.9)]),
                    startPoint: CGPoint(x: at.x - 3, y: at.y - r),
                    endPoint: CGPoint(x: at.x + 3, y: at.y + r)
                )
            )
            ball.stroke(circle(at, r - 0.5), with: .color(.white.opacity(0.22)), lineWidth: 1)
        }

        // The number rides on the knob, above the ball like in the reference.
        let digits = Text("\(count)")
            .font(.system(size: 24, weight: .medium))
            .monospacedDigit()
            .foregroundStyle(.white)
        context.scaled(knobScale, about: knobAt).draw(digits, at: knobAt)
    }

    private func glyph(_ side: Side, at c: CGPoint) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: c.x - Metrics.glyphArm, y: c.y))
        path.addLine(to: CGPoint(x: c.x + Metrics.glyphArm, y: c.y))
        if side == .plus {
            path.move(to: CGPoint(x: c.x, y: c.y - Metrics.glyphArm))
            path.addLine(to: CGPoint(x: c.x, y: c.y + Metrics.glyphArm))
        }
        return path
    }
}

private enum Metrics {
    static let width = 164.0
    static let height = 64.0
    static let knobRadius = 26.0
    /// Distance of the − and + glyphs from the pill's ends.
    static let glyphInset = 33.0
    static let glyphArm = 8.5
    static let flashRadius = 15.0
    /// How far the knob can be dragged sideways (±1) and pulled down (reset).
    static let maxX = 48.0
    static let maxY = 38.0
    static let pillGive = 7.0
    static let ballRadius = 10.0
    /// The canvas overflows the button by this much on every side to fit the pulled knob, its shadow and the ball.
    static let bleed = 64.0
    static let minimum = 0
    static let maximum = 99
    static let dragSlop = 6.0
    static let commit = 0.6
    static let ghostResume = 3.0
    static let cx = width / 2
    static let cy = height / 2
}

private let inOut = UnitCurve.bezier(
    startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.55, y: 1)
)
/// Overshoots a little: the knob snaps back like it is on a spring.
private let back = UnitCurve.bezier(
    startControlPoint: UnitPoint(x: 0.34, y: 1.56), endControlPoint: UnitPoint(x: 0.64, y: 1)
)

private enum Side { case minus, plus }

private struct Touch {
    enum Zone: Equatable { case knob, side(Side) }
    enum Axis { case horizontal, vertical }

    var zone: Zone
    var axis: Axis?
}

private struct Motion {
    var kx = Tween(0)
    var ky = Tween(0)
    var press = Tween(0)
    var flashMinus = Tween(0)
    var flashPlus = Tween(0)
    var ballX = Tween(Metrics.cx + 4)
    var ballY = Tween(Metrics.height + 60)
    var ballAlpha = Tween(0)
    var ballScale = Tween(1)
}

private func circle(_ c: CGPoint, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
}

private func clamp(_ value: Double) -> Double {
    min(max(value, 0), 1)
}

#Preview {
    ZStack {
        Color(hex: 0x2c2c2c).ignoresSafeArea()
        TallyCounterButton(autoPlay: true)
    }
}
