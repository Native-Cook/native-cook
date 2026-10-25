import SwiftUI
import UIKit

/*
 Pottery Wheel Shape — a ceramics-class screen ("Kiln Club — Throw a vase") with a lump of wet
 clay turning on a wheel. The whole studio is one Metal shader (PotteryWheelShape.metal); slip
 drops are one Path per frame in a Canvas; the readouts are rolling digits.

 Touch: drag on either side of the clay. Pressing towards the middle squeezes the wall at that
 height (and pushes the clay up), dragging up along the wall pulls it taller, down squats it; slip
 flies off while the finger works and a selection tick marks every whole centimetre of height. The
 wheel speeds up under the hand. Two seconds after the last touch the clay eases back to the lump
 (0.9 s, ease-in-out) and the loop starts again from t = 0.

 Reduced motion: the finished vase, still, no spray; the clay can still be shaped by hand.
 */
struct PotteryWheelShape: View {
    var autoPlay = false

    /// The loop's period (s); the manifest's recording loop.
    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = PotteryWheelShapeEngine()
    /// True while a hand-shaped pose is on screen under reduced motion (the timeline runs then).
    @State private var handActive = false
    @State private var handGeneration = 0
    @State private var lastDrag: CGPoint?

    private typealias M = PotteryWheelShapeMotion
    private typealias S = PotteryWheelShapeStyle

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = PotteryWheelShapeGeo(w: max(1, size.width), h: max(1, size.height))
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .topLeading) {
            S.background

            TimelineView(.animation(paused: reduced && !handActive)) { timeline in
                let f = engine.step(to: timeline.date, g: g, auto: auto, reduced: reduced)
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .colorEffect(ShaderLibrary.potteryWheelShape(
                            .float2(g.w, g.h),
                            .float2(g.cx, g.baseY),
                            .float(g.unit),
                            .float4(f.pose[1] * g.unit, f.pose[2] * g.unit, f.pose[3] * g.unit, f.pose[4] * g.unit),
                            .float4(f.pose[5] * g.unit, f.pose[6] * g.unit, f.pose[7] * g.unit, f.pose[8] * g.unit),
                            .float4(f.pose[M.pH] * g.unit, f.pose[M.pOpen], f.wob, f.speed),
                            .float(f.spin.truncatingRemainder(dividingBy: .pi * 2)),
                            .float4(f.pads[0], f.pads[1], f.pads[2], f.pads[3]),
                            .float4(f.pads[4], f.pads[5], f.pads[6], f.pads[7])
                        ))
                        .frame(width: size.width, height: size.height)

                    if !reduced {
                        Canvas { ctx, _ in
                            ctx.fill(f.drops, with: .color(S.slip))
                        }
                        .frame(width: size.width, height: size.height)
                    }

                    header(f, insets: insets)
                    card(f, insets: insets, width: size.width, height: size.height)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            Color.clear
                .contentShape(Rectangle())
                .frame(width: size.width, height: size.height)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            if let last = lastDrag {
                                engine.work(g: g, x: v.location.x, y: v.location.y,
                                            dx: v.location.x - last.x, dy: v.location.y - last.y,
                                            vx: v.velocity.width, vy: v.velocity.height)
                            } else {
                                engine.takeHold(x: v.location.x, y: v.location.y)
                                handGeneration += 1
                                handActive = true
                            }
                            lastDrag = v.location
                        }
                        .onEnded { _ in
                            lastDrag = nil
                            engine.letGo()
                            let gen = handGeneration
                            Task { @MainActor in
                                while gen == handGeneration {
                                    try? await Task.sleep(for: .milliseconds(500))
                                    if gen == handGeneration && engine.resting(autoPlay: autoPlay) {
                                        handActive = false
                                        break
                                    }
                                }
                            }
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("Clay on a potter's wheel. Drag the wall in, up or down to shape it.")
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func header(_ f: PotteryWheelShapeFrame, insets: EdgeInsets) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("KILN CLUB · LESSON 4 OF 8")
                .font(.system(size: 11, weight: .bold))
                .kerning(2)
                .foregroundStyle(S.accent)
            Text("Throw a vase")
                .font(.system(size: 34, weight: .heavy))
                .kerning(-0.6)
                .foregroundStyle(S.ink)
                .padding(.top, 8)
            Text("Keep your hands wet. Press the wall in, then pull it up slowly.")
                .font(.system(size: 15))
                .lineSpacing(3)
                .foregroundStyle(S.mute)
                .frame(maxWidth: 300, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 6)
            HStack(spacing: 8) {
                Circle()
                    .fill(S.accent)
                    .frame(width: 6, height: 6)
                    .shadow(color: S.accent.opacity(0.8), radius: 3)
                PotteryWheelShapeRollLabel(labels: M.phases + ["Shaping by hand"], index: f.phase, line: 20, style: S.pillFont)
            }
            .padding(.horizontal, 12)
            .frame(height: 32)
            .background(Capsule().fill(Color(red: 1, green: 236 / 255, blue: 218 / 255).opacity(0.06)))
            .overlay(Capsule().strokeBorder(S.line, lineWidth: 1 / displayScale))
            .padding(.top, 16)
        }
        .padding(.horizontal, 24)
        .padding(.top, insets.top + 16)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func card(_ f: PotteryWheelShapeFrame, insets: EdgeInsets, width: Double, height: Double) -> some View {
        let hair = 1 / displayScale
        return HStack(spacing: 0) {
            stat("HEIGHT") {
                PotteryWheelShapeRollNumber(value: f.pose[M.pH] / M.ptPerCm, digits: 2, line: 28, style: S.valueFont,
                                            suffix: " cm", suffixStyle: S.unitFont)
            }
            Rectangle().fill(S.line).frame(width: hair, height: 40).padding(.horizontal, 12)
            stat("WIDEST") {
                PotteryWheelShapeRollNumber(value: M.widest(f.pose) / M.ptPerCm, digits: 2, line: 28, style: S.valueFont,
                                            prefix: "Ø ", suffix: " cm", suffixStyle: S.unitFont)
            }
            Rectangle().fill(S.line).frame(width: hair, height: 40).padding(.horizontal, 12)
            stat("WHEEL") {
                PotteryWheelShapeRollNumber(value: f.speed * 60, digits: 3, line: 28, style: S.valueFont,
                                            suffix: " rpm", suffixStyle: S.unitFont)
            }
        }
        .padding(.horizontal, 20)
        .frame(width: width - 32, height: 84)
        .background {
            let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)
            ZStack {
                shape.fill(S.cardFill)
                    .shadow(color: .black.opacity(0.35), radius: 18, y: 16)
                    .shadow(color: .black.opacity(0.35), radius: 1.5, y: 1)
                shape.strokeBorder(LinearGradient(colors: [S.inkWarm.opacity(0.08), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.08)), lineWidth: 1)
                shape.strokeBorder(S.inkWarm.opacity(0.16), lineWidth: hair)
            }
        }
        .offset(x: 16, y: height - insets.bottom - 16 - 84)
    }

    private func stat<V: View>(_ label: String, @ViewBuilder value: () -> V) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11, weight: .semibold))
                .kerning(1.2)
                .foregroundStyle(S.mute)
            value()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

