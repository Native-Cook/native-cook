import SwiftUI
import UIKit

private enum ElasticFoodCardsMath {
    static func clamp(_ x: Double, _ a: Double = 0, _ b: Double = 1) -> Double { min(b, max(a, x)) }
    static func mix(_ a: Double, _ b: Double, _ u: Double) -> Double { a + (b - a) * u }
    static func smooth(_ x: Double) -> Double { let u = clamp(x); return u * u * (3 - 2 * u) }
    static func smoother(_ x: Double) -> Double { let u = clamp(x); return u * u * u * (10 + u * (-15 + 6 * u)) }
    static func smootherSlope(_ x: Double) -> Double { let u = clamp(x); return 30 * u * u * (1 - u) * (1 - u) }
    /// Linear through the measured hover range, with C2 joins into the two flat limits.
    static func softClamp(_ x: Double) -> Double {
        let u = clamp(x), edge = 0.15
        func end(_ t: Double) -> Double { t * t * t * (6 + t * (-8 + 3 * t)) }
        if u < edge { return edge * end(u / edge) }
        if u > 1 - edge { return 1 - edge * end((1 - u) / edge) }
        return u
    }
    static func color(_ value: UInt32) -> Color {
        Color(.sRGB, red: Double((value >> 16) & 255) / 255, green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255, opacity: 1)
    }
    static func rotated(_ x: Double, _ y: Double, _ degrees: Double) -> (Double, Double) {
        let a = degrees * .pi / 180
        return (x * cos(a) - y * sin(a), x * sin(a) + y * cos(a))
    }
    /// Position and velocity of a unit-mass underdamped follower after a retarget.
    static func oscillator(_ x: Double, _ v: Double, target: Double, time: Double,
                           damping z: Double, omega w: Double) -> (Double, Double) {
        guard time > 0 else { return (x, v) }
        let a = z * w, d = w * sqrt(1 - z * z), b = (v + a * (x - target)) / d
        let c = cos(d * time), s = sin(d * time), e = exp(-a * time)
        let f = (x - target) * c + b * s
        return (target + e * f, e * (-a * f - (x - target) * d * s + b * d * c))
    }
    static func hermite(_ x: Double, _ v: Double, _ y: Double, _ w: Double,
                        elapsed: Double, duration: Double) -> (Double, Double) {
        let u = clamp(elapsed / duration), u2 = u * u, u3 = u2 * u
        let position = (2 * u3 - 3 * u2 + 1) * x + (u3 - 2 * u2 + u) * duration * v
            + (-2 * u3 + 3 * u2) * y + (u3 - u2) * duration * w
        let velocity = ((6 * u2 - 6 * u) * x + (3 * u2 - 4 * u + 1) * duration * v
            + (-6 * u2 + 6 * u) * y + (3 * u2 - 2 * u) * duration * w) / duration
        return (position, velocity)
    }
    static func rubber(_ q: Double) -> Double {
        if q >= 0 && q <= 1 { return q }
        let edge = q < 0 ? 0.0 : 1.0, over = (q - edge) * 148
        return edge + 0.55 * over / (1 + abs(over) / 120) / 148
    }
    static func automatic(_ time: Double) -> ElasticFoodCardsFrame { ElasticFoodCardsMotion.frame(time) }
}

private struct ElasticFoodCardsFrame {
    var q = [Double](repeating: 0, count: 6)
    var qVel = [Double](repeating: 0, count: 6)
    var handBX = 198.0
    var handBY = -90.0
    var handAngle = 0.0
    var handAlpha = 0.0
    var grip = 0.0
    var gatherX = 190.0
    var gatherY = -90.0
    var reliefAmplitude = 0.0
    var fanWidth = 0.0
    var tailY = 120.0
    // Same order as values; velocities are retained across every interruption.
    var scalarVel = [Double](repeating: 0, count: 10)
    var values: [Double] {
        get { [handBX, handBY, handAngle, handAlpha, grip, gatherX, gatherY, reliefAmplitude, fanWidth, tailY] }
        set {
            handBX = newValue[0]; handBY = newValue[1]; handAngle = newValue[2]; handAlpha = newValue[3]
            grip = newValue[4]; gatherX = newValue[5]; gatherY = newValue[6]
            reliefAmplitude = newValue[7]; fanWidth = newValue[8]; tailY = newValue[9]
        }
    }
    func mixed(_ other: Self, _ u: Double) -> Self {
        var f = self
        f.q = zip(q, other.q).map { ElasticFoodCardsMath.mix($0, $1, u) }
        f.qVel = zip(qVel, other.qVel).map { ElasticFoodCardsMath.mix($0, $1, u) }
        f.values = zip(values, other.values).map { ElasticFoodCardsMath.mix($0, $1, u) }
        f.scalarVel = zip(scalarVel, other.scalarVel).map { ElasticFoodCardsMath.mix($0, $1, u) }
        return f
    }
    func bridged(to other: Self, elapsed: Double, duration: Double) -> Self {
        var f = self
        for i in 0..<6 {
            let pair = ElasticFoodCardsMath.hermite(q[i], qVel[i], other.q[i], other.qVel[i], elapsed: elapsed, duration: duration)
            f.q[i] = pair.0; f.qVel[i] = pair.1
        }
        var values = self.values
        for i in 0..<10 {
            let pair = ElasticFoodCardsMath.hermite(values[i], scalarVel[i], other.values[i], other.scalarVel[i], elapsed: elapsed, duration: duration)
            values[i] = pair.0; f.scalarVel[i] = pair.1
        }
        f.values = values
        return f
    }
    static func open(_ q: Double, angle: Double = 0) -> Self {
        var f = Self(); f.q = [Double](repeating: q, count: 6)
        f.handBX = 204; f.handBY = -90 + 286 * q; f.handAngle = angle
        f.handAlpha = ElasticFoodCardsMath.smoother((f.handBY - 20) / 26)
        let contact = ElasticFoodCardsMath.rotated(4, 16, angle)
        f.gatherX = f.handBX - contact.0; f.gatherY = f.handBY - contact.1; f.tailY = 263
        return f
    }
}

