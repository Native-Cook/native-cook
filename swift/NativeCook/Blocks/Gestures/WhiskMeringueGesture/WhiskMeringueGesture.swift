import SwiftUI
import UIKit

/*
 Whisk Meringue Gesture — a baking app's step ("Whisk — Step 3 of 6: stiff peaks"): a glass bowl
 of egg whites seen from above. Draw circles in the bowl and the whisk follows the finger on a
 spring; the faster it circles, the faster the whites foam (clear → foamy → soft → firm → stiff),
 the contents swirl behind it and the progress ring advances. Stop, and the foam holds while its
 peaks slowly relax; two seconds after the last touch the bowl eases back to fresh whites (0.9 s,
 the bubbles pop cell by cell) and the loop starts again from t = 0.

 The bowl is one Metal shader (WhiskMeringueGesture.metal); the whisk is two paths per frame in a
 Canvas over it, with a blurred shadow; the progress ring is a trimmed circle in the card.
 Reduced motion: the stiff meringue, still; the whisk still answers the finger.
 */
struct WhiskMeringueGesture: View {
    var autoPlay = false

    /// The loop's period (s); the manifest's recording loop.
    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = WhiskMeringueGestureEngine()
    @State private var handActive = false
    @State private var handGeneration = 0
    @State private var touched = false

    private typealias M = WhiskMeringueGestureMotion
    private typealias S = WhiskMeringueGestureStyle
    private static let cardH = 96.0
    private static let ring = 64.0

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
        let g = WhiskMeringueGestureGeo(w: max(1, size.width), h: max(1, size.height), top: insets.top + 150,
                                        bottom: size.height - insets.bottom - 16 - Self.cardH - 12)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .topLeading) {
            S.background

            TimelineView(.animation(paused: !auto && !handActive)) { timeline in
                let f = engine.step(to: timeline.date, auto: auto, reduced: reduced)
                let p = f.pose
                let whisk = M.whiskPaths(g, p[M.fHX], p[M.fHY], p[M.fLift], 0, 0)
                let l = p[M.fLift]
                let shadow = M.whiskPaths(g, p[M.fHX], p[M.fHY], l, (6 + 22 * l) * g.unit, (9 + 30 * l) * g.unit)
                let shadowAlpha = 0.2 - 0.08 * l
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .colorEffect(ShaderLibrary.whiskMeringueGesture(
                            .float2(g.w, g.h),
                            .float4(g.cx, g.cy, g.R, g.unit),
                            .float4(p[M.fFoam], p[M.fPeak], p[M.fSwirl], p[M.fAgit]),
                            .float4(p[M.fHX], p[M.fHY], p[M.fLift], f.phase),
                            .float4(p[M.fLX], p[M.fLY], p[M.fLPeak], 0)
                        ))
                        .frame(width: size.width, height: size.height)

                    Canvas { ctx, _ in
                        ctx.drawLayer { c in
                            c.opacity = shadowAlpha
                            c.addFilter(.blur(radius: 5))
                            c.stroke(shadow.handle, with: .color(S.shadow), style: StrokeStyle(lineWidth: 13 * g.unit, lineCap: .round))
                        }
                        ctx.drawLayer { c in
                            c.opacity = shadowAlpha
                            c.addFilter(.blur(radius: 2.5))
                            c.stroke(shadow.wires, with: .color(S.shadow), style: StrokeStyle(lineWidth: 2.6))
                        }
                        ctx.stroke(whisk.handle, with: .color(S.handleDark), style: StrokeStyle(lineWidth: 13 * g.unit, lineCap: .round))
                        ctx.stroke(whisk.handle, with: .color(S.handleLight), style: StrokeStyle(lineWidth: 4 * g.unit, lineCap: .round))
                        ctx.stroke(whisk.wires, with: .color(S.wireDark), style: StrokeStyle(lineWidth: 2.6, lineJoin: .round))
                        ctx.stroke(whisk.wires, with: .color(S.wireLight), style: StrokeStyle(lineWidth: 1, lineJoin: .round))
                    }
                    .frame(width: size.width, height: size.height)

                    header(insets: insets)
                    card(foam: p[M.fFoam], insets: insets, width: size.width, height: size.height)
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
                            if !touched {
                                touched = true
                                engine.grab(g: g, x: v.location.x, y: v.location.y)
                                handGeneration += 1
                                handActive = true
                            } else {
                                engine.follow(g: g, x: v.location.x, y: v.location.y)
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
                .accessibilityLabel("A bowl of egg whites. Draw circles in the bowl to whisk them into stiff peaks.")
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func header(insets: EdgeInsets) -> some View {
        let hair = 1 / displayScale
        return VStack(alignment: .leading, spacing: 0) {
            Text("WHISK · STEP 3 OF 6")
                .font(.system(size: 11, weight: .bold))
                .kerning(2)
                .foregroundStyle(S.accent)
            Text("Stiff peaks")
                .font(.system(size: 34, weight: .heavy))
                .kerning(-0.6)
                .foregroundStyle(S.ink)
                .padding(.top, 8)
            Text("Beat in quick circles until the whites hold a peak that stands up straight.")
                .font(.system(size: 15))
                .lineSpacing(3)
                .foregroundStyle(S.mute)
                .frame(maxWidth: 320, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 6)
            HStack(spacing: 8) {
                ForEach(["4 egg whites", "Pinch of salt", "About 4 min"], id: \.self) { chip in
                    Text(chip)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(S.ink)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 14).fill(S.chipFill))
                        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(S.ink.opacity(0.14), lineWidth: hair))
                }
            }
            .padding(.top, 14)
        }
        .padding(.horizontal, 24)
        .padding(.top, insets.top + 16)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func card(foam: Double, insets: EdgeInsets, width: Double, height: Double) -> some View {
        let hair = 1 / displayScale
        let ring = Self.ring
        return HStack(spacing: 16) {
            ZStack {
                Circle()
                    .stroke(S.ink.opacity(0.09), lineWidth: 6)
                    .frame(width: ring - 10, height: ring - 10)
                Circle()
                    .trim(from: 0, to: max(0, min(1, foam)) * 0.99972)
                    .stroke(AngularGradient(colors: S.ringColors, center: .center, startAngle: .degrees(90), endAngle: .degrees(450)),
                            style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .frame(width: ring - 10, height: ring - 10)
                    .opacity(foam > 0.0005 ? 1 : 0)
                WhiskMeringueGestureRollNumber(value: foam * 100, digits: 3, line: 18, style: S.ringValue,
                                               suffix: "%", suffixStyle: S.ringUnit, center: true)
            }
            .frame(width: ring, height: ring)

            VStack(alignment: .leading, spacing: 2) {
                Text("SOFT · FIRM · STIFF")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.4)
                    .foregroundStyle(S.mute)
                WhiskMeringueGestureRollLabel(labels: M.stages, index: M.stageOf(foam), line: 24, style: S.stageFont)
                Text("Tip the bowl: stiff whites stay put.")
                    .font(.system(size: 13))
                    .foregroundStyle(S.mute)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .frame(width: width - 32, height: Self.cardH)
        .background {
            let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)
            ZStack {
                shape.fill(S.card)
                    .shadow(color: Color.rgba(60, 40, 20, 0.14), radius: 16, y: 14)
                    .shadow(color: Color.rgba(60, 40, 20, 0.12), radius: 1.5, y: 1)
                shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.9), .white.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.06)), lineWidth: 1)
                shape.strokeBorder(S.ink.opacity(0.1), lineWidth: hair)
            }
        }
        .offset(x: 16, y: height - insets.bottom - 16 - Self.cardH)
    }
}

