import CoreText
import SwiftUI
import UIKit

/// A sleep-sound mix pad.  A warm puck presses a gridded membrane; while it comes to rest, the sand density is sampled
/// from the Chladni field of the current mix, so the bright grains genuinely collect on that figure rather than fading
/// into a stored picture.  The automatic path is a nine-second pure function and the DEBUG pin freezes that exact pose.
struct WaveFieldPad: View {
    var autoPlay = true
    var onClose: (() -> Void)?

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.dismiss) private var dismiss
    @State private var live = WaveFieldPadLive()
    @State private var mode: WaveFieldPadMode = .automatic
    @State private var loopStart = Date()
    @State private var lastTick = Date()
    @State private var beatOffset = 0.0
    @State private var homePulse = 0
    @State private var selectedTab = 0
    @GestureState private var padDragging = false

    private enum WaveFieldPadMode { case automatic, manual }

    init(autoPlay: Bool = true, onClose: (() -> Void)? = nil) {
        self.autoPlay = autoPlay
        self.onClose = onClose
        WaveFieldPadFonts.register()
    }

    var body: some View {
        GeometryReader { safe in
            scene(insets: safe.safeAreaInsets).ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .sensoryFeedback(.impact(flexibility: .rigid), trigger: live.wallEvents)
        .sensoryFeedback(.impact(weight: .medium), trigger: homePulse)
        .sensoryFeedback(.selection, trigger: live.selectionEvents)
        .sensoryFeedback(.impact(flexibility: .soft), trigger: live.softEvents)
        .sensoryFeedback(.impact(weight: .light), trigger: live.lightEvents)
        .onAppear { restartLoop() }
        .onChange(of: padDragging) { _, active in
            if !active, live.motion == .drag { live.release(velocity: .zero) }
        }
        .onChange(of: voiceOver) { _, enabled in
            if enabled { captureAutoplay(at: Date()) }
        }
        .onChange(of: reduceMotion) { _, reduced in
            captureAutoplay(at: Date())
            live.setReduced(reduced)
        }
    }

    private func scene(insets: EdgeInsets) -> some View {
        GeometryReader { proxy in
            let viewport = proxy.size
            let bottom = viewport.height - insets.bottom - 12
            let scale = max(0.01, min(viewport.width / WaveFieldPadMetrics.width, bottom / 780, (bottom - insets.top) / 704))
            let top = max(0, insets.top - 76 * scale)
            let size = CGSize(width: WaveFieldPadMetrics.width * scale, height: max(1, viewport.height - top))
            TimelineView(.animation(minimumInterval: 1.0 / 60, paused: timelinePaused)) { timeline in
                let frame = shownFrame(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    WaveFieldPadBackground(frame: frame, scale: scale, size: size)
                        .accessibilityHidden(true)
                    header(scale: scale)
                    wave(frame: frame, scale: scale, size: size)
                        .accessibilityHidden(true)
                    readout(frame: frame, scale: scale)
                    WaveFieldPadSurface(frame: frame, scale: scale, now: timeline.date)
                        .frame(width: size.width, height: size.height)
                        .accessibilityHidden(true)
                    tabs(scale: scale)
                    padTouch(scale: scale, now: timeline.date)
                }
                .frame(width: size.width, height: size.height)
                .coordinateSpace(name: WaveFieldPadMetrics.sceneSpace)
                .clipped()
                .position(x: viewport.width / 2, y: top + size.height / 2)
                .onChange(of: timeline.date) { _, date in advance(to: date) }
            }
        }
        .background(Color(red: 0.082, green: 0.071, blue: 0.086))
    }

    private func captureAutoplay(at date: Date) {
        guard mode == .automatic else { return }
        let time = debugTime ?? date.timeIntervalSince(loopStart)
        var frame = WaveFieldPadFrame.frame(at: time)
        let previous = WaveFieldPadFrame.frame(at: time - 1.0/60)
        frame.beat += beatOffset
        var captured = WaveFieldPadLive(frame: frame, previous: previous, reduced: false)
        captured.selectionEvents = live.selectionEvents; captured.lightEvents = live.lightEvents
        captured.softEvents = live.softEvents; captured.wallEvents = live.wallEvents
        live = captured; mode = .manual; lastTick = date
    }

    private func advance(to date: Date) {
        defer { lastTick = date }
        guard mode == .manual else { return }
        live.step(date.timeIntervalSince(lastTick), autoplayAllowed: autoPlay && !reduceMotion && !voiceOver)
        if live.returning && live.motion == .idle && live.time-live.lastTouch >= 2.65 && autoPlay && !reduceMotion && !voiceOver {
            beatOffset = live.beat; loopStart = date; mode = .automatic
        }
    }

    private var debugTime: Double? {
        #if DEBUG
        if let text = UserDefaults.standard.string(forKey: "waveFieldPadTime"), let time = Double(text) { return time }
        #endif
        return nil
    }

    private var timelinePaused: Bool { debugTime != nil && !reduceMotion && !voiceOver && mode == .automatic }

    private func shownFrame(at date: Date) -> WaveFieldPadFrame {
        if mode == .automatic && !reduceMotion && !voiceOver && autoPlay {
            var result = WaveFieldPadFrame.frame(at: debugTime ?? date.timeIntervalSince(loopStart))
            result.beat += beatOffset
            return result
        }
        return live.frame
    }

    private func header(scale: CGFloat) -> some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            if let onClose { onClose() } else { dismiss() }
        } label: {
            WaveFieldPadGlyph(index: 3)
                .stroke(style: StrokeStyle(lineWidth: 2 * scale, lineCap: .round))
                .foregroundStyle(Color(hex: 0xE9ECE8))
                .frame(width: 24 * scale, height: 24 * scale)
                .frame(width: 44 * scale, height: 44 * scale)
                .contentShape(Rectangle())
        }
            .buttonStyle(.plain)
            .accessibilityLabel("Close")
            .position(x: 360 * scale, y: 98 * scale)
    }

    private func wave(frame: WaveFieldPadFrame, scale: CGFloat, size: CGSize) -> some View {
        WaveFieldPadWave(frame: frame)
            .frame(width: size.width, height: 240 * scale)
            .position(x: size.width / 2, y: 180 * scale)
            .allowsHitTesting(false)
    }

    @ViewBuilder
    private func readout(frame: WaveFieldPadFrame, scale: CGFloat) -> some View {
        let h = min(max(frame.hz, 1), 4), base = floor(h)
        let roll = min(max((h-base-0.3)/0.4, 0), 1)
        let frequency = min(base+roll*roll*(3-2*roll), 4)
        let band = min(max((frame.hz-1.42)/0.16, 0), 1)
        HStack(spacing: 0) {
            VStack(spacing: 0) {
                ForEach(1...4, id: \.self) { digit in
                    Text("\(digit)")
                        .font(WaveFieldPadFonts.font(.tabular, size: 30 * scale))
                        .frame(width: 19 * scale, height: 36 * scale)
                }
            }
            .offset(y: -(frequency - 1) * 36 * scale)
            .frame(width: 19 * scale, height: 36 * scale, alignment: .top)
            .clipped()
            Text("Hz").font(WaveFieldPadFonts.font(.medium, size: 30 * scale))
        }
        .foregroundStyle(Color.wfpInk)
        .position(x: 201 * scale, y: 170 * scale)
        .accessibilityHidden(true)
        VStack(spacing: 0) {
            Text("Deep Delta").frame(height: 20 * scale)
            Text("Delta").frame(height: 20 * scale)
        }
        .font(WaveFieldPadFonts.font(.regular, size: 15 * scale))
        .foregroundStyle(Color.wfpInk2)
        .offset(y: -band*band*(3-2*band) * 20 * scale)
        .frame(height: 20 * scale, alignment: .top)
        .clipped()
        .position(x: 201 * scale, y: 204 * scale)
        .accessibilityHidden(true)
        Text("Shape tonight's mix")
            .font(WaveFieldPadFonts.font(.medium, size: 22 * scale))
            .foregroundStyle(Color.wfpInk)
            .position(x: 201 * scale, y: 268 * scale)
        Text("Slide the ember to balance sounds and waves.")
            .font(WaveFieldPadFonts.font(.regular, size: 13 * scale))
            .foregroundStyle(Color.wfpMute)
            .lineLimit(1)
            .position(x: 201 * scale, y: 298 * scale)
    }

    private func tabs(scale: CGFloat) -> some View {
        let tabs = ["Tune mix", "Sounds", "Timers"]
        return HStack(spacing: 10 * scale) {
            ForEach(tabs, id: \.self) { title in
                let index = tabs.firstIndex(of: title) ?? 0
                Button { selectedTab = index; live.selectionEvents += 1 } label: {
                    HStack(spacing: 7 * scale) {
                        WaveFieldPadGlyph(index: index)
                            .stroke(style: StrokeStyle(lineWidth: 2 * scale, lineCap: .round, lineJoin: .round))
                            .frame(width: 18 * scale, height: 18 * scale)
                            .accessibilityHidden(true)
                        Text(title)
                    }
                        .font(WaveFieldPadFonts.font(.medium, size: 14 * scale))
                        .foregroundStyle(index == selectedTab ? Color.wfpTabOnInk : Color.wfpTabOffInk)
                        .frame(width: (index == 0 ? 134 : 112) * scale, height: 44 * scale)
                        .background(index == selectedTab ? Color.wfpTabOn : Color.wfpTabOff, in: Capsule())
                }
                .buttonStyle(WaveFieldPadTabStyle())
                .accessibilityAddTraits(index == selectedTab ? .isSelected : [])
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Sound settings")
        .position(x: 201 * scale, y: 758 * scale)
    }

    private func padTouch(scale: CGFloat, now: Date) -> some View {
        let pad = WaveFieldPadMetrics.pad(scale: scale)
        let frame = shownFrame(at: now)
        let mix = Int((min(max(frame.u, 0), 1) * 100).rounded())
        let intensity = Int((min(max(frame.v, 0), 1) * 100).rounded())
        return Color.clear
            .frame(width: pad.width, height: pad.height)
            .contentShape(RoundedRectangle(cornerRadius: 28 * scale, style: .continuous))
            .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .named(WaveFieldPadMetrics.sceneSpace))
                .updating($padDragging) { _, active, _ in active = true }
                .onChanged { gesture in
                    let date = Date()
                    captureAutoplay(at: date)
                    advance(to: date)
                    let local = CGPoint(x: gesture.location.x - pad.minX, y: gesture.location.y - pad.minY)
                    let raw = WaveFieldPadMetrics.rawUV(fromPadPoint: local, scale: scale)
                    let point = CGPoint(x: raw.u, y: raw.v)
                    if live.motion != .drag { live.down(at: point) } else { live.move(to: point) }
                }
                .onEnded { gesture in
                    let date = Date()
                    advance(to: date)
                    live.release(velocity: CGVector(dx: gesture.velocity.width/(scale*266), dy: -gesture.velocity.height/(scale*266)))
                })
            .simultaneousGesture(TapGesture(count: 2).onEnded { recenter() })
            .accessibilityElement()
            .accessibilityLabel("Mix and intensity pad")
            .accessibilityValue("\(mix)% waves, \(intensity)% intensity, \(Int(frame.hz.rounded())) Hertz")
            .accessibilityHint("Adjust the mix, or choose an action to adjust intensity or recenter.")
            .accessibilityAdjustableAction { direction in adjustMix(direction == .increment ? 0.1 : -0.1, intensity: 0) }
            .accessibilityAction(named: "Increase intensity") { adjustMix(0, intensity: 0.1) }
            .accessibilityAction(named: "Decrease intensity") { adjustMix(0, intensity: -0.1) }
            .accessibilityAction(named: "Recenter mix") { recenter() }
            .position(x: pad.midX, y: pad.midY)
    }

    private func adjustMix(_ mix: Double, intensity: Double) {
        captureAutoplay(at: Date())
        live.adjust(mix: mix, intensity: intensity)
    }

    private func recenter() {
        captureAutoplay(at: Date())
        homePulse += 1; live.home()
    }

    private func restartLoop() {
        let date = Date()
        loopStart = date; lastTick = date; beatOffset = 0
        live = WaveFieldPadLive(); live.setReduced(reduceMotion)
        mode = autoPlay && !reduceMotion && !voiceOver ? .automatic : .manual
    }

}