private enum ElasticFoodCardsMotion {
    // Track order: five bands; hand Bx/By/angle; closure; gather x/y; relief depth; fan width.
    // Keys are [seconds,value,derivative/second]: measured PCHIP bands/material, periodic C2 hand curves.
    static let tracks: [[[Double]]] = {
        guard let url = Bundle.main.url(forResource: "ElasticFoodCards-motion", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let keys = try? JSONDecoder().decode([[[Double]]].self, from: data), keys.count == 13 else { return [] }
        return keys
    }()
    static func sample(_ keys: [[Double]], _ t: Double) -> (Double, Double) {
        guard keys.count > 1 else { return (0, 0) }
        if t <= keys[0][0] { return (keys[0][1], keys[0][2]) }
        if t >= keys[keys.count - 1][0] { let k = keys[keys.count - 1]; return (k[1], k[2]) }
        var lo = 0, hi = keys.count - 1
        while hi - lo > 1 { let m = (lo + hi) / 2; if keys[m][0] <= t { lo = m } else { hi = m } }
        let a = keys[lo], b = keys[hi]
        return ElasticFoodCardsMath.hermite(a[1], a[2], b[1], b[2], elapsed: t - a[0], duration: b[0] - a[0])
    }
    static func pchip(_ points: [[Double]]) -> [[Double]] {
        var out = points.map { [$0[0], $0[1], 0.0] }
        for i in 1..<(points.count - 1) {
            let h0 = points[i][0] - points[i - 1][0], h1 = points[i + 1][0] - points[i][0]
            let a = (points[i][1] - points[i - 1][1]) / h0, b = (points[i + 1][1] - points[i][1]) / h1
            if a * b > 0 { let w0 = 2 * h1 + h0, w1 = h1 + 2 * h0; out[i][2] = (w0 + w1) / (w0 / a + w1 / b) }
        }
        return out
    }
    static let tail = pchip([[0,263],[0.8,263],[0.9,260],[1,255],[1.1,251],[1.2,253],[1.3,255],[1.4,205],[1.5,160],[1.6,120],[3.4,120],[3.8,263],[16.0/3,263]])
    static func source(_ time: Double) -> ElasticFoodCardsFrame {
        var native = (time - 2.2).truncatingRemainder(dividingBy: ElasticFoodCards.loop)
        if native < 0 { native += ElasticFoodCards.loop }
        return frame(native)
    }
    static func frame(_ time: Double) -> ElasticFoodCardsFrame {
        let t = max(0, time).truncatingRemainder(dividingBy: ElasticFoodCards.loop)
        guard tracks.count == 13 else { return .init() }
        let samples = tracks.map { sample($0, t) }
        var f = ElasticFoodCardsFrame()
        f.q = (0..<5).map { samples[$0].0 }; f.q.append(f.q[4])
        f.qVel = (0..<5).map { samples[$0].1 }; f.qVel.append(f.qVel[4])
        f.handBX = samples[5].0; f.handBY = samples[6].0; f.handAngle = samples[7].0
        f.grip = samples[8].0; f.gatherX = samples[9].0; f.gatherY = samples[10].0
        f.reliefAmplitude = samples[11].0; f.fanWidth = samples[12].0
        f.scalarVel[0] = samples[5].1; f.scalarVel[1] = samples[6].1; f.scalarVel[2] = samples[7].1
        f.scalarVel[4] = samples[8].1; f.scalarVel[5] = samples[9].1; f.scalarVel[6] = samples[10].1
        f.scalarVel[7] = samples[11].1; f.scalarVel[8] = samples[12].1
        // During the last15% of finger closure, lock the visible fingertip landmark to the gather.
        let u = ElasticFoodCardsMath.clamp((f.grip - 0.85) / 0.15), lock = ElasticFoodCardsMath.smoother(u)
        let lockV = ElasticFoodCardsMath.smootherSlope(u) * f.scalarVel[4] / 0.15
        let r = ElasticFoodCardsMath.rotated(4, 16, f.handAngle)
        let angleV = f.scalarVel[2] * .pi / 180
        let bx = f.gatherX + r.0, by = f.gatherY + r.1
        let bxV = f.scalarVel[5] - r.1 * angleV, byV = f.scalarVel[6] + r.0 * angleV
        f.scalarVel[0] = (1 - lock) * f.scalarVel[0] + lock * bxV + lockV * (bx - f.handBX)
        f.scalarVel[1] = (1 - lock) * f.scalarVel[1] + lock * byV + lockV * (by - f.handBY)
        f.handBX = ElasticFoodCardsMath.mix(f.handBX, bx, lock); f.handBY = ElasticFoodCardsMath.mix(f.handBY, by, lock)
        let visibility = ElasticFoodCardsMath.clamp((f.handBY - 20) / 26)
        f.handAlpha = ElasticFoodCardsMath.smoother(visibility)
        f.scalarVel[3] = ElasticFoodCardsMath.smootherSlope(visibility) / 26 * f.scalarVel[1]
        let sourceT = (t + 2.2).truncatingRemainder(dividingBy: ElasticFoodCards.loop)
        let reach = sample(tail, sourceT); f.tailY = reach.0; f.scalarVel[9] = reach.1
        return f
    }
    static func recoveryPhase(_ origin: ElasticFoodCardsFrame) -> Double {
        0.55 + 0.25 * ElasticFoodCardsMath.clamp(origin.grip)
    }
    static func recoveryDuration(_ origin: ElasticFoodCardsFrame) -> Double { 0.2 + 2.2 - recoveryPhase(origin) }
    static func recovery(_ elapsed: Double, origin: ElasticFoodCardsFrame) -> ElasticFoodCardsFrame {
        let phase = recoveryPhase(origin), first = source(phase), duration = recoveryDuration(origin)
        if elapsed >= duration { return frame(0) }
        func mapped(_ sourceTime: Double) -> ElasticFoodCardsFrame {
            var f = source(sourceTime)
            for i in 0..<6 { f.q[i] *= origin.q[i]; f.qVel[i] *= origin.q[i] }
            let amplitude = ElasticFoodCardsMath.clamp(max(origin.q[0], origin.reliefAmplitude / 24), 0, 1.2)
            f.reliefAmplitude *= amplitude; f.scalarVel[7] *= amplitude
            // Relative motion preserves hidden/partial hands; no hand re-enters just to recover.
            let u = ElasticFoodCardsMath.clamp((sourceTime - 1.6) / 0.6)
            let fade = 1 - (u * u * u * (10 + u * (-15 + 6 * u)))
            let fadeV = u > 0 && u < 1 ? -30 * u * u * (1 - u) * (1 - u) / 0.6 : 0
            let dx = origin.handBX - first.handBX, dy = min(0, origin.handBY - first.handBY)
            f.handBX += dx * fade; f.handBY += dy * fade
            f.gatherX += dx * fade; f.gatherY += dy * fade
            f.scalarVel[0] += dx * fadeV; f.scalarVel[1] += dy * fadeV
            f.scalarVel[5] += dx * fadeV; f.scalarVel[6] += dy * fadeV
            let startBY = min(origin.handBY, first.handBY)
            let startVisibility = ElasticFoodCardsMath.smoother((startBY - 20) / 26)
            let uVisibility = ElasticFoodCardsMath.clamp((f.handBY - 20) / 26)
            let visibility = ElasticFoodCardsMath.smoother(uVisibility)
            let visibilityV = ElasticFoodCardsMath.smootherSlope(uVisibility) / 26 * f.scalarVel[1]
            let ratio = startVisibility > 0.00001 ? ElasticFoodCardsMath.clamp(visibility / startVisibility) : 0
            f.handAlpha = origin.handAlpha * ElasticFoodCardsMath.smoother(ratio)
            f.scalarVel[3] = ratio > 0 && ratio < 1 ? origin.handAlpha * ElasticFoodCardsMath.smootherSlope(ratio) * visibilityV / startVisibility : 0
            return f
        }
        let target = mapped(phase)
        if elapsed < 0.2 { return origin.bridged(to: target, elapsed: elapsed, duration: 0.2) }
        return mapped(phase + elapsed - 0.2)
    }
}

private struct ElasticFoodCardsTargetEvent {
    var time: Double
    var target: ElasticFoodCardsFrame
    var expanding: Bool
    var scalarTarget: [Double]
}

/// Retarget events are only written by input. Every displayed position/velocity is analytic.
private struct ElasticFoodCardsDrive {
    var initial: ElasticFoodCardsFrame
    var origin: Double
    var events: [ElasticFoodCardsTargetEvent] = []
    func frame(_ now: Double) -> ElasticFoodCardsFrame {
        var result = initial
        let initialValues = initial.values
        var resultValues = initialValues
        for i in 0..<16 {
            let isBand = i < 6, j = isBand ? i : i - 6
            var x = isBand ? initial.q[j] : initialValues[j]
            var v = isBand ? initial.qVel[j] : initial.scalarVel[j]
            var target = x, date = origin, z = 0.69, omega = 5.0
            for event in events {
                let delay = isBand ? Double(min(j, 4)) * (event.expanding ? 1.0 / 30 : 2.0 / 30) : 0
                let arrival = event.time + delay
                if arrival > now { break }
                let pair = ElasticFoodCardsMath.oscillator(x, v, target: target, time: max(0, arrival - date), damping: z, omega: omega)
                x = pair.0; v = pair.1; date = arrival
                target = isBand ? event.target.q[j] : event.scalarTarget[j]
                z = event.expanding ? 0.69 : 0.64; omega = event.expanding ? 5 : 7.1
            }
            let pair = ElasticFoodCardsMath.oscillator(x, v, target: target, time: max(0, now - date), damping: z, omega: omega)
            if isBand { result.q[j] = pair.0; result.qVel[j] = pair.1 }
            else { resultValues[j] = pair.0; result.scalarVel[j] = pair.1 }
        }
        result.values = resultValues
        result.q[5] = result.q[4]; result.qVel[5] = result.qVel[4]
        return result
    }
    mutating func append(_ target: ElasticFoodCardsFrame, at time: Double, expanding: Bool) {
        if events.count >= 96 { initial = frame(time); origin = time; events.removeAll(keepingCapacity: true) }
        events.append(.init(time: time, target: target, expanding: expanding, scalarTarget: target.values))
    }
}

private enum ElasticFoodCardsArt {
    static let names = ["ramen", "rolls", "nigiri", "miso", "hand", "hand-half", "hand-fist", "promo", "avatar"]
    static let images: [String: UIImage] = Dictionary(uniqueKeysWithValues: names.compactMap { name in
        guard let image = UIImage(named: "ElasticFoodCards-\(name).png") else { return nil }
        return (name, image.preparingForDisplay() ?? image)
    })
    static let forearms: [String: UIImage] = Dictionary(uniqueKeysWithValues: ["hand", "hand-half", "hand-fist"].compactMap { name in
        guard let source = images[name]?.cgImage,
              let strip = source.cropping(to: CGRect(x: 0, y: 200, width: source.width, height: 1)) else { return nil }
        let image = UIImage(cgImage: strip)
        return (name, image.preparingForDisplay() ?? image)
    })
    static func image(_ name: String) -> Image {
        if let image = images[name] { return Image(uiImage: image) }
        return Image(uiImage: UIImage())
    }
    static func forearm(_ name: String) -> Image { Image(uiImage: forearms[name] ?? UIImage()) }
}

private struct ElasticFoodCardsStar: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let r = min(rect.width, rect.height) * 0.5
        for i in 0..<10 {
            let angle = -Double.pi * 0.5 + Double(i) * .pi / 5
            let radius = i.isMultiple(of: 2) ? r : r * 4 / 8.5
            let point = CGPoint(x: rect.midX + cos(angle) * radius, y: rect.midY + sin(angle) * radius)
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }
}