enum WhiskMeringueGestureStyle {
    static let background = Color(hex: 0xE4DED5)
    static let ink = Color(hex: 0x2A2520)
    static let mute = Color(hex: 0x2A2520, opacity: 0.6)
    static let accent = Color(hex: 0xD98E2B)
    static let card = Color(hex: 0xFBF7F0)
    static let chipFill = Color.rgba(255, 252, 246, 0.7)
    static let shadow = Color(hex: 0x3A2C1E)
    static let handleDark = Color(hex: 0x646B71)
    static let handleLight = Color(hex: 0xD9DEE1)
    static let wireDark = Color(hex: 0x6A7177)
    static let wireLight = Color(hex: 0xF1F4F5)
    static let ringColors = [Color(hex: 0xF4D27A), Color(hex: 0xE9A93F), Color(hex: 0xD98E2B), Color(hex: 0xF4D27A)]
    static let ringValue = WhiskMeringueGestureRollFont(size: 15, weight: .bold, color: ink)
    static let ringUnit = WhiskMeringueGestureRollFont(size: 11, weight: .bold, color: mute)
    static let stageFont = WhiskMeringueGestureRollFont(size: 19, weight: .bold, color: ink, tabular: false)
}

struct WhiskMeringueGestureFrame {
    var pose: [Double]
    /// The ripples' phase (radians).
    var phase: Double
}

/// The clock, the autoPlay script and the whisk under the finger, stepped once per timeline frame.
@MainActor
final class WhiskMeringueGestureEngine {
    private typealias M = WhiskMeringueGestureMotion

    static let idle = 2.0
    static let returnTime = 0.9
    /// How much foam one radian of whisking adds, and the finger's spring (per second²).
    private static let work = 0.028
    private static let stiff = 260.0
    private static let damp = 26.0

    private enum Mode { case auto, hand, back }