struct WaveFieldPadFrame {
    let u: Double
    let v: Double
    let dentU: Double
    let dentV: Double
    let press: Double
    let settle: Double
    let ring: Double
    let lock: Double
    var beat: Double
    let ripple: (CGPoint, Double)?

    var hz: Double { min(max(1 + 6.9 * ((min(max(u, 0), 1) + min(max(v, 0), 1)) / 2 - 0.5), 0.5), 4.2) }
    struct Ripple {
        var origin: CGPoint
        var age: Double
        var amplitude: Double
        var slot = 0
    }
    var ripples: [Ripple] = []
    var time = 0.0
    var velocity = CGVector.zero
    var reduced = false
    var scaleOverride: Double? = nil
    var haloOverride: Double? = nil
    var flash = 0.0
    var puckScale: Double { scaleOverride ?? (1 - 0.06 * press) }
    var halo: Double { haloOverride ?? (0.38 + 0.17 * press + 0.06 * (1 - cos(2 * .pi * 6 * time / 9)) * (1 - press)) }
    var wall: CGPoint {
        CGPoint(x: (max(0, u - 1) - max(0, -u)) * 266 * 0.8,
                y: (max(0, -v) - max(0, v - 1)) * 266 * 0.8)
    }
    var localPuck: CGPoint { CGPoint(x: 44 + u * 266, y: 44 + (1 - v) * 266) }