enum PotteryWheelShapeStyle {
    static let background = Color(hex: 0x130F0C)
    static let ink = Color(hex: 0xF3E7DA)
    static let mute = Color(hex: 0xF3E7DA, opacity: 0.6)
    static let accent = Color(hex: 0xE39A5B)
    static let inkWarm = Color(hex: 0xFFECDA)
    static let line = Color(hex: 0xFFECDA, opacity: 0.12)
    static let cardFill = Color(hex: 0x211913)
    static let slip = Color.rgba(150, 98, 72, 0.9)
    static let pillFont = PotteryWheelShapeRollFont(size: 13, weight: .semibold, color: ink, tabular: false)
    static let valueFont = PotteryWheelShapeRollFont(size: 22, weight: .bold, color: ink)
    static let unitFont = PotteryWheelShapeRollFont(size: 14, weight: .semibold, color: mute)
}

/// What one frame shows.
struct PotteryWheelShapeFrame {
    var pose: [Double]
    var spin: Double
    var speed: Double
    var wob: Double
    var pads: [Double]
    var phase: Int
    var drops = Path()
}

/// The clock, the autoPlay script and the hand's state, stepped once per timeline frame.
@MainActor
final class PotteryWheelShapeEngine {
    private typealias M = PotteryWheelShapeMotion