private struct ElasticFoodCardsScrollOffset: PreferenceKey {
    static var defaultValue = 0.0
    static func reduce(value: inout Double, nextValue: () -> Double) { value = nextValue() }
}

/// A self-contained food screen whose refresh hand pinches the header into fabric.
struct ElasticFoodCards: View {
    static let loop = 16.0 / 3.0
    var autoPlay = false
    var previewTime: Double? = nil

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var dynamicType
    @State private var windowTopInset = 0.0
    @State private var epoch = Date()
    @State private var visible = false
    @State private var held = false
    @State private var heroPan = false
    @State private var panStart = ElasticFoodCardsFrame()
    @State private var drive: ElasticFoodCardsDrive?
    @State private var inputSettled = true
    @State private var settleTask: Task<Void, Never>?
    @State private var lastTouch = Date()
    @State private var detentPlayed = false
    @State private var scrollOffset = 0.0
    @State private var serial = 0
    @State private var resetTask: Task<Void, Never>?
    @State private var scrollReset = 0
    @State private var saved: Set<Int> = []
    @State private var selectedCategory = 0
    @State private var searchPresented = false
    @State private var searchSnapshot: ElasticFoodCardsFrame?
    @State private var searchQuery = ""
    @State private var searchDraft = ""
    @GestureState private var panActive = false