    static func frame(at raw: Double, sampled: Bool = true) -> Self {
        let t = wrap(raw)
        let point: (Double, Double)
        if t < 0.42 { point = (0.5, 0.5) }
        else if t < 1.9 { point = curve(from: (0.5, 0.5), control: (0.58, 0.8), to: (0.8, 0.78), at: segment(t, 0.42, 1.9)) }
        else if t < 3.3 { point = (0.8, 0.78) }
        else if t < 4.6 { point = curve(from: (0.8, 0.78), control: (0.6, 0.36), to: (0.22, 0.24), at: segment(t, 3.3, 4.6)) }
        else if t < 5.4 { point = (0.22, 0.24) }
        else if t < 5.75 { let k = 1 - pow(1 - segment(t, 5.4, 5.75), 3); point = (0.22 + 0.84 * k, 0.24 + 0.31 * k) }
        else if t < 6.8 { point = springPoint(t - 5.75, from: (1.06, 0.55), to: (0.9, 0.56), damping: 15.1, stiffness: 93.4) }
        else if t <= 8.2 { point = springPoint(t - 6.8, from: springPoint(1.05, from: (1.06, 0.55), to: (0.9, 0.56), damping: 15.1, stiffness: 93.4), to: (0.5, 0.5), damping: 19, stiffness: 195) }
        else { point = (0.5, 0.5) }
        let shown = WaveFieldPadMetrics.banded(u: point.0, v: point.1)
        let settle: Double
        if t < 1.9 { settle = 0 }
        else if t < 2.8 { settle = bezier(0.3, 0.35, 0.25, 1, segment(t, 1.9, 2.8)) }
        else if t < 3.3 { settle = 1 }
        else if t < 3.6 { settle = 1 - bezier(0.32, 0, 0.67, 0, segment(t, 3.3, 3.6)) }
        else if t < 4.6 { settle = 0 }
        else if t < 5.3 { settle = bezier(0.3, 0.35, 0.25, 1, segment(t, 4.6, 5.3)) }
        else if t < 5.55 { settle = 1 - bezier(0.32, 0, 0.67, 0, segment(t, 5.4, 5.55)) }
        else if t < 7.7 { settle = 0 }
        else if t < 8.4 { settle = bezier(0.3, 0.35, 0.25, 1, segment(t, 7.7, 8.4)) }
        else if t < 8.7 { settle = 1 - bezier(0.32, 0, 0.67, 0, segment(t, 8.4, 8.7)) }
        else { settle = 0 }
        let press: Double
        if t < 0.3 { press = 0 }
        else if t < 0.42 { press = easeOut(segment(t, 0.3, 0.42)) }
        else if t < 1.9 { press = 1 }
        else if t < 2.2 { press = 1 - easeOut(segment(t, 1.9, 2.2)) }
        else if t < 3.18 { press = 0 }
        else if t < 3.3 { press = easeOut(segment(t, 3.18, 3.3)) }
        else if t < 4.6 { press = 1 }
        else if t < 4.9 { press = 1 - easeOut(segment(t, 4.6, 4.9)) }
        else if t < 5.28 { press = 0 }
        else if t < 5.4 { press = easeOut(segment(t, 5.28, 5.4)) }
        else if t < 5.52 { press = 1 - easeOut(segment(t, 5.4, 5.52)) }
        else { press = 0 }
        let ring = t > 2.8 && t < 3.3 ? segment(t, 2.8, 3.3) : 0
        let lock: Double
        if t>=2.8 && t<3.6 { lock = easeOut(segment(t,2.8,2.88)) * (1-bezier(0.32,0,0.67,0,segment(t,3.3,3.45))) }
        else if t>=5.2 && t<5.6 { lock = easeOut(segment(t,5.2,5.3)) * (1-bezier(0.32,0,0.67,0,segment(t,5.4,5.5))) }
        else if t>=8.2 && t<8.7 { lock = easeOut(segment(t,8.2,8.3)) * (1-bezier(0.32,0,0.67,0,segment(t,8.4,8.55))) }
        else { lock = 0 }
        var result = Self(u: shown.u, v: shown.v,
                    dentU: sampled ? sample(tables.u, t) : shown.u,
                    dentV: sampled ? sample(tables.v, t) : shown.v,
                    press: press, settle: settle, ring: ring, lock: lock,
                    beat: sampled ? sample(tables.beat, t) : 0, ripple: nil)
        result.time = t
        let humWindow = max(
            t > 1.9 && t < 3.3 ? sin(.pi * segment(t, 1.9, 3.3)) : 0,
            max(
                t > 4.6 && t < 5.4 ? sin(.pi * segment(t, 4.6, 5.4)) : 0,
                t > 7.7 && t < 8.6 ? sin(.pi * segment(t, 7.7, 8.6)) : 0
            )
        )
        let blip = t >= 6.8 && t < 7.16 ? 0.08 * sin(.pi * segment(t, 6.8, 7.16)) : 0
        let hum: Double = 0.012 * humWindow * sin(2 * Double.pi * 3 * t)
        result.scaleOverride = 1 - 0.06 * press + blip + hum
        result.haloOverride = result.halo + 0.07 * humWindow
        result.flash = ring > 0 ? sin(.pi * ring) : 0
        if sampled {
            let prior = frame(at: t - 1.0/60, sampled: false)
            result.velocity = CGVector(dx: (result.u - prior.u) * 266 * 60, dy: -(result.v - prior.v) * 266 * 60)
            let wallTime = 5.4 + 0.35 * (1 - pow(1 - (1 - 0.22)/0.84, 1.0/3))
            let wallV = 0.24 + 0.31 * ((1 - 0.22)/0.84)
            for (slot, start, origin, amplitude) in [(0, wallTime, CGPoint(x: 354, y: 44 + (1-wallV)*266), 10.0),
                                              (1, 6.8, CGPoint(x: 44 + 0.9*266, y: 44 + 0.44*266), 8.0),
                                              (1, 8.4, CGPoint(x: 177, y: 177), 8.0)] {
                let age = t - start
                if age >= 0 && age < 0.6 { result.ripples.append(.init(origin: origin, age: age, amplitude: amplitude * (1 - easeOut(age/0.6)), slot: slot)) }
            }
        }
        return result
    }

