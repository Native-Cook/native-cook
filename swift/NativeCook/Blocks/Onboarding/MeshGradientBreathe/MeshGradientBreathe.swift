import SwiftUI
import UIKit

/// A breathing onboarding whose background is a mesh gradient that breathes: on the inhale its control points
/// swell away from the glass orb and the light warms, on the exhale they gather back and cool. The orb guides the
/// breath with the phase and a count. Three steps with native controls: a segmented picker for the rhythm, a wheel
/// for the session length and a glass button.
///
/// The film (one 16 s loop that is also one box breath) walks the three steps and turns the wheel; Continue, Skip,
/// the rhythm picker and the wheel take over, and the film returns 2 s after the last touch. Begin puts the orb in
/// the middle of the screen; End session brings the steps back. Under Reduce Motion the light holds at mid-breath
/// and the film does not run.
struct MeshGradientBreathe: View {
    var autoPlay: Bool = true

    static let loop: Double = MeshGradientBreatheMotion.loop

    private typealias M = MeshGradientBreatheMotion
    private enum Mode { case auto, manual, ret }
    private enum VisKind { case go(Int), ret, hold }
    private struct VisTween { var kind: VisKind; var from: [Double]; var at: Date }
    private struct Tween {
        var from: Double, to: Double, at: Date, duration: Double
        func value(_ now: Date) -> Double {
            from + (to - from) * M.ease(now.timeIntervalSince(at) / duration)
        }
    }

    private static let ink = Color(red: 31 / 255, green: 35 / 255, blue: 51 / 255)
    private static let inkSoft = ink.opacity(0.66)
    private static let titles = ["Take a slower minute", "Find your rhythm", "Your first session"]
    private static let bodies = [
        "Follow the light as it fills and empties. Three slow breaths are enough to start.",
        "",
        "No sound, no streaks. Just the light, for as long as you chose.",
    ]
    private static let switchS = 0.8 // a rhythm change blends the old breath into the new one

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var origin = Date()
    @State private var loopStart = Date()
    @State private var mode: Mode = .auto
    @State private var vis: VisTween?
    @State private var retEnd = Date.distantFuture
    @State private var session = Tween(from: 0, to: 0, at: .distantPast, duration: 0.7)
    @State private var step = 0
    @State private var rhythm = 0
    @State private var minutes = 3
    @State private var switchFrom = 0.0
    @State private var switchAt = -100.0
    @State private var breathOrigin = 0.0
    @State private var idleToken = 0
    @State private var awake = false

    private var film: Bool { autoPlay && !reduceMotion }

    var body: some View {
        GeometryReader { outer in
            let ins = outer.safeAreaInsets
            GeometryReader { full in
                screen(size: full.size, ins: ins)
            }
            .ignoresSafeArea()
        }
        .background(Color(red: 0xD8 / 255, green: 0xDC / 255, blue: 0xEE / 255).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            let now = Date()
            origin = now
            loopStart = now
            mode = film ? .auto : .manual
        }
    }

    // MARK: - Clock

    private func amb(_ now: Date) -> Double { reduceMotion ? 0 : now.timeIntervalSince(origin) }

    private func liveMode(_ now: Date) -> (Mode, Date) {
        if mode == .ret && now >= retEnd { return (.auto, retEnd) }
        return (mode, loopStart)
    }

    private func breath(_ now: Date) -> (full: Double, b: M.Breath) {
        let a = amb(now)
        let b = M.breath(at: a - breathOrigin, rhythm: rhythm)
        let since = a - switchAt
        let blend = since < Self.switchS ? M.clamp01(since / Self.switchS) : 1
        let full = reduceMotion ? 0.5 : switchFrom + (b.full - switchFrom) * blend
        return (full, b)
    }

    private func visibility(_ now: Date) -> [Double] {
        let (m, start) = liveMode(now)
        if m == .auto {
            let t = now.timeIntervalSince(start).truncatingRemainder(dividingBy: M.loop)
            return (0..<3).map { M.stepVis($0, t) }
        }
        guard let v = vis else { return (0..<3).map { $0 == step ? 1 : 0 } }
        let e = now.timeIntervalSince(v.at)
        switch v.kind {
        case .hold:
            return v.from
        case .go(let k):
            return (0..<3).map { j in
                j == k
                    ? (e < 0.2 ? v.from[j] : v.from[j] + (1 - v.from[j]) * M.ease((e - 0.2) / 0.34))
                    : v.from[j] * (1 - M.easeInQuad(M.clamp01(e / 0.2)))
            }
        case .ret:
            let delay = v.from[0] > 0.5 ? 0 : 0.3
            return (0..<3).map { j in
                j == 0
                    ? (e < delay ? v.from[0] : v.from[0] + (1 - v.from[0]) * M.ease((e - delay) / 0.6))
                    : v.from[j] * (1 - M.easeInQuad(M.clamp01(e / 0.3)))
            }
        }
    }

    // MARK: - Screen

