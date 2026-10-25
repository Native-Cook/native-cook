import SwiftUI
import UIKit

/*
 Laundry Spin Progress — a laundromat app's machine screen ("Suds — Washer 4"): the progress of a
 wash cycle shown through the washer's door. The water level, the tumbling clothes, the suds and
 the spin (clothes pressed to the wall, smeared by motion blur) tell the phase; the phase name,
 the minutes left and a four-step bar roll and fill below.

 Touch: turn the drum by dragging round the door (it follows the finger's angle), flick it and it
 keeps turning and slows down; slow turns tumble the clothes, past ~3 rev/s they plaster to the
 wall. Two seconds after the last touch, once the drum has slowed, the scene eases back to its
 rest (0.9 s) and the loop restarts from t = 0.

 One Metal pass (LaundrySpinProgress.metal); all motion is uniforms.
 Reduced motion: the washing phase, still; the drum still turns under a finger.
 */
struct LaundrySpinProgress: View {
    var autoPlay = false

    /// The loop's period (s); the manifest's recording loop.
    static let loop = 10.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = LaundrySpinProgressEngine()
    @State private var handActive = false
    @State private var handGeneration = 0
    @State private var touched = false

    private typealias M = LaundrySpinProgressMotion
    private typealias S = LaundrySpinProgressStyle
    private static let steps = ["Wash", "Rinse", "Spin", "Done"]

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
        let g = LaundrySpinProgressGeo(w: max(1, size.width), h: max(1, size.height))
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .topLeading) {
            S.background

            TimelineView(.animation(paused: !auto && !handActive)) { timeline in
                let f = engine.step(to: timeline.date, auto: auto, reduced: reduced, autoPlay: autoPlay)
                let s = f.scene
                let gs = (0..<M.garments).map { M.garmentAt($0, s) }
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .colorEffect(ShaderLibrary.laundrySpinProgress(
                            .float2(g.w, g.h),
                            .float4(g.cx, g.cy, g.Rg, g.unit),
                            .float4(s[M.sAngle].truncatingRemainder(dividingBy: .pi * 2), s[M.sSpeed], s[M.sLevel], s[M.sSuds]),
                            .float4(s[M.sFog], s[M.sSlosh], f.phase, 0),
                            .float4(gs[0][0], gs[0][1], gs[0][2], gs[0][3]),
                            .float4(gs[1][0], gs[1][1], gs[1][2], gs[1][3]),
                            .float4(gs[2][0], gs[2][1], gs[2][2], gs[2][3]),
                            .float4(gs[3][0], gs[3][1], gs[3][2], gs[3][3]),
                            .float4(gs[4][0], gs[4][1], gs[4][2], gs[4][3]),
                            .float4(gs[5][0], gs[5][1], gs[5][2], gs[5][3]),
                            .float4(gs[0][4], gs[1][4], gs[2][4], gs[3][4]),
                            .float4(gs[4][4], gs[5][4], 0, 0)
                        ))
                        .frame(width: size.width, height: size.height)

                    card(progress: f.progress, index: f.index, insets: insets, width: size.width, height: size.height)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            header(insets: insets, width: size.width)
                .allowsHitTesting(false)

            Color.clear
                .contentShape(Rectangle())
                .frame(width: size.width, height: size.height)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            if !touched {
                                touched = true
                                engine.grab(g: g, x: v.location.x, y: v.location.y)
                                handGeneration += 1
                                handActive = true
                            } else {
                                engine.turn(g: g, x: v.location.x, y: v.location.y)
                            }
                        }
                        .onEnded { _ in
                            touched = false
                            engine.release()
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
                .accessibilityLabel("Washer 4, seen through its door. Drag round the door to turn the drum.")
                .accessibilityAddTraits(.allowsDirectInteraction)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func header(insets: EdgeInsets, width: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("SUDS · WASHER 4")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(2)
                    .foregroundStyle(S.accent)
                Spacer(minLength: 8)
                HStack(spacing: 6) {
                    Circle().fill(S.accent).frame(width: 6, height: 6)
                    Text("Live")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(S.accent)
                }
                .padding(.horizontal, 10)
                .frame(height: 24)
                .background(Capsule().fill(S.accent.opacity(0.12)))
            }
            Text("Cottons 40°")
                .font(.system(size: 34, weight: .heavy))
                .kerning(-0.6)
                .foregroundStyle(S.ink)
                .padding(.top, 8)
            Text("Hale Street Laundromat · 1,200 rpm spin")
                .font(.system(size: 15))
                .foregroundStyle(S.mute)
                .padding(.top, 4)
        }
        .padding(.horizontal, 24)
        .padding(.top, insets.top + 16)
        .frame(width: width, alignment: .leading)
    }

    private func card(progress: Double, index: Int, insets: EdgeInsets, width: Double, height: Double) -> some View {
        let hair = 1 / displayScale
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        return VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .center) {
                LaundrySpinProgressRollLabel(labels: M.phases, index: index, line: 34, style: S.phase)
                Spacer(minLength: 8)
                LaundrySpinProgressRollNumber(value: M.minutesLeft(progress), digits: 2, line: 34, style: S.minutes,
                                              suffix: " min", suffixStyle: S.minUnit)
            }
            HStack(spacing: 6) {
                ForEach(0..<4, id: \.self) { i in
                    VStack(alignment: .leading, spacing: 8) {
                        ZStack(alignment: .leading) {
                            Capsule().fill(S.ink.opacity(0.09))
                            Capsule().fill(S.accent)
                                .scaleEffect(x: min(1, max(0, progress * 4 - Double(i))), y: 1, anchor: .leading)
                        }
                        .frame(height: 6)
                        .clipShape(Capsule())
                        Text(Self.steps[i])
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(S.ink)
                            .opacity(progress * 4 >= Double(i) - 0.001 ? 1 : 0.45)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            HStack {
                Text("We will ping you when it is done.")
                    .font(.system(size: 13))
                    .foregroundStyle(S.mute)
                Spacer(minLength: 8)
                Capsule().fill(S.accent)
                    .frame(width: 44, height: 26)
                    .overlay(alignment: .trailing) {
                        Circle().fill(S.knob)
                            .frame(width: 22, height: 22)
                            .shadow(color: .black.opacity(0.25), radius: 1, y: 1)
                            .padding(2)
                    }
            }
        }
        .padding(20)
        .frame(width: width - 32)
        .background {
            ZStack {
                shape.fill(S.card)
                    .shadow(color: Color.rgba(20, 40, 60, 0.12), radius: 18, y: 16)
                    .shadow(color: Color.rgba(20, 40, 60, 0.12), radius: 1.5, y: 1)
                shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.95), .white.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.05)), lineWidth: 1)
                shape.strokeBorder(S.ink.opacity(0.1), lineWidth: hair)
            }
        }
        .frame(width: width, height: height - insets.bottom - 16, alignment: .bottom)
    }
}