    private static let tables: (u: [Double], v: [Double], beat: [Double]) = {
        var us = [Double](), vs = [Double](), beats = [0.0]
        var x = 0.5, y = 0.5, vx = 0.0, vy = 0.0, acc = 0.0
        for pass in 0..<2 {
            for i in 0...1800 {
                if pass == 1 { us.append(x); vs.append(y) }
                if i == 1800 { break }
                if pass == 1 {
                    acc += frame(at: (Double(i) + 0.5) * 0.005, sampled: false).hz * 0.005
                    beats.append(acc)
                }
                for j in 0..<5 {
                    let p = frame(at: Double(i) * 0.005 + Double(j) * 0.001, sampled: false)
                    vx += (-110 * (x - p.u) - 11 * vx) * 0.001
                    vy += (-110 * (y - p.v) - 11 * vy) * 0.001
                    x += vx * 0.001; y += vy * 0.001
                }
            }
        }
        let whole = 2 * (acc / 2).rounded()
        return (us, vs, beats.map { $0 * whole / acc })
    }()

    private static func sample(_ table: [Double], _ t: Double) -> Double {
        let f = wrap(t) / 0.005, i = min(Int(f), 1799), k = f - Double(i)
        return table[i] + (table[i + 1] - table[i]) * k
    }