    /// Seconds after the last touch before the clay goes back to the lump, and how long that takes.
    static let idle = 2.0
    static let returnTime = 0.9
    /// Under reduced motion the screen shows this moment of the loop (the finished vase).
    private static let stillT = 6.6

    private enum Mode { case auto, hand, back }

    private var mode = Mode.auto
    private var last: Date?
    /// Seconds of screen time; a stalled frame (> 0.25 s) pauses it instead of skipping ahead.
    private var now = 0.0
    private var autoStart = 0.0
    private var spinOffset = 0.0
    private var lastTouch = 0.0
    private var touching = false
    private var hand = M.lump
    private var handSpin = 0.0
    private var handSpeed = M.slow
    /// The finger on screen [x, y, alpha] and the spray it raises [strength, share, side].
    private var pad: [Double] = [0, 0, 0]
    private var spray: [Double] = [0, 0.5, 0]
    private var returnFrom = M.lump
    private var returnStart = 0.0
    private var current: PotteryWheelShapeFrame?
    private var lastCm = -1
    private let tick = UISelectionFeedbackGenerator()
    private let soft = UIImpactFeedbackGenerator(style: .light)

    /// Whether nothing moves any more after a touch (the timeline may pause).
    func resting(autoPlay: Bool) -> Bool {
        mode == .auto
    }

    func step(to date: Date, g: PotteryWheelShapeGeo, auto: Bool, reduced: Bool) -> PotteryWheelShapeFrame {
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        let adv = raw > 0.25 || raw < 0 ? 0 : raw
        now += adv
        let dt = min(0.05, adv)

        if mode == .hand {
            let target = touching ? M.fast : handSpeed
            handSpeed += (target - handSpeed) * min(1, dt * 3)
            if !touching {
                hand = M.settlePose(hand, min(1, dt * 8))
                pad[2] = max(0, pad[2] - dt * 4)
                spray[0] = max(0, spray[0] - dt * 3)
                if now - lastTouch > Self.idle {
                    mode = .back
                    returnStart = now
                    returnFrom = hand
                }
            } else {
                spray[0] *= exp(-dt * 2.5)
            }
        } else if mode == .back {
            handSpeed += (M.slow - handSpeed) * min(1, dt * 4)
            if now - returnStart >= Self.returnTime {
                mode = .auto
                autoStart = now
                spinOffset = handSpin.truncatingRemainder(dividingBy: .pi * 2)
            }
        }
        if mode != .auto { handSpin += handSpeed * .pi * 2 * dt }

        var f = frame(g: g, auto: auto, reduced: reduced)
        if !reduced {
            let isAuto = mode == .auto && auto
            let clock = isAuto ? (now - autoStart).truncatingRemainder(dividingBy: PotteryWheelShape.loop) : now
            f.drops = Self.sprayPath(g, f, clock: clock, auto: isAuto, spray: isAuto ? [0, 0.5, 0] : spray)
        }
        current = f

        // A selection tick for every whole centimetre the hand adds or takes away.
        let cm = mode == .hand && touching ? Int((f.pose[M.pH] / M.ptPerCm).rounded()) : -1
        if cm >= 0 && lastCm >= 0 && cm != lastCm { tick.selectionChanged() }
        lastCm = cm
        return f
    }

    private func autoFrame(_ g: PotteryWheelShapeGeo, _ t: Double, _ offset: Double) -> PotteryWheelShapeFrame {
        let pose = M.poseAt(t)
        let spin = M.spinAt(t) + offset
        let wob = M.wobbleX(pose, spin, g.unit)
        let fi = M.fingersAt(t, pose)
        return PotteryWheelShapeFrame(pose: pose, spin: spin, speed: M.speedAt(t), wob: wob,
                                      pads: g.fingerOnScreen(pose, fi, 0, wob) + g.fingerOnScreen(pose, fi, 4, wob),
                                      phase: M.phaseAt(t))
    }