struct LaundrySpinProgressGeo {
    let w: Double
    let h: Double
    let unit: Double
    let cx: Double
    let cy: Double
    let Rg: Double

    init(w: Double, h: Double) {
        self.w = w
        self.h = h
        unit = max(0.74, min(1.15, min(w / 393, h / 852)))
        cx = w / 2
        cy = h * 0.45
        Rg = min(138 * unit, w * 0.35)
    }
}

enum LaundrySpinProgressStyle {
    static let background = Color(hex: 0xECEDEC)
    static let ink = Color(hex: 0x1F2A33)
    static let mute = Color.rgba(31, 42, 51, 0.58)
    static let accent = Color(hex: 0x2F8FB8)
    static let card = Color(hex: 0xFAFBFB)
    static let knob = Color(hex: 0xFBFCFC)
    static let phase = LaundrySpinProgressRollFont(size: 26, weight: .heavy, color: ink, tabular: false, kerning: -0.4)
    static let minutes = LaundrySpinProgressRollFont(size: 26, weight: .bold, color: ink)
    static let minUnit = LaundrySpinProgressRollFont(size: 15, weight: .semibold, color: mute)
}

/// What one frame shows.
struct LaundrySpinProgressFrame {
    var scene: [Double]
    /// The loop phase (0 … 1) for the bubbles and the slosh.
    var phase: Double
    var progress: Double
    var index: Int
}

/// The clock, the autoPlay script and the hand-turned drum, stepped once per timeline frame.
@MainActor
final class LaundrySpinProgressEngine {
    private typealias M = LaundrySpinProgressMotion

    static let idle = 2.0
    static let returnTime = 0.9
    /// The still moment shown under reduced motion (washing).
    private static let stillT = 3.0

    private enum Mode { case auto, hand, back }

    private var mode = Mode.auto
    private var last: Date?
    private var now = 0.0
    private var autoStart = 0.0
    private var angleOffset = 0.0
    private var lastTouch = 0.0
    private var touching = false
    /// Hand state [drum angle, speed rev/s, finger angle], and the scene it started from.
    private var hand: [Double] = [0, 0, 0]
    private var base = M.restScene()
    /// Progress and phase frozen while the hand turns the drum.
    private var heldProgress = 0.0
    private var heldIndex = 0
    private var returnFrom = M.restScene()
    private var returnStart = 0.0
    private var current = LaundrySpinProgressFrame(scene: M.restScene(), phase: 0, progress: 0, index: 0)
    private var wasPlastered = false
    private var lastQuarter = 0
    private let tick = UISelectionFeedbackGenerator()
    private let thud = UIImpactFeedbackGenerator(style: .light)