    private static func wrap(_ t: Double) -> Double { (t.truncatingRemainder(dividingBy: WaveFieldPad.loop) + WaveFieldPad.loop).truncatingRemainder(dividingBy: WaveFieldPad.loop) }
    private static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    private static func smooth(_ x: Double) -> Double { x * x * (3 - 2 * x) }
    private static func easeOut(_ x: Double) -> Double { bezier(0.2, 0.8, 0.2, 1, x) }
    static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ k: Double) -> Double {
        if k <= 0 { return 0 }; if k >= 1 { return 1 }
        var t = k
        for _ in 0..<6 {
            let x = 3 * (1-t) * (1-t) * t * x1 + 3 * (1-t) * t * t * x2 + t*t*t - k
            let dx = 3 * (1-t) * (1-t) * x1 + 6 * (1-t) * t * (x2-x1) + 3*t*t*(1-x2)
            if abs(dx) < 0.000001 { break }
            t = min(max(t - x/dx, 0), 1)
        }
        return 3 * (1-t) * (1-t) * t * y1 + 3 * (1-t) * t * t * y2 + t*t*t
    }
    private static func curve(from a: (Double, Double), control c: (Double, Double), to b: (Double, Double), at x: Double) -> (Double, Double) {
        let q = bezier(0.65, 0, 0.35, 1, x), m = 1 - q
        return (m * m * a.0 + 2 * m * q * c.0 + q * q * b.0, m * m * a.1 + 2 * m * q * c.1 + q * q * b.1)
    }
    private static func springPoint(_ t: Double, from: (Double, Double), to: (Double, Double), damping: Double, stiffness: Double) -> (Double, Double) {
        (spring(t, from.0, to.0, damping, stiffness), spring(t, from.1, to.1, damping, stiffness))
    }
    static func spring(_ t: Double, _ from: Double, _ to: Double, _ damping: Double, _ stiffness: Double, _ velocity: Double = 0) -> Double {
        guard t > 0 else { return from }
        let w = sqrt(stiffness), z = damping / (2 * w), wd = w * sqrt(max(0.001, 1 - z * z)), x = from - to
        return to + exp(-z * w * t) * (x * cos(wd * t) + (velocity + z * w * x) / wd * sin(wd * t))
    }
}

private struct WaveFieldPadBackground: View {
    let frame: WaveFieldPadFrame
    let scale: CGFloat
    let size: CGSize
    var body: some View {
        let k0 = min(max((frame.dentU - 0.42) / 0.38, 0), 1)
        let k = k0*k0*(3-2*k0)
        Rectangle().fill(.white)
            .colorEffect(ShaderLibrary.waveFieldPadPage(
                .float(Float(scale)), .float2(201, 180),
                .float2(Float(-20 + 442*k), Float(356 + 44 + (1-frame.dentV)*266)),
                .float(1), .float(356), .float(710)))
        .frame(width: size.width, height: size.height)
    }
}