    @ViewBuilder
    private func screen(size: CGSize, ins: EdgeInsets) -> some View {
        let width = size.width, height = size.height
        let compact = height < 740
        let contentW = min(width, 520) - 48
        let avail = height - ins.top - ins.bottom
        let orbR = compact ? 54 : min(width * 0.2, 80)
        let orbY = ins.top + avail * (compact ? 0.22 : 0.25)
        let textTop = orbY + orbR * 1.32 + (compact ? 18 : 30)
        let wheelH = max(96, min(height * 0.15, 136))

        TimelineView(.animation(minimumInterval: nil, paused: reduceMotion && !awake)) { tl in
            let now = tl.date
            let (m, start) = liveMode(now)
            let t = now.timeIntervalSince(start).truncatingRemainder(dividingBy: M.loop)
            let br = breath(now)
            let full = br.full
            let s = session.value(now)
            let v = visibility(now)
            let orbCY = orbY + (height * 0.44 - orbY) * s
            let orbRad = orbR * (1 + 0.3 * s) * (1 + 0.26 * full)
            let lab = M.light(full)
            let off = M.offsets(full, amb(now).truncatingRemainder(dividingBy: M.loop), 0.5, orbCY / height)
            let u: [Float] = ([width, height, width / 2, orbCY, orbRad, full, Double(UIScreen.main.scale)] + off + lab).map(Float.init)

            ZStack(alignment: .topLeading) {
                Rectangle()
                    .fill(ShaderLibrary.meshGradientBreatheLight(.floatArray(u)))
                    .frame(width: width, height: height)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)

                orbLabel(br.b, width: width)
                    .scaleEffect(0.9 + 0.14 * full + 0.12 * s)
                    .offset(y: orbCY - 40)
                    .allowsHitTesting(false)

                Group {
                    topBar(width: width).offset(y: ins.top + 6)
                    stepView(0, vis: v[0], top: textTop, width: contentW, compact: compact) {
                        Text(Self.bodies[0]).font(.system(size: 17)).lineSpacing(24 - 20.3)
                            .foregroundStyle(Self.inkSoft).padding(.top, 12)
                    }
                    stepView(1, vis: v[1], top: textTop, width: contentW, compact: compact) {
                        label("Rhythm", top: 14)
                        rhythmPicker.frame(width: contentW, height: 34)
                        label("Session length", top: compact ? 12 : 20)
                        lengthPicker.frame(width: contentW, height: wheelH).clipped()
                    }
                    stepView(2, vis: v[2], top: textTop, width: contentW, compact: compact) {
                        Text("\(minutes) min · \(M.rhythms[rhythm].detail)")
                            .font(.system(size: 17, weight: .semibold)).foregroundStyle(Self.ink).padding(.top, 10)
                        Text(Self.bodies[2]).font(.system(size: 17)).lineSpacing(24 - 20.3)
                            .foregroundStyle(Self.inkSoft).padding(.top, 12)
                    }
                    VStack(spacing: 20) {
                        HStack(spacing: 6) {
                            ForEach(0..<3, id: \.self) { k in
                                let d = M.clamp01(v[k])
                                Capsule().fill(Self.ink).frame(width: 8 + 16 * d, height: 8).opacity(0.32 + 0.68 * d)
                            }
                        }
                        .frame(height: 8)
                        primaryButton(width: contentW)
                    }
                    .frame(width: width, height: height - ins.bottom - 16, alignment: .bottom)
                }
                .opacity(1 - s)
                .offset(y: 24 * s)
                .allowsHitTesting(s < 0.5)
                .accessibilityHidden(s > 0.5)

                if s > 0.5 {
                    endButton(width: contentW)
                        .frame(width: width, height: height - ins.bottom - 16, alignment: .bottom)
                        .opacity(s)
                }
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .onChange(of: m == .auto ? M.filmStep(t) : -1) { _, fs in
                if fs >= 0 { step = fs }
            }
            .onChange(of: m == .auto ? M.filmLength(t) : -1) { _, fl in
                if fl >= 0 { withAnimation { minutes = fl } }
            }
            .onChange(of: br.b.phase) { _, k in
                if m != .auto && (k == 0 || k == 2) { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
            }
        }
    }

    private func orbLabel(_ b: M.Breath, width: CGFloat) -> some View {
        ZStack(alignment: .top) {
            ForEach(0..<3, id: \.self) { k in
                let text = k == 0 ? "Breathe in" : k == 1 ? "Hold" : "Breathe out"
                let mine = b.phase == k || (k == 1 && b.phase == 3)
                let o: Double = reduceMotion ? (k == 0 ? 1 : 0) : (mine ? min(1, b.left / 0.3, (b.length - b.left) / 0.35) : 0)
                Text(text).font(.system(size: 17, weight: .semibold)).foregroundStyle(Self.ink)
                    .opacity(o).padding(.top, 10)
            }
            Text(reduceMotion ? "" : "\(max(1, Int(ceil(b.left - 0.001))))")
                .font(.system(size: 28, weight: .light).monospacedDigit())
                .foregroundStyle(Self.inkSoft)
                .frame(width: 80)
                .padding(.top, 32)
        }
        .frame(width: width, height: 80, alignment: .top)
        .accessibilityHidden(true)
    }

    private func topBar(width: CGFloat) -> some View {
        HStack {
            Text("Stillwater").font(.system(size: 17, weight: .bold, design: .rounded)).foregroundStyle(Self.ink)
            Spacer()
            Button { goTo(2) } label: {
                Text("Skip").font(.system(size: 16, weight: .semibold)).foregroundStyle(Self.inkSoft)
                    .padding(12).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(-12)
        }
        .frame(width: width - 48, height: 32)
        .padding(.horizontal, 24)
    }

    private func stepView<C: View>(_ k: Int, vis v: Double, top: CGFloat, width: CGFloat, compact: Bool,
                                   @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(Self.titles[k])
                .font(.system(size: compact ? 28 : 34, weight: .bold))
                .tracking(-0.4)
                .foregroundStyle(Self.ink)
                .frame(height: compact ? 33 : 40)
            content()
        }
        .frame(width: width, alignment: .leading)
        .opacity(v)
        .offset(x: 24, y: top + (1 - v) * 12)
        .allowsHitTesting(step == k)
        .accessibilityHidden(step != k)
    }

    private func label(_ s: String, top: CGFloat) -> some View {
        Text(s).font(.system(size: 15, weight: .semibold)).foregroundStyle(Self.inkSoft)
            .padding(.top, top).padding(.bottom, 8)
    }

    private var rhythmPicker: some View {
        Picker("Rhythm", selection: Binding(get: { rhythm }, set: { onRhythm($0) })) {
            ForEach(Array(M.rhythms.enumerated()), id: \.offset) { i, r in Text(r.label).tag(i) }
        }
        .pickerStyle(.segmented)
    }

    private var lengthPicker: some View {
        Picker("Session length", selection: Binding(get: { minutes }, set: { touch(); minutes = $0 })) {
            ForEach(M.lengths, id: \.self) { m in Text(m == 1 ? "1 minute" : "\(m) minutes").tag(m) }
        }
        .pickerStyle(.wheel)
    }

    @ViewBuilder
    private func primaryButton(width: CGFloat) -> some View {
        let b = Button(action: onPrimary) {
            Text(step < 2 ? "Continue" : "Begin").frame(maxWidth: .infinity)
        }
        .controlSize(.large)
        .tint(Self.ink)
        .frame(width: width, height: 54)
        if #available(iOS 26, *) { b.buttonStyle(.glassProminent) } else { b.buttonStyle(.borderedProminent) }
    }

    @ViewBuilder
    private func endButton(width: CGFloat) -> some View {
        let b = Button(action: onEnd) {
            Text("End session").frame(maxWidth: .infinity)
        }
        .controlSize(.large)
        .frame(width: width, height: 54)
        if #available(iOS 26, *) { b.buttonStyle(.glass) } else { b.buttonStyle(.bordered) }
    }

    // MARK: - Touch

    private func touch() {
        let now = Date()
        if mode != .manual {
            // freeze what the film shows as the starting point of manual tweens
            let v = visibility(now)
            vis = VisTween(kind: .hold, from: v, at: now)
            mode = .manual
        }
        idleToken += 1
        let token = idleToken
        awake = true
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard token == idleToken else { return }
            awake = false
            if film && mode == .manual && session.to == 0 { startReturn() }
        }
    }