    private func frame(g: PotteryWheelShapeGeo, auto: Bool, reduced: Bool) -> PotteryWheelShapeFrame {
        if mode == .auto {
            if reduced {
                var f = autoFrame(g, Self.stillT, 0)
                f.pose = M.flare
                f.spin = 0
                f.speed = 0
                f.pads = [0, 0, 0, 1, 0, 0, 0, 1]
                return f
            }
            if auto { return autoFrame(g, (now - autoStart).truncatingRemainder(dividingBy: PotteryWheelShape.loop), spinOffset) }
            // without autoPlay the lump just turns slowly
            var f = autoFrame(g, 0, 0)
            f.spin = spinOffset + M.slow * .pi * 2 * (now - autoStart)
            f.wob = M.wobbleX(f.pose, f.spin, g.unit)
            return f
        }
        var pose = hand
        var padAlpha = pad[2]
        var phase = M.phases.count
        if mode == .back {
            let k = M.easeInOut(min(1, (now - returnStart) / Self.returnTime))
            pose = M.mixPose(returnFrom, reduced ? M.flare : M.lump, k)
            padAlpha = 0
            phase = 0
        }
        return PotteryWheelShapeFrame(pose: pose, spin: handSpin, speed: handSpeed,
                                      wob: M.wobbleX(pose, handSpin, g.unit),
                                      pads: [pad[0], pad[1], padAlpha, 15 * g.unit, 0, 0, 0, 1],
                                      phase: phase)
    }

    /// Slip drops flung off the turning wall: pure functions of the clock and hash(i).
    private static func sprayPath(_ g: PotteryWheelShapeGeo, _ f: PotteryWheelShapeFrame, clock: Double, auto: Bool, spray: [Double]) -> Path {
        var path = Path()
        let u = g.unit
        let loop = PotteryWheelShape.loop
        for idx in 0..<M.sprayN {
            let i = Double(idx)
            let age = (clock / M.sprayPeriod + M.hash(i)).truncatingRemainder(dividingBy: 1)
            var strength = spray[0]
            var share = spray[1]
            if auto {
                // the drop was thrown at its own birth time, by the script's spray at that moment
                let born = ((clock - age * M.sprayPeriod).truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
                (strength, share) = M.sprayAt(born)
            }
            if M.hash(i + 71) > strength || age > 0.8 { continue }
            let side: Double = spray[2] != 0
                ? (M.hash(i + 13) < 0.8 ? spray[2] : -spray[2])
                : (M.hash(i + 13) < 0.5 ? -1 : 1)
            let h0 = max(4, min(f.pose[M.pH] - 2, f.pose[M.pH] * share + (M.hash(i + 29) - 0.5) * 26))
            let r = M.radiusAt(f.pose, h0) * u
            let vx = (70 + 120 * M.hash(i + 3)) * u
            let vy = (30 + 90 * M.hash(i + 5)) * u
            let a = age * M.sprayPeriod
            let x = g.cx + f.wob + side * (r + vx * a)
            let y = g.baseY - h0 * u * M.cosE - vy * a + 0.5 * 900 * u * a * a
            let size = (1.2 + 2.2 * M.hash(i + 41)) * (1 - age * 0.7) * u
            path.addEllipse(in: CGRect(x: x - size, y: y - size, width: size * 2, height: size * 2))
            // a short tail along the flight
            let tx = x - side * vx * 0.012
            let ty = y + (vy - 900 * u * a) * 0.012
            let ts = size * 0.6
            path.addEllipse(in: CGRect(x: tx - ts, y: ty - ts, width: ts * 2, height: ts * 2))
        }
        return path
    }

    func takeHold(x: Double, y: Double) {
        if let f = current, mode != .hand {
            hand = f.pose
            handSpin = f.spin
            handSpeed = f.speed
        }
        mode = .hand
        touching = true
        lastTouch = now
        pad = [x, y, 1]
        soft.impactOccurred()
    }

    func work(g: PotteryWheelShapeGeo, x: Double, y: Double, dx: Double, dy: Double, vx: Double, vy: Double) {
        hand = M.shapeByFinger(g, hand, x: x, y: y, dx: dx, dy: dy)
        pad = [x, y, 1]
        lastTouch = now
        let H = hand[M.pH]
        let share = min(1, max(0, (g.baseY - y) / (g.unit * M.cosE) / H))
        let s = min(1, hypot(vx, vy) / 900)
        spray = [max(spray[0], s), share, x >= g.cx ? 1 : -1]
    }

    func letGo() {
        touching = false
        lastTouch = now
    }
}