private struct WaveFieldPadWave: View {
    let frame: WaveFieldPadFrame
    var body: some View {
        Canvas { context, size in
            let s = size.width / 402
            let span = 342 * s, left = 30 * s, centerY = 120 * s
            let v = min(max(frame.dentV, 0), 1)
            let amplitude = (v >= 0.5 ? 14 + 78.6 * (v - 0.5) : 14 + 23 * (v - 0.5)) * s
            for strand in [-2, 2, -1, 1, 0] {
                var path = Path(); var open = false
                for i in 0...96 {
                    let q = CGFloat(i) / 96, gap = abs(q - 0.5)
                    if gap < 0.105 { open = false; continue }
                    let taper0 = min(max((gap - 0.105)/0.1, 0), 1)
                    let taper = taper0*taper0*(3-2*taper0)
                    let envelope = pow(sin(CGFloat.pi*q),1.3)*taper
                    let mix = min(max(frame.dentU,0),1)
                    let baseSpread = mix>=0.5 ? 6+26.7*(mix-0.5) : 6+14.3*(mix-0.5)
                    let spread = (baseSpread+(3-baseSpread)*frame.settle)*(1-frame.lock)
                    let phase = CGFloat(strand)*spread/90 - 0.5*frame.beat
                    let pulse = cos(2*CGFloat.pi*frame.beat)
                    let travelling = envelope*(0.78+0.22*pulse)*sin(2*CGFloat.pi*((2.6+0.35*frame.hz)*q+phase))
                    let standing = taper*pulse*sin(CGFloat.pi*CGFloat(max(1,Int((1+3*frame.u).rounded())))*q)
                    let y = centerY + amplitude*((1-frame.lock)*travelling+frame.lock*standing)*(1-0.1*CGFloat(abs(strand)))
                    let point = CGPoint(x: left + q * span, y: y)
                    if open { path.addLine(to: point) } else { path.move(to: point); open = true }
                }
                context.stroke(path, with: .color(Color.wfpTeal.opacity([ -2: 0.14, 2: 0.22, -1: 0.32, 1: 0.48, 0: 1.0 ][strand] ?? 1)), style: StrokeStyle(lineWidth: (strand == 0 ? 2.5 : 1.5) * s, lineCap: .round))
            }
            let loops = max(1, Int((1+3*min(max(frame.u,0),1)).rounded()))
            for j in 1...3 where j < loops {
                let q = Double(j)/Double(loops)
                if abs(q-0.5) <= 0.135 { continue }
                let point = CGPoint(x:left+q*span,y:centerY)
                var glow = context; glow.opacity = frame.flash*frame.lock
                glow.addFilter(.blur(radius:5*s))
                glow.fill(Path(ellipseIn:CGRect(x:point.x-9*s,y:point.y-9*s,width:18*s,height:18*s)),with:.color(Color(hex:0xFFC978).opacity(0.45)))
                var node = context; node.opacity = frame.flash*frame.lock
                node.fill(Path(ellipseIn:CGRect(x:point.x-3.5*s,y:point.y-3.5*s,width:7*s,height:7*s)),with:.color(Color(hex:0xFFE7BD)))
            }
        }
    }
}

private struct WaveFieldPadSurface: View {
    let frame: WaveFieldPadFrame
    let scale: CGFloat
    let now: Date

    @Environment(\.displayScale) private var displayScale

    private func rippleArgument(_ index: Int) -> Shader.Argument {
        guard let ripple = frame.ripples.first(where: { $0.slot == index }) else { return .float4(0, 0, 9, 0) }
        return .float4(Float(ripple.origin.x), Float(ripple.origin.y), Float(ripple.age), Float(ripple.amplitude))
    }

    private var padShader: Shader {
        let puck = frame.localPuck
        let du = 44 + frame.dentU*266, dv = 44 + (1-frame.dentV)*266
        let u = min(max(frame.u, 0), 1), v = min(max(frame.v, 0), 1)
        let light = 0.85 + 0.15 * min(max((frame.halo-0.38)/0.17, 0), 1)
        return ShaderLibrary.waveFieldPadSurface(
            .float(Float(scale)), .float4(24, 356, 354, 354), .float(28),
            .float(Float(1/(scale*displayScale))),
            .float2(Float(puck.x), Float(puck.y)), .float(Float(44*frame.puckScale)),
            .float2(Float(du), Float(dv)), .float(Float(16+6*frame.press)),
            rippleArgument(0), rippleArgument(1), .float(Float(frame.settle)),
            .float2(Float(1+3*u), Float(2+3*u+2*v)), .float(Float(frame.ring)),
            .float2(Float(frame.wall.x), Float(frame.wall.y)),
            .float2(frame.reduced ? 0 : Float(-0.3*(frame.u-0.5)*266), frame.reduced ? 0 : Float(0.3*(frame.v-0.5)*266)),
            .float2(Float(frame.velocity.dx), Float(frame.velocity.dy)),
            .float(frame.reduced ? 0 : Float(Int(floor(frame.time*30)) % 270)),
            .float(14), .float(56), .float(Float(light)), .float(frame.reduced ? 0 : 26))
    }