    func resting(autoPlay: Bool) -> Bool {
        if mode == .auto { return true }
        return mode == .hand && !autoPlay && !touching && abs(hand[1]) < 0.01
    }

    func step(to date: Date, auto: Bool, reduced: Bool, autoPlay: Bool) -> LaundrySpinProgressFrame {
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        let adv = raw > 0.25 || raw < 0 ? 0 : raw
        now += adv
        let dt = min(0.033, adv)
        let loop = LaundrySpinProgress.loop

        if mode != .auto {
            if !touching {
                // a flicked drum keeps turning and slows down
                hand[1] *= exp(-dt / 1.2)
                hand[0] += hand[1] * .pi * 2 * dt
            }
            if mode == .hand && !touching && now - lastTouch > Self.idle && abs(hand[1]) < 0.3 && autoPlay {
                mode = .back
                returnStart = now
                returnFrom = M.handScene(base, hand[0], hand[1])
            } else if mode == .back && now - returnStart >= Self.returnTime {
                mode = .auto
                autoStart = now
                angleOffset = hand[0].truncatingRemainder(dividingBy: .pi * 2)
            }
        }

        let f: LaundrySpinProgressFrame
        switch mode {
        case .hand:
            f = LaundrySpinProgressFrame(scene: M.handScene(base, hand[0], hand[1]), phase: now.truncatingRemainder(dividingBy: loop) / loop,
                                         progress: heldProgress, index: heldIndex)
        case .back:
            let k = M.easeInOut(min(1, (now - returnStart) / Self.returnTime))
            let rest = reduced ? M.sceneAt(Self.stillT) : M.restScene()
            var s = returnFrom
            for i in 1..<s.count { s[i] = returnFrom[i] + (rest[i] - returnFrom[i]) * k }
            s[0] = hand[0]
            f = LaundrySpinProgressFrame(scene: s, phase: now.truncatingRemainder(dividingBy: loop) / loop,
                                         progress: heldProgress * (1 - k), index: k > 0.5 ? 0 : heldIndex)
        case .auto:
            let t = reduced ? Self.stillT : auto ? (now - autoStart).truncatingRemainder(dividingBy: loop) : 0
            var s = M.sceneAt(t)
            s[M.sAngle] += angleOffset
            f = LaundrySpinProgressFrame(scene: s, phase: t / loop, progress: M.progressAt(t), index: M.phaseAt(t))
        }
        current = f

        let plastered = mode == .hand && f.scene[M.sPlaster] > 0.5
        if plastered && !wasPlastered { thud.impactOccurred() }
        wasPlastered = plastered
        let quarter = mode == .hand && touching ? Int(floor(hand[0] / (.pi / 2))) : 0
        if quarter != lastQuarter && touching { tick.selectionChanged() }
        lastQuarter = quarter
        return f
    }

    private func fingerAngle(_ g: LaundrySpinProgressGeo, _ x: Double, _ y: Double) -> Double {
        atan2(-(y - g.cy), x - g.cx)
    }

    func grab(g: LaundrySpinProgressGeo, x: Double, y: Double) {
        let f = current
        if mode != .hand {
            base = f.scene
            heldProgress = f.progress
            heldIndex = f.index
            hand = [f.scene[M.sAngle], f.scene[M.sSpeed], fingerAngle(g, x, y)]
        } else {
            hand[2] = fingerAngle(g, x, y)
        }
        mode = .hand
        touching = true
        lastTouch = now
        lastQuarter = Int(floor(hand[0] / (.pi / 2)))
    }

    func turn(g: LaundrySpinProgressGeo, x: Double, y: Double) {
        guard touching else { return }
        let a = fingerAngle(g, x, y)
        var d = a - hand[2]
        if d > .pi { d -= .pi * 2 }
        if d < -.pi { d += .pi * 2 }
        // only a drag far enough from the centre turns the drum (near it, angles jump)
        let r = hypot(x - g.cx, y - g.cy)
        let k = min(1, r / (g.Rg * 0.35))
        let dt = max(1.0 / 120, now - lastTouch)
        let v = d * k / (.pi * 2) / dt
        hand = [hand[0] + d * k, hand[1] * 0.7 + v * 0.3, a]
        lastTouch = now
    }

    func release() {
        touching = false
        lastTouch = now
    }
}