    private var mode = Mode.auto
    private var last: Date?
    private var now = 0.0
    private var autoStart = 0.0
    private var lastTouch = 0.0
    private var touching = false
    private var hand = M.restPose()
    private var vel = (0.0, 0.0)
    private var target = (0.0, 0.0)
    private var returnFrom = M.restPose()
    private var returnStart = 0.0
    private var current: [Double]?
    private var lastStage = -1
    private let tick = UISelectionFeedbackGenerator()
    private let soft = UIImpactFeedbackGenerator(style: .light)

    /// Whether nothing moves any more after a touch (the timeline may pause).
    func resting(autoPlay: Bool) -> Bool {
        mode == .auto
    }

    func step(to date: Date, auto: Bool, reduced: Bool) -> WhiskMeringueGestureFrame {
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        let adv = raw > 0.25 || raw < 0 ? 0 : raw
        now += adv
        let dt = min(0.05, adv)

        if mode == .hand {
            var p = hand
            var (vx, vy) = vel
            if touching {
                vx += ((target.0 - p[M.fHX]) * Self.stiff - vx * Self.damp) * dt
                vy += ((target.1 - p[M.fHY]) * Self.stiff - vy * Self.damp) * dt
            } else {
                vx *= exp(-dt * 10)
                vy *= exp(-dt * 10)
            }
            let ox = p[M.fHX], oy = p[M.fHY]
            p[M.fHX] += vx * dt
            p[M.fHY] += vy * dt
            // how far the whisk turned round the bowl's middle this frame
            var dA = atan2(p[M.fHY], p[M.fHX]) - atan2(oy, ox)
            if dA > .pi { dA -= .pi * 2 }
            if dA < -.pi { dA += .pi * 2 }
            let reach = min(1, hypot(p[M.fHX], p[M.fHY]) / 0.25)
            let w = abs(dA) * reach
            if !reduced { p[M.fFoam] = min(1, p[M.fFoam] + w * Self.work) }
            p[M.fSwirl] += dA * 0.62 * reach
            let omega = dt > 0 ? w / dt : 0
            p[M.fAgit] += (min(1, omega / (2.3 * .pi * 2)) - p[M.fAgit]) * min(1, dt * 6)
            // peaks stand while whisking; once the whisk stops they relax a little
            let want = max(0, (p[M.fFoam] - 0.45) / 0.55)
            p[M.fPeak] += ((touching ? want : want * 0.6) - p[M.fPeak]) * min(1, dt * 1.5)
            p[M.fLift] *= exp(-dt * 6)
            p[M.fLPeak] *= exp(-dt * 0.5)
            hand = p
            vel = (vx, vy)
            if !touching && now - lastTouch > Self.idle {
                mode = .back
                returnStart = now
                returnFrom = p
            }
        } else if mode == .back && now - returnStart >= Self.returnTime {
            mode = .auto
            autoStart = now
        }

        let rest = reduced ? M.stiffPose() : M.restPose()
        let pose: [Double]
        switch mode {
        case .hand: pose = hand
        case .back: pose = M.mixPose(returnFrom, rest, M.easeInOut(min(1, (now - returnStart) / Self.returnTime)))
        case .auto: pose = auto ? M.poseAt((now - autoStart).truncatingRemainder(dividingBy: WhiskMeringueGesture.loop)) : rest
        }
        current = pose

        let t = (now - autoStart).truncatingRemainder(dividingBy: WhiskMeringueGesture.loop)
        let phase = mode == .auto ? t / WhiskMeringueGesture.loop * .pi * 2 * 9 : now.truncatingRemainder(dividingBy: 100) * .pi * 2

        let stage = mode == .hand ? M.stageOf(pose[M.fFoam]) : -1
        if stage >= 0 && lastStage >= 0 && stage != lastStage { tick.selectionChanged() }
        lastStage = stage
        return WhiskMeringueGestureFrame(pose: pose, phase: phase)
    }

    /// A finger in bowl units, kept inside the bowl with a rubber band past 0.8 of the radius.
    private func fingerInBowl(_ g: WhiskMeringueGestureGeo, _ x: Double, _ y: Double) -> (Double, Double) {
        let bx = (x - g.cx) / g.R
        let by = (y - g.cy) / (g.R * M.squash)
        let r = hypot(bx, by)
        let lim = 0.8
        if r <= lim { return (bx, by) }
        let over = r - lim
        let rr = lim + over / (1 + over / 0.12)
        return (bx / r * rr, by / r * rr)
    }

    func grab(g: WhiskMeringueGestureGeo, x: Double, y: Double) {
        if mode != .hand {
            hand = current ?? M.restPose()
            vel = (0, 0)
        }
        mode = .hand
        touching = true
        lastTouch = now
        target = fingerInBowl(g, x, y)
        soft.impactOccurred()
    }

    func follow(g: WhiskMeringueGestureGeo, x: Double, y: Double) {
        target = fingerInBowl(g, x, y)
        lastTouch = now
    }

    func release() {
        touching = false
        lastTouch = now
    }
}