    var body: some View {
        ZStack {
            Rectangle().fill(.white).colorEffect(padShader)
            Canvas { context, _ in
                let pad = WaveFieldPadMetrics.pad(scale: scale)
                let puck = WaveFieldPadMetrics.padPoint(u: frame.u, v: frame.v, scale: scale, origin: pad.origin)
                labels(&context, pad: pad)
                drawPuck(&context, at: puck, scale: scale)
            }
        }
        .allowsHitTesting(false)
    }

    private func drawPuck(_ context: inout GraphicsContext, at point: CGPoint, scale: CGFloat) {
        let r = 44 * scale * frame.puckScale
        let halo = 100 * scale
        var glow = context; glow.opacity = frame.halo / 0.55
        let haloGradient = Gradient(stops:[.init(color:.wfpHalo.opacity(0.55),location:0),.init(color:.wfpHalo.opacity(0.55),location:0.44),.init(color:.wfpHalo.opacity(0.18),location:0.72),.init(color:.clear,location:1)])
        glow.fill(Path(ellipseIn:CGRect(x:point.x-halo,y:point.y-halo,width:2*halo,height:2*halo)),with:.radialGradient(haloGradient,center:point,startRadius:0,endRadius:halo))
        var shadow = context; shadow.addFilter(.blur(radius:8*scale))
        shadow.fill(Path(ellipseIn:CGRect(x:point.x-r,y:point.y-r+3*scale,width:2*r,height:2*r)),with:.color(.black.opacity(0.35)))
        let lx = (frame.dentU-frame.u)*266*scale, ly = -(frame.dentV-frame.v)*266*scale
        let lag = hypot(lx,ly), k = lag>0.001 ? min(lag,4*scale)/lag : 0
        let core = CGPoint(x:point.x-0.18*r+lx*k,y:point.y-0.2*r+ly*k)
        let dome = Path(ellipseIn:CGRect(x:point.x-r,y:point.y-r,width:2*r,height:2*r))
        let body = Gradient(stops:[.init(color:.wfpEmberCore,location:0),.init(color:.wfpEmberCore,location:0.16),.init(color:.wfpEmber,location:0.5),.init(color:Color(hex:0xEE7A1A),location:0.84),.init(color:.wfpEmberDeep,location:1)])
        context.fill(dome,with:.radialGradient(body,center:core,startRadius:0,endRadius:r*1.2))
        context.stroke(dome,with:.color(Color.wfpRim.opacity(0.6)),lineWidth:1.5*scale)
        context.stroke(dome,with:.linearGradient(Gradient(colors:[Color(hex:0xFFE2B0).opacity(0.6),.clear]),startPoint:CGPoint(x:point.x-r,y:point.y-r),endPoint:CGPoint(x:point.x+0.4*r,y:point.y+0.4*r)),lineWidth:1.5*scale)
        var spec = context
        spec.translateBy(x:point.x-0.4*r,y:point.y-0.52*r); spec.rotate(by:.degrees(-38)); spec.addFilter(.blur(radius:0.8*scale))
        spec.fill(Path(ellipseIn:CGRect(x:-10*scale,y:-4*scale,width:20*scale,height:8*scale)),with:.linearGradient(Gradient(colors:[.white.opacity(0.55),.white.opacity(0.08)]),startPoint:CGPoint(x:0,y:-4*scale),endPoint:CGPoint(x:0,y:4*scale)))
        let arm = 8*scale*frame.puckScale
        var plus = Path(); plus.move(to:CGPoint(x:point.x-arm,y:point.y));plus.addLine(to:CGPoint(x:point.x+arm,y:point.y));plus.move(to:CGPoint(x:point.x,y:point.y-arm));plus.addLine(to:CGPoint(x:point.x,y:point.y+arm))
        context.stroke(plus,with:.color(.wfpPlus),style:StrokeStyle(lineWidth:2*scale,lineCap:.round))
    }

    private func labels(_ parent: inout GraphicsContext, pad: CGRect) {
        let font = WaveFieldPadFonts.font(.medium, size: 13 * scale)
        let puck = WaveFieldPadMetrics.padPoint(u: frame.u, v: frame.v, scale: scale, origin: pad.origin)
        let labels: [(String, CGPoint, Double)] = [
            ("Intense", CGPoint(x: pad.midX, y: pad.minY + 22 * scale), 0),
            ("Easy", CGPoint(x: pad.midX, y: pad.maxY - 22 * scale), 0),
            ("Sounds", CGPoint(x: pad.minX + 22 * scale, y: pad.midY), -90),
            ("Waves", CGPoint(x: pad.maxX - 22 * scale, y: pad.midY), 90)
        ]
        for (title, point, angle) in labels {
            var ink = parent
            let distance = hypot(point.x-puck.x, point.y-puck.y)/scale
            let k = min(max((distance-38)/28,0),1)
            ink.opacity = k*k*(3-2*k)
            ink.addFilter(.shadow(color:.black.opacity(0.8),radius:3*scale))
            ink.translateBy(x:point.x,y:point.y); ink.rotate(by:.degrees(angle))
            ink.draw(Text(title).font(font).foregroundColor(.wfpInk),at:.zero)
        }
    }

}