    private func startReturn() {
        let now = Date()
        let v = visibility(now)
        vis = VisTween(kind: .ret, from: v, at: now)
        let end = now.addingTimeInterval((v[0] > 0.5 ? 0 : 0.3) + 0.6)
        retEnd = end
        mode = .ret
        step = 0
        minutes = 3
        rhythm = 0
        let token = idleToken
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(end.timeIntervalSinceNow))
            guard token == idleToken, mode == .ret else { return }
            loopStart = end
            retEnd = .distantFuture
            mode = .auto
        }
    }

    private func goTo(_ k: Int) {
        let now = Date()
        let from = visibility(now)
        touch()
        step = k
        vis = VisTween(kind: .go(k), from: from, at: now)
    }

    private func onPrimary() {
        if step < 2 {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            goTo(step + 1)
            return
        }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        let now = Date()
        let cur = session.value(now)
        touch()
        session = Tween(from: cur, to: 1, at: now, duration: 0.7)
    }

    private func onEnd() {
        let now = Date()
        let cur = session.value(now)
        session = Tween(from: cur, to: 0, at: now, duration: 0.6)
        touch()
    }

    private func onRhythm(_ r: Int) {
        let now = Date()
        let cur = breath(now).full
        touch()
        rhythm = r
        switchFrom = cur
        switchAt = amb(now)
        breathOrigin = amb(now)
    }
}