    private let page = ElasticFoodCardsMath.color(0xDFDFDD)
    private let card = ElasticFoodCardsMath.color(0xE8E8E6)
    private let primary = ElasticFoodCardsMath.color(0x101110)
    private let secondary = ElasticFoodCardsMath.color(0x8F9290)
    private let names = ["Harbor Ramen", "Sunrise Rolls", "Salmon Bites", "Garden Miso"]
    private let prices = ["$ 15.00", "$ 9.50", "$ 7.00", "$ 4.50"]
    private let ratings = ["4.5", "4.8", "5.0", "4.3"]
    private var accessibilityLayout: Bool { dynamicType.isAccessibilitySize }
    private var cardExtraHeight: Double { accessibilityLayout ? 44 : 0 }
    private var clockPaused: Bool {
        !visible || previewTime != nil || reduced || scenePhase != .active || searchPresented || searchSnapshot != nil
            || (held && inputSettled) || (!autoPlay && drive == nil)
    }

    private func frame(_ now: Date) -> ElasticFoodCardsFrame {
        if reduced { return .init() }
        if let previewTime { return ElasticFoodCardsMath.automatic(previewTime) }
        if let searchSnapshot { return searchSnapshot }
        guard let drive else { return ElasticFoodCardsMath.automatic(autoPlay ? now.timeIntervalSince(epoch) : 0) }
        let current = drive.frame(now.timeIntervalSinceReferenceDate)
        if held || searchPresented { return current }
        let idle = now.timeIntervalSince(lastTouch) - 2
        if idle <= 0 { return current }
        let origin = drive.frame(lastTouch.timeIntervalSinceReferenceDate + 2)
        let duration = ElasticFoodCardsMotion.recoveryDuration(origin)
        if idle < duration { return ElasticFoodCardsMotion.recovery(idle, origin: origin) }
        return ElasticFoodCardsMath.automatic(autoPlay ? idle - duration : 0)
    }

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            let height = geometry.size.height
            let scale = width / 393
            let baseY = max(0, (height - 852 * scale) * 0.5)
            TimelineView(.animation(minimumInterval: 1.0 / 120, paused: clockPaused)) { timeline in
                let current = frame(timeline.date)
                ZStack(alignment: .topLeading) {
                    page
                    screen(current, bottomInset: geometry.safeAreaInsets.bottom / scale,
                           clipTop: windowTopInset >= 44 ? max(0, (windowTopInset - 14 - baseY) / scale) : 0)
                        .frame(width: 393, height: 852, alignment: .topLeading)
                        .scaleEffect(scale, anchor: .topLeading)
                        .offset(y: baseY)
                }
                .frame(width: width, height: height, alignment: .topLeading)
                .contentShape(Rectangle())
                .clipped()
                .allowsHitTesting(previewTime == nil)
                .simultaneousGesture(pan(scale: scale, baseY: baseY))
            }
        }
        .background(page)
        .background { ElasticFoodCardsWindowInset { windowTopInset = $0 } }
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .onAppear { visible = true; resetLifecycle() }
        .onDisappear { visible = false; held = false; resetTask?.cancel(); settleTask?.cancel() }
        .onChange(of: scenePhase) { _, phase in
            resetTask?.cancel()
            held = false
            if phase == .active { resetLifecycle() }
        }
        .onChange(of: reduced) { _, _ in resetLifecycle() }
        .onChange(of: autoPlay) { _, _ in resetLifecycle() }
        .onChange(of: previewTime) { _, _ in resetLifecycle() }
        .onChange(of: panActive) { wasActive, active in
            if wasActive && !active && held {
                let ticket = serial
                Task { @MainActor in
                    await Task.yield()
                    if held && ticket == serial { finishPan(velocity: 0) }
                }
            }
        }
        .alert("Search food", isPresented: $searchPresented) {
            TextField("Food or ingredients", text: $searchDraft)
            Button("Search") {
                searchQuery = searchDraft
                let snapshot = searchSnapshot; searchSnapshot = nil
                registerAction(captured: snapshot); selectedCategory = 0
            }
            Button("Cancel", role: .cancel) {
                let snapshot = searchSnapshot; searchSnapshot = nil
                registerAction(captured: snapshot)
            }
        }
        .accessibilityAction(named: "Refresh food") { refreshFood() }
    }

    private func screen(_ f: ElasticFoodCardsFrame, bottomInset: Double, clipTop: Double) -> some View {
        ZStack(alignment: .topLeading) {
            ElasticFoodCardsSurface(anchor: CGPoint(x: f.gatherX, y: f.gatherY), amplitude: f.reliefAmplitude, fanWidth: f.fanWidth, tailY: f.tailY, contact: f.grip)
                .frame(width: 393, height: 852, alignment: .topLeading)
                .accessibilityHidden(true)
            hand(f, inlet: CGPoint(x: 196.5, y: clipTop - 8), clipTop: clipTop).accessibilityHidden(true)
            header(f).offset(y: 148 * f.q[0])
            title.offset(y: 148 * f.q[1])
            categories.offset(y: 148 * f.q[2])
            promo.offset(y: 148 * f.q[3])
            grid(f, bottomInset: bottomInset)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private func hand(_ f: ElasticFoodCardsFrame, inlet: CGPoint, clipTop: Double) -> some View {
        let closure = ElasticFoodCardsMath.clamp(f.grip)
        let bx = f.handBX, by = f.handBY
        let hover = ElasticFoodCardsMath.softClamp((194 - by) / 10)
        let lift = ElasticFoodCardsMath.smoother((180 - by) / 140)
            * ElasticFoodCardsMath.smoother((closure - 0.65) / 0.35)
        let castX = ElasticFoodCardsMath.mix(4 * (2 * ElasticFoodCardsMath.softClamp((f.handAngle / 24 + 1) / 2) - 1), 3, closure)
        let castY = ElasticFoodCardsMath.mix(12 + 32 * hover, 18, closure) + 9 * lift
        let castBlur = ElasticFoodCardsMath.mix(6 + 6 * hover, 7, closure) + 4 * lift
        let castAlpha = ElasticFoodCardsMath.mix(0.20 + 0.02 * hover, 0.26, closure) * (1 - 0.28 * lift)
        return ZStack(alignment: .topLeading) {
            // The same anchored arm ribbon and hand cast onto a bounded upper-page layer.
            handPoses(f, bx: bx, by: by, inlet: inlet, width: 393, height: 480).colorMultiply(.black)
                .clipped().blur(radius: castBlur)
                .offset(x: castX, y: castY)
                .opacity(castAlpha)
            Ellipse().fill(Color.black.opacity((0.10 + 0.06 * closure) * (1 - lift)))
                .frame(width: (54 - 14 * closure) * (1 - 0.25 * lift), height: (18 - 6 * closure) * (1 - 0.35 * lift))
                .blur(radius: 5 - 2 * closure + 1.5 * lift)
                .position(x: bx, y: by + 5 - closure)
            handPoses(f, bx: bx, by: by, inlet: inlet)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .compositingGroup()
        .opacity(ElasticFoodCardsMath.clamp(f.handAlpha))
        .mask(alignment: .topLeading) { Rectangle().frame(width: 393, height: 852 - clipTop).offset(y: clipTop) }
        .allowsHitTesting(false)
    }

    private func handPoses(_ f: ElasticFoodCardsFrame, bx: Double, by: Double, inlet: CGPoint, width: Double = 393, height: Double = 852) -> some View {
        let closure = ElasticFoodCardsMath.clamp(f.grip)
        let first = ElasticFoodCardsMath.smoother(2 * closure), second = ElasticFoodCardsMath.smoother(2 * closure - 1)
        let weights = [1 - first, first - second, second]
        let names = ["hand", "hand-half", "hand-fist"]
        let anchors = [CGPoint(x: 137, y: 363), CGPoint(x: 138, y: 344), CGPoint(x: 132, y: 344)]
        let shaftCenters = [138.5, 140.0, 133.0]
        return ZStack(alignment: .topLeading) {
                ForEach(0..<3, id: \.self) { index in
                    if weights[index] > 0 {
                    let p = anchors[index]
                    let center = ElasticFoodCardsMath.rotated(0.47 * (125 - p.x), 0.47 * (200 - p.y), f.handAngle)
                    let wrist = ElasticFoodCardsMath.rotated(0.47 * (125 - p.x), 0.47 * (202 - p.y), f.handAngle)
                    ZStack(alignment: .topLeading) {
                        // Center the visible shaft alpha at the inlet, rather than its transparent PNG frame.
                        armRibbon(ElasticFoodCardsArt.forearm(names[index]),
                                  from: CGPoint(x: inlet.x - 0.47 * (shaftCenters[index] - 125), y: inlet.y),
                                  to: CGPoint(x: bx + wrist.0, y: by + wrist.1), angle: f.handAngle)
                        ElasticFoodCardsArt.image(names[index]).resizable().interpolation(.high)
                            .frame(width: 117.5, height: 188)
                            .mask(alignment: .top) { Rectangle().frame(height: 166 * 0.47).offset(y: 199 * 0.47) }
                            .rotationEffect(.degrees(f.handAngle))
                            .position(x: bx + center.0, y: by + center.1)
                    }
                    .frame(width: width, height: height, alignment: .topLeading)
                    .compositingGroup()
                    .opacity(weights[index]).blendMode(.plusLighter)
                    }
                }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .compositingGroup()
    }

    /// A short textured ribbon keeps its upper end inside the real hardware cutout.
    private func armRibbon(_ image: Image, from a: CGPoint, to w: CGPoint, angle: Double) -> some View {
        Canvas { context, _ in
            guard w.y > a.y else { return }
            let radians = angle * .pi / 180
            let tangent = CGPoint(x: -sin(radians), y: cos(radians))
            let reach = hypot(w.x - a.x, w.y - a.y) * 0.34
            let c1 = CGPoint(x: a.x, y: a.y + reach)
            let c2 = CGPoint(x: w.x - tangent.x * reach, y: w.y - tangent.y * reach)
            func edge(_ t: Double, _ side: Double) -> CGPoint {
                let u = 1 - t
                let x = u*u*u*a.x + 3*u*u*t*c1.x + 3*u*t*t*c2.x + t*t*t*w.x
                let y = u*u*u*a.y + 3*u*u*t*c1.y + 3*u*t*t*c2.y + t*t*t*w.y
                let dx = 3*u*u*(c1.x-a.x) + 6*u*t*(c2.x-c1.x) + 3*t*t*(w.x-c2.x)
                let dy = 3*u*u*(c1.y-a.y) + 6*u*t*(c2.y-c1.y) + 3*t*t*(w.y-c2.y)
                let length = max(0.001, hypot(dx, dy))
                return CGPoint(x: x + side * 58.75 * dy / length, y: y - side * 58.75 * dx / length)
            }
            func triangle(_ p0: CGPoint, _ p1: CGPoint, _ p2: CGPoint, _ second: Bool) {
                var c = context
                var path = Path(); path.move(to: p0); path.addLine(to: p1); path.addLine(to: p2); path.closeSubpath()
                c.clip(to: path, style: FillStyle(antialiased: false))
                let m: CGAffineTransform
                if !second {
                    m = CGAffineTransform(a: (p1.x-p0.x)/250, b: (p1.y-p0.y)/250,
                                          c: (p2.x-p0.x)/2, d: (p2.y-p0.y)/2, tx: p0.x, ty: p0.y)
                } else {
                    let ax = (p1.x-p2.x)/250, ay = (p1.y-p2.y)/250
                    m = CGAffineTransform(a: ax, b: ay, c: (p1.x-p0.x)/2, d: (p1.y-p0.y)/2,
                                          tx: p0.x - ax*250, ty: p0.y - ay*250)
                }
                // Extend the constant-row texture beyond each triangle to avoid filtered edge seams.
                c.concatenate(m); c.draw(image, in: CGRect(x: 0, y: -1, width: 250, height: 4))
            }
            for i in 0..<8 {
                let t0 = Double(i)/8, t1 = Double(i+1)/8
                let l0 = edge(t0, -1), r0 = edge(t0, 1), l1 = edge(t1, -1), r1 = edge(t1, 1)
                triangle(l0, r0, l1, false); triangle(r0, r1, l1, true)
            }
        }
        .opacity(ElasticFoodCardsMath.smoother((w.y - a.y) / 16))
        .allowsHitTesting(false)
    }

    private func text(_ string: String, _ size: Double, _ color: Color, _ rect: CGRect,
                      weight: Font.Weight = .regular, alignment: Alignment = .leading) -> some View {
        Text(string).font(.system(size: size, weight: weight)).foregroundStyle(color)
            .lineLimit(1).frame(width: rect.width, height: rect.height, alignment: alignment)
            .position(x: rect.midX, y: rect.midY)
    }

    private func header(_ f: ElasticFoodCardsFrame) -> some View {
        ZStack(alignment: .topLeading) {
            Button { registerAction(); searchDraft = searchQuery; searchSnapshot = frame(Date()); searchPresented = true } label: {
                Canvas { context, _ in
                    var p = Path(ellipseIn: CGRect(x: 8.5, y: 8.5, width: 23, height: 23))
                    p.move(to: CGPoint(x: 28, y: 29)); p.addLine(to: CGPoint(x: 37, y: 38))
                    context.stroke(p, with: .color(ElasticFoodCardsMath.color(0x7B817D)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                }.frame(width: 44, height: 44)
            }.buttonStyle(.plain).frame(width: 44, height: 44).position(x: 44, y: 88)
                .accessibilityLabel("Search food")
            VStack(spacing: 1) {
                Text("location:").font(.system(size: 13)).foregroundStyle(secondary).frame(height: 18)
                Text("Fable Bay").font(.system(size: 17, weight: .medium)).foregroundStyle(primary).frame(height: 22)
            }.frame(width: 172, height: 41).position(x: 196, y: 89.5)
                .accessibilityElement(children: .ignore).accessibilityLabel("Delivery location, Fable Bay")
            ElasticFoodCardsArt.image("avatar").resizable().scaledToFill().frame(width: 32, height: 32).clipShape(Circle())
                .position(x: 349, y: 89).accessibilityHidden(true)
            Circle().fill(ElasticFoodCardsMath.color(0xCA5E67)).frame(width: 6.4, height: 6.4)
                .position(x: 363, y: 74).accessibilityHidden(true)
        }.frame(width: 393, height: 852, alignment: .topLeading)
    }

    private var title: some View {
        ZStack(alignment: .topLeading) {
            Text("Popular Food").font(.system(size: 28)).tracking(-0.4).foregroundStyle(primary)
                .frame(width: 270, height: 36, alignment: .leading).position(x: 151, y: 153)
            Button { registerAction(); selectedCategory = 0; searchQuery = ""; scrollReset += 1 } label: {
                Text("view all").font(.system(size: 17)).foregroundStyle(secondary)
                    .frame(width: 84, height: 44, alignment: .trailing)
            }.buttonStyle(.plain).position(x: 335, y: 154.5).accessibilityLabel("View all food")
        }.frame(width: 393, height: 852, alignment: .topLeading)
    }

    private var categories: some View {
        let items: [(String, String, Double, Double)] = [("Ramen", "miso", 16, 117), ("Sushi", "nigiri", 139, 108), ("Rolls", "rolls", 253, 104), ("Soup", "miso", 363, 104)]
        return ZStack(alignment: .topLeading) {
            ForEach(items.indices, id: \.self) { i in
                let item = items[i]
                Button {
                    registerAction(); selectedCategory = i
                } label: {
                    ZStack(alignment: .topLeading) {
                        RoundedRectangle(cornerRadius: 24).fill(ElasticFoodCardsMath.color(0xE5E5E3))
                            .overlay(RoundedRectangle(cornerRadius: 24).stroke(primary.opacity(selectedCategory == i && i != 0 ? 0.12 : 0), lineWidth: 0.8))
                        ElasticFoodCardsArt.image(item.1).resizable().scaledToFit().frame(width: i == 0 ? 22 : 24, height: 20)
                            .position(x: 25, y: 22)
                        Text(item.0).font(.system(size: 18)).foregroundStyle(primary).lineLimit(1)
                            .frame(width: item.3 - 48, height: 23, alignment: .leading).position(x: 47 + (item.3 - 48) / 2, y: 23.5)
                    }.frame(width: item.3, height: 45)
                }.buttonStyle(.plain).position(x: item.2 + item.3 / 2, y: 216.5)
                    .accessibilityAddTraits(selectedCategory == i ? .isSelected : [])
            }
        }.frame(width: 393, height: 852, alignment: .topLeading)
    }

    private var promo: some View {
        ZStack(alignment: .topLeading) {
            ZStack {
                ElasticFoodCardsMath.color(0x060707)
                ElasticFoodCardsArt.image("promo").resizable().scaledToFill().frame(width: 363, height: 121).clipped()
                LinearGradient(stops: [.init(color: .clear, location: 145 / 363), .init(color: .black.opacity(0.65), location: 182 / 363), .init(color: .black, location: 210 / 363)], startPoint: .leading, endPoint: .trailing)
            }.frame(width: 363, height: 121).clipShape(RoundedRectangle(cornerRadius: 34))
                .position(x: 197.5, y: 314.5)
            Text("Discount 50%").font(.system(size: 23)).tracking(-0.3).foregroundStyle(ElasticFoodCardsMath.color(0xE0E0DE))
                .frame(width: 162, height: 31, alignment: .leading).position(x: 288, y: 305.5)
            text("learn more...", 13, secondary, CGRect(x: 207, y: 319, width: 155, height: 18))
        }.frame(width: 393, height: 852, alignment: .topLeading)
            .accessibilityElement(children: .ignore).accessibilityLabel("Discount 50 percent")
    }

    private func grid(_ f: ElasticFoodCardsFrame, bottomInset: Double) -> some View {
        let desiredTop = min(381, 389 + 148 * min(0, f.q[4]))
        let clipTop = max(desiredTop, min(381, 379 + 148 * f.q[3]))
        let topPadding = 389 - clipTop
        return ScrollViewReader { reader in
            ScrollView(.vertical, showsIndicators: false) {
                ZStack(alignment: .topLeading) {
                    GeometryReader { g in
                        Color.clear.preference(key: ElasticFoodCardsScrollOffset.self, value: -Double(g.frame(in: .named("ElasticFoodCardsScroll")).minY))
                    }.frame(height: 1).id("ElasticFoodCardsTop")
                    ForEach(0..<4, id: \.self) { index in
                        let row = index / 2
                        let x = index.isMultiple(of: 2) ? 16.0 : 203.0
                        let y = topPadding + Double(row) * (238 + cardExtraHeight) + 148 * f.q[row + 4]
                        foodCard(index).position(x: x + 88.5, y: y + (225 + cardExtraHeight) / 2)
                    }
                }.frame(width: 393, height: 463 + topPadding + cardExtraHeight * 2 + max(0, 148 * f.q[5]) + bottomInset, alignment: .topLeading)
            }
            .coordinateSpace(name: "ElasticFoodCardsScroll")
            .scrollDisabled(f.q[0] > 0.02 || (held && heroPan))
            .onPreferenceChange(ElasticFoodCardsScrollOffset.self) { offset in
                if abs(offset - scrollOffset) > 0.1 { scrollOffset = offset }
            }
            .onChange(of: scrollReset) { _, _ in reader.scrollTo("ElasticFoodCardsTop", anchor: .top) }
        }.frame(width: 393, height: 852 - clipTop, alignment: .topLeading).position(x: 196.5, y: (852 + clipTop) / 2)
    }

    private func foodCard(_ index: Int) -> some View {
        let product = ["ramen", "rolls", "nigiri", "miso"][index]
        let height = 225 + cardExtraHeight
        // Alpha margins differ between the original generated product renders.
        let imageRects = [CGRect(x: 19, y: 52.35, width: 140, height: 74.3), CGRect(x: 21.5, y: 54.75, width: 137, height: 68.5), CGRect(x: 20, y: 45.25, width: 139, height: 92.5), CGRect(x: 27.5, y: 45, width: 124, height: 93)]
        let art = imageRects[index]
        let matches = searchQuery.isEmpty || names[index].localizedCaseInsensitiveContains(searchQuery)
        let categoryMatches = selectedCategory == 0 || (selectedCategory == 1 && index == 2) || (selectedCategory == 2 && index == 1) || (selectedCategory == 3 && index == 3)
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 31).fill(card)
                .shadow(color: .black.opacity(0.025), radius: 18, x: 0, y: 9)
                .shadow(color: .black.opacity(0.04), radius: 3, x: 0, y: 1.5)
            ElasticFoodCardsArt.image(product).resizable().scaledToFit().frame(width: art.width, height: art.height)
                .position(x: art.midX, y: art.midY).accessibilityHidden(true)
            Text(names[index]).font(.system(size: accessibilityLayout ? 20 : 17)).foregroundStyle(primary)
                .lineLimit(accessibilityLayout ? 2 : 1).frame(width: 151, height: 26 + cardExtraHeight, alignment: .leading)
                .position(x: 89.5, y: 159 + cardExtraHeight / 2)
            text(prices[index], accessibilityLayout ? 19 : 16, primary,
                 CGRect(x: 14, y: 179 + cardExtraHeight, width: 84, height: 25))
            ElasticFoodCardsStar().fill(ElasticFoodCardsMath.color(0xE8B14B)).frame(width: 17, height: 17)
                .position(x: 123, y: 191 + cardExtraHeight).accessibilityHidden(true)
            text(ratings[index], accessibilityLayout ? 18 : 15, secondary,
                 CGRect(x: 136, y: 179 + cardExtraHeight, width: 32, height: 25))
            Button { toggleSaved(index) } label: {
                Image(systemName: saved.contains(index) ? "heart.fill" : "heart")
                    .font(.system(size: 19, weight: .ultraLight)).foregroundStyle(primary)
                    .frame(width: 44, height: 44)
            }.buttonStyle(.plain).position(x: 23.5, y: 29.5)
                .accessibilityLabel("\(saved.contains(index) ? "Unsave" : "Save") \(names[index])")
                .accessibilityValue(saved.contains(index) ? "Saved" : "Not saved")
        }.frame(width: 177, height: height, alignment: .topLeading)
            .opacity(matches && categoryMatches ? 1 : 0.32)
            .accessibilityElement(children: .contain)
            .accessibilityLabel("\(names[index]), \(prices[index]), rated \(ratings[index]) out of 5")
            .accessibilityAction(named: saved.contains(index) ? "Unsave food" : "Save food") { toggleSaved(index) }
    }

    private func toggleSaved(_ index: Int) {
        registerAction()
        withAnimation(.easeInOut(duration: reduced ? 0 : 0.12)) {
            if saved.contains(index) { saved.remove(index) } else { saved.insert(index) }
        }
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.4)
    }

    private func refreshFood() {
        guard previewTime == nil else { return }
        registerAction()
        if !reduced {
            let now = Date(), current = frame(now)
            drive = ElasticFoodCardsDrive(initial: current, origin: now.timeIntervalSinceReferenceDate)
            drive?.append(.open(1), at: now.timeIntervalSinceReferenceDate, expanding: true)
        }
    }

    private func pan(scale: Double, baseY: Double) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .updating($panActive) { _, state, _ in state = true }
            .onChanged { value in
                guard previewTime == nil, !reduced, !searchPresented, scenePhase == .active else { return }
                let now = Date(), time = now.timeIntervalSinceReferenceDate
                if !held {
                    resetTask?.cancel(); serial += 1
                    panStart = frame(now)
                    drive = ElasticFoodCardsDrive(initial: panStart, origin: time)
                    drive?.append(panStart, at: time, expanding: true)
                    held = true; detentPlayed = panStart.q[0] >= 0.9
                    let startY = (value.startLocation.y - baseY) / scale
                    heroPan = scrollOffset <= 0.5 && startY <= 220 + 148 * max(0, panStart.q[0])
                }
                lastTouch = now; scheduleInputSettle()
                guard heroPan else { return }
                let distance = value.translation.height / scale
                let target = panStart.q[0] + ElasticFoodCardsMath.rubber(panStart.q[0] + distance / 148) - ElasticFoodCardsMath.rubber(panStart.q[0])
                let delta = target - panStart.q[0]
                let blend = ElasticFoodCardsMath.smooth(abs(distance) / 60)
                var pose = ElasticFoodCardsFrame.open(target, angle: panStart.handAngle + ElasticFoodCardsMath.clamp(value.translation.width / scale * 0.03, -6, 6))
                pose.handBX += ElasticFoodCardsMath.clamp(value.translation.width / scale * 0.04, -12, 12)
                let r = ElasticFoodCardsMath.rotated(4, 16, pose.handAngle)
                pose.gatherX = pose.handBX - r.0; pose.gatherY = pose.handBY - r.1
                var next = panStart.mixed(pose, ElasticFoodCardsMath.smooth(abs(distance) / 40))
                next.q = panStart.q.map { ElasticFoodCardsMath.mix($0 + delta, target, blend) }
                next.q[5] = next.q[4]
                drive?.append(next, at: time, expanding: true)
                if target >= 0.9 && !detentPlayed {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.45); detentPlayed = true
                }
            }
            .onEnded { value in finishPan(velocity: value.velocity.height / scale) }
    }

    private func finishPan(velocity: Double) {
        guard held else { return }
        let now = Date(), current = frame(now), time = now.timeIntervalSinceReferenceDate
        held = false; inputSettled = false; settleTask?.cancel()
        var initial = current
        initial.qVel = current.qVel.map { ElasticFoodCardsMath.clamp($0, -700 / 148, 700 / 148) }
        drive = ElasticFoodCardsDrive(initial: initial, origin: time)
        let q = current.q[0] >= 0.55 || velocity >= 650 ? 1.0 : 0.0
        let target = heroPan ? ElasticFoodCardsFrame.open(q, angle: current.handAngle) : current
        drive?.append(target, at: time, expanding: !heroPan || q >= current.q[0])
        heroPan = false; lastTouch = now
        scheduleReset()
    }

    private func registerAction(captured: ElasticFoodCardsFrame? = nil) {
        let now = Date(), current = captured ?? frame(now)
        resetTask?.cancel(); settleTask?.cancel(); serial += 1
        held = false; heroPan = false; inputSettled = false
        drive = ElasticFoodCardsDrive(initial: current, origin: now.timeIntervalSinceReferenceDate)
        drive?.append(current, at: now.timeIntervalSinceReferenceDate, expanding: true)
        lastTouch = now; scheduleReset()
    }

    private func scheduleInputSettle() {
        settleTask?.cancel(); inputSettled = false
        let ticket = serial
        settleTask = Task { @MainActor in
            do { try await Task.sleep(nanoseconds: 2_000_000_000) } catch { return }
            guard !Task.isCancelled, ticket == serial, held else { return }
            inputSettled = true
        }
    }

    private func scheduleReset() {
        resetTask?.cancel()
        guard !reduced else { return }
        let ticket = serial
        let origin = drive?.frame(lastTouch.timeIntervalSinceReferenceDate + 2) ?? .init()
        let duration = 2 + ElasticFoodCardsMotion.recoveryDuration(origin)
        resetTask = Task { @MainActor in
            do { try await Task.sleep(nanoseconds: UInt64(duration * 1_000_000_000)) } catch { return }
            guard !Task.isCancelled, ticket == serial, !held, !searchPresented, scenePhase == .active else { return }
            drive = nil; epoch = Date(); inputSettled = true
            saved = []; selectedCategory = 0; searchQuery = ""; scrollReset += 1
        }
    }

    private func resetLifecycle() {
        resetTask?.cancel(); settleTask?.cancel(); serial += 1
        held = false; heroPan = false; drive = nil; inputSettled = true
        epoch = Date(); saved = []; selectedCategory = 0; searchQuery = ""; searchDraft = ""; searchPresented = false; searchSnapshot = nil
        scrollOffset = 0; scrollReset += 1
    }

}