enum WaveFieldPadMetrics {
    static let sceneSpace = "WaveFieldPadScene"
    static let width: CGFloat = 402, padX: CGFloat = 24, padY: CGFloat = 356, puckRadius: CGFloat = 44, range: CGFloat = 266
    static func pad(scale: CGFloat) -> CGRect {
        let origin = CGPoint(x: padX * scale, y: padY * scale)
        let side: CGFloat = 354 * scale
        return CGRect(origin: origin, size: CGSize(width: side, height: side))
    }
    static func rawUV(fromPadPoint p: CGPoint, scale: CGFloat) -> (u: Double, v: Double) {
        ((Double(p.x / scale - puckRadius) / Double(range)), 1 - (Double(p.y / scale - puckRadius) / Double(range)))
    }
    /// Design-point limit along each wall normal; a diagonal corner may reach 14 * sqrt(2).
    static let maximumOvershoot = 14.0
    static func band(_ raw: Double) -> Double {
        if raw > 1 { return 1 + maximumOvershoot * tanh((raw - 1) * Double(range) / maximumOvershoot) / Double(range) }
        if raw < 0 { return -maximumOvershoot * tanh(-raw * Double(range) / maximumOvershoot) / Double(range) }
        return raw
    }
    /// Saturation loses raw precision. Epsilon protects only the inverse, never the displayed curve.
    static func unband(_ shown: Double) -> Double {
        let over = shown > 1 ? (shown - 1) * Double(range) : shown < 0 ? -shown * Double(range) : 0
        guard over > 0 else { return shown }
        let q = min(over / maximumOvershoot, 1 - 1e-12)
        let rawOver = maximumOvershoot * 0.5 * (log1p(q) - log1p(-q)) / Double(range)
        return shown > 1 ? 1 + rawOver : -rawOver
    }
    static func banded(u: Double, v: Double) -> (u: Double, v: Double) { (band(u), band(v)) }
    static func padPoint(u: Double, v: Double, scale: CGFloat, origin: CGPoint? = nil) -> CGPoint {
        let o = origin ?? CGPoint(x: padX * scale, y: padY * scale)
        return CGPoint(x: o.x + (puckRadius + CGFloat(u) * range) * scale, y: o.y + (puckRadius + (1 - CGFloat(v)) * range) * scale)
    }
}

private struct WaveFieldPadTabStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label.scaleEffect(configuration.isPressed ? 0.96 : 1) }
}

private enum WaveFieldPadFonts {
    static func font(_ face: WaveFieldPadFace, size: CGFloat) -> Font { .custom(face.name, fixedSize: size) }
    static func cgFont(size: CGFloat) -> Font { font(.regular, size: size) }
    static func register() { _ = registered }
    private static let registered: Bool = {
        ["WaveFieldPad-Outfit-Regular", "WaveFieldPad-Outfit-Medium", "WaveFieldPad-OutfitTabular-Medium"].forEach {
            if let url = Bundle.main.url(forResource: $0, withExtension: "ttf") { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
        }
        return true
    }()
}

private enum WaveFieldPadFace {
    case regular, medium, tabular
    var name: String { switch self { case .regular: "Outfit-Regular"; case .medium: "Outfit-Medium"; case .tabular: "OutfitTabular-Medium" } }
}

private extension Color {
    static let wfpPage = Color(hex: 0x1A1F1B), wfpPageLow = Color(hex: 0x151216), wfpPageDeep = Color(hex: 0x131115), wfpLift = Color(hex: 0x22302A), wfpAmb = Color(hex: 0x271C0E)
    static let wfpPad = Color(hex: 0x232227), wfpEmber = Color(hex: 0xF79A2E), wfpEmberCore = Color(hex: 0xF4D28C), wfpEmberDeep = Color(hex: 0xC85A14), wfpEmberLight = Color(hex: 0xFFB25A), wfpRim = Color(hex: 0xFFC07A), wfpHalo = Color(hex: 0xF0801E), wfpPlus = Color(hex: 0xFFF3DC), wfpSand = Color(hex: 0xF6E2BE)
    static let wfpInk = Color(hex: 0xF2F4F1), wfpInk2 = Color(hex: 0xE3E7E2), wfpMute = Color(hex: 0x7D857D), wfpTeal = Color(hex: 0x3FD6A8), wfpTabOn = Color(hex: 0xF6F6F4), wfpTabOnInk = Color(hex: 0x17161A), wfpTabOff = Color(hex: 0x1F1C23), wfpTabOffInk = Color(hex: 0xE6E6E4)
    init(hex: UInt32) { self.init(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255) }
}