/// A continuous height field on the blank header. Product text and cards stay rigid above this layer.
struct ElasticFoodCardsSurface: View {
    var anchor: CGPoint
    var amplitude: Double
    var fanWidth: Double
    var tailY: Double
    var contact: Double

    var body: some View {
        Color(.sRGB, red: 223.0 / 255, green: 223.0 / 255, blue: 221.0 / 255, opacity: 1)
            .frame(width: 393, height: 300, alignment: .topLeading)
            .colorEffect(
                ShaderLibrary.elasticFoodCardsSurface(
                    .float2(Float(anchor.x), Float(anchor.y)),
                    .float(Float(min(24, max(0, amplitude)))),
                    .float(Float(min(378, max(0, fanWidth)))),
                    .float(Float(min(263, max(160, tailY)))),
                    .float(Float(min(1, max(0, contact))))
                ),
                isEnabled: amplitude > 0 && fanWidth > 0
            )
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}


private struct ElasticFoodCardsWindowInset: UIViewRepresentable {
    var changed: (Double) -> Void
    func makeUIView(context: Context) -> ElasticFoodCardsInsetView {
        let view = ElasticFoodCardsInsetView(); view.isUserInteractionEnabled = false; view.changed = changed; return view
    }
    func updateUIView(_ uiView: ElasticFoodCardsInsetView, context: Context) { uiView.changed = changed; uiView.report() }
}
private final class ElasticFoodCardsInsetView: UIView {
    var changed: ((Double) -> Void)?
    private var previous = -1.0
    override func didMoveToWindow() { super.didMoveToWindow(); report() }
    override func safeAreaInsetsDidChange() { super.safeAreaInsetsDidChange(); report() }
    func report() {
        guard let window else { return }
        let top = Double(window.safeAreaInsets.top)
        guard top != previous else { return }; previous = top
        DispatchQueue.main.async { [weak self] in self?.changed?(top) }
    }
}
